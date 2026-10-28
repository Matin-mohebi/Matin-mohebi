# 🖥️ Matin AI Desktop — متین ای‌آی دسکتاپ

نسخه دسکتاپ (ویندوز / لینوکس / مک) با **Electron** — همان رابط کاربری سبک ChatGPT با موتور GitHub Models.

The desktop version (Windows / Linux / macOS) built with **Electron** — same ChatGPT-style UI, powered by GitHub Models.

## ✨ ویژگی‌ها | Features

- 🔌 **بدون مشکل CORS** — درخواست‌ها از پروسه اصلی Electron با پروکسی داخلی (`matinai://`) انجام می‌شود و به GitHub Models وصل می‌شود؛ بدون نیاز به پراکسی خارجی.
- ⚡ پاسخ‌های استریم زنده (SSE)
- 💾 چت‌ها و کلید روی خود دستگاه (localStorage وب‌ویو)
- 🔐 بدون Node Integration · Sandbox فعال · فقط اتصال خروجی به سرور AI

## 📦 دانلود | Download

از [Releases](https://github.com/Matin-mohebi/Matin-mohebi/releases) آخرین نسخه را بگیرید:

| فایل | پلتفرم | روش نصب |
|:---|:---|:---|
| `MatinAI-Windows-Setup-x64.exe` | ویندوز ۱۰/۱۱ | اجرا کن — نصب خودکار |
| `MatinAI-Windows-x64.zip` | ویندوز (پرتابل) | باز کن → `Matin AI.exe` |
| `MatinAI-Linux-x64.AppImage` | لینوکس | `chmod +x *.AppImage && ./…` |
| `MatinAI-Linux-x64.zip` | لینوکس (پرتابل) | باز کن → `matin-ai` |
| `MatinAI-macOS-arm64.zip` / `-x64.zip` | مک (Apple Silicon / Intel) | باز کن → Matin AI.app |

> macOS: چون اپ امضا نشده، اولین اجرا: راست‌کلیک → Open (یا System Settings → Privacy & Security → Open Anyway).

## 🛠️ اجرا از سورس | Run from source

```bash
git clone https://github.com/Matin-mohebi/Matin-mohebi.git
cd Matin-mohebi/MatinAIDesktop
npm install
npm start          # اجرای محلی
npm run dist       # ساخت پکیج‌های نصب
```

## 🏗️ نحوه کار پروکسی داخلی | How the built-in proxy works

مرورگرهای معمولی به دلیل CORS نمی‌توانند مستقیم به `models.github.ai` وصل شوند؛ اما Electron درخواست را در **پروسه اصلی** انجام می‌دهد:

```
Renderer (app-ui)  --fetch-->  matinai://models.github.ai/inference/chat/completions
                                     │  (protocol.handle در main.js)
                                     ▼
Main process  --https-->  https://models.github.ai/inference/chat/completions
```

استریم SSE هم از همین مسیر پاس داده می‌شود؛ بنابراین تایپ زنده جواب مثل ChatGPT کار می‌کند.
