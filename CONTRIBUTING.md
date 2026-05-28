# Contributing

XReader is currently a personal native Android app, but contributions should still follow production standards: no placeholder UI, no fake integrations, and no features exposed before the behavior is real.

## Setup

Install:

- JDK 21
- Android SDK with compileSdk 36
- Android Studio or command-line SDK tools

Build:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Optional connected-device install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.xreader.app/.MainActivity
```

## Development Rules

- Keep the app local-first unless a feature explicitly requires network access.
- Use Android SAF for imports. Do not request broad all-files access.
- Preserve user metadata edits during automated backfills or repairs.
- Keep MOBI/AZW3 out of the UI until real conversion exists.
- Do not commit copyrighted book files, commercial cover art, or screenshots containing copyrighted book text.
- Use Readium for EPUB/PDF reading behavior instead of rebuilding reader primitives locally.
- Put file/database work in services or repositories, not directly in Compose UI.
- Add focused tests for import, parsing, persistence, search, analytics, and dictionary changes.

## Checks

Run before pushing:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --console=plain
./gradlew :app:lintRelease :app:assembleRelease --console=plain
```

For reader/UI changes, also install on a device or emulator and smoke-test a real EPUB/PDF when possible.

For startup, reader-open, import, or UI performance changes, capture a local adb baseline:

```bash
tools/perf_baseline.sh --iterations 7 --reader-tap 400 780
```

## Documentation

Update documentation when behavior changes:

- `README.md` for user-facing capability/build changes.
- `docs/ARCHITECTURE.md` for data flow or dependency changes.
- `docs/PERFORMANCE.md` for performance methodology or headline baseline changes.
- `docs/ROADMAP.md` for product scope changes.
- `CHANGELOG.md` for notable shipped changes.
- `NOTICE` when bundled assets or third-party attribution changes.
