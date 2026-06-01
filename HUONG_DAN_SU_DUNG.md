# Huong Dan Su Dung Gemma LiteRT Server

Tai lieu nay ghi lai cach dung repo `gemma-litert-server` de chay Gemma 4 E4B IT bang LiteRT-LM JVM tren Oracle VM.

## 1. Muc tieu

Server nay la mot backend nho gon:

```text
Client gui prompt + anh
  -> HTTP API
  -> LiteRT-LM JVM
  -> gemma-4-E4B-it.litertlm
  -> JSON response
```

No khong phai Android app, khong co UI, khong dung Transformers, va khong dung file `safetensors` 15GB.

## 2. Cong nghe dang dung

```text
Java: OpenJDK 21
Build: Gradle wrapper 8.10.2
Kotlin plugin: 2.3.0
Inference: LiteRT-LM JVM 0.12.0
Model: gemma-4-E4B-it.litertlm
HTTP server: JDK built-in HttpServer
```

Model da test:

```text
models/gemma-4-E4B-it.litertlm
size: 3.5G
source: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
```

## 3. Cai dat tren Oracle VM

Can JDK 21 day du, gom ca `javac`:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk
```

Kiem tra:

```bash
java -version
javac -version
```

Ket qua dung nen la Java 21.

## 4. Build

```bash
./gradlew clean build
```

Neu build thanh cong:

```text
BUILD SUCCESSFUL
```

## 5. Chay mock backend

Mock backend dung de test HTTP API ma khong load model that.

```bash
GEMMA_ENGINE=mock ./gradlew run
```

Test:

```bash
scripts/test-health.sh
scripts/test-generate-text.sh
scripts/test-generate-image.sh
```

## 6. Chay LiteRT-LM backend that

```bash
GEMMA_ENGINE=litert \
GEMMA_MODEL_PATH=models/gemma-4-E4B-it.litertlm \
./gradlew run
```

Health check:

```bash
curl http://127.0.0.1:8765/health
```

Vi du response:

```json
{
  "status": "ok",
  "engine": "litert",
  "model_loaded": true,
  "model_path": "models/gemma-4-E4B-it.litertlm",
  "model_load_ms": 13635
}
```

## 7. Goi API text

```bash
curl -X POST http://127.0.0.1:8765/generate \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Say hello in Vietnamese","max_tokens":64,"temperature":0.2}'
```

## 8. Goi API voi anh

```bash
image_b64="$(base64 -w 0 samples/sample.png)"
json_file="$(mktemp)"
printf '{"prompt":"Reply in at most five words. What is in this image?","image_base64":"%s","max_tokens":32,"temperature":0.1}' "$image_b64" > "$json_file"
curl -X POST http://127.0.0.1:8765/generate \
  -H 'Content-Type: application/json' \
  --data-binary "@$json_file"
rm -f "$json_file"
```

## 9. Ket qua test tren Oracle VM

Da test thanh cong tren Oracle VM Ubuntu arm64:

```text
Model load: 13.6s
Text inference: 49.7s
Image inference: 23.0s
Image response: A blank white image.
```

Ket luan:

```text
LiteRT-LM JVM chay duoc tren Oracle VM ARM CPU.
API prompt + image_base64 hoat dong dung.
Toc do CPU cham hon ROG Phone 6 Edge Gallery ro ret.
```

## 10. File quan trong

```text
src/main/java/dev/gemma/server/http/HttpServerApp.java
  HTTP routes: /health, /generate

src/main/java/dev/gemma/server/inference/InferenceEngine.java
  Interface chung cho backend inference

src/main/java/dev/gemma/server/inference/MockInferenceEngine.java
  Backend gia lap de test API

src/main/kotlin/dev/gemma/server/inference/LiteRtLmInferenceEngine.kt
  Backend that dung LiteRT-LM JVM

docs/test-results.md
  Ket qua test da ghi lai
```

## 11. Luu y hien tai

- Server mac dinh bind local `127.0.0.1:8765`.
- Chua co streaming endpoint.
- LiteRT-LM 0.12.0 `SamplerConfig` co `topK`, `topP`, `temperature`, `seed`, nhung khong expose `maxTokens`.
- API van nhan `max_tokens` de giu contract, nhung hien chua enforce duoc qua LiteRT-LM.
- Neu can toc do nhu Edge Gallery tren ROG Phone 6, nen lam Android backend app dung cung API contract.

## 12. Lenh nhanh

```bash
./gradlew clean build
GEMMA_ENGINE=litert GEMMA_MODEL_PATH=models/gemma-4-E4B-it.litertlm ./gradlew run
```
