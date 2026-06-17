# Roadmap

This is the working product roadmap for the personal APK line. Items should appear in the app only after they are backed by real behavior.

## Near Term

- Continue splitting large Compose screen files into focused component files.
- Expand public-domain fixture coverage into screenshot and reader navigation QA.
- Track startup and reader-open baseline history after each major UI, import, Readium, or audiobook-generation change.
- Add connected-device screenshot QA for the refreshed reading-stats screen across empty, light-use, and long-history libraries.
- Verify readability repair labels on real imported EPUB/PDF/TXT samples during the next connected-device pass.
- Device-profile embedded Kokoro v1.0 audiobook generation on small, medium, and long books, including model download/install/reinstall/delete, voice selection, sample and first-chapter generation, full-book generation, provider used, battery/thermal behavior, generated ZIP size, partial playback, resume, delete, and retry behavior after app/process interruption.
- Execute the audiobook acceleration timeline in [Audiobook Generation](AUDIOBOOK_GENERATION.md): XNNPACK baseline first, then a measured Qualcomm QNN/NPU prototype, then provider fallback decisions.

## Reader Polish

- Evaluate page-turn animation styles supported by Readium and Android without adding custom fake page effects.
- Continue hardening read-aloud lifecycle behavior across installed Android TTS engines, including local neural/offline providers selected through Settings, and long background sessions beyond the in-reader pause/resume, sleep timer, passage controls, audio-focus handling, Android media-session transport controls, and media-playback foreground notification.
- Continue hardening embedded Kokoro audiobook generation: text preparation, sentence-aware segmentation, progress/ETA accuracy, cancellation/resume for long generation jobs, playback of generated segment sets inside the app, delete/regenerate flows, and measured WebGPU/XNNPACK/thread defaults per device class.
- Continue evaluating PDF crop/reflow only where the rendering stack can support it directly and predictably; the current Readium-backed path now has adaptive fit for phone, landscape, foldable, and tablet-style viewports.
- Continue device QA for tap-zone presets on gesture-navigation phones and foldable widths.

## Library Polish

- Continue metadata cleanup tuning beyond the current automatic canonicalization, same-author title-pattern series inference, matching-series cleanup, Settings repair harmonization, and edit-dialog suggestions.
- Continue tuning grouped-library navigation now that Authors, Series, Genres, and Years honor the existing sort control for both group order and in-group book order and Books can surface a compact next-in-series recommendation.

## Later

- AZW3/KF8 conversion through a real local conversion pipeline.
- CBR/DJVU/legacy binary DOC only if each format can land as a real import path without cluttering the reader.
- Continue hardening MHTML imports against larger browser-created web archives and remaining unusual MIME encodings.
- Continue hardening OPDS catalog import against larger public catalogs and remaining real-world feed variants beyond the current Atom and OPDS 2-style JSON paths.
- Evaluate additional embedded voices only after Kokoro v1.0 has battery, latency, retry, storage, and quality evidence from real devices. Do not add duplicate voice families to the UI without a clear quality or performance win.
- Add hardware acceleration only as a real runtime provider. XReader's next target is a Qualcomm QNN development build; no acceleration toggle should appear until provider selection and fallback behavior are implemented and measured.
- Optional encrypted local backup.
- Play Store packaging pass with signed release, shrinker configuration, dependency/license review, and APK/AAB size pass.

## Out Of Scope For Now

- DRM removal or DRM playback.
- Cloud sync.
- Ads or subscriptions.
- Social reading features.
