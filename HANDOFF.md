# Handoff: gemma-litert-server

## Purpose

Continue work on `gemma-litert-server`, an Android + Termux local OCR backend using Gemma 4 E4B IT LiteRT-LM on ROG Phone 6. The current pipeline is:

```text
Termux CLI -> localhost HTTP -> Android foreground backend -> LiteRT-LM Gemma -> JSON response + timing
```

## Workspace

- Repo path: `/home/ubuntu/workspaces2/projects/gemma-litert-server`
- Branch: `docs/vietnamese-guide-android-plan`
- Worktree at handoff: clean and pushed to `origin/docs/vietnamese-guide-android-plan`.

## Important Artifacts

- Android backend plan: `ANDROID_BACKEND_APP_PLAN.md`
- APK Actions debug runbook: `GITHUB_ACTIONS_APK_DEBUG_RUNBOOK.md`
- Benchmark plan: `docs/benchmark-plan.md`
- Termux test plan: `docs/termux-client-test-plan.md`
- Termux client: `termux-bridge/client.py`
- Termux client tests: `termux-bridge/test_client.py`
- Android backend entry points:
  - `android-backend/src/main/java/dev/gemma/androidbackend/MainActivity.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/ServerService.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/HttpApiServer.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/LiteRtGemmaRunner.java`

## Recent Commits

- `dd61f0c` Add Termux image preprocessing benchmark options
- `89d4fab` Add project handoff summary
- `6d6444c` Increase Gradle heap for Android APK packaging
- `04f1672` Enable LiteRT GPU backend on Android
- `3af9a1c` Use multipart image upload for Android backend

## Confirmed Working State

- Model picker copies `gemma-4-E4B-it.litertlm` from shared storage into app-private storage.
- Termux `generate-image` now sends image as `multipart/form-data`, not base64 JSON.
- Termux `generate-image` supports optional `--resize-max-edge` and `--jpeg-quality` preprocessing before multipart upload. This requires Pillow only when preprocessing is requested.
- Benchmark rows now include `image_original_bytes`, `image_upload_bytes`, `image_preprocess_ms`, and `image_resized` for image requests.
- Android backend reads multipart `image` part and reports `meta.image_bytes`.
- LiteRT GPU backend is enabled with `Backend.GPU()` for both model and vision backend.
- CPU backend is still available from the app for comparison/debug.
- Latest successful APK build:
  - Run: `https://github.com/tqa25/gemma-litert-server/actions/runs/26893255010`
  - Artifact: `gemma-android-backend-debug-apk`
  - APK verified locally at download time: `/tmp/apk-artifact-26893255010/gemma-android-backend-debug-apk/android-backend-debug.apk`, `26038433 bytes`

## Latest Device Results

Device: ROG Phone 6.

### Original Image

```bash
python3 termux-bridge/client.py generate-image -i /sdcard/Download/test_img.jpg --prompt "Extract visible text from this image. Return concise text." --max-tokens 256 --temperature 0.1
```

```text
engine: litert-android-gpu
has_image: true
image_bytes: 1372346
5-run total_ms: 15677, 15723, 15381, 15487, 16337
avg total_ms: ~15721
OCR quality: best of tested presets for this screenshot
```

This is faster than the user's Edge Gallery measurement on the same image/model, which was about 18 seconds.

### Resize 1280 / JPEG 85

```bash
python3 termux-bridge/client.py generate-image --image /sdcard/Download/test_img.jpg --resize-max-edge 1280 --jpeg-quality 85 --prompt "Extract visible text from this image. Return concise text." --max-tokens 256 --temperature 0.1
```

```text
engine: litert-android-gpu
has_image: true
image_bytes: 125437
5-run total_ms: 19475, 13050, 13348, 12894, 14045
avg total_ms without first outlier: ~13347
latency improvement vs original: ~15%
OCR quality: acceptable, but slightly worse on small Vietnamese text
```

Recommended speed preset: `--resize-max-edge 1280 --jpeg-quality 85`.

### Resize 1600 / JPEG 90

```text
image_bytes: 207192
6-run total_ms: 15969, 13971, 15232, 15794, 15363, 15928
avg total_ms: ~15376
OCR quality: not clearly better than 1280 / JPEG 85 for this screenshot
```

Conclusion: avoid `1600 / JPEG 90` for this screenshot class; it reduces bytes but does not materially reduce latency.

## Latest Completed Work

Added optional Termux-side image preprocessing for upload/latency experiments:

```bash
python3 termux-bridge/client.py generate-image \
  --image /sdcard/Download/test.png \
  --resize-max-edge 1280 \
  --jpeg-quality 85 \
  --prompt "Extract visible text from this image. Return concise text."
```

Documented the compressed-run workflow in `termux-bridge/README.md` and `docs/termux-client-test-plan.md`.

Local verification passed:

```bash
python3 -m unittest termux-bridge/test_client.py
python3 -m py_compile termux-bridge/client.py termux-bridge/test_client.py
```

Note: local sandbox execution intermittently failed before command startup with `bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted`, so Python checks were run with approved escalation.

GitHub Actions verification passed:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/26893255010
Commit: dd61f0c5864f8a5280132b12cece24bea7dbfb42
Conclusion: success
Artifact: gemma-android-backend-debug-apk
APK size: 26038433 bytes
```

## Key Debug History

- Initial `/sdcard/Models/...` direct model path failed with `PERMISSION_DENIED`; fixed by model picker + copy to app-private storage.
- Initial image upload used large base64 JSON; ROG backend received `has_image=false`; fixed by multipart upload.
- CPU backend on ROG took about 41 seconds on `test_img.jpg`; GPU backend reduced this to about 14 seconds.
- GitHub Actions GPU build initially failed with `Java heap space` during APK packaging; fixed by adding `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8` to `gradle.properties`.

## Suggested Skills

- Use `github-actions-apk-build` for any APK build, GitHub Actions failure, artifact verification, or workflow debugging.
- Use `diagnose` for runtime failures on ROG Phone 6, LiteRT engine startup errors, HTTP request parsing bugs, or performance regressions.
- Use `handoff` again before another context reset.

## Next Useful Work

1. Add Android app diagnostics for the latest request: engine, image bytes, inference time, total time, and request status visible in the UI.
2. Add a small benchmark helper in Termux to run N repeated requests and summarize min/avg/max from `benchmark.jsonl`.
3. Decide whether the default user-facing preset should be original image or `1280 / JPEG 85`, depending on OCR accuracy tolerance.
4. Consider a streaming endpoint later if the workflow needs first-token latency rather than total latency.
5. Keep `Start LiteRT CPU Server` as a debug comparator, but default future testing to `Start LiteRT GPU Server`.

## Fresh Session Instruction

In a new chat, start with:

```text
Read HANDOFF.md, then continue work in /home/ubuntu/workspaces2/projects/gemma-litert-server.
```
