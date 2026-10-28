# 🌐 Matin AI Web — متین ای‌آی وب

<div align="center">
<img src="icons/icon-192.png" width="90" alt="Matin AI"/>
<br/>
<b>دستیار هوشمند فارسی با هوش مصنوعی گیت‌هاب — Persian AI assistant powered by GitHub Models</b>
</div>

نسخه وب اپ Matin AI با ظاهری شبیه ChatGPT و Claude — یک اپ PWA سبک که روی هر دستگاهی اجرا می‌شود.
The web version of Matin AI with a ChatGPT/Claude-style UI — a lightweight PWA that runs anywhere.

## ✨ Features | امکانات

- 💬 **پاسخ‌های استریم زنده** (مثل ChatGPT حرف‌به‌حرف تایپ می‌شود) — Live streaming responses
- 🗂️ **تاریخچه گفتگوها** با سایدبار — Chat history with a sidebar
- 📝 **رندر مارک‌داون** + بلوک کد با دکمه کپی — Markdown + code blocks with copy buttons
- 🤖 **پیش‌فرض: GitHub Models** — فقط با توکن گیت‌هاب خودت رایگان چت کن · Default to GitHub's AI with your own GitHub token
- 🔄 سازگار با OpenAI، OpenRouter و هر سرویس سازگار — Works with any OpenAI-compatible service
- 📱 **PWA**: روی آیفون و اندروید نصب می‌شود — Installable on iPhone & Android
- 🌓 تم تیره، RTL کامل فارسی، دوزبانه — Dark theme, full Persian RTL, bilingual

## 🚀 Use online | استفاده آنلاین

**آدرس:** [https://matin-mohebi.github.io/Matin-mohebi/](https://matin-mohebi.github.io/Matin-mohebi/)

1. توکن گیت‌هاب بساز: [github.com/settings/tokens](https://github.com/settings/tokens) (Generate new token — بدون scope خاص کافی است)
   Create a GitHub token — no special scope needed.
2. اپ را باز کن → **تنظیمات** → توکن را وارد کن → ذخیره
   Open the app → **Settings** → paste token → Save
3. چت را شروع کن! 🎉 Start chatting!

### 📲 Install on iPhone (PWA) | نصب روی آیفون

1. در Safari آدرس اپ را باز کن · Open the URL in Safari
2. دکمه **Share** (مربع با فلش) را بزن · Tap the **Share** button
3. **Add to Home Screen** را انتخاب کن · Choose **Add to Home Screen**
4. اپ مثل یک اپ واقعی تمام‌صفحه اجرا می‌شود ✅ Runs full-screen like a native app

> 💡 اپ اندروید نیتیو هم از [Releases](https://github.com/Matin-mohebi/Matin-mohebi/releases) قابل نصب است.
> A native Android app is also available in Releases.

## 🖥️ Desktop | دسکتاپ (ویندوز / لینوکس / مک)

از [Releases](https://github.com/Matin-mohebi/Matin-mohebi/releases) دانلود کن:
Download from Releases:

- `MatinAI-Windows-x64.zip` → باز کن و `matin-ai-win_x64.exe` را اجرا کن
- `MatinAI-Linux-x64.zip` → `chmod +x matin-ai-linux_x64 && ./matin-ai-linux_x64`
- `MatinAI-macOS.zip` → binary + resources.neu کنار هم باشند

دیتای اپ (چت‌ها و کلید) روی خود دستگاه ذخیره می‌شود · Data stays on your device.

## 🤖 GitHub Models | هوش مصنوعی گیت‌هاب

پیش‌فرض اپ، **GitHub Models** است:
The default backend is **GitHub Models**:

| تنظیم | مقدار |
|:---|:---|
| Base URL | `https://models.github.ai` |
| Model | `openai/gpt-4.1-mini` (یا از چیپ‌های آماده انتخاب کن) |
| API Key | توکن گیت‌هاب شما |

مدل‌های پیشنهادی: `openai/gpt-4.1-mini` · `openai/gpt-4o-mini` · `meta/Llama-4-Scout-17B-16E-Instruct` · `deepseek/DeepSeek-V3-0324`

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
