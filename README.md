# gemma-litert-server

Tiny HTTP inference server skeleton for Gemma 4 E4B IT on Oracle VM.

MVP goals:

- Start a local HTTP server.
- Expose `GET /health`.
- Expose `POST /generate`.
- Keep HTTP/config/benchmark code separate from model runtime.
- Run today with a mock engine using only JDK 21.
- Leave a clean `InferenceEngine` boundary for LiteRT-LM JVM integration.

Default bind is localhost only:

```text
127.0.0.1:8765
```

## Run mock server

```bash
./scripts/run-mock.sh
```

Test:

```bash
./scripts/test-health.sh
./scripts/test-generate-text.sh
./scripts/test-generate-image.sh
```

## Config

```text
GEMMA_HOST=127.0.0.1
GEMMA_PORT=8765
GEMMA_MODEL_PATH=/home/ubuntu/models/gemma-4-E4B-it.litertlm
GEMMA_ENGINE=mock
GEMMA_MAX_PROMPT_CHARS=4000
GEMMA_MAX_IMAGE_MB=8
GEMMA_DEFAULT_MAX_TOKENS=512
GEMMA_REQUEST_TIMEOUT_MS=120000
```

## LiteRT-LM target

The planned real runtime is LiteRT-LM JVM:

```text
com.google.ai.edge.litertlm:litertlm-jvm
```

The integration point is:

```text
src/main/java/dev/gemma/server/inference/InferenceEngine.java
```

Current real-engine class is intentionally a stub until Gradle/Kotlin or another JVM build path is installed on the Oracle VM.
