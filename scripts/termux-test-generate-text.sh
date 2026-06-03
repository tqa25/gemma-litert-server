#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 termux-bridge/client.py generate-text   --prompt "Say hello in Vietnamese. Keep it short."   --max-tokens 64   --temperature 0.2
