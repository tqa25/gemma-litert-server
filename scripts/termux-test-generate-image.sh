#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 1 ]]; then
  echo "Usage: $0 /path/to/image.png" >&2
  exit 2
fi
cd "$(dirname "$0")/.."
python3 termux-bridge/client.py generate-image   --image "$1"   --prompt "Extract visible text from this image. Return concise text."   --max-tokens 256   --temperature 0.1
