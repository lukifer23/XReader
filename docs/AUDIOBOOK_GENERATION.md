# Audiobook Generation

XReader's embedded audiobook path is local-first and model-specific. V1 supports one maintained neural voice package: Sherpa-ONNX Kokoro v1.0.

## Current Runtime

- Model: `kokoro-multi-lang-v1_0`
- Engine: Sherpa-ONNX OfflineTTS through the app-bundled JNI bridge.
- Provider: strict `qnn-htp` only for preview and full-book neural generation. `qnn-gpu`, `nnapi`, `webgpu`, `xnnpack`, and `cpu` are not packaged or selected as alternate generation backends.
- The runtime attempts one strict QNN HTP/NPU provider. If that provider fails to initialize, lacks a strict-compatible model artifact, assigns graph work to CPU, or runs too slowly, generation fails closed instead of trying another backend.

Neural generation now fails closed unless the selected strict QNN HTP/NPU provider initializes and then proves sustained faster-than-realtime performance on generated segment output for full-book work. The repository requests a single hardware provider, rejects CPU-backed providers up front, then applies the sustained speed gate during generation after enough segment/audio evidence exists. Providers slower than `0.55x` audio time are rejected instead of silently producing unusably slow full-book jobs, so a run that degrades toward realtime stops early with a provider diagnostic instead of wasting hours.

Generation speed is measured as end-to-end segment cost: native model synthesis plus WAV save and atomic finalize. Generated-audiobook manifests keep `generationComputeMillis` for the total measured generation cost and `generationSaveMillis` for the file-output portion, so slow runs can be traced to provider inference or disk/encode overhead without guessing.

User-facing performance labels report this as `x audio time`, where lower is better: `0.5x audio time` means generation took half the audio duration, while `2.0x audio time` means generation was slower than realtime.

Strict QNN providers require a prepared `model.qnn.onnx` beside the installed model and a matching `xreader-qnn-model-manifest.json` whose `strict_qnn_compatible` field is `true`. A bare `model.qnn.onnx` is not enough, because current device evidence showed an artifact can still contain control-flow, sequence, random, and dynamic-input graph surfaces that leave unsupported work on CPU. QNN provider configs set `disable_cpu_ep_fallback=1`; if ONNX Runtime assigns nodes to CPU, generation fails instead of pretending to accelerate.

Readiness checks used by Settings and generation dialogs are deliberately non-mutating: they report strict hardware availability using provider labels and do not create provider config files. Actual strict QNN provider configs are written only when a real synthesis runtime starts.

WebGPU is retired from the active audiobook generation path. A 2026-06-12 full-book run reached `362/397` generated segments, then the native process aborted after ONNX Runtime WebGPU reported `Failed to download data from buffer: [Device] is lost.` XReader now keeps neural generation on the strict QNN path only.

## Text Preparation

Audiobook generation uses the app's indexed reading-order text, then prepares audiobook segments by:

- sorting source chunks by reading order
- removing URLs, email addresses, ISBN fragments, soft hyphens, repeated boilerplate, duplicate passages, standalone table-of-contents entry rows, and isolated page markers
- normalizing smart quotes, dashes, punctuation, and paragraph spacing
- splitting around sentence and clause boundaries
- skipping obvious publisher/copyright front matter before an early Prologue/Chapter marker
- detecting anchored chapter labels including numeric, roman numeral, `Chapter`, `Section`, `Episode`, `Prologue`, `Epilogue`, and word-number headings such as `ONE`/`TWO`
- preserving extractor-provided bare numeric and roman numeral headings as chapter markers while continuing to drop body/footer page numbers from narration text
- treating `Part` and `Book` headings as audiobook section labels while preserving them in generated audio and chapter navigation
- normalizing bare chapter tokens such as `1`, `IV`, or `ONE` into user-facing labels such as `Chapter 1`, `Chapter IV`, or `Chapter One`
- targeting roughly 560 characters per narration segment, with an 850-character hard cap for normal text and shorter prompts for heading-only passages
- merging very short orphan segments when doing so does not create an oversized prompt
- writing `chapters.tsv` and `segments.tsv` sidecars so playback knows chapter grouping, segment text, and intended pause length after each generated file

