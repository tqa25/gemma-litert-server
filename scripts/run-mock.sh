#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
GEMMA_ENGINE=mock ./gradlew run
