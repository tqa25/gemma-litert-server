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

The next active slice is Shizuku-backed phone automation for a Chrome Discover + Gemini summary workflow. Streaming generation is intentionally postponed.

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

## Automation Runtime

```text
Termux automation client
  -> http://127.0.0.1:8765/automation/*
  -> HttpApiServer
  -> AutomationController
  -> ShizukuShellExecutor
  -> Android shell commands: input, screencap, uiautomator, dumpsys, monkey
  -> JSON response and app-private automation run files
```

Android floating overlay:

```text
MainActivity
  -> user grants Display over other apps
  -> FloatingAutomationService
  -> draggable "G" bubble over Chrome/Gemini
  -> menu actions call localhost /automation/*
  -> draggable crosshair saves calibration coordinates
```

The first workflow is not a general autonomous agent. It is a calibrated macro with guardrails for the user's real Chrome workflow:

```text
Chrome Discover feed already open
  -> tap calibrated article card
  -> wait for article page in the same Chrome tab
  -> input keyevent --longpress KEYCODE_HOME
  -> Gemini overlay
  -> tap "Tóm tắt trang" / "Summarize page" by XML, fallback coordinate
  -> tap copy by XML/content-desc, fallback coordinate
  -> read ClipboardManager
  -> save summary and metadata locally
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
- Expose initial Shizuku-backed automation primitives and workflow execution over localhost HTTP.

Important files:

- `MainActivity.java`: UI, model picker, server controls, OCR Runner, image compression for Fast OCR, copy-to-clipboard, OCR History persistence/rendering.
- `ServerService.java`: foreground service lifecycle, runner creation, server lifecycle.
- `HttpApiServer.java`: `GET /health`, `POST /generate`, request parsing, benchmark logging, diagnostics updates.
- `AutomationController.java`: automation status, stop flag, primitives, calibration config, Chrome Discover + Gemini workflow, local run storage.
- `ShizukuShellExecutor.java`: executes shell commands through Shizuku after binder and permission checks.
- `AutomationConfig.java`: default package/timing/coordinate config for automation.
- `FloatingAutomationService.java`: WindowManager overlay for fullscreen Chrome calibration and manual automation controls.
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
- Call automation endpoints through `automation_client.py`.
- Save screenshots/XML returned from automation endpoints for debugging.

Important files:

- `client.py`: CLI implementation.
- `automation_client.py`: CLI for `/automation/*` primitives, calibration, and workflow execution.
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

### Automation API

Automation endpoints are local-only on the same backend port.

Status and config:

```text
GET  /automation/status
GET  /automation/config
POST /automation/stop
```

Primitive actions:

```text
POST /automation/tap              {"x":540,"y":700}
POST /automation/swipe            {"x1":540,"y1":1800,"x2":540,"y2":700,"duration_ms":500}
POST /automation/home             {}
POST /automation/back             {}
POST /automation/longpress-home   {}
POST /automation/wait             {"ms":1000}
GET  /automation/current-app
GET  /automation/screenshot
GET  /automation/screen-xml
POST /automation/open-app         {"package":"com.android.chrome"}
```

Calibration and workflow:

```text
POST /automation/calibrate
  {"key":"chrome_discover_first_article","x":540,"y":700}

POST /automation/workflows/run
  {"workflow":"chrome-discover-gemini-summary-once","debug_capture":true}
```

Important MVP constraints:

- Shizuku must be running and permission must be granted to the app.
- Current workflow starts with Chrome Discover feed already visible.
- Main package guard is `com.android.chrome`.
- Gemini/Google package is allowed because the overlay may surface through Google app.
- Errors should stop the workflow and capture debug files when possible.

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

### Chrome Discover Gemini Summary Once

```text
automation_client.py run chrome-discover-gemini-summary-once
  -> POST /automation/workflows/run
  -> AutomationController loads automation_config.json
  -> verify calibration exists
  -> verify current package is com.android.chrome
  -> tap chrome_discover_first_article
  -> wait article_load_ms
  -> verify current package is still Chrome
  -> longpress HOME
  -> wait gemini_open_ms
  -> dump XML and tap "Tóm tắt trang" / "Summarize page" if visible
  -> otherwise tap calibrated gemini_summary_button
  -> wait gemini_summary_ms
  -> dump XML and tap copy if visible
  -> otherwise tap calibrated gemini_copy_button
  -> read Android ClipboardManager
  -> save summary.txt, metadata.json, run.json, workflow_log.jsonl
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
dev.rikka.shizuku:api:13.1.5
dev.rikka.shizuku:provider:13.1.5
```

Gradle uses `android.useAndroidX=true` because Shizuku provider pulls AndroidX annotation.

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
- Automation hardening: replace reflection-based Shizuku shell process with a Shizuku UserService if runtime tests show reflection is blocked or unreliable.
- Batch workflow: `chrome-discover-gemini-summary-batch` after one-article workflow is stable on device.
- Accessibility Service: later option if XML/copy/tap reliability is insufficient through Shizuku shell alone.
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

### Automation Runs

```text
AutomationController successful run
  -> getFilesDir()/automation_runs/{run_id}/
  -> run.json
  -> workflow_log.jsonl
  -> article_001/summary.txt
  -> article_001/metadata.json
```

With `debug_capture=true` or on error, the run may also include screenshots, XML snapshots, and `error.json` under the article directory.
