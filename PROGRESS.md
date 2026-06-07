# Progress: gemma-litert-server

Last updated: 2026-06-06

## Startup Instructions For New Agents

When starting a new session, the user can say:

```text
Work in /home/ubuntu/workspaces2/projects/gemma-litert-server.
Before changing anything, read PROGRESS.md, docs/architecture.md, and HANDOFF.md.
Summarize the current architecture, current progress, known working state, and next recommended steps.
After that, wait for my next instruction or propose a short plan if I already gave you a task.
Whenever you change the codebase, runtime behavior, API contract, build workflow, docs, or device benchmark conclusions, update PROGRESS.md before finishing. If the architecture changes, update docs/architecture.md too.
```

Short version:

```text
Read PROGRESS.md, docs/architecture.md, and HANDOFF.md in /home/ubuntu/workspaces2/projects/gemma-litert-server, then continue from the current state.
```

## Memory Contract

This repo uses three persistent context files:

- `PROGRESS.md`: living project state, recent work, device results, next actions, and update rules.
- `docs/architecture.md`: stable system map, module responsibilities, request flows, and extension points.
- `HANDOFF.md`: compact transfer note for the next agent, including latest commits, builds, and device checkpoints.

Update rules:

- Update `PROGRESS.md` after any meaningful codebase change, benchmark result, APK build, device test, or decision.
- Update `docs/architecture.md` when a module boundary, request flow, API contract, runtime path, or build architecture changes.
- Update `HANDOFF.md` before ending a long session, switching agents, or after a major milestone.
- Do not rely on chat memory alone for project state.

## Current Objective

Build and validate a local OCR/image-understanding backend for ROG Phone 6 using Gemma 4 E4B IT LiteRT-LM. The practical working path is:

```text
Termux CLI or Android OCR Runner
  -> http://127.0.0.1:8765
  -> Android foreground backend service
  -> LiteRT-LM Gemma runner
  -> JSON response with timing and diagnostics
```

The project is not yet a full phone automation agent. Voice, Accessibility, Shizuku, Codex/Antigravity integration, and streaming are future phases.

Near-term priority changed on 2026-06-06: postpone `/generate-stream` and build phone automation first. The first automation MVP targets the user's real Chrome Discover workflow:

```text
Chrome new tab / Discover feed already open
  -> tap one article in the same Chrome tab
  -> long-press HOME through Shizuku shell to open Gemini overlay
  -> tap Gemini "Tóm tắt trang" / "Summarize page"
  -> tap/click Gemini copy button
  -> Android backend reads ClipboardManager
  -> save summary locally under automation_runs
```

## Workspace

- Repo path: `/home/ubuntu/workspaces2/projects/gemma-litert-server`
- Remote: `https://github.com/tqa25/gemma-litert-server.git`
- Main working branch: `docs/vietnamese-guide-android-plan`
- Current build source of truth for APK: GitHub Actions on x86_64, not local Oracle ARM Gradle.

## Current Working State

- Android app can copy `gemma-4-E4B-it.litertlm` from shared storage into app-private storage.
- Android foreground service exposes localhost HTTP API on `127.0.0.1:8765`.
- `/health` reports engine, model status, model path, model load time, uptime, and latest request diagnostics.
- `/generate` supports text-only and image requests.
- Image upload works through `multipart/form-data`; legacy JSON `image_base64` remains supported.
- LiteRT Android GPU backend is enabled for both language and vision backends.
- CPU backend remains available for comparison/debug.
- Android app includes latest-request diagnostics in the UI.
- Android app includes an OCR Runner UI that calls the same HTTP `/generate` endpoint.
- Android app stores OCR History locally in app-private `ocr_history.json`. It keeps the 20 most recent OCR results with text and metadata only, not source images.
- Termux client supports `health`, `generate-text`, `generate-image`, and `benchmark-image`.
- Termux image workflows support `--ocr-mode fast`, `--ocr-mode full`, and lower-level overrides.
- Android backend now includes an initial Shizuku-backed automation API under `/automation/*`.
- Android app now includes an initial floating automation overlay service for Chrome/Gemini calibration and manual workflow control.
- Termux now includes `termux-bridge/automation_client.py` for automation status, primitives, calibration, screenshots/XML, and the first Chrome Discover + Gemini workflow.
- Fallback automation workflow name: `chrome-discover-gemini-summary-once`.
- Primary reusable automation workflow name: `chrome-discover-reading-gemma-summary-once`.

