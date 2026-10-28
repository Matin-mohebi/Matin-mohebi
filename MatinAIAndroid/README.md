# 🤖 Matin AI for Android | متین AI برای اندروید

<div align="center">
<img src="https://raw.githubusercontent.com/Matin-mohebi/Matin-mohebi/main/assets/matin-ai-icon.png" width="110" alt="Matin AI icon"/>
</div>

A lightweight **Persian AI assistant** app built with **Kotlin + Jetpack Compose** — now with live streaming, chat history and **OpenRouter (free)** as the default AI.
اپ دستیار هوشمند **فارسی** و سبک با **Kotlin + Jetpack Compose** — حالا با پاسخ‌های استریم زنده، تاریخچه چت و **OpenRouter (رایگان)** به‌عنوان پیش‌فرض.

## ✨ Features | امکانات

| | فارسی | English |
|:---:|:---|:---|
| ⚡ | پاسخ‌های استریم زنده (کلمه‌به‌کلمه مثل ChatGPT) | Live streaming responses, word by word |
| 🗂️ | تاریخچه گفتگوها با منوی کشویی | Chat history with a navigation drawer |
| 🤖 | پیش‌فرض: **OpenRouter (رایگان)** — سرور و مدل خودکار پر شده‌اند | Default: **OpenRouter (free)** — server & model pre-filled |
| 🌐 | سازگار با هر سرویس OpenAI-compatible | Works with any OpenAI-compatible service |
| ✏️ | تنظیم آدرس سرور و مدل از داخل اپ | Editable server URL & model in settings |
| 🔑 | کلید فقط روی خود گوشی ذخیره می‌شود | Key stored locally on the device |
| 📱 | سبک — اندروید ۶ به بالا (minSdk 23) | Lightweight, Android 6.0+ |

## 📥 Download | دانلود

- **GitHub Releases (پیشنهادی):** [⬇️ Download latest APK](https://github.com/Matin-mohebi/Matin-mohebi/releases)
- **GitHub Actions:** هر push یک بیلد تازه می‌سازد — از تب Actions آرتیفکت `MatinAI-APK` را دانلود کن.
  Every push builds fresh APKs — grab the `MatinAI-APK` artifact from the Actions tab.

## ⚙️ Setup | راه‌اندازی

1. APK را نصب کن (اجازه «نصب از منابع ناشناس» را بده). Install the APK (allow unknown sources).
2. از **☰ تاریخچه** یک چت جدید بساز، بعد **تنظیمات** را باز کن. Open **Settings** from the top bar.
3. یک کلید **رایگان** از [openrouter.ai/keys](https://openrouter.ai/keys) بساز (ثبت‌نام با گوگل/گیت‌هاب) و در تنظیمات وارد کن — آدرس سرور و مدل خودکار پر شده‌اند. Get a **free** key at openrouter.ai/keys and paste it in Settings (server & model are pre-filled).
   > ⚠️ سرویس GitHub Models در ۳۰ ژوئیه ۲۰۲۶ تعطیل شد؛ نسخه ۱.۴.۰ تنظیمات قدیمی را خودکار به OpenRouter منتقل می‌کند · GitHub Models retired 2026-07-30 — v1.4.0 migrates old settings automatically.
4. ذخیره کن و بپرس! 🎉 Save and start chatting!

### نمونه تنظیمات | Example settings

| سرویس Service | Base URL | Model نمونه |
|:---|:---|:---|
| **OpenRouter** (پیش‌فرض، رایگان) | `https://openrouter.ai/api/v1` | `openrouter/free` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4.1-mini` |
| Groq | `https://api.groq.com/openai/v1` | `llama-3.3-70b-versatile` |

## 🛠️ Build from source | ساخت از سورس

```bash
cd MatinAIAndroid
./gradlew assembleRelease   # signed with debug key if keystore.properties is absent
./gradlew assembleDebug
```

Release signing: create `MatinAIAndroid/keystore.properties` (git-ignored):

```properties
storeFile=matin-ai-release.jks
storePassword=...
keyAlias=matin-ai
keyPassword=...
```

## 🔒 Security | امنیت

- کلید فقط در `SharedPreferences` خود گوشی ذخیره می‌شود؛ هیچ کلیدی داخل اپ یا مخزن قرار ندارد.
  The key lives only in on-device `SharedPreferences`; no key ships with the app or the repo.
- هرگز کلید را کامیت نکنید — `keystore.properties` و `*.jks` در `.gitignore` هستند.
  Never commit your key — they are git-ignored.

Package: `ir.matincode.matinai` · Version: `1.2.0`
