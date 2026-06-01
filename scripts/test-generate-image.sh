#!/usr/bin/env bash
set -euo pipefail

image_b64="$(base64 -w 0 samples/sample.png)"
json_file="$(mktemp)"
trap 'rm -f "$json_file"' EXIT
printf '{"prompt":"Reply in at most five words. What is in this image?","image_base64":"%s","max_tokens":32,"temperature":0.1}' "$image_b64" > "$json_file"
curl -sS -X POST http://127.0.0.1:8765/generate \
  -H 'Content-Type: application/json' \
  --data-binary "@$json_file"
printf '\n'
