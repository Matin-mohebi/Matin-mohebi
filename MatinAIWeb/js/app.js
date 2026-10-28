/* Matin AI — web/desktop/iOS app logic (Persian-first, ZERO-CONFIG + SMART AUTO-ROUTING) */
"use strict";

/* ================= Constants & defaults =================
 * v1.6.0 — SMART MULTI-AI ENGINE:
 *  - Connects to every free keyless AI automatically (no token, no signup)
 *  - Classifies each question (code / math / general / creative / translate)
 *    and routes it to the best available model — auto-switching between AIs
 *  - Auto-failover: if a provider fails/rate-limits, the next AI answers silently
 *  - Anti-hallucination system prompt for fewer mistakes
 *  - Live "thinking" preview for reasoning models + per-answer AI badge
 */
const DEFAULTS = {
  baseUrl: "https://text.pollinations.ai",
  model: "openai",
};
const SYSTEM_PROMPT =
  "تو Matin AI هستی، دستیار شخصی فارسی‌زبان و بسیار دقیق.\n" +
  "قواعد دقت (خیلی مهم):\n" +
  "1. فقط چیزهایی را بگو که مطمئنی؛ اگر چیزی را نمی‌دانی صریح بگو «مطمئن نیستم».\n" +
  "2. هیچ لینک، منبع، آمار، تاریخ یا ارجاعی از خودت نساز (hallucination ممنوع).\n" +
  "3. برای محاسبات ریاضی مرحله‌به‌مرحله حساب کن و نتیجه نهایی را جدا بنویس.\n" +
  "4. اگر سوال مبهم است، اول یک سوال کوتاه روشن‌کننده بپرس.\n" +
  "5. اگر کاربر انگلیسی نوشت انگلیسی جواب بده، در غیر این صورت فارسی روان جواب بده.\n" +
  "6. پاسخ‌ها را با مارک‌داون تمیز و ساختارمند کن؛ برای کد حتماً بلوک کد با نام زبان بگذار.\n" +
  "You are Matin AI — a precise Persian-first personal assistant. Never invent facts, sources or links.";

