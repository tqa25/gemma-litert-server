# Handoff: gemma-litert-server

## Purpose

Continue work on `gemma-litert-server`, an Android + Termux local OCR backend using Gemma 4 E4B IT LiteRT-LM on ROG Phone 6. The current pipeline is:

```text
Termux CLI -> localhost HTTP -> Android foreground backend -> LiteRT-LM Gemma -> JSON response + timing
```

## Workspace

- Repo path: `/home/ubuntu/workspaces2/projects/gemma-litert-server`
- Branch: `docs/vietnamese-guide-android-plan`
- Worktree at handoff: clean

## Important Artifacts

- Android backend plan: `ANDROID_BACKEND_APP_PLAN.md`
- APK Actions debug runbook: `GITHUB_ACTIONS_APK_DEBUG_RUNBOOK.md`
- Benchmark plan: `docs/benchmark-plan.md`
- Termux test plan: `docs/termux-client-test-plan.md`
- Termux client: `termux-bridge/client.py`
- Android backend entry points:
  - `android-backend/src/main/java/dev/gemma/androidbackend/MainActivity.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/ServerService.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/HttpApiServer.java`
  - `android-backend/src/main/java/dev/gemma/androidbackend/LiteRtGemmaRunner.java`

## Recent Commits

- `6d6444c` Increase Gradle heap for Android APK packaging
- `04f1672` Enable LiteRT GPU backend on Android
- `3af9a1c` Use multipart image upload for Android backend
- `e357f22` Copy selected model into Android app storage
- `e6daaba` Add Termux client for Android backend IPC

## Confirmed Working State

- Model picker copies `gemma-4-E4B-it.litertlm` from shared storage into app-private storage.
- Termux `generate-image` now sends image as `multipart/form-data`, not base64 JSON.
- Android backend reads multipart `image` part and reports `meta.image_bytes`.
- LiteRT GPU backend is enabled with `Backend.GPU()` for both model and vision backend.
- CPU backend is still available from the app for comparison/debug.
- Latest successful APK build:
  - Run: `https://github.com/tqa25/gemma-litert-server/actions/runs/26876517510`
  - Artifact: `gemma-android-backend-debug-apk`
  - APK verified locally at download time: `android-backend-debug.apk`, `25775049 bytes`

## Latest Device Result

Device: ROG Phone 6.

Command:

```bash
python3 termux-bridge/client.py generate-image -i /sdcard/Download/test_img.jpg --prompt "Extract visible text from this image. Return concise text." --max-tokens 256 --temperature 0.1
```

Result summary:

```text
engine: litert-android-gpu
has_image: true
image_bytes: 1372346
inference_ms: 13988
total_ms: 14044
OCR quality: good Vietnamese text extraction
```

This is faster than the user's Edge Gallery measurement on the same image/model, which was about 18 seconds.

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

1. Run a 5-run benchmark on the same image to separate cold-start and warm-start latency.
2. Add optional Termux-side image resize/compress before upload and compare quality vs latency.
3. Consider a streaming endpoint later if the workflow needs first-token latency rather than total latency.
4. Keep `Start LiteRT CPU Server` as a debug comparator, but default future testing to `Start LiteRT GPU Server`.

## Fresh Session Instruction

In a new chat, start with:

```text
Read HANDOFF.md, then continue work in /home/ubuntu/workspaces2/projects/gemma-litert-server.
```