## Important Files

- `HANDOFF.md`: latest human/agent handoff.
- `docs/architecture.md`: system architecture map.
- `termux-bridge/client.py`: Termux CLI client and benchmark helper.
- `termux-bridge/automation_client.py`: Termux CLI for Android automation API.
- `termux-bridge/test_client.py`: unit tests for Termux image/OCR option resolution and multipart helpers.
- `termux-bridge/README.md`: Termux setup and usage.
- `docs/termux-client-test-plan.md`: device test plan for Termux path.
- `android-backend/src/main/java/dev/gemma/androidbackend/MainActivity.java`: Android UI, model picker, server controls, OCR Runner, OCR History.
- `android-backend/src/main/java/dev/gemma/androidbackend/ServerService.java`: foreground service and runner/server lifecycle.
- `android-backend/src/main/java/dev/gemma/androidbackend/HttpApiServer.java`: NanoHTTPD API server.
- `android-backend/src/main/java/dev/gemma/androidbackend/AutomationController.java`: automation primitives, calibration, workflow runner, local run storage.
- `android-backend/src/main/java/dev/gemma/androidbackend/ShizukuShellExecutor.java`: Shizuku shell command adapter.
- `android-backend/src/main/java/dev/gemma/androidbackend/AutomationConfig.java`: automation defaults and calibration shape.
- `android-backend/src/main/java/dev/gemma/androidbackend/FloatingAutomationService.java`: draggable floating `G` button, overlay menu, crosshair calibration, and workflow controls.
- `android-backend/src/main/java/dev/gemma/androidbackend/LiteRtGemmaRunner.java`: LiteRT-LM Android runner.
- `.github/workflows/android-backend-apk.yml`: APK build workflow.
- `GITHUB_ACTIONS_APK_DEBUG_RUNBOOK.md`: required debug workflow for APK build failures.

## Recent Commits

- `4e33c70` Add Android OCR history
- `1f306c2` Update handoff after OCR UX APK build [skip ci]
- `d695340` Polish Android OCR runner UX
- `bb35f16` Update handoff after Android OCR UI APK build
- `84ee76f` Add Android OCR runner UI
- `24fb9d1` Add Termux OCR workflow modes [skip ci]
- `37a2ddc` Add Termux image quality presets [skip ci]
- `121f12a` Add Android request diagnostics and image benchmark helper

## Latest APK Checkpoint

Latest successful APK build currently recorded in handoff:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/27071567879
Commit: 90d798a883da4dcd4b70b7b66c44fc3cdbe90130
Artifact: gemma-android-backend-debug-apk
APK size: 26088578 bytes
```

This APK includes the initial Shizuku automation API and `chrome-discover-gemini-summary-once` workflow. User previously confirmed the Android OCR Runner UI worked on-device through the requested test flow; automation still needs ROG Phone 6 device validation.

Superseding Shizuku shell wait fix APK:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/27081096555
Commit: b2bdef2
Artifact: gemma-android-backend-debug-apk
APK size: 26088786 bytes
Reason: fixes ShizukuRemoteProcess wait/exit handling after device error "process hasn't exited".
```

## Device Results So Far

Device: ROG Phone 6.

Original image:

```text
image_bytes: 1372346
5-run total_ms: 15677, 15723, 15381, 15487, 16337
avg total_ms: about 15721 ms
OCR quality: best of tested presets for the first screenshot
```

Resize 1280 / JPEG 85:

```text
image_bytes: 125437
5-run total_ms: 19475, 13050, 13348, 12894, 14045
avg total_ms without first outlier: about 13347 ms
latency improvement vs original: about 15%
OCR quality: acceptable, slightly worse on small Vietnamese text
```

Resize 1600 / JPEG 90:

```text
image_bytes: 207192
6-run total_ms: 15969, 13971, 15232, 15794, 15363, 15928
avg total_ms: about 15376 ms
OCR quality: not clearly better than 1280 / JPEG 85 for this screenshot
```

