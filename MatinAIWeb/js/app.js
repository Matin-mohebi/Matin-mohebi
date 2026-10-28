/* Matin AI — web/desktop/iOS app logic (Persian-first, ZERO-CONFIG) */
"use strict";

/* ================= Constants & defaults =================
 * v1.5.0 — FULLY AUTOMATIC: no token, no signup, no settings needed.
 * Default provider is Pollinations — free anonymous tier, CORS-open
 * (Access-Control-Allow-Origin: *), OpenAI-compatible, NO API KEY required.
 * The model list is fetched live from the server (GET /models).
 */
const DEFAULTS = {
  baseUrl: "https://text.pollinations.ai",
  model: "openai",
  systemPrompt:
    "تو Matin AI هستی، دستیار شخصی فارسی‌زبان. دقیق، محترمانه و کاربردی جواب بده. " +
    "اگر کاربر انگلیسی پرسید، انگلیسی جواب بده. پاسخ‌ها را با مارک‌داون مرتب کن. " +
    "You are Matin AI, a Persian-speaking personal assistant. Be precise, polite and practical.",
};
/* Shown only if the live /models fetch fails. */
const FALLBACK_MODELS = ["openai", "openai-fast"];
/* Endpoints of services that no longer exist — settings pointing here are auto-migrated. */
const DEAD_ENDPOINTS = /models\.github\.ai|models\.inference\.ai\.azure\.com/i;
const LS_CHATS = "matin_ai_chats_v1";
const LS_SETTINGS = "matin_ai_settings_v1";

/* ================= Runtime detection =================
 * - Electron: main process proxies requests via matinai:// protocol (no CORS, full streaming)
 * - Capacitor (iOS/Android native): CapacitorHttp plugin patches fetch -> native HTTP (no CORS, no SSE)
 * - Plain browser (web/PWA): Pollinations is CORS-open -> direct calls work, no proxy needed
 */
const IS_ELECTRON = /Electron/i.test(navigator.userAgent) || !!window.matinDesktop;
const IS_CAPACITOR = !!(window.Capacitor && typeof window.Capacitor.isNativePlatform === "function" && window.Capacitor.isNativePlatform());

/* ================= State ================= */
let settings = loadSettings();
let chats = loadChats();
let activeChatId = null;
let streaming = null; // AbortController while generating

/* ================= Helpers ================= */
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s);

let settingsMigrated = false; // true when an old config was auto-fixed
function loadSettings() {
  try {
    const s = JSON.parse(localStorage.getItem(LS_SETTINGS) || "{}");
    const out = {
      apiKey: s.apiKey || "",
      baseUrl: s.baseUrl || DEFAULTS.baseUrl,
      model: s.model || DEFAULTS.model,
      proxyUrl: s.proxyUrl || "",
    };
    // v1.5.0 migration — move every legacy config to the zero-config provider:
    // 1) dead GitHub Models endpoints (retired 2026-07-30)
    // 2) OpenRouter default with an empty key (OpenRouter cannot work keyless -> Pollinations can)
    const legacyOpenRouterEmpty = !out.apiKey && /openrouter\.ai/i.test(out.baseUrl);
    if (DEAD_ENDPOINTS.test(out.baseUrl) || legacyOpenRouterEmpty) {
      out.baseUrl = DEFAULTS.baseUrl;
      out.model = DEFAULTS.model;
      settingsMigrated = true;
    }
    return out;
  } catch {
    return { apiKey: "", baseUrl: DEFAULTS.baseUrl, model: DEFAULTS.model, proxyUrl: "" };
  }
}
function saveSettings() {
  localStorage.setItem(LS_SETTINGS, JSON.stringify(settings));
  updateModelChip();
}
function loadChats() {
  try {
    const c = JSON.parse(localStorage.getItem(LS_CHATS) || "[]");
    return Array.isArray(c) ? c : [];
  } catch {
    return [];
  }
}
function persistChats() {
  try {
    localStorage.setItem(LS_CHATS, JSON.stringify(chats.slice(0, 60)));
  } catch (e) {
    console.warn("storage full", e);
  }
}
function activeChat() {
  return chats.find((c) => c.id === activeChatId) || null;
}
function newChat() {
  return { id: "c" + Date.now().toString(36), title: "", messages: [], createdAt: Date.now(), updatedAt: Date.now() };
}

