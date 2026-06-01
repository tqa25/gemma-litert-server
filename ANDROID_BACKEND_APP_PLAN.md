# Plan Xay Dung Android Backend App Cho Gemma 4 OCR/Image Inference

Muc tieu: tao mot Android app backend cuc gon, khong can UI phuc tap, chay tren ROG Phone 6, load `gemma-4-E4B-it.litertlm`, nhan `prompt + image`, tra response qua HTTP API.

## 1. Ly do lam Android backend app

Oracle VM LiteRT-LM JVM da chay duoc, nhung test CPU cho thay latency cham:

```text
Text inference: ~49.7s
Image inference: ~23.0s voi anh 1x1
```

ROG Phone 6 voi Edge Gallery bat dau stream cau tra loi sau khoang 5s vi dung Android-native LiteRT-LM va accelerator tot hon.

Vi vay ta giu cung API contract, nhung doi runtime sang Android backend app.

## 2. Kien truc muc tieu

```text
Termux / PC / app automation
  -> HTTP localhost hoac ADB forward
  -> Android Backend Service
  -> LiteRT-LM Android
  -> gemma-4-E4B-it.litertlm
  -> JSON response hoac stream token
```

Khong can app UI day du. Chi can man hinh nho de:

```text
Start server
Stop server
Model loaded / not loaded
Port
Last request time
```

## 3. API contract giu giong Oracle VM

### GET /health

Response:

```json
{
  "status": "ok",
  "engine": "litert-android",
  "model_loaded": true,
  "model_path": "/sdcard/Models/gemma-4-E4B-it.litertlm",
  "model_load_ms": 9000,
  "uptime_ms": 120000
}
```

### POST /generate

Request:

```json
{
  "prompt": "Extract visible text from this screenshot.",
  "image_base64": "...",
  "max_tokens": 256,
  "temperature": 0.1
}
```

Response:

```json
{
  "request_id": "...",
  "response": "...",
  "timing": {
    "inference_ms": 5200,
    "total_ms": 5300
  },
  "meta": {
    "engine": "litert-android",
    "has_image": true,
    "prompt_chars": 48,
    "response_chars": 420
  }
}
```

### POST /generate-stream

Lam sau MVP. Dung SSE hoac chunked response:

```text
data: {"type":"token","text":"Detected"}
data: {"type":"token","text":" text"}
data: {"type":"done","time_to_first_token_ms":5100,"total_ms":8200}
```

## 4. Tech stack de xuat

```text
Language: Kotlin
Build: Gradle Android plugin
Inference: LiteRT-LM Android
HTTP server: NanoHTTPD hoac Ktor embedded CIO
Service: Foreground Service
Model path: /sdcard/Models/gemma-4-E4B-it.litertlm
Default bind: 127.0.0.1:8765
```

Khuyen nghi MVP dung NanoHTTPD vi nhe va de debug.

## 5. Folder structure

```text
gemma-android-backend/
  app/
    src/main/
      AndroidManifest.xml
      java/dev/gemma/androidbackend/
        MainActivity.kt
        ServerService.kt
        GemmaRunner.kt
        HttpApiServer.kt
        RequestModels.kt
        BenchmarkLogger.kt
        ImageUtil.kt
  docs/
    api.md
    setup-rog-phone-6.md
    benchmark.md
  scripts/
    adb-forward.sh
    test-health.sh
    test-generate-image.sh
  samples/
    request-image.json
    sample.png
```

## 6. Vai tro tung thanh phan

### MainActivity.kt

UI toi thieu:

```text
- Start Server
- Stop Server
- Status
- Port
- Model path
```

### ServerService.kt

Foreground service de Android khong kill backend.

Nhiem vu:

```text
- load model mot lan
- start HTTP server
- stop server khi user yeu cau
- release engine khi service destroy
```

### GemmaRunner.kt

Wrapper duy nhat quanh LiteRT-LM:

```text
loadModel(modelPath)
generate(prompt, imageBytes, options)
generateStream(...)
close()
```

### HttpApiServer.kt

Expose:

```text
GET /health
POST /generate
POST /generate-stream sau MVP
```

### BenchmarkLogger.kt

