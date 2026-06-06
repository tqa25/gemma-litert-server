# Termux Client Test Plan

## Goal

Verify the first IPC slice for the on-device agent project:

```text
Termux client -> Android backend app -> Gemma 4 E4B IT LiteRT -> response JSON
```

This phase intentionally does not add voice, Accessibility, Shizuku, or CLI-agent execution yet.

## Preconditions

1. APK is installed on ROG Phone 6.
2. Android app is open.
3. For mock test: press `Start Mock Server`.
4. For real model test: tap `Select/Copy Model File`, choose `gemma-4-E4B-it.litertlm`, and wait until the app reports the model is ready in app storage.
5. Press `Start LiteRT GPU Server` for the fast path. Use `Start LiteRT CPU Server` only for comparison/debug.

## Termux setup

```bash
pkg update
pkg install python curl
```

## Test sequence

### 1. Health

```bash
python3 termux-bridge/client.py health
```

Pass if:

```text
status = ok
model_loaded = true
engine = mock-android or litert-android
```

### 2. Text generation

```bash
python3 termux-bridge/client.py generate-text   --prompt "Say hello in Vietnamese. Keep it short."   --max-tokens 64   --temperature 0.2
```

Pass if response JSON contains:

```text
response
timing.total_ms
meta.engine
```

### 3. Image generation

Fast OCR for UI/social screenshots:

```bash
pkg install python-pillow
python3 termux-bridge/client.py generate-image   --image /sdcard/Download/test.png   --ocr-mode fast
```

Full OCR for dense pages/documents:

```bash
python3 termux-bridge/client.py generate-image   --image /sdcard/Download/test.png   --ocr-mode full
```

Use lower-level `--preset`, `--prompt`, `--max-tokens`, `--resize-max-edge`, or `--jpeg-quality` flags to override an OCR mode.

For repeated runs, prefer the benchmark helper:

```bash
python3 termux-bridge/client.py benchmark-image   --image /sdcard/Download/test.png   --runs 5   --ocr-mode fast
```

Pass if:

```text
response is non-empty
meta.engine = litert-android-gpu for GPU test
meta.has_image = true
meta.image_bytes > 0
timing.total_ms exists
Android app latest-request panel shows engine, image bytes, inference time, and total time
```

For mock server, the response text should include:

```text
image_bytes=...
```

If mock returns `no_image`, stop and fix the HTTP upload path before testing LiteRT.

## Metrics to record

For each run, save:

```text
engine
model_load_ms from /health
client_total_ms from termux-bridge/benchmark.jsonl
server timing.inference_ms
server timing.total_ms
response quality note
image size and screenshot type
image_original_bytes, image_upload_bytes, image_preprocess_ms, image_resized from termux-bridge/benchmark.jsonl
```

## Decision gate

Continue to Termux CLI wrapper only after:

```text
Mock server passes health/text/image.
LiteRT server passes health/text/image.
Image latency and quality are recorded for at least 3 real screenshots.
Compressed-image latency and OCR quality are compared against the original upload on the same screenshot.
```
