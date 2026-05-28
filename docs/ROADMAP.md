# Roadmap

This is the working product roadmap for the personal APK line. Items should appear in the app only after they are backed by real behavior.

## Near Term

- Split the large Compose UI file into smaller screen/components packages.
- Add public-domain test fixtures for screenshots and instrumented reader QA.
- Track startup and reader-open baseline history after each major UI, import, or Readium change.

## Reader Polish

- Evaluate page-turn animation styles supported by Readium and Android without adding custom fake page effects.
- Harden read-aloud voice controls with voice/rate selection if device testing shows the single play/stop control is too limited.
- Add a reader gesture calibration screen if Samsung/gesture-navigation edge conflicts remain visible.
- Add more accessible spacing presets without crowding the normal reader controls.

## Library Polish

- Add broader bulk metadata tools beyond the current matching-series genre/series cleanup.
- Add sort controls within each group.

## Later

- MOBI/AZW3 conversion through a real local conversion pipeline.
- OPDS catalog import if it can stay optional and low-bloat.
- Optional encrypted local backup.
- Play Store packaging pass with signed release, shrinker configuration, dependency/license review, and APK/AAB size pass.

## Out Of Scope For Now

- DRM removal or DRM playback.
- Cloud sync.
- Ads or subscriptions.
- Social reading features.
