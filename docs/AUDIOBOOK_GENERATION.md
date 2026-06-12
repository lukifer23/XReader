# Audiobook Generation

XReader's embedded audiobook path is local-first and model-specific. V1 supports one maintained neural voice package: Sherpa-ONNX Kokoro v1.0.

## Current Runtime

- Model: `kokoro-multi-lang-v1_0`
- Engine: Sherpa-ONNX OfflineTTS through the app-bundled JNI bridge.
- Providers: `webgpu`, then `xnnpack`, then `cpu`.
- Disabled provider: `nnapi`.

WebGPU/Dawn is the active Android GPU path. On the Samsung SM-F966U / SM8750 test device, ONNX Runtime WebGPU opens the Adreno Vulkan driver and Kokoro preview generation succeeds. Some Kokoro nodes are not currently accepted by WebGPU with CPU fallback disabled, so production WebGPU uses ORT's normal fallback for unsupported nodes while supported subgraphs run through Dawn/Vulkan.

NNAPI is intentionally not used for production OfflineTTS. Current device evidence shows it compiles Kokoro through `nnapi-reference`, which is CPU/reference rather than a real accelerator. XNNPACK remains the stable optimized fallback path. QNN is staged as an internal native bring-up path only; it is not enabled by default until a no-fallback hardware smoke proves that the model is actually running on Qualcomm hardware.

## Text Preparation

Full-book generation uses the app's indexed reading-order text, then prepares audiobook segments by:

- sorting source chunks by reading order
- removing URLs, email addresses, ISBN fragments, soft hyphens, repeated boilerplate, duplicate passages, and isolated page markers
- normalizing smart quotes, dashes, punctuation, and paragraph spacing
- splitting around sentence and clause boundaries
- targeting roughly 520 characters per segment, with a 780-character hard cap for normal text
- merging very short orphan segments when doing so does not create an oversized prompt

The segment target is deliberately below Kokoro's known long-utterance risk area. Kokoro voices can sound rushed on very long prompts, so smaller sentence-aware segments are preferred over fewer giant segments.

## User-Facing Controls

The app should keep the audiobook surface compact:

- one model row: Kokoro v1.0
- narrator filter by gender
- narrator chips filtered by gender, with the selected narrator always visible
- tone and pacing controls
- explicit download/install/reinstall/delete model actions
- scan summary before generation
- persisted progress, ETA, cancellation, resume/retry, generated playback, delete, and export

Do not add placeholder model rows, fake acceleration toggles, or unsupported voice families.

## Acceleration Timeline

### Phase 1: Stabilized XNNPACK Baseline

Status: retained as fallback.

Tasks:

- Use `xnnpack` before `cpu` and remove NNAPI from OfflineTTS provider selection.
- Record provider used in generated audio manifests.
- Benchmark Kokoro v1.0 preview generation and full-book generation on the Samsung test device.
- Capture speed, ETA accuracy, battery/thermal behavior, and provider logs.
- Tune segment length and thread count from measured results, not guesses.

Acceptance:

- No NNAPI crash/fallback logs during preview or generation.
- Provider manifest shows `webgpu`, `xnnpack`, or clean `cpu` fallback.
- A short book and a medium book complete, save, play, resume, delete, and regenerate.

### Phase 2: Native QNN Prototype

Status: native build path wired; disabled by default after Samsung device validation failed no-fallback QNN.

Tasks:

- Stage the public Qualcomm Android QNN runtime libraries with `tools/stage_public_qnn_runtime.sh`.
- If we need to rebuild Sherpa-ONNX itself, use Qualcomm QAIRT/QNN SDK plus Android NDK with `tools/build_sherpa_qnn_android.sh`, then stage those outputs with `tools/stage_qnn_android_runtime.sh`.
- Package only the required QNN/Sherpa/ONNX shared libraries for a development build.
- Use the internal provider probe to report staged QNN libraries, but do not put `qnn` in the production provider order until a no-fallback smoke passes.
- Record QNN initialization success/failure in logcat and in generated audiobook manifests through the selected provider field.
- Keep QNN off the user-facing UI until it produces a complete audiobook successfully.

Acceptance:

- App launches with QNN libraries on the Samsung device.
- Runtime can initialize Kokoro without crashing.
- Provider logs and generated manifests prove whether QNN is actually used.
- QNN must fail closed when ONNX Runtime assigns nodes to CPU; silent CPU fallback is not accepted as acceleration.

Current local status:

