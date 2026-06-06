# System Architecture

Last updated: 2026-06-06

## System Goal

`gemma-litert-server` provides a local OCR/image-understanding backend for Android devices, currently validated on ROG Phone 6 with Gemma 4 E4B IT LiteRT-LM.

The current stable slice is:

```text
Termux CLI or Android OCR Runner
  -> localhost HTTP API
  -> Android foreground backend service
  -> LiteRT-LM Android engine
  -> JSON response with text, timing, metadata, and diagnostics
```

The repo also contains an older JVM server skeleton for Oracle VM experiments. The Android backend is the active, device-validated runtime.

## High-Level Runtime

```text
User
  |
  | chooses image / command
  v
+-----------------------+       +--------------------------+
| Termux client         |       | Android OCR Runner UI    |
| termux-bridge/client  |       | MainActivity             |
+-----------------------+       +--------------------------+
          |                                  |
          | HTTP multipart or JSON          | HTTP multipart
          |                                  |
          +------------------+---------------+
                             |
                             v
                 http://127.0.0.1:8765
                             |
                             v
                 +-------------------------+
                 | HttpApiServer           |
                 | NanoHTTPD               |
                 +-------------------------+
                             |
                             v
                 +-------------------------+
                 | GemmaRunner             |
                 | Mock or LiteRT Android  |
                 +-------------------------+
                             |
                             v
                 +-------------------------+
                 | LiteRT-LM Engine        |
                 | GPU or CPU backend      |
                 +-------------------------+
                             |
                             v
                 JSON response + diagnostics
```

## Modules

### Android Backend App

Path: `android-backend/`

Responsibilities:

- Provide a small Android UI.
- Copy the selected model into app-private storage.
- Start/stop a foreground service.
- Expose localhost HTTP API through NanoHTTPD.
- Run mock, LiteRT GPU, or LiteRT CPU backend.
- Show latest-request diagnostics.
- Provide an in-app OCR Runner that calls the same HTTP API.
- Persist recent OCR results locally as text and metadata so users can revisit/copy results without running inference again.

Important files:

- `MainActivity.java`: UI, model picker, server controls, OCR Runner, image compression for Fast OCR, copy-to-clipboard, OCR History persistence/rendering.
- `ServerService.java`: foreground service lifecycle, runner creation, server lifecycle.
- `HttpApiServer.java`: `GET /health`, `POST /generate`, request parsing, benchmark logging, diagnostics updates.
- `LiteRtGemmaRunner.java`: LiteRT-LM Android engine wrapper.
- `MockGemmaRunner.java`: mock backend for request-path testing.
- `RequestDiagnostics.java`: latest request state for UI and `/health`.
- `BenchmarkLogger.java`: Android-side JSONL request log.
- `BackendConfig.java`: host, port, model file path, limits.

### Termux Bridge

Path: `termux-bridge/`

Responsibilities:

- Call Android backend over localhost from Termux.
- Exercise health/text/image flows without Android UI OCR Runner.
- Upload images as multipart files.
- Optionally preprocess images with Pillow.
- Run repeated benchmarks and summarize timing.
- Write client-side benchmark JSONL.

Important files:

- `client.py`: CLI implementation.
- `test_client.py`: unit tests for option resolution, multipart helper, and benchmark summary.
- `README.md`: Termux usage.

### JVM Server Skeleton

Path: `src/main/`

Responsibilities:

- Original Oracle VM HTTP server skeleton.
- Mock server and LiteRT-LM JVM integration boundary.
- Useful for API shape and early experiments, but not the currently validated Android runtime.

## API Contract

### GET /health

Purpose:

- Confirm server is running.
- Report selected engine and model state.
- Report latest request diagnostics.

Current Android response shape:

```json
{
  "status": "ok",
  "engine": "litert-android-gpu",
  "model_loaded": true,
  "model_path": "/data/user/0/dev.gemma.androidbackend/files/models/gemma-4-E4B-it.litertlm",
  "model_load_ms": 12345,
  "uptime_ms": 60000,
  "last_request": {
    "status": "success",
    "request_id": "...",
    "engine": "litert-android-gpu",
    "has_image": true,
    "image_bytes": 125437,
    "inference_ms": 14000,
    "total_ms": 14010,
    "response_chars": 330,
    "error": "",
    "updated_at_ms": 123456789
  }
}
```

### POST /generate

Preferred image request format:

```text
multipart/form-data
fields:
  prompt
  max_tokens
  temperature
file:
  image
```

Legacy JSON image format remains supported:

```json
{
  "prompt": "Extract visible text from this image",
  "image_base64": "...",
  "max_tokens": 256,
  "temperature": 0.1
}
```

Response shape:

```json
{
  "request_id": "...",
  "response": "recognized text",
  "timing": {
    "inference_ms": 14000,
    "total_ms": 14010
  },
  "meta": {
    "engine": "litert-android-gpu",
    "has_image": true,
    "prompt_chars": 58,
    "image_bytes": 125437,
    "response_chars": 330,
    "output_tokens_estimate": 85
  }
}
```

## Request Flows

### Termux Fast OCR

```text
client.py generate-image --ocr-mode fast
  -> resolve ocr mode:
       preset speed
       prompt: concise OCR
       max_tokens: 256
       image max edge: 1280
       JPEG quality: 85
  -> load image bytes
  -> Pillow resize/compress if needed
  -> multipart POST /generate
  -> print JSON
  -> append termux-bridge/benchmark.jsonl
```

### Termux Full OCR

```text
client.py generate-image --ocr-mode full
  -> resolve ocr mode:
       preset accuracy
       prompt: extract all text and preserve line breaks
       max_tokens: 768
       original image bytes
  -> multipart POST /generate
  -> print JSON
  -> append termux-bridge/benchmark.jsonl
```

