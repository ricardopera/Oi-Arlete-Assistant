# Quickstart: Arlete (Android TV)

## Prerequisites

- Android Studio Hedgehog+ with SDK 34
- Device/Emulator Android TV (minSdk 26)
- Secrets:
  - local.properties: PORCUPINE_ACCESS_KEY=...
  - gradle.properties or env: LLM_API_KEY=...
- Assets/models:
  - app/src/main/res/raw/arlete_wake_word.ppn
  - app/src/main/assets/model/vosk-pt-br/

## Build & Run

1. Open project in Android Studio.
2. Configure Run for Android TV emulator or physical device.
3. Grant permissions on first run: RECORD_AUDIO, WAKE_LOCK, FOREGROUND_SERVICE, INTERNET, RECEIVE_BOOT_COMPLETED.
4. Start app; service enters foreground and waits for wake word.

## Smoke Test

1. Say “Oi Arlete!” → hear a short beep; listening starts.
2. Say “tocar playlist relaxante no YouTube Music” → app opens playing/searching.
3. Say “parar” → assistant exits active listening.

## HTTP/MCP Contract Test (MockWebServer)

- Start MockWebServer on instrumented test; validate request against `contracts/mcp.http.json` and response against `contracts/mcp.http.response.json`.

## Notes

- If TTS fails, retry once; on persistent failure, play fallback tone and log error.
- Without internet, remote path is disabled; local responses continue.