/* ---- Provider registry: every known backend, keyless first ---- */
const PROVIDER_REGISTRY = [
  { id: "pollinations", label: "Pollinations", baseUrl: "https://text.pollinations.ai", keyless: true, dead: false },
  { id: "openrouter",   label: "OpenRouter",   baseUrl: "https://openrouter.ai/api/v1",   keyless: false, dead: false },
  { id: "groq",         label: "Groq",         baseUrl: "https://api.groq.com/openai/v1", keyless: false, dead: false },
  { id: "openai",       label: "OpenAI",       baseUrl: "https://api.openai.com/v1",      keyless: false, dead: false },
  { id: "gemini",       label: "Gemini",       baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai", keyless: false, dead: false },
  { id: "github",       label: "GitHub AI",    baseUrl: "https://models.github.ai",       keyless: false, dead: true,
    deadNote: "سرویس GitHub AI از ۳۰ ژوئیه ۲۰۲۶ تعطیل شده و دیگر از هیچ‌جا در دسترس نیست · retired July 30, 2026" },
];
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
let streaming = null;        // AbortController while generating
let lastCandidates = [];     // routing candidates of the last generate() call
let lastUsedIndex = -1;      // which candidate answered last (for "regen with another AI")
let modelList = [];          // presets for the settings modal (current provider)

/* ================= Helpers ================= */
const $ = (id) => document.getElementById(id);

let settingsMigrated = false; // true when an old config was auto-fixed
function loadSettings() {
  try {
    const s = JSON.parse(localStorage.getItem(LS_SETTINGS) || "{}");
    const out = {
      apiKey: s.apiKey || "",
      baseUrl: s.baseUrl || DEFAULTS.baseUrl,
      model: s.model || DEFAULTS.model,
      proxyUrl: s.proxyUrl || "",
      autoMode: s.autoMode !== false, // default ON — smart routing
    };
    // v1.5/1.6 migration — move every legacy config to the zero-config provider:
    // 1) dead GitHub Models endpoints (retired 2026-07-30)
    // 2) OpenRouter default with an empty key (OpenRouter cannot work keyless)
    const legacyOpenRouterEmpty = !out.apiKey && /openrouter\.ai/i.test(out.baseUrl);
    if (DEAD_ENDPOINTS.test(out.baseUrl) || legacyOpenRouterEmpty) {
      out.baseUrl = DEFAULTS.baseUrl;
      out.model = DEFAULTS.model;
      settingsMigrated = true;
    }
    return out;
  } catch {
    return { apiKey: "", baseUrl: DEFAULTS.baseUrl, model: DEFAULTS.model, proxyUrl: "", autoMode: true };
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
 *   https://text.pollinations.ai          -> .../openai
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
  return b + "/v1/chat/completions";
}

/** Models-list endpoint per provider (fills the model pool live from the server). */
function modelsEndpoint(base) {
  let b = (base || "").trim().replace(/\/+$/, "");
  if (!b) b = DEFAULTS.baseUrl;
  if (/pollinations\.ai/i.test(b)) return "https://text.pollinations.ai/models";
  if (/openrouter\.ai/i.test(b) || /api\.openai\.com/i.test(b) || /groq\.com/i.test(b)) return b + "/models";
  if (/\/chat\/completions$/.test(b)) return b.replace(/\/chat\/completions$/, "/models");
  if (/\/openai$/.test(b)) return b.replace(/\/openai$/, "/models");
  return b + "/v1/models";
}

function providerIdOf(base) {
  const p = PROVIDER_REGISTRY.find((p) => p.baseUrl.replace(/\/+$/, "") === (base || "").trim().replace(/\/+$/, ""));
  return p ? p.id : "custom";
}
function providerLabel(base) {
  const p = PROVIDER_REGISTRY.find((p) => p.baseUrl.replace(/\/+$/, "") === (base || "").trim().replace(/\/+$/, ""));
  if (p) return p.label;
  try {
    return new URL(base).hostname.replace(/^www\./, "");
  } catch { return "Custom"; }
}
/** Only non-keyless endpoints need a key — Pollinations (default) does not. */
function needsKey(base) {
  const p = PROVIDER_REGISTRY.find((p) => p.baseUrl.replace(/\/+$/, "") === (base || "").trim().replace(/\/+$/, ""));
  return p ? !p.keyless : true;
}

/* ================= SMART ROUTING ENGINE =================
 * 1) classify(question)  -> type: code | math | translate | summary | creative | general
 * 2) model pools         -> live /models of every reachable provider (keyless always, keyed if saved)
 * 3) buildCandidates()   -> ordered list of {baseUrl, model} — best match first
 * 4) generate()          -> tries candidates in order; on failure silently switches to the next AI
 */
/* Persian-aware keyword routing: \b word-boundaries do NOT work on Persian
 * script (ASCII-only), so we tokenize and match words/phrases explicitly. */
const ROUTER_WORDS = {
  translate: ["translate", "ترجمه", "به انگلیسی", "به فارسی", "مترجم"],
  summary: ["summarize", "summary", "tldr", "خلاصه", "چکیده"],
  code: ["code", "coding", "کد", "کدنویسی", "برنامه", "برنامه‌نویسی", "اسکریپت", "script", "function", "تابع", "پایتون", "python", "javascript", "typescript", "kotlin", "swift", "sql", "html", "css", "react", "bug", "خطا", "دیباگ", "debug", "api", "regex", "الگوریتم", "algorithm", "دیتابیس", "database", "shell", "bash"],
  math: ["calculate", "math", "equation", "integral", "derivative", "محاسبه", "حساب", "ریاضی", "معادله", "انتگرال", "مشتق", "درصد", "جذر", "احتمال", "هندسه", "ضربدر", "تقسیم", "جمع کن"],
  creative: ["story", "poem", "song", "essay", "caption", "slogan", "داستان", "شعر", "ترانه", "کپشن", "اسلوگان", "نامه", "مقاله"],
};

function classifyQuestion(q) {
  const s = " " + String(q || "").toLowerCase().replace(/[،؛؟?!.,()\[\]{}"'\n\r\t]/g, " ").replace(/\s+/g, " ").trim() + " ";
  for (const [type, words] of Object.entries(ROUTER_WORDS)) {
    for (const raw of words) {
      const w = raw.toLowerCase();
      if (w.includes(" ")) {           // multi-word phrase: substring match
        if (s.includes(w)) return type;
      } else if (s.includes(" " + w + " ")) {
        return type;                   // exact token
      } else if (w.length >= 4 && s.includes(w)) {
        return type;                   // substring for longer words (کدنویسی…)
      }
    }
  }
  if (/[0-9۰-۹]+\s*[+\-*/^×÷]\s*[0-9۰-۹]+/.test(q) || /[0-9۰-۹]+\s+درصد/.test(q)) return "math";
  return "general";
}

/* model-id keywords preferred per question type (first match wins) */
const PREFER = {
  code:     ["coder", "codestral", "qwen", "deepseek", "devstral", "starcoder", "gpt-oss", "70b", "llama", "mistral"],
  math:     ["reasoning", "r1", "o1", "o3", "o4", "think", "gpt-oss", "70b", "qwen", "llama"],
  translate: ["gpt-oss", "70b", "llama", "qwen", "mistral", "mistral-nemo"],
  summary:  ["gpt-oss", "70b", "llama", "qwen", "mistral"],
  creative: ["70b", "llama", "mistral", "qwen", "gpt-oss", "nemo"],
  general:  ["gpt-oss", "70b", "llama", "qwen", "mistral", "nemo"],
};

function scoreModel(modelId, type) {
  const m = (modelId || "").toLowerCase();
  const prefs = PREFER[type] || PREFER.general;
  for (let i = 0; i < prefs.length; i++) {
    if (m.includes(prefs[i])) return i; // 0 = best
  }
  return prefs.length + 1; // unrated -> end
}

/* ---- live model pools per provider ---- */
const modelPools = new Map(); // baseUrl -> [{model, scoreBonus}] normalized ids
let poolsReady = null;        // promise so boot/settings/routing share one refresh

function normalizeModels(j) {
  const arr = Array.isArray(j) ? j : (Array.isArray(j?.data) ? j.data : []);
  const ids = arr
    .map((m) => (typeof m === "string" ? m : m.name || m.id || ""))
    .filter(Boolean);
  // free/anonymous models first (Pollinations marks usable models tier:"anonymous")
  const isFree = (m) => (m && typeof m === "object" && (m.tier === "anonymous" || m.tier === "free") ? 0 : 1);
  const tagged = arr.length && typeof arr[0] === "object"
    ? ids.map((id, i) => ({ id, t: isFree(arr[i]) }))
    : ids.map((id) => ({ id, t: 1 }));
  tagged.sort((a, b) => a.t - b.t);
  return tagged.map((x) => x.id);
}

async function fetchPool(baseUrl) {
  try {
    const res = await fetch(requestUrl(modelsEndpoint(baseUrl)), { signal: AbortSignal.timeout ? AbortSignal.timeout(12000) : undefined });
    if (!res.ok) throw new Error("HTTP " + res.status);
    const ids = normalizeModels(await res.json());
    if (!ids.length) throw new Error("empty");
    modelPools.set(baseUrl.trim().replace(/\/+$/, ""), ids);
    return ids;
  } catch {
    if (/pollinations\.ai/i.test(baseUrl)) {
      modelPools.set(baseUrl.trim().replace(/\/+$/, ""), FALLBACK_MODELS.slice());
      return FALLBACK_MODELS.slice();
    }
    modelPools.delete(baseUrl.trim().replace(/\/+$/, ""));
    return [];
  }
}

/** Refresh pools for: the default keyless providers + the user's current provider (if keyed). */
async function refreshPools() {
  const bases = new Set();
  PROVIDER_REGISTRY.filter((p) => p.keyless && !p.dead).forEach((p) => bases.add(p.baseUrl));
  if (settings.apiKey.trim() && !PROVIDER_REGISTRY.find((p) => p.baseUrl === settings.baseUrl)?.keyless) {
    bases.add(settings.baseUrl); // the configured keyed provider joins the routing pool
  } else {
    bases.add(settings.baseUrl); // always include current (covers custom keyless endpoints)
  }
  await Promise.all([...bases].map((b) => fetchPool(b)));
  poolsReady = Promise.resolve();
}

async function ensurePools() {
  if (!poolsReady || !modelPools.has(settings.baseUrl.trim().replace(/\/+$/, ""))) {
    poolsReady = refreshPools();
  }
  await poolsReady;
}

/** Build the ordered AI candidate chain for a question. */
async function buildCandidates(question) {
  await ensurePools();
  const type = classifyQuestion(question || "");
  const cands = [];
  // manual mode: only the user's exact choice
  if (!settings.autoMode) {
    cands.push({ baseUrl: settings.baseUrl, model: settings.model.trim() || DEFAULTS.model });
    return cands;
  }
  // keyless providers first (always usable), then the keyed/current provider
  const bases = [];
  PROVIDER_REGISTRY.filter((p) => p.keyless && !p.dead).forEach((p) => bases.push(p.baseUrl));
  const cur = settings.baseUrl.trim().replace(/\/+$/, "");
  if (!bases.includes(cur)) bases.push(cur);
  for (const base of bases) {
    const models = modelPools.get(base) || [];
    models.forEach((m) => cands.push({ baseUrl: base, model: m, s: scoreModel(m, type) }));
  }
  if (!cands.length) cands.push({ baseUrl: settings.baseUrl, model: settings.model.trim() || DEFAULTS.model });
  else cands.sort((a, b) => (a.s ?? 999) - (b.s ?? 999));
  // de-dupe by base+model
  const seen = new Set();
  return cands.filter((c) => {
    const k = c.baseUrl + "|" + c.model;
    if (seen.has(k)) return false;
    seen.add(k);
    return true;
  });
}

/* ================= Request plumbing ================= */
/**
 * Wrap an endpoint URL for the current runtime:
 * - Electron: matinai://<host>/<path>  (main-process proxy, CORS-free, streaming)
 * - Custom CORS proxy set in Settings (rarely needed).
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

function authHeaders(baseUrl) {
  const h = { "Content-Type": "application/json" };
  // key applies only when the target provider actually needs one
  const key = (settings.apiKey || "").trim();
  if (key && needsKey(baseUrl)) h["Authorization"] = "Bearer " + key;
  if (/openrouter\.ai/i.test(baseUrl)) {
    h["HTTP-Referer"] = location.origin || "https://matin-mohebi.github.io";
    h["X-Title"] = "Matin AI";
  }
  return h;
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
  const via = document.createElement("div");
  via.className = "via";
  via.hidden = true;
  const md = document.createElement("div");
  md.className = "md";
  md.dir = "auto";
  content.appendChild(via);
  content.appendChild(md);
  const actions = document.createElement("div");
  actions.className = "actions";
  actions.innerHTML = `<button class="act-btn" data-act="copy">کپی · Copy</button>
    <button class="act-btn" data-act="regen">🔄 دوباره با AI دیگر · Regenerate</button>`;
  actions.querySelector('[data-act="copy"]').onclick = () =>
    navigator.clipboard.writeText(md._raw || md.textContent);
  actions.querySelector('[data-act="regen"]').onclick = () => regenerate();
  content.appendChild(actions);
  div.appendChild(av);
  div.appendChild(content);
  return { root: div, md, via, actions };
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
      const { root, md, via, actions } = aiBubbleEl();
      md._raw = m.content;
      md.innerHTML = renderMarkdown(m.content);
      enhanceCodeBlocks(md);
      if (m.via) {
        via.textContent = "🧠 " + m.via;
        via.hidden = false;
      }
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
      settings.autoMode = false;
      $("auto-toggle").checked = false;
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
  $("auto-toggle").checked = !!settings.autoMode;
  $("settings-modal").hidden = false;
  renderModelPresets(true);
  await fetchPool(settings.baseUrl.trim() || DEFAULTS.baseUrl);
  modelList = modelPools.get(settings.baseUrl.trim().replace(/\/+$/, "")) || [];
  renderModelPresets(false);
  renderHealth();
}
function closeSettings() {
  $("settings-modal").hidden = true;
}
function updateModelChip() {
  const mode = settings.autoMode ? "🧠 هوشمند" : "دستی";
  $("model-chip").textContent = settings.model + " · " + providerLabel(settings.baseUrl) + " · " + mode;
}

/* ---- health dashboard ---- */
function renderHealth() {
  const grid = $("health-grid");
  if (!grid) return;
  grid.innerHTML = "";
  PROVIDER_REGISTRY.forEach((p) => {
    const chip = document.createElement("div");
    chip.className = "health-chip" + (p.dead ? " dead" : " ok");
    chip.textContent = (p.dead ? "⛔ " : "✅ ") + p.label + (p.dead ? " (تعطیل)" : p.keyless ? " · بدون کلید" : " · با کلید");
    if (p.dead) chip.title = p.deadNote || "";
    grid.appendChild(chip);
  });
  const models = modelPools.get(settings.baseUrl.trim().replace(/\/+$/, "")) || [];
  const info = document.createElement("div");
  info.className = "health-info";
  info.textContent = `📡 مدل‌های زنده ${providerLabel(settings.baseUrl)}: ` + (models.slice(0, 6).join(" · ") || "—");
  grid.appendChild(info);
}

async function runHealthCheck() {
  const grid = $("health-grid");
  const prev = grid.innerHTML;
  grid.innerHTML = `<div class="health-info">⏳ در حال تست همه سرویس‌ها… · testing all providers…</div>`;
  const results = [];
  for (const p of PROVIDER_REGISTRY) {
    if (p.dead) { results.push(`⛔ ${p.label}: تعطیل شده (۲۰۲۶/۰۷/۳۰)`); continue; }
    const ok = await (async () => {
      try {
        const res = await fetch(requestUrl(modelsEndpoint(p.baseUrl)), { signal: AbortSignal.timeout ? AbortSignal.timeout(8000) : undefined });
        return res.ok;
      } catch { return false; }
    })();
    results.push(`${ok ? "✅" : "❌"} ${p.label}: ${ok ? "آنلاین" : "در دسترس نیست"}`);
  }
  grid.innerHTML = "";
  results.forEach((r) => {
    const d = document.createElement("div");
    d.className = "health-chip " + (r.startsWith("✅") ? "ok" : "dead");
    d.textContent = r;
    grid.appendChild(d);
  });
}

/* ---- export chat ---- */
function exportChat() {
  const chat = activeChat();
  if (!chat || !chat.messages.length) {
    showError("گفتگویی برای خروجی نیست · No chat to export.");
    return;
  }
  const md = ["# Matin AI — " + (chat.title || "Chat"), "", ...chat.messages.map((m) => `## ${m.role === "user" ? "شما · You" : "Matin AI" + (m.via ? ` (${m.via})` : "")}\n\n${m.content}\n`)].join("\n");
  const blob = new Blob([md], { type: "text/markdown;charset=utf-8" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "matin-ai-chat-" + new Date().toISOString().slice(0, 10) + ".md";
  a.click();
  URL.revokeObjectURL(a.href);
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

function addMessage(role, content, via) {
  const chat = ensureChat();
  const msg = { role, content };
  if (via) msg.via = via;
  chat.messages.push(msg);
  chat.updatedAt = Date.now();
  if (role === "user" && !chat.title) {
    chat.title = content.replace(/\s+/g, " ").trim().slice(0, 34) || "گفتگوی جدید";
  }
  persistChats();
}

async function send(text) {
  text = (text || "").trim();
  if (!text || streaming) return;
  hideError();

  const chat = ensureChat();
  $("welcome").style.display = "none";
  messagesEl.appendChild(userBubbleEl(text));
  scrollDown(true);
  addMessage("user", text);
  renderSidebar();

  await generate();
}

/** Stream one candidate; returns accumulated text or throws. */
async function streamCandidate(cand, history, md, controller, onDelta) {
  const res = await fetch(requestUrl(chatEndpoint(cand.baseUrl)), {
    method: "POST",
    signal: controller.signal,
    headers: authHeaders(cand.baseUrl),
    body: JSON.stringify({
      model: cand.model,
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
  let acc = "";
  if (isStream && res.body) {
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buf = "";
    let reasoning = "";
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
          const d = j.choices?.[0]?.delta || {};
          if (d.reasoning) {
            reasoning += d.reasoning;
            if (reasoning.length < 220) onDelta(acc, reasoning); // live thinking preview
          }
          if (d.content) {
            acc += d.content;
            onDelta(acc, reasoning);
          }
        } catch {}
      }
    }
  } else {
    const j = await res.json();
    acc = j.choices?.[0]?.message?.content || "";
  }
  if (!acc.trim()) throw new Error("empty response");
  return acc;
}

async function generate(startAt = 0) {
  const chat = ensureChat();
  const question = [...chat.messages].reverse().find((m) => m.role === "user")?.content || "";
  const { root, md, via, actions } = aiBubbleEl();
  md.innerHTML = `<span class="cursor-blink"></span>`;
  messagesEl.appendChild(root);
  scrollDown(true);

  const controller = new AbortController();
  streaming = controller;
  setSendingUI(true);

  let acc = "";
  try {
    const history = [{ role: "system", content: SYSTEM_PROMPT }];
    chat.messages.slice(-24).forEach((m) => history.push({ role: m.role, content: m.content }));

    // ---- SMART ROUTING: ordered candidate chain, auto-failover ----
    let candidates = startAt > 0 && lastCandidates.length
      ? lastCandidates.slice(startAt)
      : await buildCandidates(question);
    if (startAt === 0) lastCandidates = candidates;
    lastUsedIndex = -1;

    const MAX_TRIES = Math.min(candidates.length, 5);
    for (let i = 0; i < MAX_TRIES; i++) {
      const cand = candidates[i];
      try {
        acc = await streamCandidate(cand, history, md, controller, (text, reasoning) => {
          const think = reasoning
            ? `<div class="thinking">💭 ${renderMarkdown(reasoning.slice(0, 200))}…</div>`
            : "";
          md.innerHTML = think + renderMarkdown(text) + `<span class="cursor-blink"></span>`;
          scrollDown();
        });
        lastUsedIndex = startAt + i;
        const viaText = providerLabel(cand.baseUrl) + " · " + cand.model;
        via.textContent = "🧠 " + viaText;
        via.hidden = false;
        break; // this AI answered — done
      } catch (e) {
        if (e.name === "AbortError") throw e;         // user pressed stop — bubble already handled below
        acc = "";
        if (i === MAX_TRIES - 1) throw new Error("همه سرویس‌ها امتحان شدند · " + e.message);
        // else: silently switch to the next AI
      }
    }

    md.innerHTML = renderMarkdown(acc);
    enhanceCodeBlocks(md);
    actions.style.opacity = "1";
    addMessage("assistant", acc, lastUsedIndex >= 0
      ? providerLabel(lastCandidates[lastUsedIndex].baseUrl) + " · " + lastCandidates[lastUsedIndex].model
      : undefined);
    renderSidebar();
  } catch (e) {
    if (e.name === "AbortError") {
      if (acc.trim()) {
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
        hint = IS_ELECTRON || IS_CAPACITOR
          ? " — اتصال برقرار نشد؛ اینترنت را چک کن · Network failed — check connection."
          : " — اتصال برقرار نشد. اینترنت را چک کن یا کمی بعد دوباره امتحان کن · Connection failed — check network or retry.";
      }
      const m = e.message || "";
      if (/403|turnstile/i.test(m)) {
        hint = " — اگر روی لوکال (localhost) اجرا می‌کنی، سرویس درخواست‌های لوکال را می‌بندد؛ از آدرس اصلی اپ استفاده کن · Localhost dev is blocked by the provider — use the official app URL.";
      }
      showError("❌ " + m + hint);
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
/** Regenerate — with the NEXT AI in the routing chain ("دوباره با AI دیگر"). */
function regenerate() {
  if (streaming) return;
  const chat = activeChat();
  if (!chat || !chat.messages.length) return;
  if (chat.messages[chat.messages.length - 1].role === "assistant") {
    chat.messages.pop();
    persistChats();
    renderAll();
  }
  // start from the candidate after the one that answered last time
  const next = lastUsedIndex >= 0 && lastCandidates.length ? lastUsedIndex + 1 : 0;
  generate(next > 0 && next < lastCandidates.length ? next : 0);
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
$("auto-toggle").addEventListener("change", () => {
  settings.autoMode = $("auto-toggle").checked;
  saveSettings();
});
$("set-url").addEventListener("change", async () => {
  modelList = [];
  renderModelPresets(true);
  await fetchPool($("set-url").value.trim() || DEFAULTS.baseUrl);
  modelList = modelPools.get(($("set-url").value.trim() || DEFAULTS.baseUrl).trim().replace(/\/+$/, "")) || [];
  renderModelPresets(false);
  renderHealth();
});
$("btn-save-settings").onclick = () => {
  settings.apiKey = $("set-key").value.trim();
  settings.baseUrl = $("set-url").value.trim() || DEFAULTS.baseUrl;
  settings.model = $("set-model").value.trim() || DEFAULTS.model;
  settings.proxyUrl = $("set-proxy").value.trim();
  saveSettings();
  poolsReady = null; // re-pool with the new provider
  closeSettings();
  hideError();
};
$("btn-health").onclick = runHealthCheck;
$("btn-export").onclick = exportChat;

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
      headers: authHeaders(baseUrl),
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
  // warm up the routing pools in the background (no UI blocking)
  refreshPools();
  if (settingsMigrated) {
    showError("⚙️ تنظیمات قدیمی خودکار منتقل شد — حالا هوش مصنوعی‌ها خودکار عوض می‌شوند · Old settings migrated — smart auto-routing is ON.");
  } else if (window.matchMedia("(min-width: 861px)").matches) {
    showError("🧠 حالت هوشمند روشن است — بر اساس سوالت بهترین AI را خودکار انتخاب می‌کند · Smart routing ON — picks the best AI per question.");
  }
  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }
})();