/**
 * Resolve the chat-completions endpoint from any common base URL:
 *   https://text.pollinations.ai          -> .../openai                      (Pollinations, no key)
 *   https://text.pollinations.ai/openai   -> as-is
 *   https://models.github.ai              -> .../inference/chat/completions  (GitHub Models, retired)
 *   https://api.openai.com/v1             -> .../v1/chat/completions
 *   https://openrouter.ai/api/v1          -> .../v1/chat/completions
 *   anything ending in /chat/completions  -> as-is
 *   anything else                         -> .../v1/chat/completions
 */
function chatEndpoint(base) {
  let b = (base || "").trim().replace(/\/+$/, "");
  if (!b) b = DEFAULTS.baseUrl;
  if (/\/chat\/completions$/.test(b)) return b;
  if (/\/openai$/.test(b)) return b; // Pollinations chat endpoint
  if (/pollinations\.ai$/i.test(b)) return b + "/openai";
  if (/\/inference$/.test(b)) return b + "/chat/completions";
  if (/\/v1$/.test(b)) return b + "/chat/completions";
  if (/models\.github\.ai$/i.test(b)) return b + "/inference/chat/completions";
  return b + "/v1/chat/completions";
}

/**
 * Resolve the models-list endpoint (used to fill the model picker live from the server).
 *   Pollinations -> https://text.pollinations.ai/models
 *   OpenRouter   -> .../api/v1/models
 *   OpenAI       -> .../v1/models
 */
function modelsEndpoint(base) {
  let b = (base || "").trim().replace(/\/+$/, "");
  if (!b) b = DEFAULTS.baseUrl;
  if (/pollinations\.ai/i.test(b)) return "https://text.pollinations.ai/models";
  if (/openrouter\.ai/i.test(b) || /api\.openai\.com/i.test(b)) return b + "/models";
  if (/\/chat\/completions$/.test(b)) return b.replace(/\/chat\/completions$/, "/models");
  if (/\/openai$/.test(b)) return b.replace(/\/openai$/, "/models");
  return b + "/v1/models";
}

/** Only OpenRouter/OpenAI/custom endpoints need a key — Pollinations (default) does not. */
function needsKey(base) {
  return !/pollinations\.ai/i.test(base || "");
}

function isPollinations(base) {
  return /pollinations\.ai/i.test(base || "");
}

/* ================= Live model list (auto from server) ================= */
let modelList = [];              // normalized model ids from the server
const modelListCache = new Map(); // baseUrl -> ids
let modelsFetchSeq = 0;

function normalizeModels(j) {
  const arr = Array.isArray(j) ? j : (Array.isArray(j?.data) ? j.data : []);
  const ids = arr
    .map((m) => (typeof m === "string" ? m : m.name || m.id || ""))
    .filter(Boolean);
  // free/anonymous models first
  const tier = (m) => (m && typeof m === "object" && m.tier === "anonymous" ? 0 : 1);
  const withTier = arr.length && typeof arr[0] === "object"
    ? ids.map((id, i) => ({ id, t: tier(arr[i]) }))
    : ids.map((id) => ({ id, t: 1 }));
  withTier.sort((a, b) => a.t - b.t);
  return withTier.map((x) => x.id);
}

async function fetchModels(baseUrl) {
  const base = (baseUrl || settings.baseUrl).trim();
  if (modelListCache.has(base)) {
    modelList = modelListCache.get(base);
    return modelList;
  }
  const seq = ++modelsFetchSeq;
  try {
    const res = await fetch(requestUrl(modelsEndpoint(base)));
    if (!res.ok) throw new Error("HTTP " + res.status);
    const j = await res.json();
    const ids = normalizeModels(j);
    if (!ids.length) throw new Error("empty list");
    modelListCache.set(base, ids);
    if (seq !== modelsFetchSeq) return modelList; // a newer fetch superseded this one
    modelList = ids;
    return modelList;
  } catch {
    if (seq !== modelsFetchSeq) return modelList;
    modelList = isPollinations(base) ? FALLBACK_MODELS.slice() : [];
    return modelList;
  }
}

