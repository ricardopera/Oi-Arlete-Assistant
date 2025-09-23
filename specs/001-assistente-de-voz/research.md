# Phase 0 Research: Arlete (Android TV)

Date: 2025-09-22

## Unknowns to Resolve (from Technical Context)

- MCP base URL, auth method, timeout policies
- Exact YouTube Music intent/URI formats across devices
- Preferred PT-BR TTS voice, speed, offline availability
- Simple vs complex classification rules/thresholds
- Target WER and latency budgets for PT-BR ASR

## Technology Best Practices

### Wake Word: Porcupine SDK

- Decision: Use Porcupine with custom PPn ("Oi Arlete") and tuned sensitivity.
- Rationale: Low-latency, on-device detection, ARM64-v8a support.
- Alternatives: Snowboy (deprecated), Vosk keyword spotting (less optimized).

### ASR Offline: VOSK Android

- Decision: VOSK PT-BR model in assets with lazy load when entering active listen.
- Rationale: Offline, acceptable WER for casual commands; simple API.
- Alternatives: Kaldi custom builds (complex), Android SpeechRecognizer (online, privacy concerns).

### LLM Local: MediaPipe + Gemma 2B

- Decision: Use MediaPipe LLM Inference with small Gemma 2B quantized for quick local replies.
- Rationale: Low latency, local privacy for simple Q&A.
- Alternatives: ONNX Runtime with TinyLlama; implement later if needed.

### LLM Remoto/MCP: Retrofit/Ktor

- Decision: Retrofit + OkHttp with interceptors (timeouts, retries minimal) to call MCP/LLM.
- Rationale: Mature ecosystem, easy mocking with MockWebServer.
- Alternatives: Ktor client (lighter), may swap if footprint demands.

### TTS: Android TextToSpeech

- Decision: Use built-in TTS setLanguage(new Locale("pt", "BR")), check voices, fallback to default.
- Rationale: Zero extra deps, offline voices may be available.
- Alternatives: Google Cloud TTS (online, not desired initially).

### Background & Resilience

- Decision: ForegroundService + BroadcastReceiver(BOOT_COMPLETED). Optional WorkManager for health checks.
- Rationale: Meets minSdk 26 requirements, common Android patterns.
- Alternatives: JobScheduler only (insufficient for persistent listening).

## Integration Patterns

- Intents: Validate `Intent.ACTION_VIEW` with `ytmusic://` or search uri; fallback to app package launch + query.
- HTTP/MCP: JSON schemas versioned, 5s connect / 15s read; exponential backoff minimal for idempotent GET.
- TTS: Pre-warm engine on service start; handle onInit callbacks; queue flush on new speech.

## Privacy & Logging

- No audio persistence; only ephemeral buffers.
- Logs: minimal, no PII; errors with codes and context (no transcripts).

## Risks & Mitigations

- Performance on TV boxes: choose quantized models, stop ASR when idle; cap thread usage.
- Wake word false positives: tune sensitivity; add short confirmation window.
- YouTube Music variability: implement robust fallback search.
- Licensing: Verify Porcupine/VOSK/MediaPipe licenses; include attributions.

## Decisions Summary

- Keep single module `:app`.
- Tests-first for contracts (MCP), intents, TTS.
- Integration-first smoke suite in CI emulator.