Additional screenshot benchmark summary:

```text
test1:
  fast/speed: about 19.98s avg total, 122943 upload bytes, 413 response chars
  accuracy: about 20.35s avg total, 758438 upload bytes, 414 response chars

test2:
  fast/speed: about 54.79s avg total, 116457 upload bytes, 1958 response chars
  accuracy: about 56.78s avg total, 555648 upload bytes, 2176 response chars
  note: dense text case; full OCR or higher max tokens may matter

test3:
  fast/speed: about 24.93s avg total, 131744 upload bytes, 623 response chars
  accuracy: about 26.41s avg total, 754902 upload bytes, 665 response chars
```

## Current Decisions

- Default practical OCR path: Fast OCR for repeated UI/social screenshots.
- Use Full OCR for dense pages, small text, line preservation, or when Fast OCR misses important content.
- Keep CPU server button as a debug comparator.
- Use LiteRT GPU server for normal testing.
- Keep Termux CLI and Android OCR Runner aligned: both call the HTTP API instead of bypassing the backend.
- OCR History should save only OCR text and metadata; source images are intentionally not persisted.
- Do not add a third `balanced` preset yet. Existing evidence is not strong enough.
- For APK builds and failures, use the GitHub Actions workflow and the debug runbook.
- Do not implement `/generate-stream` yet; automation is higher priority.
- Automation MVP should start from Chrome Discover feed already open, not from Home.
- Chrome Discover is preferred over Google News app because tapped articles open in the same Chrome tab on the user's phone, and one Back returns to the Discover feed.
- Automation must use Shizuku first; Accessibility Service remains a later option.
- Automation guardrails: start with package whitelist, stop/status endpoints, max run duration, calibration for fixed tap points, and stop-on-failure snapshot in debug/error paths.
- Reading Mode + Gemma local is now the preferred reusable path when Chrome `Show Reading mode` can be opened reliably from fixed coordinates.
- Gemini overlay remains as a fallback summary backend when Reading Mode extraction is unavailable or too short.
- Use the Android floating automation overlay for fullscreen Chrome calibration instead of split screen.

## Known Issues And Caveats

- Local Gradle on Oracle ARM may fail at Android AAPT2 x86 loader before code compile. This does not prove Android code is broken.
- `docs/api.md` still emphasizes JSON `image_base64`; implementation now prefers multipart image upload.
- OCR quality conclusions based only on response length are weak. Visual comparison against the source image is still needed.
- The Android OCR Runner UI is intentionally simple Java UI, not a polished design-system app.
- OCR History is app-local only. It is not exported, searchable, or synced.
- `/generate-stream` and `/diagnostics` are not implemented yet.
- The JVM server skeleton still exists, but the active validated path is the Android backend.
- Shizuku API integration uses `dev.rikka.shizuku:api/provider:13.1.5` and requires the user to have Shizuku running and grant permission to the app.
- Shizuku shell execution currently uses reflection against Shizuku's private `newProcess` method because API 13.1.5 no longer exposes it publicly. It now waits through ShizukuRemoteProcess `waitForTimeout` when available. If runtime still blocks this, replace it with a Shizuku UserService implementation.
- Clipboard reading after Gemini copy must be validated on device; Android clipboard foreground restrictions may require adjustments.
- Automation workflow has not yet been fully device-validated on ROG Phone 6.
- ROG Phone 6 device checkpoint after APK run `27081096555`: Shizuku became available, permission prompt was granted after first shell action, and primitives passed for `current-app`, `screenshot --output screen.png`, and `screen-xml --output screen.xml`.
- Floating overlay requires Android "Display over other apps" permission. The app exposes buttons to open overlay permission settings and show/hide floating automation controls.
- Floating overlay crosshair is now a small independent `+` target with separate Save/Cancel controls, so it can be moved to screen edges such as Chrome's `...` button.

## Verification Commands

Termux/local Python checks:

```bash
python3 -m unittest termux-bridge/test_client.py
python3 -m py_compile termux-bridge/client.py termux-bridge/test_client.py
python3 termux-bridge/client.py generate-image --help
python3 termux-bridge/client.py benchmark-image --help
```