The segment target is deliberately below Kokoro's long-utterance risk area while avoiding one-WAV-per-sentence choppiness. Kokoro v1.0 is an 82M-parameter Apache-licensed model whose public examples stream generated chunks from input text rather than treating an entire long work as one prompt. XReader therefore keeps prompts sentence-aware, uses paragraph/chapter/question-aware pause metadata during playback, and keeps chapter changes longer than normal intra-paragraph transitions.

Generation scope is persisted per voice profile:

- `FULL_BOOK` generates every prepared segment and is the only generated-audio scope used by in-reader generated read-aloud.
- `SAMPLE` generates the first 12 prepared segments so the user can test narrator, tone, pacing, save quality, and playback before committing to a long job.
- `FIRST_CHAPTER` is applied after audiobook text preparation, using the detected first prepared chapter's segment count so the scan estimate and generated output agree. It falls back to a 60-segment cap when chapter metadata is missing.
- Stopped, failed, and actively generating rows with completed segments remain playable from their existing WAV files.
- Retrying the same scope/profile resumes from the first missing segment instead of overwriting completed audio.
- Fresh generation clears stale output before writing manifest, chapter, and segment metadata so the newly generated WAV files keep matching sidecars for chapter navigation and playback cadence.
- Generated audio writes `chapters.tsv` and `segments.tsv`; playback sanitizes chapter sidecars against the playable segment count, ignores invalid segment chapter IDs, and falls back to sanitized chapter ranges when metadata is missing or stale.
- Interrupted generation recovery reconciles both Room state and the app-private manifest, so stale partial jobs export/debug as canceled or failed with the repaired completed count instead of continuing to advertise `generating`.
- Older generated audio whose chapter sidecar is missing or unreadable falls back to one scope-labeled playback section when verified WAV segments exist, so partial audio remains identifiable without inventing fake chapter boundaries.
- ZIP export preserves existing sidecars when present and writes safe fallback `chapters.tsv`/`segments.tsv` entries for older playable audio whose metadata files are missing.
- ZIP export maps `manifest.in-progress.txt` to `manifest.txt` for partial, stopped, or failed generated audio when no final manifest exists, so exported partial audio still carries status, provider, progress, scope, and voice metadata.
- ZIP export bounds `segments.tsv` to the verified playable WAV prefix, keeping pause/text metadata for exported audio while omitting future segments that were not generated yet.
- Playback position persists the current segment and offset, and final-segment completion is stored as an explicit completed position so finished audio does not reappear as a misleading resume-from-start action.
- Playback setup clears cached sidecar/file state whenever playback resources reset, so deleting, regenerating, or starting a different generated audiobook cannot reuse stale chapter or segment metadata.
- Reader playback lookup queries only rows for the selected model/speaker/pace/tone profile, ranks likely generated-audio candidates from Room first, then repairs and verifies only the selected candidate before falling through to the next option. This keeps playback startup from loading or scanning unrelated generated-audio rows for the book.
- Long native segment synthesis polls for cancellation separately from heartbeat writes. Cancellation remains responsive, while row-dirtying heartbeat updates are limited to long-running segments so Room invalidations and audiobook-screen recomposition do not churn every few seconds under native generation load. The loop also re-checks cancellation after synthesis and after WAV save before recording progress or moving to the next segment, so stop/clear actions are honored even when progress writes are coalesced.
- Heartbeat cleanup waits for the heartbeat coroutine to finish after each native segment call, preventing a stale heartbeat from racing a completed, stopped, or deleted row.
- Active generation UI should avoid filesystem-heavy sidecar reads until playable segments exist. During `GENERATING`, rows use bounded fallback section metadata and cache unchanged row models so progress/ETA refreshes do not force repeated chapter parsing on the main library surface.
- The global Audiobooks screen is fed by a single Room relation query for visible generated-audio rows and their books. It does not combine the entire library with the entire audio table on every progress tick.
- Audiobook row cache entries are pruned to the currently visible active set so old sidecar metadata cannot accumulate or leak into later regenerated rows.
- Audiobook row caching stores expensive file-derived metadata separately from the live Room row, so heartbeat-only progress updates can refresh ETA/provider/timing labels without reparsing chapter sidecars.
- Startup maintenance seeds the supported model catalog early, repairs interrupted model installs early, then defers stale audiobook repair and obsolete model-storage pruning so app launch and the library surface do not compete with heavy filesystem work.

