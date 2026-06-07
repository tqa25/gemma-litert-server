# Handoff: gemma-litert-server

## Purpose

Continue work on `gemma-litert-server`, an Android + Termux local OCR backend using Gemma 4 E4B IT LiteRT-LM on ROG Phone 6. OCR remains working, and the current priority has shifted to Shizuku-backed phone automation before streaming. The current OCR pipeline is:

```text
Termux CLI -> localhost HTTP -> Android foreground backend -> LiteRT-LM Gemma -> JSON response + timing
```

The new automation MVP pipeline is:

```text
Termux automation CLI -> localhost /automation/* -> Android backend -> Shizuku shell -> Chrome/Gemini UI -> local summary files
```

## Workspace

- Repo path: `/home/ubuntu/workspaces2/projects/gemma-litert-server`
- Branch: `docs/vietnamese-guide-android-plan`
- Worktree at handoff: latest Android OCR UI commit pushed to `origin/docs/vietnamese-guide-android-plan`; final handoff update may be the newest commit.

## Important Artifacts

- Progress and startup memory: `PROGRESS.md`
- System architecture: `docs/architecture.md`
- Android backend plan: `ANDROID_BACKEND_APP_PLAN.md`
- APK Actions debug runbook: `GITHUB_ACTIONS_APK_DEBUG_RUNBOOK.md`
- Benchmark plan: `docs/benchmark-plan.md`
- Termux test plan: `docs/termux-client-test-plan.md`
- Termux client: `termux-bridge/client.py`
- Termux automation client: `termux-bridge/automation_client.py`
- Termux client tests: `termux-bridge/test_client.py`
- Android backend entry points:
  - `android-backend/src/main/java/dev/gemma/androidbackend/MainActivity.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/ServerService.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/HttpApiServer.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/LiteRtGemmaRunner.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/AutomationController.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/ShizukuShellExecutor.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/AutomationConfig.java`

## Recent Commits

- `4e33c70` Add Android OCR history
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
- Android app stores OCR History locally in app-private `ocr_history.json`, keeping 20 recent successful OCR results with text and metadata only. History items can be tapped to restore/copy text without rerunning inference, and can be cleared from the app.
- `/health` includes a `last_request` diagnostics object.
- LiteRT GPU backend is enabled with `Backend.GPU()` for both model and vision backend.
- CPU backend is still available from the app for comparison/debug.
- Initial automation API exists under `/automation/*`.
- Initial floating automation overlay exists; MainActivity has buttons to enable overlay permission and show/hide controls.
- Shizuku dependencies were added: `dev.rikka.shizuku:api:13.1.5` and `dev.rikka.shizuku:provider:13.1.5`; manifest now includes `rikka.shizuku.ShizukuProvider`.
- `gradle.properties` now sets `android.useAndroidX=true` because Shizuku provider depends on AndroidX annotation.
- Termux automation CLI supports status, stop, current-app, tap, swipe, home, back, longpress-home, wait, screenshot, screen-xml, open-app, calibration, and workflow run.
- Floating overlay supports Capture, Mark Article, Mark Menu, Mark Reading, Mark Summary, Mark Copy, Run Reading, Run Gemini, Stop, and Hide.
- Preferred reusable workflow is `chrome-discover-reading-gemma-summary-once`.
- Fallback Gemini overlay workflow is `chrome-discover-gemini-summary-once`.
- MVP start state is Chrome new tab / Discover feed already open. User confirmed Chrome articles open in the same tab and Back returns to Chrome Discover feed.
- Latest successful APK build:
  - Run: `https://github.com/tqa25/gemma-litert-server/actions/runs/27081804289`
  - Commit: `8e54c50`
  - Artifact: `gemma-android-backend-debug-apk`
  - APK verified locally at download time: `/tmp/apk-artifact-27081804289/gemma-android-backend-debug-apk/android-backend-debug.apk`, `26097862 bytes`

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

Latest code work added the initial Shizuku automation MVP:

```text
Android:
  /automation/status
  /automation/config
  /automation/stop
  /automation/tap
  /automation/swipe
  /automation/home
  /automation/back
  /automation/longpress-home
  /automation/wait
  /automation/current-app
  /automation/screenshot
  /automation/screen-xml
  /automation/open-app
  /automation/calibrate
  /automation/workflows/run

Termux:
  termux-bridge/automation_client.py
```

The implemented workflow is intentionally narrow:

```text
chrome-discover-gemini-summary-once:
  Chrome Discover feed already visible
  -> tap calibrated article card
  -> longpress HOME
  -> tap Gemini summarize page by XML or fallback coordinate
  -> tap copy by XML/content-desc or fallback coordinate
  -> read ClipboardManager
  -> save summary under app-private automation_runs
```

