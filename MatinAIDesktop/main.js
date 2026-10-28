/* Matin AI — Electron main process
 * Includes a CORS-free main-process proxy (matinai:// scheme) so the same UI
 * can call GitHub Models and any OpenAI-compatible API without a server. */
"use strict";

const { app, BrowserWindow, protocol, shell, Menu } = require("electron");
const path = require("path");

// Custom privileged scheme — MUST be registered before app is ready.
protocol.registerSchemesAsPrivileged([
  {
    scheme: "matinai",
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      stream: true,
      corsEnabled: true,
    },
  },
]);

const FORWARDED_HEADERS = ["authorization", "content-type", "accept"];
const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type, Accept",
};

/** matinai://<host>/<path>?<query> -> https://<host>/<path>?<query> */
function targetUrl(reqUrl) {
  const u = new URL(reqUrl);
  return "https://" + u.host + u.pathname + u.search;
}

function forwardedHeaders(reqHeaders) {
  const h = {};
  for (const [k, v] of reqHeaders) {
    const lk = k.toLowerCase();
    if (FORWARDED_HEADERS.includes(lk)) h[lk] = v;
  }
  return h;
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1180,
    height: 780,
    minWidth: 420,
    minHeight: 500,
    backgroundColor: "#10111A",
    title: "Matin AI",
    icon: path.join(__dirname, "build-res", "icon.png"),
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
    },
  });

  Menu.setApplicationMenu(null);
  win.loadFile(path.join(__dirname, "app-ui", "index.html"));

  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url)) shell.openExternal(url);
    return { action: "deny" };
  });
  win.webContents.on("will-navigate", (e, url) => {
    // keep the SPA in place; only file:// app-ui is allowed
    if (!url.startsWith("file://")) {
      e.preventDefault();
      if (/^https?:/i.test(url)) shell.openExternal(url);
    }
  });
}

app.whenReady().then(() => {
  protocol.handle("matinai", async (req) => {
    if (req.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }
    try {
      const init = {
        method: req.method,
        headers: forwardedHeaders(req.headers),
      };
      if (req.method !== "GET" && req.method !== "HEAD") {
        init.body = await req.arrayBuffer();
      }
      const upstream = await fetch(targetUrl(req.url), init);
      const h = new Headers(upstream.headers);
      Object.entries(CORS_HEADERS).forEach(([k, v]) => h.set(k, v));
      h.delete("content-encoding"); // already decoded by undici
      h.delete("content-length");   // re-computed for the streamed body
      return new Response(upstream.body, { status: upstream.status, headers: h });
    } catch (e) {
      return new Response(JSON.stringify({ error: "Proxy failed: " + (e?.message || e) }), {
        status: 502,
        headers: { "Content-Type": "application/json", ...CORS_HEADERS },
      });
    }
  });

  createWindow();
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
