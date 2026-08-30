# Roadmap

This is the working product roadmap for the personal APK line. Items should appear in the app only after they are backed by real behavior.

## Near Term

- Continue splitting large Compose screen files into focused component files.
- Run the compiled Room 1 through 16 migration suite on the connected device and validate retained ebook data, the new audiobook/narration tables, and process-death restore behavior.
- Complete and device-accept imported audiobook confirmation/grouping, metadata editing, ebook linking, collections, bookmarks, speed, sleep timer, folder import, Open with/Share, notification controls, and codec coverage. The current checkpoint has the schema, bounded media parsing, private-copy/repair transaction, playback controller/service, basic import/search/playback UI, and intent classification, but not the full acceptance surface.
- Finish backup-v2 restore planning/results UX and execute crash-journal, DataStore rollback, repeated-import, missing-media, and full restore instrumentation on device. The bounded ZIP codec and Room/DataStore recovery protocol compile and have JVM coverage, but the durable per-section result/retry screen remains open.
- Validate narration review and exact-phrase pronunciation on real public-domain EPUB/PDF/TXT/DOCX/HTML/MOBI fixtures, then add retained/excluded golden files for all supported extraction paths.
- Run connected-device accessibility and layout acceptance for 48 dp controls and actual-window responsive behavior across phone, landscape, tablet, and foldable widths.
- Expand public-domain fixture coverage into screenshot and reader navigation QA.
- Track startup and reader-open baseline history after each major UI, import, Readium, or audiobook-generation change.
- Add connected-device screenshot QA for the refreshed reading-stats screen across empty, light-use, and long-history libraries.
- Verify readability repair labels on real imported EPUB/PDF/TXT samples during the next connected-device pass.
- Make embedded Kokoro v1.0 neural generation hardware-accelerated, stable, and faster than realtime before expanding adjacent audiobook features. Current focus is one strict QNN HTP/NPU path on the Samsung SM-F966U, with no GPU/OpenCL/NNAPI/WebGPU/CPU alternate provider packaged or selected and no generation provider enabled without a strict-compatible prepared Kokoro artifact.
- Execute the audiobook acceleration timeline in [Audiobook Generation](AUDIOBOOK_GENERATION.md): strict QNN provider config, strict-compatible fixed-bucket Kokoro artifact, connected-device smoke proof, then Simpleperf/Perfetto evidence for speed, thermal behavior, and UI responsiveness.

## Reader Polish

- Evaluate page-turn animation styles supported by Readium and Android without adding custom fake page effects.
- Continue hardening read-aloud lifecycle behavior across installed Android TTS engines, including local neural/offline providers selected through Settings, and long background sessions beyond the in-reader pause/resume, sleep timer, passage controls, audio-focus handling, Android media-session transport controls, and media-playback foreground notification.
- Continue hardening embedded Kokoro audiobook generation: text preparation, sentence-aware segmentation, progress/ETA accuracy, cancellation/resume for long generation jobs, playback of generated segment sets inside the app, delete/regenerate flows, and strict hardware provider stability per device class.
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
- Add bounded OPDS 2 open-audio acquisition after real catalog validation. Do not add authenticated loan playback or scrape Libby.
- Enable a branded Libby reading-data importer only after validating a real user-produced export. Keep ISBN-first matching, explicit confirmation, attribution, and unmatched-row reporting; do not infer an undocumented format from fabricated fixtures.
- Evaluate additional embedded voices only after Kokoro v1.0 has battery, latency, retry, storage, and quality evidence from real devices. Do not add duplicate voice families to the UI without a clear quality or performance win.
- Add hardware acceleration only as a real runtime provider. XReader's active target is a Qualcomm QNN HTP/NPU development build; no acceleration toggle should appear until provider selection, no-CPU-fallback behavior, prepared-model compatibility, and generation speed are proven on device.
- Optional encryption for the current versioned local full-backup format.
- Release AAB assembly/size verification, native-symbol artifact handling, and the remaining Qualcomm/ONNX Runtime/Sherpa/Kokoro/media redistribution audit. Public distribution stays blocked for artifacts without documented authority.
- Play Store packaging pass with signed release, shrinker configuration, dependency/license review, and APK/AAB size pass.

## Out Of Scope For Now

- DRM removal or DRM playback.
- Libby loan playback, library-card credential storage, Libby scraping, or Adobe DRM fulfillment. Current interoperability is open EPUB/PDF, private DRM-free audio, ACSM external handoff, and future real-export reading-data import.
- Cloud sync.
- Ads or subscriptions.
- Social reading features.
