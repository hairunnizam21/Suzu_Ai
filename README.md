# Suzu_Ai

> Self-hosted Devin-style coding agent on Android. Lite client, full power server.

Suzu_Ai is a personal AI coding assistant inspired by Devin AI, packaged as:

- **Android app (`android/`)** — Kotlin + Jetpack Compose, single hacker-terminal theme, runs on your phone.
- **Backend agent (`server/`)** — FastAPI agent loop on your own server (8 GB RAM is plenty). Talks to Claude / OpenAI-compatible APIs, executes shell, reads/writes files, decompiles & recompiles APKs.

The phone is a thin client. All heavy work — agent loop, tool execution, APK reverse engineering — runs on your server.

## Status

Early build. The architecture, server agent loop, and Android skeleton are in place. See `docs/ARCHITECTURE.md`.

## Quick start

### Server (Ubuntu 22.04, 8 GB RAM, 4 vCPU)

```bash
git clone https://github.com/hairunnizam21/Suzu_Ai.git
cd Suzu_Ai/server
sudo ./install.sh         # installs JDK 17, Android build-tools, apktool, jadx, apksigner, python3.11
./run.sh                  # starts FastAPI on :8765
```

Then note your server's public IP (or Cloudflare Tunnel hostname) and the auth token printed by `install.sh`.

### Android app

```bash
cd Suzu_Ai/android
./gradlew assembleDebug    # output at app/build/outputs/apk/debug/app-debug.apk
```

Install the APK, open it, then:

1. Sidebar → **Settings → Server config**: paste `https://your.server:8765` and the auth token.
2. Sidebar → **Settings → API Providers**: add at least one provider (Anthropic, OpenAI, AfiqStoreAPI, OpenRouter, ...). You can add multiple API keys per provider — Suzu rotates automatically when one hits a quota.
3. Sidebar → **New Chat**, start talking.

## Features

- **Multi-provider** with auto-fallback on quota/auth errors.
- **Agent loop** with configurable `max_iterations` (default 100, was 12 — that was the bug).
- **Tools**: `shell`, `read`, `write`, `edit`, `grep`, `ls`, `apk_decompile`, `apk_recompile`, `apk_sign`.
- **Streaming SSE** with defensive JSON parsing (no more `JsonNull is not a JsonObject` crashes).
- **Chat history** persisted locally on the phone (Room DB) — rename, delete, resume.
- **Copy** on every message, code block, and tool output.

## License

MIT. Use it however you want.
