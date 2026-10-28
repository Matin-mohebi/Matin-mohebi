# 🌐 Matin AI Web — متین ای‌آی وب

<div align="center">
<img src="icons/icon-192.png" width="90" alt="Matin AI"/>
<br/>
<b>دستیار هوشمند فارسی — بدون توکن، بدون ثبت‌نام · Persian AI assistant — zero config, no token</b>
</div>

نسخه وب اپ Matin AI با ظاهری شبیه ChatGPT و Claude — یک اپ PWA سبک که روی هر دستگاهی اجرا می‌شود.
The web version of Matin AI with a ChatGPT/Claude-style UI — a lightweight PWA that runs anywhere.

## ✨ Features | امکانات

- 💬 **پاسخ‌های استریم زنده** (مثل ChatGPT حرف‌به‌حرف تایپ می‌شود) — Live streaming responses
- 🗂️ **تاریخچه گفتگوها** با سایدبار — Chat history with a sidebar
- 📝 **رندر مارک‌داون** + بلوک کد با دکمه کپی — Markdown + code blocks with copy buttons
- 🤖 **پیش‌فرض: Pollinations (رایگان، بدون کلید)** — همه‌چیز خودکار است؛ هیچ توکنی لازم نیست · Everything is automatic — no token, no signup
- 📡 **لیست مدل‌ها زنده از سرور گرفته می‌شود** · The model list is fetched live from the server
- 🔄 سازگار با OpenAI، OpenRouter، Gemini، Groq و هر سرویس سازگار (اختیاری) — Works with any OpenAI-compatible service (optional)
- 📱 **PWA**: روی آیفون و اندروید نصب می‌شود — Installable on iPhone & Android
- 🌓 تم تیره، RTL کامل فارسی، دوزبانه — Dark theme, full Persian RTL, bilingual

## 🚀 Use online | استفاده آنلاین

