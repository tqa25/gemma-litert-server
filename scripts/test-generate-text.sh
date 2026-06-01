#!/usr/bin/env bash
set -euo pipefail

curl -sS -X POST http://127.0.0.1:8765/generate \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Say hello in Vietnamese","max_tokens":64,"temperature":0.2}'
printf '\n'
