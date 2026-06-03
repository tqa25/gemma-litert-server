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
5. Press `Start LiteRT Server`.

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

```bash
python3 termux-bridge/client.py generate-image   --image /sdcard/Download/test.png   --prompt "Extract visible text from this image. Return concise text."   --max-tokens 256   --temperature 0.1
```

Pass if:

```text
response is non-empty
meta.has_image = true
timing.total_ms exists
```

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
```

## Decision gate

Continue to Termux CLI wrapper only after:

```text
Mock server passes health/text/image.
LiteRT server passes health/text/image.
Image latency and quality are recorded for at least 3 real screenshots.
```
