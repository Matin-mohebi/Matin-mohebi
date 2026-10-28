#!/usr/bin/env bash
# Build Matin AI desktop apps (Windows + Linux) with Neutralino.
# Usage: bash build-desktop.sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/MatinAIDesktop"

# 1) Sync the web app into the desktop bundle
rm -rf web
cp -r "$ROOT/MatinAIWeb" web

# 2) Install Neutralino CLI if missing
if ! command -v neu >/dev/null 2>&1; then
  echo "[*] Installing @neutralinojs/neu CLI…"
  npm install -g @neutralinojs/neu@11.3.1 --silent
fi

# 3) Download Neutralino binaries + build
neu update
neu build --release

echo "[OK] Desktop builds:"
ls -la dist/
