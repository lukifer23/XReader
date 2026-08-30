# Paused Work: Backup v2, Narration, and Imported Audio

Status: in progress on `main` as of 2026-08-30. This checkpoint is intentionally committed so the work can resume from one synchronized branch. It is not a claim that the full approved reliability/interoperability plan is complete.

## Included in this checkpoint

- Room schema 16 and exported schema for imported audiobook publications, tracks, chapters, bookmarks, collection membership, narration rules/overrides, and restore-operation records.
- A bounded `.xreader-backup` ZIP codec with five declared JSON sections, compressed/uncompressed/section/record limits, path validation, size/count/SHA-256 checks, and legacy unified-v1/separate JSON compatibility.
- A restore journal that uses `AtomicFile` to coordinate DataStore settings with one Room restore transaction and a committed operation record.
- Imported-audio validation/copy/checksum repair for MP3, M4B/M4A/AAC, OGG/Opus, FLAC, and WAV, plus bounded ID3/MP4/Vorbis-style chapter parsing.
- Separate imported-audio playback through Android `MediaPlayer`, audio focus, MediaSession, resume persistence, and a media-playback foreground service.
- Basic Audiobooks import/search/playback/delete UI and Open with/Share classification that keeps audio out of ebook conversion.
- ACSM recognition and external authorized-app handoff with an explicit no-DRM boundary.
- Narration reports, structural exclusion reasons, per-book include/exclude review, exact phrase pronunciation persistence, English-profile enforcement, and preservation of intentional non-adjacent repetition.
- Deterministic QNN artifact-manifest generation and app validation for model hash/size, source revision, toolchain, token bucket, blocker analysis, and provenance/license metadata.
- JVM coverage for backup archive attacks/integrity, narration rules/exclusions, QNN manifest invalidation, and Room schema shape. Android migration coverage compiles through version 16.

## Resume in this order

1. Install the exact checkpoint debug APK on `RFCY90NPZBN` (Samsung SM-F966U, Android 16) before making more changes. If the source tree changes first, rerun the full clean gate from `README.md` and install the newly verified artifact.
2. Execute the Room 1 to 16 migration instrumentation suite and add a full restore instrumentation path covering journal recovery, DataStore rollback, repeated restore, and missing audio/book references.
3. Finish the durable restore-results screen with per-section restorable/unchanged/missing/invalid/conflicting outcomes and real retry/reimport actions.
4. Finish imported-audiobook confirmation/grouping, folder import, editable metadata, explicit ISBN-first ebook linking, collections, bookmarks, speed, sleep timer, chapter navigation, export, and missing-file repair UI.
5. Exercise real MP3/M4B/AAC/OGG/Opus/FLAC/WAV samples on device, including metadata/artwork/chapters, audio focus, notification controls, process death, and folded/unfolded layouts.
6. Add real public-domain EPUB/PDF/TXT/DOCX/HTML/MOBI narration fixtures and golden retained/excluded/chapter/segment/pause/word-count outputs.
7. Split the remaining large neural generation repository along install, validation, preparation, lifecycle, recovery, export, and storage boundaries without changing service intents or strict-QNN behavior.
8. Add bounded OPDS 2 open-audio acquisition only against real catalogs. Keep the branded Libby reading-data importer disabled until a real user-produced export defines the format.
9. Add release AAB assembly/size verification, native symbol handling, ABI/largest-entry reports, forward-looking large-blob policy, and complete redistribution authority records. Do not remove QNN/DSP binaries without connected-device proof.
10. Run strict QNN initialization, no-CPU-fallback, preview, throughput, stability, thermal, and long-job UI tests on device. Do not publish a prepared model until both redistribution and device gates pass.

## Hard boundaries

- No DRM removal, Libby scraping, authenticated loan playback, library-card credential storage, or fabricated Libby export format.
- No CPU/XNNPACK/NNAPI/WebGPU/GPU fallback for neural generation. `qnn-htp`, `disable_cpu_ep_fallback`, prepared-artifact validation, and faster-than-realtime failure gates remain intact.
- Imported books/audio, covers, generated WAVs, neural models, private paths, and user-facing checksum inventories stay outside backups.
- The backup v2 manifest detects corruption but is not encrypted and does not prove cryptographic authenticity.
- Keep work on `main` and push only to `origin/main` after a clean gate.

## Evidence at pause

- The full documented clean gate passes: debug/release lint, 622 JVM tests with no failures or skips, debug APK, Android-test APK, unsigned release APK, and release packaging verification.
- Both lint variants report zero errors and 41 warnings.
- The unsigned release APK is 210,485,914 bytes against a 220,200,960-byte ceiling.
- Connected device detected, but this checkpoint was paused before installing or exercising the new behavior. No new runtime/device claim is made.
