# Termux Bridge Client

This is Phase A of the on-device agent plan: Termux calls the Android Gemma backend over localhost.

It does not run Codex/Antigravity yet. It only verifies the IPC path:

```text
Termux -> http://127.0.0.1:8765 -> Android backend -> Gemma 4 LiteRT -> JSON response
```

## Setup in Termux

```bash
pkg update
pkg install python curl
```

Clone or copy this repo folder to Termux, then run from the repo root.

## Test health

Start the Android app and press either `Start Mock Server` or `Start LiteRT Server`, then:

```bash
python3 termux-bridge/client.py health
```

## Test text

```bash
python3 termux-bridge/client.py generate-text   --prompt "Say hello in Vietnamese. Keep it short."   --max-tokens 64
```

## Test image

Fast OCR uses the measured speed path: resize to 1280px, JPEG 85, concise output, and 256 max tokens.

```bash
pkg install python-pillow
python3 termux-bridge/client.py generate-image   --image /sdcard/Download/test.png   --ocr-mode fast
```

Full OCR uploads the original image, preserves line breaks, and allows 768 max tokens.

```bash
python3 termux-bridge/client.py generate-image   --image /sdcard/Download/test.png   --ocr-mode full
```

Use lower-level `--preset`, `--prompt`, `--max-tokens`, `--resize-max-edge`, or `--jpeg-quality` flags to override an OCR mode.

## Benchmark image

Run repeated image requests and summarize timings:

```bash
python3 termux-bridge/client.py benchmark-image   --image /sdcard/Download/test.png   --runs 5   --ocr-mode fast
```

Per-run progress is printed to stderr. The final stdout value is JSON with min/avg/max for `inference_ms`, `total_ms`, and `client_total_ms`.

## Benchmark log

Image requests are sent as multipart file uploads, not base64 JSON, so large screenshots are handled more reliably.

Every generate request appends a JSONL row:

```text
termux-bridge/benchmark.jsonl
```

Use this to compare Mock vs LiteRT and ROG Phone 6 vs Oracle VM.
For image requests, the log also records original upload bytes, preprocessed upload bytes, preprocessing time, and whether the image was resized.

## Automation JSON workflows

The automation client can run a small sequential JSON workflow. This is the first n8n-like layer: each node has an `id`, a `type`, parameters, and can reference earlier output through `{{node.field}}`.

Example:

```bash
python3 termux-bridge/automation_client.py run-json \
  --file docs/automation-workflows/chrome_discover_visible_xml_summary.json
```

The first supported node types are:

```text
tap_coordinate, tap, swipe, wait, current_app, dump_xml, screenshot,
extract_text_from_xml, gemma_summarize, gemma_generate, save_file,
assert_text_contains, back, home, open_app, capture_debug
```

`tap_coordinate` uses the existing calibration registry, for example `chrome_discover_first_article`, `chrome_menu_button`, and `chrome_show_reading_mode`.
