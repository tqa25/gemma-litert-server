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

```bash
python3 termux-bridge/client.py generate-image   --image /sdcard/Download/test.png   --prompt "Extract visible text from this image. Return concise text."
```

## Benchmark log

Image requests are sent as multipart file uploads, not base64 JSON, so large screenshots are handled more reliably.

Every generate request appends a JSONL row:

```text
termux-bridge/benchmark.jsonl
```

Use this to compare Mock vs LiteRT and ROG Phone 6 vs Oracle VM.