Verification completed:

```bash
python3 -m py_compile termux-bridge/client.py termux-bridge/test_client.py termux-bridge/automation_client.py
python3 -m unittest termux-bridge/test_client.py
python3 termux-bridge/automation_client.py --help
python3 termux-bridge/automation_client.py run --help
./gradlew :android-backend:compileDebugJavaWithJavac -x :android-backend:processDebugResources --stacktrace
```

Full local APK assemble still fails at the known Oracle ARM AAPT2 x86 loader issue:

```text
x86_64-binfmt-P: Could not open '/lib64/ld-linux-x86-64.so.2': No such file or directory
```

Use GitHub Actions for the APK build source of truth.

GitHub Actions verification for this automation APK passed:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/27081096555
Commit: b2bdef2
Conclusion: success
Artifact: gemma-android-backend-debug-apk
Downloaded APK: /tmp/apk-artifact-27081096555/gemma-android-backend-debug-apk/android-backend-debug.apk
APK size: 26088786 bytes
```

Device debug note: user reached `shizuku_available=true` and `shizuku_permission_granted=true`, but primitive commands failed with `{"message":"process hasn't exited"}`. Commit `b2bdef2` changes `ShizukuShellExecutor` to wait through ShizukuRemoteProcess `waitForTimeout` and then read the exit code via `waitFor()`.

Latest device checkpoint after installing the fix APK:

```text
python3 termux-bridge/automation_client.py current-app
  -> package: com.termux

python3 termux-bridge/automation_client.py screenshot --output screen.png
  -> ok, image_bytes: 278065

python3 termux-bridge/automation_client.py screen-xml --output screen.xml
  -> ok, xml_chars: 5897
```

This confirms Shizuku shell primitives are working on the ROG Phone 6. The first status after reinstall showed permission false; running `current-app` triggered permission handling, and the next `current-app` succeeded.

Latest code checkpoint adds `FloatingAutomationService` for fullscreen Chrome calibration. It uses Android overlay permission and calls the same localhost automation API.

GitHub Actions verification for the floating overlay APK passed:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/27081477480
Commit: 5537f82
Conclusion: success
Artifact: gemma-android-backend-debug-apk
Downloaded APK: /tmp/apk-artifact-27081477480/gemma-android-backend-debug-apk/android-backend-debug.apk
APK size: 26094850 bytes
```

GitHub Actions verification for Reading Mode + Gemma workflow passed:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/27081804289
Commit: 8e54c50
Conclusion: success
Artifact: gemma-android-backend-debug-apk
Downloaded APK: /tmp/apk-artifact-27081804289/gemma-android-backend-debug-apk/android-backend-debug.apk
APK size: 26097862 bytes
```

Previous OCR work added optional Termux-side image preprocessing for upload/latency experiments:

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

0. In every new session, read `PROGRESS.md`, `docs/architecture.md`, and this `HANDOFF.md` before changing files. After meaningful codebase/runtime/API/build/benchmark changes, update `PROGRESS.md`; if architecture changes, update `docs/architecture.md` too.
1. Build the new automation APK through GitHub Actions and install it on the ROG Phone 6.
2. Start Shizuku on the phone, open the Android backend app once, and grant Shizuku permission when requested.
3. Start backend server, then from Termux run `python3 termux-bridge/automation_client.py status`; verify `shizuku_available=true` and `shizuku_permission_granted=true`.
4. With Chrome Discover feed open, test primitives: `current-app`, `screenshot`, `screen-xml`, `longpress-home`, then calibrate `chrome_discover_first_article`, `gemini_summary_button`, and `gemini_copy_button`.
5. Run `python3 termux-bridge/automation_client.py run chrome-discover-reading-gemma-summary-once --debug-capture` and inspect `automation_runs/{run_id}`.
6. If Shizuku reflection shell fails at runtime, replace `ShizukuShellExecutor` with a Shizuku UserService implementation.
7. Only after one-article workflow is stable, add batch mode and Chrome feed swipe calibration. Streaming remains postponed.

## Fresh Session Instruction

In a new chat, start with:

```text
Work in /home/ubuntu/workspaces2/projects/gemma-litert-server.
Before changing anything, read PROGRESS.md, docs/architecture.md, and HANDOFF.md.
Summarize the current architecture, progress, known working state, and next recommended steps.
Whenever you change the codebase, runtime behavior, API contract, build workflow, docs, or device benchmark conclusions, update PROGRESS.md before finishing. If architecture changes, update docs/architecture.md too.
```
