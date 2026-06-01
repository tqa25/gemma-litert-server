# Test Results

## Environment

```text
Host: Oracle VM Ubuntu arm64
Java: OpenJDK 21.0.11
Gradle wrapper: 8.10.2
LiteRT-LM JVM: 0.12.0 via latest.release
Model: models/gemma-4-E4B-it.litertlm
Model size: 3.5G
```

## Build

```text
./gradlew clean build
BUILD SUCCESSFUL
```

## Mock HTTP test

```text
GEMMA_ENGINE=mock ./gradlew run
scripts/test-health.sh: PASS
scripts/test-generate-image.sh: PASS
```

## LiteRT-LM real model test

Server command:

```bash
GEMMA_ENGINE=litert GEMMA_MODEL_PATH=models/gemma-4-E4B-it.litertlm ./gradlew run
```

Health result:

```json
{"model_load_ms":13635,"model_path":"models/gemma-4-E4B-it.litertlm","engine":"litert","status":"ok","model_loaded":true}
```

Text generation result:

```text
Prompt: Say hello in Vietnamese
Inference time: 49719 ms
Total time: 49724 ms
Result: PASS
```

Image generation result:

```text
Image: samples/sample.png, 1x1 PNG
Prompt: Reply in at most five words. What is in this image?
Response: A blank white image.
Inference time: 23065 ms
Total time: 23067 ms
Result: PASS
```

## Notes

- LiteRT-LM JVM loads and runs on Oracle VM arm64 CPU.
- CPU latency is much slower than ROG Phone 6 Edge Gallery for image tasks.
- The API path is valid: prompt + image_base64 -> LiteRT-LM -> JSON response.
- `SamplerConfig` in LiteRT-LM 0.12.0 does not expose max token limit; keep prompts concise or add streaming/cancel timeout next.