/**
 * Wrap an endpoint URL for the current runtime:
 * - Electron: matinai://<host>/<path>  (main-process proxy, CORS-free, streaming)
 * - Custom CORS proxy set in Settings:
 *     contains "{url}"  -> replaced with encodeURIComponent(endpoint)
 *     ends with "="     -> endpoint (percent-encoded) is appended
 *     otherwise         -> proxy prefix + endpoint (path-style, e.g. cors.sh)
 */
function requestUrl(endpoint) {
  if (IS_ELECTRON && /^https?:\/\//i.test(endpoint)) {
    try {
      const u = new URL(endpoint);
      return "matinai://" + u.host + u.pathname + u.search;
    } catch { /* fall through */ }
  }
  const p = (settings.proxyUrl || "").trim();
  if (!p || !/^https?:\/\//i.test(endpoint)) return endpoint;
  if (p.includes("{url}")) return p.replace("{url}", encodeURIComponent(endpoint));
  if (/=$/.test(p)) return p + encodeURIComponent(endpoint);
  return p.replace(/\/+$/, "") + "/" + endpoint;
}

/* ================= Markdown ================= */
marked.setOptions({ gfm: true, breaks: true });
function renderMarkdown(text) {
  const html = marked.parse(text || "");
  return DOMPurify.sanitize(html, { ADD_ATTR: ["target"] });
}
function enhanceCodeBlocks(container) {
  container.querySelectorAll("pre").forEach((pre) => {
    if (pre.parentElement?.classList.contains("codeblock")) return;
    const code = pre.querySelector("code");
    const langMatch = code ? (code.className.match(/language-([\w+-]+)/) || [])[1] : "";
    const wrap = document.createElement("div");
    wrap.className = "codeblock";
    const head = document.createElement("div");
    head.className = "code-head";
    head.innerHTML = `<span>${langMatch || "code"}</span>`;
    const btn = document.createElement("button");
    btn.className = "copy-code";
    btn.textContent = "Copy";
    btn.onclick = () => {
      navigator.clipboard.writeText(code?.innerText || pre.innerText).then(() => {
        btn.textContent = "Copied ✓";
        setTimeout(() => (btn.textContent = "Copy"), 1400);
      });
    };
    head.appendChild(btn);
    pre.replaceWith(wrap);
    wrap.appendChild(head);
    wrap.appendChild(pre);
  });
  container.querySelectorAll("a").forEach((a) => (a.target = "_blank"));
}

/* ================= Rendering ================= */
const messagesEl = $("messages");
const chatScroll = $("chat-scroll");

function nearBottom() {
  return chatScroll.scrollHeight - chatScroll.scrollTop - chatScroll.clientHeight < 130;
}
function scrollDown(force) {
  if (force || nearBottom()) chatScroll.scrollTop = chatScroll.scrollHeight;
}

function userBubbleEl(text) {
  const div = document.createElement("div");
  div.className = "msg-user";
  const b = document.createElement("div");
  b.className = "bubble";
  b.dir = "auto";
  b.textContent = text;
  div.appendChild(b);
  return div;
}

function aiBubbleEl() {
  const div = document.createElement("div");
  div.className = "msg-ai";
  const av = document.createElement("img");
  av.className = "avatar";
  av.src = "icons/icon-192.png";
  av.alt = "";
  const content = document.createElement("div");
  content.className = "content";
  const md = document.createElement("div");
  md.className = "md";
  md.dir = "auto";
  content.appendChild(md);
  const actions = document.createElement("div");
  actions.className = "actions";
  actions.innerHTML = `<button class="act-btn" data-act="copy">کپی · Copy</button>
    <button class="act-btn" data-act="regen">🔄 دوباره · Regenerate</button>`;
  actions.querySelector('[data-act="copy"]').onclick = () =>
    navigator.clipboard.writeText(md._raw || md.textContent);
  actions.querySelector('[data-act="regen"]').onclick = () => regenerate();
  content.appendChild(actions);
  div.appendChild(av);
  div.appendChild(content);
  return { root: div, md, actions };
}

function renderAll() {
  messagesEl.innerHTML = "";
  const chat = activeChat();
  const empty = !chat || chat.messages.length === 0;
  $("welcome").style.display = empty ? "" : "none";
  if (empty) return;
  chat.messages.forEach((m) => {
    if (m.role === "user") messagesEl.appendChild(userBubbleEl(m.content));
    else {
      const { root, md, actions } = aiBubbleEl();
      md._raw = m.content;
      md.innerHTML = renderMarkdown(m.content);
      enhanceCodeBlocks(md);
      actions.style.opacity = "1";
      messagesEl.appendChild(root);
    }
  });
  scrollDown(true);
}

/* ================= Sidebar ================= */
function renderSidebar() {
  const list = $("chat-list");
  list.innerHTML = "";
  if (!chats.length) {
    list.innerHTML = `<div class="empty-note">هنوز چتی نداری —<br>یکی شروع کن! ✨</div>`;
    return;
  }
  const label = document.createElement("div");
  label.className = "chat-list-label";
  label.textContent = "گفتگوها · Chats";
  list.appendChild(label);
  [...chats]
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .forEach((c) => {
      const item = document.createElement("div");
      item.className = "chat-item" + (c.id === activeChatId ? " active" : "");
      const t = document.createElement("span");
      t.className = "title";
      t.textContent = c.title || "گفتگوی جدید";
      const del = document.createElement("button");
      del.className = "del";
      del.textContent = "🗑";
      del.title = "حذف · Delete";
      del.onclick = (e) => {
        e.stopPropagation();
        if (!confirm("این گفتگو حذف شود؟")) return;
        chats = chats.filter((x) => x.id !== c.id);
        if (activeChatId === c.id) {
          activeChatId = chats.length ? chats[0].id : null;
        }
        persistChats();
        renderSidebar();
        renderAll();
      };
      item.appendChild(t);
      item.appendChild(del);
      item.onclick = () => {
        activeChatId = c.id;
        renderSidebar();
        renderAll();
        closeSidebarMobile();
      };
      list.appendChild(item);
    });
}

function openSidebarMobile() {
  $("sidebar").classList.remove("hidden");
  $("sidebar-backdrop").classList.add("show");
}
function closeSidebarMobile() {
  if (window.innerWidth <= 860) {
    $("sidebar").classList.add("hidden");
    $("sidebar-backdrop").classList.remove("show");
  }
}

/* ================= Settings modal ================= */
function renderModelPresets(loading) {
  const presets = $("model-presets");
  const status = $("models-status");
  presets.innerHTML = "";
  if (loading) {
    if (status) status.textContent = "⏳ در حال گرفتن لیست مدل‌ها از سرور… · fetching model list…";
    return;
  }
  if (status) status.textContent = modelList.length
    ? "✅ لیست مدل‌ها زنده از سرور گرفته شد · list fetched live from the server"
    : "";
  const source = modelList.length ? modelList : [settings.model || DEFAULTS.model];
  source.slice(0, 12).forEach((m) => {
    const b = document.createElement("button");
    b.className = "preset" + (m === settings.model ? " active" : "");
    b.textContent = m;
    b.onclick = () => {
      $("set-model").value = m;
      presets.querySelectorAll(".preset").forEach((x) => x.classList.remove("active"));
      b.classList.add("active");
    };
    presets.appendChild(b);
  });
}

async function openSettings() {
  $("set-key").value = settings.apiKey;
  $("set-url").value = settings.baseUrl;
  $("set-model").value = settings.model;
  $("set-proxy").value = settings.proxyUrl || "";
  $("settings-modal").hidden = false;
  renderModelPresets(true);
  await fetchModels($("set-url").value.trim() || DEFAULTS.baseUrl);
  renderModelPresets(false);
}
function closeSettings() {
  $("settings-modal").hidden = true;
}
function providerName(baseUrl) {
  try {
    const h = new URL(baseUrl).hostname.replace(/^www\./, "");
    if (/pollinations\.ai$/i.test(h)) return "Pollinations · بدون کلید · no key";
    if (/openrouter\.ai$/i.test(h)) return "OpenRouter";
    if (/models\.github\.ai|models\.inference\.ai\.azure\.com/i.test(h)) return "GitHub Models (retired)";
    return h;
  } catch { return "Pollinations"; }
}
function updateModelChip() {
  $("model-chip").textContent = settings.model + " · " + providerName(settings.baseUrl);
}
function ensureProviderReady() {
  // Zero-config default works with NO key. Only custom providers need one.
  if (needsKey(settings.baseUrl) && !settings.apiKey.trim()) {
    showError("سرویس انتخابی به کلید نیاز دارد — در تنظیمات کلید بگذار یا به پیش‌فرض (بدون کلید) برگرد · This provider needs an API key — add one in Settings or switch back to the keyless default.");
    openSettings();
    return false;
  }
  return true;
}

/* ================= Errors ================= */
let errTimer;
function showError(msg) {
  const bar = $("error-bar");
  bar.textContent = msg;
  bar.hidden = false;
  clearTimeout(errTimer);
  errTimer = setTimeout(() => (bar.hidden = true), 12000);
}

/* ================= Chat flow ================= */
function ensureChat() {
  let chat = activeChat();
  if (!chat) {
    chat = newChat();
    chats.push(chat);
    activeChatId = chat.id;
  }
  return chat;
}

function addMessage(role, content) {
  const chat = ensureChat();
  chat.messages.push({ role, content });
  chat.updatedAt = Date.now();
  if (role === "user" && !chat.title) {
    chat.title = content.replace(/\s+/g, " ").trim().slice(0, 34) || "گفتگوی جدید";
  }
  persistChats();
}

function authHeaders(extra) {
  // No key stored -> no Authorization header (Pollinations default needs none).
  const h = Object.assign({ "Content-Type": "application/json" }, extra || {});
  const key = (settings.apiKey || "").trim();
  if (key) h["Authorization"] = "Bearer " + key;
  if (/openrouter\.ai/i.test(settings.baseUrl)) {
    h["HTTP-Referer"] = location.origin || "https://matin-mohebi.github.io";
    h["X-Title"] = "Matin AI";
  }
  return h;
}

async function send(text) {
  text = (text || "").trim();
  if (!text || streaming) return;
  if (!ensureProviderReady()) return;
  hideError();

  const chat = ensureChat();
  $("welcome").style.display = "none";
  messagesEl.appendChild(userBubbleEl(text));
  scrollDown(true);
  addMessage("user", text);
  renderSidebar();

  await generate();
}

async function generate() {
  const chat = ensureChat();
  const { root, md, actions } = aiBubbleEl();
  md.innerHTML = `<span class="cursor-blink"></span>`;
  messagesEl.appendChild(root);
  scrollDown(true);

  const controller = new AbortController();
  streaming = controller;
  setSendingUI(true);

  let acc = "";
  try {
    const history = [{ role: "system", content: DEFAULTS.systemPrompt }];
    chat.messages.slice(-24).forEach((m) => history.push({ role: m.role, content: m.content }));

    const res = await fetch(requestUrl(chatEndpoint(settings.baseUrl)), {
      method: "POST",
      signal: controller.signal,
      headers: authHeaders(),
      body: JSON.stringify({
        model: settings.model.trim() || DEFAULTS.model,
        messages: history,
        stream: !IS_CAPACITOR, // CapacitorHttp buffers the whole response; SSE is unavailable there
      }),
    });

    if (!res.ok) {
      let detail = "";
      try {
        const j = await res.json();
        detail = j?.error?.message || "";
      } catch {}
      throw new Error(detail || `HTTP ${res.status}`);
    }

    const isStream = res.headers.get("content-type")?.includes("event-stream");
    if (isStream && res.body) {
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buf = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += decoder.decode(value, { stream: true });
        const lines = buf.split("\n");
        buf = lines.pop();
        for (const line of lines) {
          const l = line.trim();
          if (!l.startsWith("data:")) continue;
          const data = l.slice(5).trim();
          if (data === "[DONE]") continue;
          try {
            const j = JSON.parse(data);
            const delta = j.choices?.[0]?.delta?.content || "";
            if (delta) {
              acc += delta;
              md._raw = acc;
              md.innerHTML = renderMarkdown(acc) + `<span class="cursor-blink"></span>`;
              scrollDown();
            }
          } catch {}
        }
      }
    } else {
      const j = await res.json();
      acc = j.choices?.[0]?.message?.content || "پاسخی دریافت نشد · No response.";
      md._raw = acc;
      md.innerHTML = renderMarkdown(acc);
    }

    if (!acc.trim()) acc = "پاسخی دریافت نشد · No response.";
    md._raw = acc;
    md.innerHTML = renderMarkdown(acc);
    enhanceCodeBlocks(md);
    actions.style.opacity = "1";
    addMessage("assistant", acc);
    renderSidebar();
  } catch (e) {
    if (e.name === "AbortError") {
      if (acc.trim()) {
        md._raw = acc;
        md.innerHTML = renderMarkdown(acc) + "\n\n*(متوقف شد · stopped)*";
        enhanceCodeBlocks(md);
        actions.style.opacity = "1";
        addMessage("assistant", acc);
        renderSidebar();
      } else {
        root.remove();
      }
    } else {
      root.remove();
      let hint = "";
      if (e instanceof TypeError) {
        if (IS_CAPACITOR) {
          hint = " — اتصال برقرار نشد؛ اینترنت را چک کن · Network failed — check connection.";
        } else if (IS_ELECTRON) {
          hint = " — اتصال برقرار نشد؛ اینترنت را چک کن · Network failed — check connection.";
        } else {
          hint = " — اتصال برقرار نشد. سرویس پیش‌فرض (Pollinations) از مرورگر مستقیم کار می‌کند؛ اینترنت را چک کن یا کمی بعد دوباره امتحان کن · Connection failed — the default provider works browser-direct; check your network or retry shortly.";
        }
      }
      showError("❌ " + (e.message || "ارتباط برقرار نشد") + hint);
    }
  } finally {
    streaming = null;
    setSendingUI(false);
    scrollDown();
  }
}