**آدرس:** [https://matin-mohebi.github.io/Matin-mohebi/](https://matin-mohebi.github.io/Matin-mohebi/)

1. اپ را باز کن — همین! 🎉 Open the app — that's it!
2. بنویس و جواب بگیر؛ سرور، مدل و لیست مدل‌ها همه خودکار از سرور می‌آیند · The server, model and model list all come from the server automatically. **هیچ توکنی لازم نیست · No token needed.**

> 📝 فقط اگر بخواهی سرویس دیگری (OpenRouter/OpenAI/…) بگذاری، در تنظیمات آدرس را عوض کن و کلید همان سرویس را وارد کن — پیش‌فرض هیچ کلیدی نمی‌خواهد · Only if you switch providers you'll need that provider's key — the keyless default just works.
>
> ⚠️ سرویس **GitHub Models در تاریخ ۳۰ ژوئیه ۲۰۲۶ تعطیل شد**؛ نسخه‌های قدیمی دیگر به آن وصل نمی‌شوند. نسخه ۱.۵.۰ همه تنظیمات قدیمی را خودکار به سرویس بدون‌کلید منتقل می‌کند · **GitHub Models was retired on July 30, 2026**; v1.5.0 migrates old settings to the keyless provider automatically.

### 📲 Install on iPhone (PWA) | نصب روی آیفون

1. در Safari آدرس اپ را باز کن · Open the URL in Safari
2. دکمه **Share** (مربع با فلش) را بزن · Tap the **Share** button
3. **Add to Home Screen** را انتخاب کن · Choose **Add to Home Screen**
4. اپ مثل یک اپ واقعی تمام‌صفحه اجرا می‌شود ✅ Runs full-screen like a native app

> 💡 اپ اندروید نیتیو هم از [Releases](https://github.com/Matin-mohebi/Matin-mohebi/releases) قابل نصب است.
> A native Android app is also available in Releases.

### 📱 Native iOS app | اپ نیتیو iOS (Capacitor)

سورس کامل پروژه Xcode در پوشه [`MatinAIiOS`](../MatinAIiOS/) است (Capacitor 8 · Bundle ID: `ir.matincode.matinai`).
The complete Xcode project source lives in [`MatinAIiOS`](../MatinAIiOS/) (Capacitor 8).

**روش ۱ — IPA آماده (بدون نیاز به مک):** فایل `MatinAI-iOS-unsigned.ipa` را از [Releases](https://github.com/Matin-mohebi/Matin-mohebi/releases) بگیر و با [Sideloadly](https://sideloadly.io) یا [AltStore](https://altstore.io) روی آیفون نصب کن (با کابل و اپل‌آیدی رایگان — امضای ۷ روزه).
**Option 1 — ready-made IPA:** grab `MatinAI-iOS-unsigned.ipa` from Releases and sideload it with Sideloadly or AltStore (free Apple ID, 7-day signing).

**روش ۲ — ساخت با Xcode:** پوشه `MatinAIiOS/ios` را در Xcode (روی مک) باز کن، Team را در Signing انتخاب کن و Run بزن — یا خودت Archive بگیر و IPA بساز.
**Option 2 — build with Xcode:** open `MatinAIiOS/ios` in Xcode on a Mac, set your Team in Signing and hit Run.

ساخت خودکار IPA در هر پوشش، با GitHub Actions (runner مک) انجام می‌شود · IPA builds automatically on every push via GitHub Actions (macOS runner).

## 🖥️ Desktop | دسکتاپ (ویندوز / لینوکس / مک)

از [Releases](https://github.com/Matin-mohebi/Matin-mohebi/releases) دانلود کن:
Download from Releases:

- `MatinAI-Windows-Setup-x64.exe` → نصب‌کننده ویندوز (یا `MatinAI-Windows-x64.zip` پرتابل)
- `MatinAI-Linux-x64.AppImage` → `chmod +x *.AppImage && ./*.AppImage`
- `MatinAI-macOS-arm64.zip` / `MatinAI-macOS-x64.zip` → مک (Apple Silicon / Intel)

> دسکتاپ از Electron با پروکسی داخلی استفاده می‌کند؛ برای همین بدون مشکل CORS به هر سرویسی وصل می‌شود. جزئیات: [`MatinAIDesktop/README.md`](../MatinAIDesktop/README.md)

دیتای اپ (چت‌ها و کلیدِ اختیاری) روی خود دستگاه ذخیره می‌شود · Data stays on your device.

## 🤖 سرویس پیش‌فرض: Pollinations (بدون کلید) | Default provider

پیش‌فرض اپ (نسخه ۱.۵.۰ به بعد)، **Pollinations** است — رایگان، بدون هیچ کلیدی، با CORS باز (مرورگر مستقیم وصل می‌شود):
The default backend (since v1.5.0) is **Pollinations** — free, NO API key, CORS-open (browser-direct):

| تنظیم | مقدار |
|:---|:---|
| Base URL | `https://text.pollinations.ai` |
| Chat endpoint | `https://text.pollinations.ai/openai` (سازگار با OpenAI · OpenAI-compatible) |
| Model | `openai` — پیش‌فرض خودکار · auto default |
| Model list | `https://text.pollinations.ai/models` — زنده از سرور · fetched live |
| API Key | ❌ **لازم نیست · NOT needed** |

هر سرویس سازگار با OpenAI هم کار می‌کند (OpenRouter، OpenAI، Gemini، Groq، Ollama محلی و…) — فقط Base URL را عوض کن و کلید همان سرویس را در تنظیمات بگذار.
Any OpenAI-compatible service works too — change the Base URL and add that provider's key in Settings (optional).

## ⚠️ CORS در نسخه وب | CORS on the web

سرویس پیش‌فرض (Pollinations) CORS باز دارد و از مرورگر مستقیم کار می‌کند — پراکسی نمی‌خواهد؛ فقط برای سرویس‌های دیگرِ بدون CORS:
The default provider is CORS-open and works browser-direct — a proxy is only needed for other providers:
- **آسان‌ترین راه:** از اپ‌های نیتیو (دسکتاپ / iOS / اندروید) استفاده کن — نیاز به پراکسی ندارند.
- **یا** یک Worker رایگان Cloudflare بساز و آدرسش را در تنظیمات وارد کن → راهنمای کامل: [`proxy/README.md`](./proxy/README.md)
- دکمه **«تست اتصال»** در تنظیمات وضعیت اتصال را دقیق نشان می‌دهد (موفق/خطا + دلیل).

## 🛠️ Run locally | اجرای محلی

```bash
cd MatinAIWeb
python3 -m http.server 8080
# باز کن: http://localhost:8080
```

نیازی به build یا نصب چیزی نیست — فقط HTML/CSS/JS خالص.
No build step needed — pure HTML/CSS/JS.

## 🔒 Security | امنیت

- پیش‌فرض هیچ کلیدی ندارد؛ اگر کلید سرویس دیگری بگذاری، فقط در `localStorage` همین دستگاه ذخیره می‌شود و مستقیم به همان سرویس ارسال می‌شود.
  The keyless default stores nothing; an optional key lives only in this device's `localStorage` and is sent directly to your chosen service.
- کلید را با کسی به اشتراک نگذار · Never share your key.
