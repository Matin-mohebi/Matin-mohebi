# 🌉 CORS Proxy — پراکسی وب Matin AI

## چرا لازم است؟ | Why?

GitHub Models (`models.github.ai`) هدرهای CORS ندارد، بنابراین مرورگر اجازه اتصال مستقیم نمی‌دهد:

```
Access to fetch at 'https://models.github.ai/inference/chat/completions'
from origin 'https://matin-mohebi.github.io' has been blocked by CORS policy
```

- ✅ **اپ دسکتاپ (Electron)، اپ iOS (Capacitor) و اپ اندروید نیازی به پراکسی ندارند** — درخواست‌ها بیرون از مرورگر انجام می‌شود.
- 🌐 فقط **نسخه وب / PWA** برای GitHub Models به پراکسی نیاز دارد (یا می‌توانید از سرویس‌های دارای CORS مثل OpenRouter با همان تنظیمات Base URL استفاده کنید).

## راه‌اندازی رایگان در ۳ دقیقه (Cloudflare Workers) | Free 3-minute setup

1. وارد [dash.cloudflare.com](https://dash.cloudflare.com) شوید (ثبت‌نام رایگان) → **Workers & Pages** → **Create application** → **Create Worker** → نام دلخواه (مثلا `matin-ai-proxy`) → **Deploy**.
2. روی **Edit code** بزنید، کل کد پیش‌فرض را پاک کنید و محتوای [`cloudflare-worker.js`](./cloudflare-worker.js) را بچسبانید → **Deploy**.
3. آدرس Worker خود را کپی کنید (مثل `https://matin-ai-proxy.xxx.workers.dev`).
4. در Matin AI → **تنظیمات** → فیلد **پراکسی CORS** این را وارد کنید:
   ```
   https://matin-ai-proxy.xxx.workers.dev/?target={url}
   ```
   (عبارت `{url}` را حتما نگه دارید — آدرس انتهایی API جای آن می‌نشیند.)
5. دکمه **«تست اتصال»** را بزنید — باید ✅ متصل ببینید.

### روش دوم: با خط فرمان (wrangler)

```bash
npm install -g wrangler
wrangler login
cd MatinAIWeb/proxy
cp cloudflare-worker.js worker.js   # یا worker.js را از همین کد بسازید
wrangler deploy worker.js --name matin-ai-proxy --compatibility-date 2025-01-01
```

## امنیت | Security

- توکن گیت‌هاب شما فقط در هدر `Authorization` به مقصد ارسال می‌شود و در Worker **ذخیره نمی‌شود**.
- لیست میزبان‌های مجاز (`ALLOWED_HOSTS`) فقط شامل APIهای هوش مصنوعی است؛ Worker شما به‌عنوان پراکسی عمومی قابل استفاده نیست.
- پلن رایگان Cloudflare: ۱۰۰٬۰۰۰ درخواست در روز — برای استفاده شخصی بسیار کافی است.

## سازگاری با پراکسی‌های دیگر | Other proxies

فرمت‌های زیر در فیلد پراکسی پشتیبانی می‌شوند:

| فرمت | مثال |
|:---|:---|
| جای‌نگهدار `{url}` | `https://my-proxy.dev/?target={url}` |
| پایان با `=` | `https://corsproxy.io/?url=` |
| پیشوند مسیری | `https://proxy.cors.sh/` |