Ghi JSONL local:

```text
/sdcard/Android/data/<package>/files/benchmark.jsonl
```

Moi request ghi:

```json
{
  "timestamp": "...",
  "request_id": "...",
  "has_image": true,
  "image_bytes": 842133,
  "prompt_chars": 120,
  "model_load_ms": 9000,
  "time_to_first_token_ms": 5100,
  "total_ms": 8200,
  "response_chars": 620
}
```

## 7. Phase trien khai

### Phase 1: Android skeleton

Muc tieu:

```text
Build duoc APK
Mo app duoc
Start/Stop foreground service
GET /health tra JSON mock
```

Checklist:

```text
- Tao Android Kotlin project
- Them MainActivity toi thieu
- Them ServerService
- Them NanoHTTPD
- Bind 127.0.0.1:8765
- scripts/adb-forward.sh
- scripts/test-health.sh
```

### Phase 2: Mock /generate

Muc tieu:

```text
POST /generate nhan prompt + image_base64 va tra response mock
```

Checklist:

```text
- Parse JSON request
- Decode image_base64
- Validate max image size
- Return JSON response
- Ghi benchmark mock
```

### Phase 3: LiteRT-LM Android real backend

Muc tieu:

```text
Load gemma-4-E4B-it.litertlm va generate text/image that
```

Checklist:

```text
- Copy model vao /sdcard/Models/gemma-4-E4B-it.litertlm
- Them LiteRT-LM Android dependency
- Implement GemmaRunner
- EngineConfig(modelPath, backend, visionBackend)
- engine.initialize()
- Content.ImageBytes + Content.Text
- conversation.sendMessage(...)
```

### Phase 4: Benchmark voi anh that

Dung 5 anh test co dinh:

```text
1. Screenshot nhieu text
2. Screenshot form login
3. Screenshot co button/menu
4. Anh text nho
5. Anh nhieu nhieu/anh xau
```

Moi anh chay:

```text
1 lan warmup
3 lan measure
```

Ghi:

```text
model_load_ms
time_to_first_token_ms neu co streaming
total_ms
response_chars
OCR correctness note
```

### Phase 5: Streaming

Muc tieu:

```text
/generate-stream tra token som, giong cam giac Edge Gallery
```

Checklist:

```text
- Dung sendMessageAsync
- SSE hoac chunked response
- Do time_to_first_token_ms
- Client curl/Termux doc token dan
```

### Phase 6: Safety va hardening

Checklist:

```text
- Chi bind localhost mac dinh
- Request timeout
- Max prompt chars
- Max image MB
- Single-flight lock neu engine khong xu ly song song tot
- Wake lock optional
- Foreground notification ro rang
- Khong mo LAN neu chua co API key
```

## 8. Cach test tu Termux

Neu server bind localhost tren cung may:

```bash
curl http://127.0.0.1:8765/health
```

Gui anh:

```bash
image_b64="$(base64 -w 0 screenshot.png)"
printf '{"prompt":"Extract visible text","image_base64":"%s"}' "$image_b64" > /tmp/request.json
curl -X POST http://127.0.0.1:8765/generate \
  -H 'Content-Type: application/json' \
  --data-binary @/tmp/request.json
```

## 9. Cach test tu PC qua ADB

```bash
adb forward tcp:8765 tcp:8765
curl http://127.0.0.1:8765/health
```

## 10. Tieu chi thanh cong MVP

MVP dat neu:

```text
1. APK build duoc.
2. App start service duoc.
3. /health tra ok.
4. /generate mock pass.
5. LiteRT-LM load duoc model.
6. /generate image that tra response.
7. Ghi duoc benchmark JSONL.
8. Toc do image gan Edge Gallery hon Oracle VM.
```

## 11. Quyet dinh sau benchmark

Neu ROG Phone 6 backend dat:

```text
time_to_first_token <= 6s
total image inference <= 15s
OCR/screen understanding tot
```

Thi dung Android backend lam inference server chinh.

Neu latency khong tot:

```text
- thu GPU/CPU backend khac
- resize/crop anh truoc khi gui model
- them streaming
- dung OCR hybrid: ML Kit OCR + Gemma 4 reasoning
```