function stopStreaming() {
  streaming?.abort();
}
function regenerate() {
  if (streaming) return;
  const chat = activeChat();
  if (!chat || !chat.messages.length) return;
  if (chat.messages[chat.messages.length - 1].role === "assistant") {
    chat.messages.pop();
    persistChats();
    renderAll();
  }
  if (!ensureProviderReady()) return;
  generate();
}

/* ================= Composer & UI wiring ================= */
const input = $("input");
const btnSend = $("btn-send");

function setSendingUI(on) {
  btnSend.classList.toggle("sending", on);
  $("icon-send").style.display = on ? "none" : "";
  $("icon-stop").style.display = on ? "" : "none";
  $("btn-send").disabled = on ? false : input.value.trim() === "";
}

input.addEventListener("input", () => {
  input.style.height = "auto";
  input.style.height = Math.min(input.scrollHeight, 180) + "px";
  if (!streaming) $("btn-send").disabled = input.value.trim() === "";
});
input.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    const v = input.value;
    input.value = "";
    input.style.height = "auto";
    send(v);
  }
});
btnSend.onclick = () => {
  if (streaming) stopStreaming();
  else {
    const v = input.value;
    input.value = "";
    input.style.height = "auto";
    send(v);
  }
};

$("btn-new-chat").onclick = () => {
  if (streaming) stopStreaming();
  const c = newChat();
  chats.push(c);
  activeChatId = c.id;
  persistChats();
  renderSidebar();
  renderAll();
  closeSidebarMobile();
  input.focus();
};
$("btn-settings").onclick = openSettings;
$("btn-eye").onclick = () => {
  const k = $("set-key");
  k.type = k.type === "password" ? "text" : "password";
};
$("set-url").addEventListener("change", async () => {
  // user switched provider -> refetch its model list live
  modelList = [];
  renderModelPresets(true);
  await fetchModels($("set-url").value.trim() || DEFAULTS.baseUrl);
  renderModelPresets(false);
});
$("btn-save-settings").onclick = () => {
  settings.apiKey = $("set-key").value.trim();
  settings.baseUrl = $("set-url").value.trim() || DEFAULTS.baseUrl;
  settings.model = $("set-model").value.trim() || DEFAULTS.model;
  settings.proxyUrl = $("set-proxy").value.trim();
  saveSettings();
  closeSettings();
  hideError();
};

