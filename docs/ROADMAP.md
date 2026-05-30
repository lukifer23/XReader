# Roadmap

This is the working product roadmap for the personal APK line. Items should appear in the app only after they are backed by real behavior.

## Near Term

- Continue splitting large Compose screen files into focused component files.
- Expand public-domain fixture coverage into screenshot and reader navigation QA.
- Track startup and reader-open baseline history after each major UI, import, or Readium change.

## Reader Polish

- Evaluate page-turn animation styles supported by Readium and Android without adding custom fake page effects.
- Continue hardening read-aloud lifecycle behavior across device TTS engine differences and long background sessions beyond the in-reader pause/resume, sleep timer, passage controls, audio-focus handling, Android media-session transport controls, and media-playback foreground notification.
- Continue evaluating PDF crop/reflow only where the rendering stack can support it directly and predictably.
- Continue device QA for tap-zone presets on gesture-navigation phones and foldable widths.

## Library Polish

- Add broader bulk metadata tools beyond the current matching-series author/genre/series cleanup.
- Continue tuning grouped-library navigation now that Authors, Series, Genres, and Years honor the existing sort control for both group order and in-group book order and Books can surface a compact next-in-series recommendation.

## Later

- MOBI/AZW3 conversion through a real local conversion pipeline.
- CBR/DJVU/legacy binary DOC only if each format can land as a real import path without cluttering the reader.
- OPDS catalog import if it can stay optional and low-bloat.
- Evaluate an optional small on-device neural/NLP TTS model after APK size, battery, latency, privacy, licensing, and text-position sync testing. Android TextToSpeech remains the default unless a local model is genuinely better offline, streams reliably on midrange phones, and can keep page-level spoken-locator sync at least as reliably as the current read-aloud path.
- Optional encrypted local backup.
- Play Store packaging pass with signed release, shrinker configuration, dependency/license review, and APK/AAB size pass.

## Out Of Scope For Now

- DRM removal or DRM playback.
- Cloud sync.
- Ads or subscriptions.
- Social reading features.
