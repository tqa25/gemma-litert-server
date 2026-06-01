# API

## GET /health

Returns server, model, and uptime status.

## POST /generate

Request:

```json
{
  "prompt": "Extract visible text from this image",
  "image_base64": "...",
  "max_tokens": 512,
  "temperature": 0.1
}
```

`image_base64` is optional.

Response:

```json
{
  "request_id": "...",
  "response": "...",
  "timing": {
    "inference_ms": 0,
    "total_ms": 1
  },
  "meta": {
    "model_path": "models/gemma-4-E4B-it.litertlm",
    "engine": "mock",
    "prompt_chars": 36,
    "has_image": true,
    "response_chars": 120,
    "output_tokens_estimate": 20
  }
}
```
