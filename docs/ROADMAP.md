# Roadmap

This is the working product roadmap for the personal APK line. Items should appear in the app only after they are backed by real behavior.

## Near Term

- Continue splitting large Compose screen files into focused component files.
- Expand public-domain fixture coverage into screenshot and reader navigation QA.
- Track startup and reader-open baseline history after each major UI, import, or Readium change.

## Reader Polish

- Evaluate page-turn animation styles supported by Readium and Android without adding custom fake page effects.
- Continue hardening read-aloud lifecycle behavior across lock screen, app backgrounding, Bluetooth/headset controls, and device TTS engine differences beyond the in-reader sleep timer.
- Continue device QA for tap-zone presets on gesture-navigation phones and foldable widths.

## Library Polish

- Add broader bulk metadata tools beyond the current matching-series genre/series cleanup.
- Continue tuning grouped-library navigation now that Authors, Series, Genres, and Years honor the existing sort control for both group order and in-group book order.

## Later

- MOBI/AZW3 conversion through a real local conversion pipeline.
- OPDS catalog import if it can stay optional and low-bloat.
- Evaluate an optional small on-device neural/NLP TTS model after APK size, battery, latency, privacy, and text-position sync testing. Android TextToSpeech remains the default unless a local model is genuinely better offline.
- Optional encrypted local backup.
- Play Store packaging pass with signed release, shrinker configuration, dependency/license review, and APK/AAB size pass.

## Out Of Scope For Now

- DRM removal or DRM playback.
- Cloud sync.
- Ads or subscriptions.
- Social reading features.
