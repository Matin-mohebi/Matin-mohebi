# 🤖 Matin AI for Android | متین AI برای اندروید

<div align="center">
<img src="https://raw.githubusercontent.com/Matin-mohebi/Matin-mohebi/main/assets/matin-ai-icon.png" width="120" alt="Matin AI icon"/>
</div>

A lightweight **Persian AI assistant** app built with **Kotlin + Jetpack Compose**.
اپ دستیار هوشمند **فارسی** و سبک، ساخته‌شده با **Kotlin + Jetpack Compose**.

## ✨ Features | امکانات

| | فارسی | English |
|:---:|:---|:---|
| 💬 | رابط چت فارسی، تم تیره زیبا | Persian chat UI with a clean dark theme |
| 🌐 | اتصال به **هر سرویس سازگار با OpenAI** (OpenAI، OpenRouter و...) | Connects to **any OpenAI-compatible API** (OpenAI, OpenRouter, ...) |
| ✏️ | تنظیم آدرس سرور و نام مدل از داخل اپ | Editable server URL & model name from in-app settings |
| 🔑 | کلید API روی خود گوشی ذخیره می‌شود (نه در سرور) | API key is stored locally on the device |
| 📱 | سبک و مناسب دستگاه‌های ضعیف — اندروید ۶ به بالا (minSdk 23) | Lightweight, for Android 6.0+ devices |

## 📥 Download | دانلود

- **GitHub Releases (پیشنهادی):** [⬇️ Download latest APK](https://github.com/Matin-mohebi/Matin-mohebi/releases)
- **GitHub Actions:** any push to `MatinAIAndroid/` builds fresh APKs — open the Actions tab and download the `MatinAI-APK` artifact.

## ⚙️ Setup | راه‌اندازی

1. فایل APK را نصب کنید (اجازه «نصب از منابع ناشناس» را بدهید).
   Install the APK (allow "install from unknown sources").
2. اپ را باز کنید و به **تنظیمات (Settings)** بروید.
   Open the app and go to **Settings**.
3. کلید API، آدرس سرور (Base URL) و نام مدل را وارد کنید و ذخیره کنید.
   Enter your API key, server Base URL and model name, then Save.
4. شروع به گفتگو کنید! 🎉
   Start chatting! 🎉

### نمونه تنظیمات | Example settings

| سرویس Service | Base URL | Model نمونه |
|:---|:---|:---|
| OpenAI | `https://api.openai.com/v1` | `gpt-4.1-mini` |
| OpenRouter (مدل‌های رایگان) | `https://openrouter.ai/api/v1` | `google/gemini-2.0-flash-exp:free` |
| هر سرور سازگار دیگر | `https://your-host/v1` | بسته به سرویس / per service |

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

- کلید API فقط در `SharedPreferences` خود گوشی ذخیره می‌شود؛ هیچ کلیدی داخل اپ یا مخزن قرار ندارد.
  The key lives only in on-device `SharedPreferences`; no key ships with the app or the repo.
- هرگز کلید را در گیت‌هاب کامیت نکنید. فایل‌های `keystore.properties` و `*.jks` در `.gitignore` هستند.
  Never commit your key. `keystore.properties` and `*.jks` are git-ignored.

Package: `ir.matincode.matinai` · Version: `1.1.0`
