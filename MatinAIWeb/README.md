# 🌐 Matin AI Web — متین ای‌آی وب

<div align="center">
<img src="icons/icon-192.png" width="90" alt="Matin AI"/>
<br/>
<b>دستیار هوشمند فارسی — رایگان با OpenRouter — Persian AI assistant powered by OpenRouter (free)</b>
</div>

نسخه وب اپ Matin AI با ظاهری شبیه ChatGPT و Claude — یک اپ PWA سبک که روی هر دستگاهی اجرا می‌شود.
The web version of Matin AI with a ChatGPT/Claude-style UI — a lightweight PWA that runs anywhere.

## ✨ Features | امکانات

- 💬 **پاسخ‌های استریم زنده** (مثل ChatGPT حرف‌به‌حرف تایپ می‌شود) — Live streaming responses
- 🗂️ **تاریخچه گفتگوها** با سایدبار — Chat history with a sidebar
- 📝 **رندر مارک‌داون** + بلوک کد با دکمه کپی — Markdown + code blocks with copy buttons
- 🤖 **پیش‌فرض: OpenRouter (رایگان)** — سرور و مدل خودکار پر شده‌اند؛ فقط یک کلید رایگان لازم داری · Defaults pre-configured — only a free key needed
- 🔄 سازگار با OpenAI، Gemini، Groq و هر سرویس سازگار — Works with any OpenAI-compatible service
- 📱 **PWA**: روی آیفون و اندروید نصب می‌شود — Installable on iPhone & Android
- 🌓 تم تیره، RTL کامل فارسی، دوزبانه — Dark theme, full Persian RTL, bilingual

## 🚀 Use online | استفاده آنلاین

**آدرس:** [https://matin-mohebi.github.io/Matin-mohebi/](https://matin-mohebi.github.io/Matin-mohebi/)

1. یک کلید **رایگان** بساز: [openrouter.ai/keys](https://openrouter.ai/keys) — ثبت‌نام با گوگل/گیت‌هاب، بعد «Create Key»
   Create a **free** key at openrouter.ai/keys (sign in with Google/GitHub).
2. اپ را باز کن → **تنظیمات** → کلید را وارد کن → ذخیره (آدرس سرور و مدل خودکار پر شده‌اند)
   Open the app → **Settings** → paste the key → Save (server & model are pre-filled)
3. چت را شروع کن! 🎉 Start chatting!

> ⚠️ سرویس **GitHub Models در تاریخ ۳۰ ژوئیه ۲۰۲۶ به‌طور کامل تعطیل شد**؛ نسخه‌های قدیمی اپ دیگر به آن وصل نمی‌شوند. نسخه ۱.۴.۰ به‌صورت خودکار تنظیمات قبلی را به OpenRouter منتقل می‌کند.
> **GitHub Models was fully retired on July 30, 2026** — old app versions can no longer connect; v1.4.0 migrates saved settings to OpenRouter automatically.

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

دیتای اپ (چت‌ها و کلید) روی خود دستگاه ذخیره می‌شود · Data stays on your device.

## 🤖 سرویس پیش‌فرض: OpenRouter (رایگان) | Default provider

پیش‌فرض اپ (نسخه ۱.۴.۰ به بعد)، **OpenRouter** است — رایگان، با CORS (مرورگر مستقیم وصل می‌شود):
The default backend (since v1.4.0) is **OpenRouter** — free and CORS-enabled:

| تنظیم | مقدار |
|:---|:---|
| Base URL | `https://openrouter.ai/api/v1` |
| Model | `openrouter/free` (خودکار بهترین مدل رایگان لحظه را انتخاب می‌کند) |
| API Key | کلید رایگان شما از [openrouter.ai/keys](https://openrouter.ai/keys) |

مدل‌های رایگان پیشنهادی (اگر مدل مشخصی خواستی): `google/gemma-4-31b-it:free` · `nvidia/nemotron-3-ultra-550b-a55b:free` · `thinkingmachines/inkling:free` — لیست زنده: [openrouter.ai/models?q=free](https://openrouter.ai/models?q=free)

هر سرویس سازگار با OpenAI هم کار می‌کند (OpenAI، Gemini، Groq، Ollama محلی و…) — فقط Base URL و کلید را عوض کن.
Any OpenAI-compatible service works too — just change the Base URL and key.

## ⚠️ CORS در نسخه وب | CORS on the web

OpenRouter از مرورگر مستقیم قابل استفاده است (CORS فعال است) و پراکسی نمی‌خواهد — فقط برای سرویس‌های دیگرِ بدون CORS:
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

- کلید/توکن فقط در `localStorage` همین دستگاه ذخیره می‌شود و مستقیم به سرویس انتخابی شما ارسال می‌شود.
  The key lives only in this device's `localStorage` and is sent directly to your chosen service.
- توکن را با کسی به اشتراک نگذار · Never share your token.
