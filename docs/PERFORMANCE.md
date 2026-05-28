# Performance Baselines

XReader performance work should be evidence-driven. Use the adb baseline script before and after changes that affect startup, library rendering, reader opening, parsing, or Readium integration.

## Baseline Script

Build and install a debug APK:

```bash
./gradlew :app:assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Capture cold startup timings:

```bash
tools/perf_baseline.sh --iterations 7
```

Capture startup plus reader-open frame data when the library already has at least one visible book:

```bash
tools/perf_baseline.sh --iterations 7 --reader-tap 400 780
```

The script writes artifacts to `build/perf/<timestamp>/`, including:

- `context.txt`
- `startup.tsv`
- `summary.txt`
- `startup-gfxinfo.txt`
- `startup-framestats.txt`
- `startup-ui.xml`
- `startup.png`
- optional `reader-open-gfxinfo.txt`
- optional `reader-open-framestats.txt`
- optional `reader-open-ui.xml`
- optional `reader-open.png`

## Reading Results

Use `summary.txt` for repeated cold startup timing. Use `gfxinfo` outputs for frame counts, janky frame percentages, missed frame deadlines, slow UI thread work, and slow draw commands.

Treat single-device numbers as local baselines, not universal claims. Record:

- device model
- Android version
- build type
- imported library size
- command used
- artifact directory
- startup median and p90
- reader-open jank headline when captured

## Current Local Baseline

2026-05-28 local debug baseline after reader warmup and analytics polish:

- Device: Samsung SM-F966U, Android 16 / API 36
- Library: three imported EPUBs, including one in-progress book with cover art
- Command: `tools/perf_baseline.sh --serial RFCY90NPZBN --iterations 7 --reader-tap 400 780`
- Artifact directory: `build/perf/20260528-134003/`
- Startup total time: average 594.7 ms, median 590 ms, p90 612 ms
- Startup frame capture: 8 frames, 4 janky frames, 90th percentile 200 ms, 2 missed vsync
- Reader-open frame capture: 45 frames, 6 janky frames, 90th percentile 24 ms, 1 missed vsync
- Simpleperf startup artifact: `build/perf/20260528-134003/simpleperf-startup/`

The reader-open capture confirms the tap opened the real EPUB reader. The capture window is fixed at roughly five seconds; use the frame data as the current jank baseline, not as a direct reader-open latency measurement. The startup Simpleperf capture is dominated by cold debug APK dex/class loading, verification, and JIT activity rather than a clear app-owned startup hotspot. Treat startup jank improvements as requiring release/profileable build measurement or a baseline-profile pass, not just one debug Simpleperf run.