## User-Facing Controls

The app should keep the audiobook surface compact:

- one model row: Kokoro v1.0
- narrator filter by gender
- narrator chips filtered by gender, with the selected narrator always visible
- narration style and pacing controls backed by the Kokoro runtime's real speed and silence/cadence parameters
- explicit download/install/reinstall/delete model actions
- scan summary before generation, including detected chapters, local storage estimate, duration estimate, and compact per-scope segment/duration labels
- generate sample, detected first chapter, and full-book actions
- persisted progress, ETA, cancellation, resume/retry, partial playback, generated playback, chapter picker/jump controls, delete, and export
- active generation status should say which segment is being worked on instead of only showing completed segments, because a long hardware call can legitimately spend time inside the current segment before the completed count advances
- partial rows must explicitly say `Play partial` and `Save partial` so the user can tell incomplete generated audio apart from a completed audiobook
- play, save, partial, and resume labels must be based on verified contiguous WAV files, not only database counters; repaired or missing audio should show missing/partial state and should not offer impossible resume positions
- chapter jump controls must use the prepared/generated chapter sidecar boundaries instead of only current-playback metadata, so older or repaired audio never exposes a previous/next chapter action that cannot actually move
- single-section generated audio should show the section count honestly but should not expose a chapter picker that can only jump to the current section

Do not add placeholder model rows, fake acceleration toggles, or unsupported voice families.

## Acceleration Timeline

### Phase 1: Retired CPU Preview Baseline

Status: retired from app runtime. Historical measurements remain useful only for comparison.

Tasks:

- Keep CPU/XNNPACK out of runtime provider selection; unsupported devices show a blocked state instead of a slow generated preview.
- Record provider used in generated audio manifests.
- Benchmark Kokoro v1.0 preview generation on the Samsung test device.
- Capture speed, ETA accuracy, battery/thermal behavior, and provider logs.
- Tune segment length and thread count from measured results, not guesses.

Acceptance:

- Preview generation completes, saves, plays, and deletes through the same strict hardware provider path.
- CPU-backed preview providers are not selected.

### Phase 2: Native QNN Bring-Up

Status: active primary focus.

Tasks:

- Stage Qualcomm Android QNN runtime libraries with `tools/stage_public_qnn_runtime.sh` or a QAIRT-matched local package.
- If we need to rebuild Sherpa-ONNX itself, use Qualcomm QAIRT/QNN SDK plus Android NDK with `tools/build_sherpa_qnn_android.sh`, then stage those outputs with `tools/stage_qnn_android_runtime.sh`.
- Package only the required QNN/Sherpa/ONNX shared libraries and DSP assets for a development build.
- Use the internal provider probe to report staged QNN libraries, and keep provider selection honest: strict QNN HTP/NPU only, no secondary provider fallback.
- Record QNN initialization success/failure in logcat and in generated audiobook manifests through the selected provider field.
- Keep QNN off the user-facing UI until it produces a complete audiobook successfully.

Acceptance:

- App launches with QNN libraries on the Samsung device.
- Runtime can initialize Kokoro without crashing.
- Provider logs and generated manifests prove whether QNN is actually used.
- Strict QNN must fail closed when ONNX Runtime assigns nodes to CPU.
- A short generation completes faster than realtime on device, saves playable WAV output, records the QNN provider in the manifest, and does not freeze or crash the main app process.

Current local status:

- This machine has Android NDK `28.2.13676358` and a local QAIRT SDK extraction at `.native-cache/qairt-sdk/qairt/2.40.0.251030`.
- QAIRT 2.40 local runtime artifacts are the current alignment target for the packaged ONNX Runtime/QNN build.
- A Sherpa-ONNX JNI rebuild with a local QNN provider patch can call ONNX Runtime's `QNNExecutionProvider`.
- Samsung SM-F966U / SM8750 validation on 2026-06-12 did not pass no-fallback QNN:
  - QNN HTP with the 2.47 runtime failed device creation with `QNN_DEVICE_ERROR_INVALID_CONFIG`.
  - QNN GPU failed because vendor OpenCL libraries were not accessible from the app namespace and reported `QNN_COMMON_ERROR_PLATFORM_NOT_SUPPORTED`.
  - ONNX Runtime then assigned graph nodes to CPU; with `session.disable_cpu_ep_fallback=1`, this correctly fails instead of pretending to accelerate.
- QNN GPU/OpenCL is retired from the packaged audiobook path. The app no longer stages `libQnnGpu.so`, `libQnnGpuNetRunExtensions.so`, `libOpenCL.so`, or `libOpenCL_adreno.so`, and provider selection treats `qnn-gpu` as unsupported instead of an alternate backend.
- Samsung SM-F966U / SM8750 validation on 2026-06-22 confirmed that a rebuilt debug APK with GPU/OpenCL libraries still failed installed-model strict smoke because ONNX Runtime assigned stock Kokoro graph nodes to CPU with strict fallback disabled. That evidence is why the current path is HTP/NPU-only plus a strict-compatible prepared model artifact.
- Samsung SM-F966U / SM8750 validation on 2026-06-23 exposed QNN HTP transport sensitivity. Over-specified HTP options such as signed process-domain, forced `soc_model`, and forced `htp_arch` can trigger `QNN_DEVICE_ERROR_INVALID_CONFIG`, so the app now writes a minimal HTP provider config by default and keeps those values only as explicit instrumentation overrides.
- App-side QNN model selection now requires both `model.qnn.onnx` and a strict-compatible `xreader-qnn-model-manifest.json` for HTP/NPU. The model-prep tool writes `strict_qnn_compatible` plus a blocker report for control-flow, sequence, random, and dynamic-input surfaces so incompatible artifacts do not become selectable.
- 2026-06-30 local rebuild: `libsherpa-onnx-jni.so` was rebuilt from the patched Sherpa tree against the cached QAIRT 2.40 SDK and cached QNN-enabled ONNX Runtime package, then staged into the debug APK with QNN runtime libraries. Packaging tests now assert that the APK's Sherpa JNI contains `strict_token_bucket`, `session.disable_cpu_ep_fallback`, and `QNNExecutionProvider`, and no longer contains the old unsupported-provider CPU fallback string.
- QNN libraries may be staged in development builds, but `qnn` must still pass strict no-fallback smoke and a real generation speed/stability gate on device before it counts as usable generation acceleration.

### Phase 2b: Retired WebGPU/Vulkan Path

Status: retired from active generation.

Samsung SM-F966U / SM8750 evidence from 2026-06-12:

- `TtsAcceleration: WebGPU provider enabled for Android Vulkan GPU acceleration.`
- `AdrenoVK-0: Driver Path : /vendor/lib64/hw/vulkan.adreno.so`
- `AdrenoVK-0: Engine Name : Dawn`
- `sherpa-onnx: Use WebGpuExecutionProvider`
- Instrumented Kokoro smoke test passed.
- Full-book crash evidence: WebGPU generated 362 segments before native aborting with Dawn device lost. The mitigation is removal from the neural generation provider set, not runtime fallback.

### QNN Development Commands

Public QNN runtime flow:

