# Handoff: gemma-litert-server

## Purpose

Continue work on `gemma-litert-server`, an Android + Termux local OCR backend using Gemma 4 E4B IT LiteRT-LM on ROG Phone 6. The current pipeline is:

```text
Termux CLI -> localhost HTTP -> Android foreground backend -> LiteRT-LM Gemma -> JSON response + timing
```

## Workspace

- Repo path: `/home/ubuntu/workspaces2/projects/gemma-litert-server`
- Branch: `docs/vietnamese-guide-android-plan`
- Worktree at handoff: latest Android OCR UI commit pushed to `origin/docs/vietnamese-guide-android-plan`; final handoff update may be the newest commit.

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

- `d695340` Polish Android OCR runner UX
- `84ee76f` Add Android OCR runner UI
- `121f12a` Add Android request diagnostics and image benchmark helper
- `6bfb9a8` Update handoff with benchmark checkpoint
- `dd61f0c` Add Termux image preprocessing benchmark options
- `6d6444c` Increase Gradle heap for Android APK packaging
- `04f1672` Enable LiteRT GPU backend on Android
- `3af9a1c` Use multipart image upload for Android backend

## Confirmed Working State

- Model picker copies `gemma-4-E4B-it.litertlm` from shared storage into app-private storage.
- Termux `generate-image` now sends image as `multipart/form-data`, not base64 JSON.
- Termux `generate-image` supports optional `--resize-max-edge` and `--jpeg-quality` preprocessing before multipart upload. This requires Pillow only when preprocessing is requested.
- Termux image commands support `--preset speed` (`1280 / JPEG 85`) and `--preset accuracy` (original image). Explicit resize/quality flags override presets.
- Termux image commands support `--ocr-mode fast` for speed/concise OCR and `--ocr-mode full` for original-image, line-preserving OCR with a higher token limit.
- Benchmark rows now include `image_original_bytes`, `image_upload_bytes`, `image_preprocess_ms`, and `image_resized` for image requests.
- Android backend reads multipart `image` part and reports `meta.image_bytes`.
- Android app shows a latest-request diagnostics panel with engine, image bytes, inference time, total time, response chars, request id prefix, and errors.
- Android app now includes an OCR Runner UI: backend URL input, image picker, Fast OCR, Full OCR, Run OCR, selectable result text, and copy-to-clipboard. User confirmed on-device UI test passed through steps 1-6 on ROG Phone 6.
- Android OCR Runner calls HTTP `/generate` through the same localhost boundary instead of calling `GemmaRunner` directly, so the UI can later point to another host such as an Oracle VM backend.
- Fast OCR in Android UI resizes longest edge to 1280px and JPEG 85 before multipart upload; Full OCR uploads the original image and uses the line-preserving prompt with 768 max tokens. The UI now persists backend URL and OCR mode, disables OCR controls while a request is running, shows upload/loading state, and formats common backend errors more clearly.
- `/health` includes a `last_request` diagnostics object.
- LiteRT GPU backend is enabled with `Backend.GPU()` for both model and vision backend.
- CPU backend is still available from the app for comparison/debug.
- Latest successful APK build:
  - Run: `https://github.com/tqa25/gemma-litert-server/actions/runs/27063108323`
  - Commit: `d695340d6da234ae91e072f1ff9353d3e6171a3d`
  - Artifact: `gemma-android-backend-debug-apk`
  - APK verified locally at download time: `/tmp/apk-artifact-27063108323/gemma-android-backend-debug-apk/android-backend-debug.apk`, `26048350 bytes`

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

Recommended fast workflow: `--ocr-mode fast` (`speed`, 256 tokens, concise OCR).
Recommended full workflow for dense text: `--ocr-mode full` (`accuracy`, 768 tokens, line-preserving OCR).

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
  --ocr-mode fast
```

Documented the compressed-run workflow in `termux-bridge/README.md` and `docs/termux-client-test-plan.md`.

Local verification passed:

```bash
python3 -m unittest termux-bridge/test_client.py
python3 -m py_compile termux-bridge/client.py termux-bridge/test_client.py
```

Note: local sandbox execution intermittently failed before command startup with `bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted`, so Python checks were run with approved escalation.

GitHub Actions verification passed for Termux preprocessing:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/26893255010
Commit: dd61f0c5864f8a5280132b12cece24bea7dbfb42
Conclusion: success
Artifact: gemma-android-backend-debug-apk
APK size: 26038433 bytes
```

Latest GitHub Actions verification passed for Android diagnostics and benchmark helper:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/26895910326
Commit: 121f12ab90dda5dfa15df31022b1228b47f2e050
Conclusion: success
Artifact: gemma-android-backend-debug-apk
APK size: 26040769 bytes
```

Latest Termux-only verification for image presets and OCR modes:

```bash
python3 -m unittest termux-bridge/test_client.py
python3 -m py_compile termux-bridge/client.py termux-bridge/test_client.py
python3 termux-bridge/client.py generate-image --help
python3 termux-bridge/client.py benchmark-image --help
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

## Latest Android UI Checkpoint

Added Android in-app OCR Runner and verified APK build through GitHub Actions. User later confirmed the installed UI works well on-device through the requested test flow. Local Gradle on Oracle ARM still fails at AAPT2 x86 loader before code compile; GitHub Actions x86_64 is the APK build source of truth.

Suggested device test flow:

1. Install the APK from run `27062255260`.
2. Open the Android app and ensure the model is copied/ready.
3. Tap `Start LiteRT GPU Server`.
4. In `OCR Runner`, keep backend URL `http://127.0.0.1:8765`.
5. Select an image, choose `Fast OCR` or `Full OCR`, then tap `Run OCR`.
6. Confirm result text, timing, image bytes, and latest-request diagnostics update.
7. Use `Copy OCR Text` to verify clipboard output.

## Next Useful Work

1. Install the next UX-polish APK and verify URL/mode persistence, disabled controls during OCR, loading text, copy button state, and clearer errors when the backend is stopped.
2. Run `benchmark-image --runs 3 --ocr-mode fast` and `--ocr-mode full` across 3-5 real screenshots and record OCR quality notes.
3. Decide whether the next app-level default should expose fast/full OCR choices or keep modes Termux-only.
4. Consider adding a `/diagnostics` endpoint if direct health polling is not enough for external tools.
5. Consider a streaming endpoint later if the workflow needs first-token latency rather than total latency.
6. Keep `Start LiteRT CPU Server` as a debug comparator, but default future testing to `Start LiteRT GPU Server`.

## Fresh Session Instruction

In a new chat, start with:

```text
Read HANDOFF.md, then continue work in /home/ubuntu/workspaces2/projects/gemma-litert-server.
```