- This machine has Android NDK `28.2.13676358` and a local QAIRT SDK extraction at `.native-cache/qairt-sdk/qairt/2.40.0.251030`.
- Public Qualcomm runtime libraries can be staged from Maven; latest checked runtime was `qnn-runtime:2.47.0`.
- A Sherpa-ONNX JNI rebuild with a local QNN provider patch can call ONNX Runtime's `QNNExecutionProvider`.
- Samsung SM-F966U / SM8750 validation on 2026-06-12 did not pass no-fallback QNN:
  - QNN HTP with the 2.47 runtime failed device creation with `QNN_DEVICE_ERROR_INVALID_CONFIG`.
  - QNN GPU failed because vendor OpenCL libraries were not accessible from the app namespace and reported `QNN_COMMON_ERROR_PLATFORM_NOT_SUPPORTED`.
  - ONNX Runtime then assigned graph nodes to CPU; with `session.disable_cpu_ep_fallback=1`, this correctly fails instead of pretending to accelerate.
- The shipped app runtime now uses Sherpa-ONNX against standard ONNX Runtime with WebGPU, XNNPACK, NNAPI, and CPU providers available.
- QNN libraries may be staged in development builds, but `qnn` stays out of production provider order until QNN passes a no-fallback hardware smoke.

### Phase 2b: WebGPU/Vulkan Android GPU Path

Status: enabled after Samsung device validation.

Tasks:

- Rebuild Sherpa-ONNX JNI against the standard ONNX Runtime Android package that includes WebGPU, XNNPACK, and NNAPI.
- Add a native `webgpu` provider selector that calls ONNX Runtime's generic `SessionOptionsAppendExecutionProvider("WebGPU", ...)`.
- Let Dawn select its Android Vulkan backend; do not force the rejected lowercase `vulkan` provider option.
- Keep CPU fallback enabled for WebGPU because Kokoro v1.0 is not fully covered by WebGPU kernels.
- Put `webgpu` before `xnnpack` in app provider order on Vulkan-capable Android devices.

Acceptance:

- Device logcat shows `Use WebGpuExecutionProvider`.
- Device logcat shows Adreno Vulkan / Dawn initialization for `com.xreader.app`.
- Kokoro preview generation completes with non-empty samples.

Samsung SM-F966U / SM8750 evidence from 2026-06-12:

- `TtsAcceleration: WebGPU provider enabled for Android Vulkan GPU acceleration.`
- `AdrenoVK-0: Driver Path : /vendor/lib64/hw/vulkan.adreno.so`
- `AdrenoVK-0: Engine Name : Dawn`
- `sherpa-onnx: Use WebGpuExecutionProvider`
- Instrumented Kokoro smoke test passed.

### QNN Development Commands

Public QNN runtime flow:

```bash
tools/stage_public_qnn_runtime.sh
./gradlew --no-daemon clean :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Full source rebuild flow:

```bash
export ANDROID_NDK=/Users/admin/Library/Android/sdk/ndk/28.2.13676358
export QNN_SDK_ROOT=/path/to/Qualcomm/AI_Runtime_SDK

SHERPA_LIB_DIR="$(tools/build_sherpa_qnn_android.sh)"
ONNXRUNTIME_LIB_DIR=/path/to/onnxruntime-android-qnn/jni/arm64-v8a tools/stage_qnn_android_runtime.sh "$SHERPA_LIB_DIR"
./gradlew --no-daemon clean :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --console=plain
```

After installing the resulting APK on a Qualcomm device, verify provider selection:

```bash
adb logcat -c
adb shell am force-stop com.xreader.app
adb shell monkey -p com.xreader.app 1
adb logcat -d | grep -E "TtsAcceleration|NeuralTtsRepository"
```

### Phase 3: QNN Measurement And Fallback

Status: planned.

Tasks:

- Benchmark QNN against XNNPACK on identical preview text and identical book segments.
- Measure initialization overhead, per-segment latency, total generation time, battery, and thermal throttling.
- Verify fallback to XNNPACK when QNN is unavailable, unsupported, or slower.
- Benchmark WebGPU against XNNPACK on identical preview text and identical book segments.
- Measure whether WebGPU's partial acceleration beats XNNPACK for full-book generation on real books.
- Decide whether QNN is worth exposing automatically.

Acceptance:

- QNN beats XNNPACK enough on real book generation to justify binary/runtime complexity, or it stays internal/disabled.
- No user-visible acceleration toggle exists unless the provider is real, measured, and reliable.

## Research Notes

- Sherpa-ONNX issue guidance says not to enable NNAPI for OfflineTTS; use CPU or XNNPACK instead.
- Sherpa-ONNX documents a Qualcomm NPU/QNN build path requiring `QNN_SDK_ROOT` and `ANDROID_NDK`.
- ONNX Runtime's QNN Execution Provider targets Qualcomm Snapdragon acceleration through QAIRT/QNN.
- Kokoro-82M is an Apache-licensed 82M-parameter TTS model with a documented voice list and known long-utterance caveats.