```bash
tools/stage_public_qnn_runtime.sh
./gradlew --no-daemon clean :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Full source rebuild flow:

```bash
export ANDROID_NDK=/Users/admin/Library/Android/sdk/ndk/28.2.13676358
export QNN_SDK_ROOT=/Users/admin/Documents/XReader/.native-cache/qairt-sdk/qairt/2.40.0.251030
export SHERPA_ONNX_ONNXRUNTIME_ROOT=/Users/admin/Documents/XReader/.native-build/onnxruntime-qnn-android-package

SHERPA_LIB_DIR="$(tools/build_sherpa_qnn_android.sh)"
ONNXRUNTIME_LIB_DIR="$SHERPA_ONNX_ONNXRUNTIME_ROOT/jni/arm64-v8a" tools/stage_qnn_android_runtime.sh "$SHERPA_LIB_DIR"
./gradlew --no-daemon clean :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --console=plain
```

GPU/OpenCL staging is intentionally absent. The strict-QNN contract packages no GPU/OpenCL, NNAPI, WebGPU, XNNPACK, or CPU alternate generation provider. `:app:verifyReleasePackaging` enforces the prohibited fallback inventory on the assembled release APK; connected-device QNN proof is still required before claiming usable acceleration.

After installing the resulting APK on a Qualcomm device, verify provider selection:

```bash
adb logcat -c
adb shell am force-stop com.xreader.app
adb shell monkey -p com.xreader.app 1
adb logcat -d | grep -E "TtsAcceleration|NeuralTtsRepository"
```

### Phase 3: QNN Measurement And Stabilization

Status: in progress, after strict QNN initialization is stable.

Tasks:

- Benchmark strict QNN HTP/NPU against the historical CPU baseline on identical preview text and identical book segments.
- Measure initialization overhead, per-segment latency, total generation time, battery, and thermal throttling.
- Prepare a QNN-ready Kokoro artifact instead of assuming the stock `model.onnx` can fully offload. ONNX Runtime's strict QNN backend requires a fixed compatible graph, and XReader now looks for `model.qnn.onnx` plus a strict-compatible manifest beside the installed Kokoro model before enabling HTP/NPU full-book generation.
- Generate that artifact with representative calibration tensors:

```bash
python3 -m pip install onnx onnxruntime numpy
tools/prepare_kokoro_qnn_model.py \
  --source /path/to/kokoro-multi-lang-v1_0.tar.bz2 \
  --calibration-dir /path/to/kokoro-calibration-npz \
  --output /path/to/kokoro-multi-lang-v1_0-qnn \
  --require-strict-qnn-compatible
```

The output directory contains the normal Kokoro support files plus `model.qnn.onnx` and `xreader-qnn-model-manifest.json`. Push or package that directory so both files sit next to the installed `model.onnx`. The app chooses the prepared model for strict hardware providers only when the manifest declares `strict_qnn_compatible: true`; otherwise it fails closed and reports the missing strict-compatible artifact.
- Verify that QNN fails closed when unavailable, unsupported, or slower than the full-book audio-time threshold.
- Capture Simpleperf/Perfetto evidence for a short generation run on the Samsung test device and confirm the selected provider in logcat plus the generated manifest.
- Confirm long-segment heartbeat updates, stop handling, and UI responsiveness while a strict provider is actively generating, not only after segment completion.
- Measure whether prepared QNN HTP/NPU beats realtime enough on real book generation to justify the packaged runtime cost.
Acceptance:

- QNN HTP/NPU beats the historical XNNPACK/CPU baseline enough on real book generation to justify binary/runtime complexity, or it stays internal/disabled.
- No user-visible acceleration toggle exists unless the provider is real, measured, and reliable.

## Research Notes

- Sherpa-ONNX issue guidance says not to enable NNAPI for OfflineTTS; use CPU or XNNPACK instead.
- Sherpa-ONNX documents a Qualcomm NPU/QNN build path requiring `QNN_SDK_ROOT` and `ANDROID_NDK`.
- ONNX Runtime's QNN Execution Provider targets Qualcomm Snapdragon acceleration through QAIRT/QNN.
- Kokoro-82M is an Apache-licensed 82M-parameter TTS model with a documented voice list and known long-utterance caveats.