/* ---- Test connection ---- */
$("btn-test").onclick = async () => {
  const out = $("test-result");
  const key = $("set-key").value.trim();
  const model = $("set-model").value.trim() || DEFAULTS.model;
  const baseUrl = $("set-url").value.trim() || DEFAULTS.baseUrl;
  const proxy = $("set-proxy").value.trim();
  const prevProxy = settings.proxyUrl;
  const prevKey = settings.apiKey;
  settings.proxyUrl = proxy; settings.apiKey = key; // temp for requestUrl()/authHeaders()
  out.className = "test-result show";
  out.textContent = "⏳ در حال تست اتصال…";
  const t0 = performance.now();
  try {
    const res = await fetch(requestUrl(chatEndpoint(baseUrl)), {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify({ model, messages: [{ role: "user", content: "سلام — فقط بگو: متصل است" }], max_tokens: 40, stream: false }),
    });
    const ms = Math.round(performance.now() - t0);
    if (!res.ok) {
      let d = "";
      try { d = (await res.json())?.error?.message || ""; } catch {}
      out.textContent = "❌ HTTP " + res.status + (d ? " — " + d.slice(0, 140) : "") + (res.status === 401 ? " (کلید نامعتبر است · invalid key)" : res.status === 404 ? " (مدل پیدا نشد · unknown model)" : res.status === 429 ? " (محدودیت نرخ — کمی صبر کن · rate limited)" : "");
    } else {
      const j = await res.json();
      const c = (j.choices?.[0]?.message?.content || "").trim().slice(0, 60);
      out.textContent = "✅ متصل! · Connected — " + ms + "ms" + (c ? " — «" + c + "»" : "");
    }
  } catch (e) {
    const viaProxy = !!(IS_ELECTRON || proxy);
    out.textContent = viaProxy
      ? "❌ اتصال برقرار نشد — اینترنت/آدرس پراکسی را چک کن · Connection failed — check network or proxy URL."
      : "❌ اتصال برقرار نشد — اینترنت را چک کن یا بعداً دوباره امتحان کن · Connection failed — check network or retry.";
  } finally {
    settings.proxyUrl = prevProxy; settings.apiKey = prevKey;
  }
};
document.querySelectorAll("[data-close]").forEach((el) => (el.onclick = closeSettings));
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") closeSettings();
});
$("btn-menu").onclick = () => {
  const sb = $("sidebar");
  sb.classList.toggle("hidden");
  $("sidebar-backdrop").classList.toggle("show", !sb.classList.contains("hidden"));
};
$("sidebar-backdrop").onclick = () => {
  $("sidebar").classList.add("hidden");
  $("sidebar-backdrop").classList.remove("show");
};
$("suggestions").addEventListener("click", (e) => {
  if (e.target.classList.contains("chip")) {
    input.value = e.target.textContent.replace(/^[^\w\u0600-\u06FF]+\s*/, "");
    input.dispatchEvent(new Event("input"));
    input.focus();
  }
});
function hideError() {
  $("error-bar").hidden = true;
}

/* ================= Boot ================= */
(function boot() {
  if (chats.length) activeChatId = chats[0].id;
  updateModelChip();
  renderSidebar();
  renderAll();
  if (window.innerWidth <= 860) {
    $("sidebar").classList.add("hidden"); // mobile: start with drawer closed
  }
  // warm up: fetch the live model list in the background (no UI blocking)
  fetchModels(settings.baseUrl);
  if (settingsMigrated) {
    showError("⚙️ تنظیمات قدیمی به‌صورت خودکار منتقل شد — حالا بدون توکن و بدون ثبت‌نام کار می‌کند · Old settings migrated automatically — now works with no token and no signup.");
  } else if (window.matchMedia("(min-width: 861px)").matches) {
    showError("👋 خوش آمدی! همه‌چیز خودکار است — بدون توکن، بدون ثبت‌نام؛ فقط بنویس · Welcome! Everything is automatic — no token, no signup; just type.");
  }
  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }
})();