### Android OCR Runner

```text
MainActivity
  -> user selects image
  -> user selects Fast OCR or Full OCR
  -> Fast OCR compresses to 1280px JPEG 85
  -> Full OCR uploads original bytes
  -> multipart POST to backend URL, default http://127.0.0.1:8765
  -> parse response
  -> show engine, image bytes, inference ms, total ms, recognized text
  -> save successful result into app-private OCR History
  -> allow copy-to-clipboard
```

### Android Backend Generate

```text
HttpApiServer.handleGenerate
  -> parse NanoHTTPD body
  -> read prompt/max_tokens/temperature from multipart params or JSON
  -> read image from multipart file path, fallback to image_base64
  -> validate prompt and image size
  -> create GenerationRequest
  -> runner.generate(request)
  -> log benchmark row
  -> update RequestDiagnostics
  -> return JSON response
```

### LiteRT Runner

```text
LiteRtGemmaRunner.initialize
  -> choose Backend.GPU or Backend.CPU
  -> configure EngineConfig with same backend for model and vision
  -> initialize Engine

LiteRtGemmaRunner.generate
  -> create Contents from image bytes plus text, or text only
  -> create ConversationConfig with concise OCR backend system text
  -> create a new Conversation for this request
  -> send message
  -> extract Content.Text from response
  -> return GenerationResult
```

## Model Storage

Earlier direct access to `/sdcard/Models/gemma-4-E4B-it.litertlm` failed with Android permission errors. The current model flow is:

```text
User taps Select/Copy Model File
  -> ACTION_OPEN_DOCUMENT file picker
  -> copy selected model stream into app-private storage
  -> final path:
     /data/user/0/dev.gemma.androidbackend/files/models/gemma-4-E4B-it.litertlm
  -> LiteRT server starts from that private path
```

## Engines

Supported Android runtime engines:

- `mock`: no model, verifies HTTP path and image upload.
- `litert-gpu`: normal fast path, returns `litert-android-gpu`.
- `litert-cpu`: comparator/debug path, returns `litert-android-cpu`.

Normal testing should use `Start LiteRT GPU Server`.

## OCR Modes And Image Presets

```text
fast:
  preset: speed
  prompt: Extract visible text from this image. Return concise text.
  max_tokens: 256
  image: resize longest edge to 1280, JPEG 85
  use for: UI/social screenshots, repeated quick checks

full:
  preset: accuracy
  prompt: Extract all visible text from this image. Preserve line breaks. Return only the text.
  max_tokens: 768
  image: original upload
  use for: dense documents, small text, line-sensitive OCR
```

Lower-level image presets:

```text
speed:
  max_edge: 1280
  jpeg_quality: 85

accuracy:
  max_edge: none
  jpeg_quality: none
```

Termux explicit flags override mode/preset defaults.

## Diagnostics And Benchmarks

Android-side:

- `RequestDiagnostics` keeps latest request success/error state.
- `MainActivity` refreshes latest request panel every second while resumed.
- `/health.last_request` exposes the same latest state.
- `BenchmarkLogger` appends per-request JSONL rows in Android app storage.

Termux-side:

- `client.py` measures `client_total_ms`.
- Server response reports `timing.inference_ms` and `timing.total_ms`.
- `benchmark-image` prints per-run progress to stderr and final JSON summary to stdout.
- `termux-bridge/benchmark.jsonl` records image original bytes, upload bytes, preprocess time, resize state, OCR mode, preset, max tokens, server timing, engine, response chars, and request id.

## Build Architecture

Root Gradle build includes:

- Android application module: `android-backend`.
- JVM application skeleton for Oracle VM experiments.

Android dependencies:

```text
com.google.ai.edge.litertlm:litertlm-android:latest.release
org.nanohttpd:nanohttpd:2.3.1
```

APK workflow:

```text
.github/workflows/android-backend-apk.yml
  -> setup JDK 21
  -> setup Android SDK
  -> ./gradlew :android-backend:assembleDebug --stacktrace
  -> upload gemma-android-backend-debug-apk
```

Important build caveat:

- Local Oracle ARM Gradle can fail on Android AAPT2 x86 loader before compile.
- GitHub Actions x86_64 is the APK build source of truth.
- For build failures, follow `GITHUB_ACTIONS_APK_DEBUG_RUNBOOK.md`.

## Extension Points

Likely next architecture additions:

- `GET /diagnostics`: dedicated diagnostics endpoint if `/health.last_request` becomes too crowded.
- `POST /generate-stream`: SSE or chunked streaming for first-token latency.
- OCR quality dataset/results doc: visual comparison across real screenshots.
- Phone automation layer: later Termux/Codex/Accessibility/Shizuku integration after OCR backend stabilizes.
- External backend URL support: Android OCR Runner already accepts a backend URL, so it can point to another host later.

## Documentation Discipline

When changing the codebase:

- Update `PROGRESS.md` with what changed, verification, open risks, and next steps.
- Update this file when request flow, module boundaries, runtime path, API shape, build flow, or engine behavior changes.
- Update `HANDOFF.md` before session handoff or after major milestones.

## Local Persistence

### Android OCR History

```text
MainActivity successful OCR response
  -> create OcrHistoryEntry
  -> prepend to getFilesDir()/ocr_history.json
  -> trim to 20 entries
  -> render OCR History buttons in the app
  -> tap history item to restore text + metadata without calling /generate
```

Stored fields:

- timestamp
- OCR mode
- backend URL
- engine
- source image display name
- image bytes
- inference ms
- total ms
- OCR text

Source image bytes are not stored.
