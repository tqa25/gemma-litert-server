#!/usr/bin/env bash
set -euo pipefail
adb forward tcp:8765 tcp:8765
curl -sS http://127.0.0.1:8765/health
printf '
'