Automation local checks:

```bash
python3 -m py_compile termux-bridge/automation_client.py
python3 termux-bridge/automation_client.py --help
python3 termux-bridge/automation_client.py run --help
./gradlew :android-backend:compileDebugJavaWithJavac -x :android-backend:processDebugResources --stacktrace
```

Latest automation APK build verification:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/27071567879
Commit: 90d798a883da4dcd4b70b7b66c44fc3cdbe90130
Artifact: gemma-android-backend-debug-apk
Downloaded APK: /tmp/apk-artifact-27071567879/gemma-android-backend-debug-apk/android-backend-debug.apk
APK size: 26088578 bytes
```

Latest Shizuku wait fix APK:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/27081096555
Commit: b2bdef2
Artifact: gemma-android-backend-debug-apk
Downloaded APK: /tmp/apk-artifact-27081096555/gemma-android-backend-debug-apk/android-backend-debug.apk
APK size: 26088786 bytes
```

Latest floating overlay APK:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/27081477480
Commit: 5537f82
Artifact: gemma-android-backend-debug-apk
Downloaded APK: /tmp/apk-artifact-27081477480/gemma-android-backend-debug-apk/android-backend-debug.apk
APK size: 26094850 bytes
```

Latest Reading Mode + Gemma workflow APK:

```text
Workflow: Android Backend APK
Run: https://github.com/tqa25/gemma-litert-server/actions/runs/27081804289
Commit: 8e54c50
Artifact: gemma-android-backend-debug-apk
Downloaded APK: /tmp/apk-artifact-27081804289/gemma-android-backend-debug-apk/android-backend-debug.apk
APK size: 26097862 bytes
```

Automation device commands after installing an APK built on GitHub Actions:

```bash
python3 termux-bridge/automation_client.py status
python3 termux-bridge/automation_client.py current-app
python3 termux-bridge/automation_client.py screenshot --output screen.png
python3 termux-bridge/automation_client.py screen-xml --output screen.xml
python3 termux-bridge/automation_client.py calibrate chrome_discover_first_article --x 540 --y 700
python3 termux-bridge/automation_client.py calibrate chrome_menu_button --x 1010 --y 120
python3 termux-bridge/automation_client.py calibrate chrome_show_reading_mode --x 720 --y 860
python3 termux-bridge/automation_client.py calibrate gemini_summary_button --x 540 --y 1800
python3 termux-bridge/automation_client.py calibrate gemini_copy_button --x 960 --y 2100
python3 termux-bridge/automation_client.py run chrome-discover-reading-gemma-summary-once --debug-capture
```

Fast OCR:

```bash
pkg install python-pillow
python3 termux-bridge/client.py generate-image \
  --image /sdcard/Download/test.png \
  --ocr-mode fast
```

Full OCR:

```bash
python3 termux-bridge/client.py generate-image \
  --image /sdcard/Download/test.png \
  --ocr-mode full
```

Benchmark:

```bash
python3 termux-bridge/client.py benchmark-image \
  --image /sdcard/Download/test.png \
  --runs 3 \
  --ocr-mode fast
```

APK build/debug in GitHub Actions:

```bash
gh run list --workflow android-backend-apk.yml --branch docs/vietnamese-guide-android-plan --limit 5
gh run watch <RUN_ID> --exit-status
gh run view <RUN_ID> --job <JOB_ID> --log
```

## Next Recommended Work

1. Install and verify the next OCR History APK from the latest successful Actions artifact.
2. Run Fast OCR twice and Full OCR once from Android OCR Runner, then confirm all three results appear in OCR History.
3. Close and reopen the app, tap old history items, copy text, and clear history.
4. Run Fast OCR and Full OCR from Android OCR Runner on `test1`, `test2`, and `test3`, then record visual OCR quality notes.
5. Update `PROGRESS.md` with visual quality conclusions for each image.
6. Update `docs/api.md` so multipart image upload is documented as the preferred path.
7. Consider adding a `/diagnostics` endpoint if `/health.last_request` becomes too cramped.
8. Consider streaming later if first-token latency becomes important.
9. Keep building toward an on-device agent only after the OCR/backend slice is stable.
