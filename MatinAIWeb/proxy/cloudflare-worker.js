/**
 * Matin AI — CORS proxy for GitHub Models (and other OpenAI-compatible APIs)
 * ------------------------------------------------------------------------
 * مرورگرها اجازه اتصال مستقیم به models.github.ai را ندارند (CORS).
 * این Worker کوچک روی Cloudflare (پلن رایگان) درخواست‌ها را پاس می‌دهد.
 *
 * Deploy:
 *   1) dash.cloudflare.com → Workers & Pages → Create → "Hello World"
 *   2) کل کد این فایل را جایگزین کد پیش‌فرض کن → Deploy
 *   3) در Matin AI → تنظیمات → «پراکسی CORS» این را وارد کن:
 *        https://<your-worker>.<your-subdomain>.workers.dev/?target={url}
 *
 * Usage:
 *   GET/POST https://<worker>?target=<url-encoded endpoint>
 *
 * Security: only AI API hosts are allowed, tokens are forwarded but never stored.
 */
const ALLOWED_HOSTS = [
  "models.github.ai",
  "models.inference.ai.azure.com",
  "api.openai.com",
  "openrouter.ai",
];

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type, Accept",
  "Access-Control-Max-Age": "86400",
};

export default {
  async fetch(request) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    const target = url.searchParams.get("target");

    if (!target || !/^https?:\/\//i.test(target)) {
      return json({ error: "Missing ?target=<encoded endpoint URL>" }, 400);
    }

    let parsed;
    try {
      parsed = new URL(target);
    } catch {
      return json({ error: "Invalid target URL" }, 400);
    }

    if (!ALLOWED_HOSTS.includes(parsed.hostname)) {
      return json({ error: "Blocked — host not in ALLOWED_HOSTS: " + parsed.hostname }, 403);
    }

    const headers = new Headers();
    for (const name of ["authorization", "content-type", "accept"]) {
      const v = request.headers.get(name);
      if (v) headers.set(name, v);
    }

    const init = {
      method: request.method,
      headers,
      body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body,
    };

    try {
      const upstream = await fetch(parsed.toString(), init);
      const h = new Headers(upstream.headers);
      Object.entries(CORS_HEADERS).forEach(([k, v]) => h.set(k, v));
      h.delete("content-encoding"); // body is already decoded by the worker runtime
      return new Response(upstream.body, { status: upstream.status, headers: h });
    } catch (e) {
      return json({ error: "Upstream request failed: " + (e?.message || e) }, 502);
    }
  },
};

function json(obj, status) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}
