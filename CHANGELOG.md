# Changelog

## Unreleased

### Added

- Imported audiobooks now have their own Room-backed publication, track, chapter, bookmark, collection, link, and playback state instead of pretending audio is an ebook or generated narration. The current checkpoint includes private-copy import, checksum repair, embedded metadata/chapter parsing, Android media playback, and a dedicated foreground-service lifecycle; full device acceptance and the remaining edit/link/collection controls are tracked in the roadmap.
- Full backup v2 now writes a bounded `.xreader-backup` ZIP with separate library, annotations, settings, audiobook, and narration sections plus declared counts, sizes, and SHA-256 validation. An atomic restore journal coordinates DataStore rollback with one Room transaction and a durable operation record, while unified v1 and legacy separate imports remain accepted.
- Narration scans now report source sections and exclusion reasons, preserve intentional non-adjacent repetition, support persisted per-book include/exclude decisions, and apply bounded exact-phrase pronunciation rules before strict-provider generation.
- Android Open with/Share classification now routes supported audio away from ebook conversion and recognizes ACSM as an Adobe license instruction with an external-app handoff instead of DRM handling.
- QNN prepared-model validation now checks the artifact hash/size, source revision, toolchain, fixed token buckets, blocker analysis, and provenance/license fields rather than trusting one compatibility boolean.

- A versioned full backup now carries library metadata, collections, progress, sessions, settings, per-book appearance, notes, highlights, tags, and bookmarks in one validated local file while preserving the existing separate import tools for compatibility.
- Settings search now finds existing appearance, typography, reading, library, and maintenance controls without adding another settings hierarchy.
- Room schema 15 removes the redundant B-tree index on full-text content while retaining FTS as the search authority, with the complete migration chain compiled into Android instrumentation coverage.
- Release packaging verification now inspects the real APK, enforces a 210 MiB ceiling, inventories intentional QNN native/DSP duplicates against an explicit allowlist, and rejects prohibited GPU/OpenCL fallback runtimes.
- Embedded Kokoro v1.0 audiobook generation with model download/install/delete controls, narrator selection, narration style, pacing, scan summaries, sample/first-chapter/full-book scopes, persisted progress/ETA, partial playback, generated-audio resume, chapter picker/jump controls, delete, and ZIP export.
- Global Audiobooks screen for completed, partial, active, failed, and generated audio rows, with compact playback controls and chapter navigation.
- Collection additions can now be applied to every book in the same-author series from the existing book collections dialog.
- QNN/ONNX Runtime development tooling for real hardware-accelerated audiobook generation, including packaged QNN runtime libraries, DSP asset staging, strict provider configs, device smoke-test hooks, and prepared Kokoro model manifest validation.

### Changed

- Library grouping now persists with sort and density, while transient search state survives recreation through saved state.
- Library filtering, grouping, continuation, and recommendation projection now runs off the Compose thread and cancels stale calculations before publishing stable display sections.
- Reader and library layout decisions now use actual Compose window/container measurements, and compact icon controls use 48 dp touch targets.
- Library audiobook controls and the full Settings surface now live in focused screen files instead of the two catch-all Compose files, with navigation and actions unchanged.
- All ten EPUB-producing converters now share tested ZIP-entry and XML-escaping primitives while preserving their format-specific parsing, metadata, chapter, and navigation behavior.
- Read-aloud, generated playback, and audiobook generation services now share Android compatibility helpers for notification channels, foreground startup/teardown, and app intents without merging their distinct lifecycles.
- Documentation now records the current audiobook acceleration focus: strict hardware generation only, no CPU fallback masquerading as acceleration, and prepared QNN/NPU artifacts gated by a strict compatibility manifest.
- Audiobook hardware readiness now probes strict QNN availability with non-mutating provider labels; real provider config files are written only when generation initializes a runtime.
- Long native audiobook segment generation now separates lightweight cancellation polling from row-dirtying heartbeat writes, reducing Room invalidations and UI churn while generation is under hardware load.
- Audiobook generation speed metrics and fail-fast checks now include WAV save and atomic-finalize time, while manifests record `generationSaveMillis` separately for diagnosing file-output stalls.
- Audiobook generation now re-checks cancellation after synthesis and after segment save, preventing stop/clear actions from being missed when progress writes are intentionally coalesced.
- Generated-audiobook playback lookup now selects likely candidates from Room first and repairs/verifies one candidate at a time, avoiding broad filesystem repair before reader playback starts.
- Reader generated-audiobook playback now queries only the selected voice/profile rows instead of loading every generated-audio row for the book before ranking playback candidates.
- Generated-audiobook performance labels now say `x audio time` instead of ambiguous `x realtime`, making slow generation read as elapsed generation cost rather than playback speed.
- Slow hardware-generation failures now report `audioTimeFactor` so diagnostics match the end-to-end generation metric shown in the UI.
- Internal audiobook speed-gate helpers now use audio-time terminology, while keeping the existing manifest key for backward-compatible generated-audio metadata.
- Audiobook generation Stop actions now carry the exact book, voice, pace, tone, and scope being generated; the foreground service ignores stale targeted cancel intents instead of letting an old notification or wrong row stop the current job.
- Audiobook generation service teardown now marks the active generation row canceled from the application scope before canceling its service scope, preventing process/service destruction from leaving a permanently `GENERATING` audiobook row.
- Audiobook generation setup failures after the preparing row is created now mark the exact generation row failed immediately, so indexing/planning errors do not leave a stuck active job until startup repair.
- Library sorting now includes `Date added`, using import time independently from reading activity and honoring the option in grouped views.
- Library search now matches multi-term queries across metadata, collections, file format, year, favorite state, and reading status such as unread, in progress, or finished.
- Library search now normalizes pasted punctuation, separators, and accents, so queries like `red-rising`, `sci_fi`, or `cafe` match clean book metadata.
- Collections can now be renamed from the book collections dialog, merging into an existing collection when names collide.
- Notes now normalize pasted whitespace before saving, reject empty note annotations at the repository boundary, and keep highlight notes optional.
- Audiobook generation dialogs now surface active sample, chapter, and full-book generation rows consistently, so stuck or running selected-profile jobs expose their stop/recovery controls instead of leaving generation actions disabled without context.
- Neural voice downloads can now be stopped from Library audiobook dialogs and Settings, and canceled downloads are moved into a retryable failed state instead of leaving generation locked behind an indefinite spinner.
- Audiobook text segmentation now coalesces adjacent tiny extracted passages into Kokoro-safe prompts and batches manifest checkpoints, reducing full-book generation segment counts and disk churn while preserving chapter and emphasis pauses.
- Audiobook recovery now verifies generated WAV files directly instead of trusting stale database progress, so partial full-book generations show playable segment counts after crashes or interrupted runs.
- Audiobook UI state now snapshots generated files once per row and neural voice previews prepare asynchronously, reducing repeated filesystem work and avoiding preview-playback UI hitches.
- New bookmarks now save compact chapter/heading labels with percent-read context, making in-book bookmark lists easier to scan.
- Audiobook text preparation now uses anchored chapter detection, normalized chapter labels, shorter Kokoro-safe prompts, paragraph/question/chapter pause metadata, and prepared-chapter scoped first-chapter generation so scan estimates match generated output.
- Audiobook text preparation now preserves extractor-provided numeric and roman numeral chapter headings while still dropping ordinary body page markers.
- Generated audiobook sidecars are sanitized on read so stale, overlapping, out-of-range, or invalid chapter metadata cannot break playback navigation.
- Generated audiobooks with missing or unreadable chapter sidecars now fall back to a single scope-labeled section when playable audio files exist, keeping older partial output identifiable in playback.
- Generated audiobook ZIP export now includes safe fallback chapter and segment sidecars when older playable audio is missing metadata files.
- Fresh audiobook generation now clears stale output before writing manifests and chapter/segment sidecars, preserving chapter navigation and cadence metadata for newly generated audio.
- Generated audiobook playback now persists finished playback as an explicit completed position instead of falling back to the beginning after the final segment.
- Generated audiobook resume, partial-play, and playback icon labels now use shared formatting across Library and Audiobooks surfaces.
- Per-book generated-audio controls now share the same play/save/delete enablement rules as the global Audiobooks screen, avoiding invalid actions for empty or actively generating audio.
- Partial generated audiobooks now label global Audiobooks actions as `Play partial` and `Save partial` so incomplete output is not presented like a finished full-book audiobook.
- Generated audiobook play, save, partial, and resume labels now use verified playable WAV files rather than stale database segment counters, so repaired or missing audio cannot surface broken controls or impossible resume positions.
- Generated audiobook chapter jump buttons now use prepared chapter boundaries from the generated audio sidecars, avoiding dead controls when repaired or older audio lacks current-chapter metadata.
- Generated audiobook rows now use singular/plural chapter labels and hide the chapter picker when there is only one generated section, reducing Audiobooks screen clutter.
- Active generated audiobook rows now keep chapter context in one dedicated line instead of repeating the chapter title in the compact status text.
- Per-book audiobook generation dialogs now use the same singular/plural chapter labels as the Audiobooks screen and suppress single-section chapter picker controls.
- Partial generated audiobook ZIP exports now include the in-progress manifest as `manifest.txt` when a final manifest does not exist, preserving provider, scope, progress, and status metadata.
- Partial generated audiobook ZIP exports now trim `segments.tsv` to verified playable segments, preserving existing metadata for playable audio without exporting future, not-yet-generated rows.
- Generated audiobook playback now gates startup on verified contiguous WAV files instead of database progress counters, avoiding misleading preparing states when stale records reference missing audio.
- Generated audiobook playback now clears cached file/chapter state whenever playback resources reset, avoiding stale playback metadata after delete, regenerate, or switching to a different generated audiobook.
- Stale audiobook generation recovery now updates the on-disk manifest status, completed count, timestamp, and failure reason so partial exports and debugging evidence match the repaired database row.
- Audiobook text preparation now drops standalone table-of-contents entry rows such as chapter/page listings before narration, while preserving real chapter headings.
- Generated-audio rows now separate metadata from playback/export/delete controls and allow the control strip to wrap, avoiding cramped audiobook dialogs on phone-width screens.
- Audiobook generation controls now explain why generation is disabled when the selected voice is missing, installing, failed, or already generating audio.
- Audiobook generation now writes throttled live heartbeat progress during long native segment synthesis, so active rows and notifications can show liveness without forcing constant Room invalidations while a hardware provider is working on a large segment.
- Active generated-audiobook rows now avoid repeated chapter-sidecar reads while generation is still running and reuse unchanged UI row models, reducing library/audiobook screen churn during progress updates.
- Audiobook heartbeat cleanup now waits for the heartbeat worker to finish after segment synthesis, reducing races between long native calls, completion writes, cancellation, and delete/clear actions.
- The global Audiobooks screen now uses one Room relation query for visible generated-audio rows and their books, avoiding a full-library/full-audio combine on every generation progress tick.
- The global Audiobooks screen now keeps Room rows and cached audio UI rows in order instead of building an extra id map during active generation refreshes.
- Generated-audiobook UI row caches now prune entries outside the active row set, preventing stale sidecar metadata from accumulating after delete, repair, or regeneration.
- Generated-audiobook UI row caches now keep expensive sidecar metadata separate from live progress rows, so heartbeat ETA/provider/timing updates do not force chapter metadata reparsing.
- Generated-audiobook list and row UI state now declare immutable Compose value objects, helping unaffected audiobook rows skip recomposition during playback and generation progress ticks.
- Passive generated-audiobook UI now has one non-verifying row conversion path; file verification remains in playback, export, repair, and recovery instead of unused UI helpers.
- Neural audiobook startup maintenance now runs cheap catalog/install repair early and defers stale-audio repair plus obsolete storage pruning, reducing cold-start and first-library-render contention.
- Generated-audiobook foreground playback now requests its foreground service once per active playback session instead of repeatedly starting it on play, resume, and pause state transitions.
- Reader navigation filtering now matches across punctuation boundaries, so queries like `chapter-1`, `landing-sequence`, or `#character-later` find the expected TOC entries, bookmarks, notes, and highlights.
- The in-reader find bar is now narrower and less banner-like while keeping previous, next, edit, and close actions available.
- Reading analytics now treats the initially visible page as a position anchor instead of words read, keeping early WPM, sessions, and ETA from inflating before actual page movement.
- Reader bottom chrome now clamps page labels and disables the progress scrubber until multiple pages are available, avoiding impossible page counts during reader startup or reflow.
- In-reader return history now de-duplicates equivalent Readium locations, so search, TOC, bookmark, and scrubber jumps do not create redundant return targets.
- In-reader fallback search results now use the same word-safe, multi-term snippet builder as library search, producing cleaner previews when Readium search is unavailable or times out.
- Reader quick settings now clearly state whether appearance changes apply globally or only to the current book.
- The Audiobooks screen now has local search across book title, author, voice/profile, scope, and generation status so large generated-audio libraries stay navigable without extra clutter.
- Library grouping now includes a Formats view, using original import extensions such as EPUB, PDF, TXT, MOBI, CBZ, and document conversions so mixed libraries are easier to audit.
- Empty grouped-library views now give specific guidance for missing author, series, genre, format, and year groups instead of a generic empty message.
- Library search now matches custom collections, file format, original extension, file name, and publication year in addition to title, author, series, and genre.
- Extensionless imports with generic MIME types, including folder-scan candidates, now sniff PDF headers and ZIP-backed EPUB/FB2/ODT/DOCX/CBZ structure before rejecting the file.
- Unsupported import summaries now explain modern Kindle AZW/AZW3/KF8/KFX and other deferred formats instead of reducing them to a generic unsupported count.
- Converted imports now derive cleaner fallback titles from downloaded filenames, including URL-decoded names, underscores, paths, query strings, fragments, and full double extensions such as `.fb2.zip`.
- Library filtering now trims and normalizes pasted whitespace, so searches such as `Red   Rising` or `  sci-fi  ` still match expected books and collections.
- Library sorting now includes `Longest first`, using extracted word count when available and file size as a fallback for formats without reliable text counts.
- Library repair now clearly backfills readability metrics, search rows, covers, metadata, and series order, and its result messages call out readability updates and missing private book files.
- Markdown notes export now includes readable percent positions for highlights, notes, and bookmarks, with safer Markdown escaping for book titles, authors, tags, and bookmark labels.
- Notes/bookmarks restore now counts malformed backup rows as invalid items instead of silently ignoring them.
- Library and notes backup restore now trims and normalizes checksum references before matching imported books, making hand-inspected or case-shifted JSON backups less fragile.
- Full-book neural generation now fails closed unless an eligible strict hardware provider initializes with the required model artifact; CPU-backed `xnnpack`/`cpu` remain available only for short previews and are no longer treated as acceptable full-book generation providers.

## 0.1.0 - 2026-05-28

Initial public repository snapshot.

### Added

- Native Android app scaffold for XReader.
- EPUB and PDF reading through Readium Kotlin.
- TXT import converted into EPUB packages, including chaptered output for headed text files and legacy text decoding for older plain-text sources.
- CBZ import converted into fixed-layout EPUB packages.
- FB2 and zipped `.fb2.zip` import converted into EPUB packages with FictionBook metadata and embedded cover support.
- RTF import converted into EPUB packages with title/author metadata and searchable text extraction.
- DRM-free legacy MOBI/PalmDOC import converted into EPUB packages with title/author metadata and searchable text extraction.
- ODT import converted into EPUB packages with OpenDocument metadata, headings, paragraphs, lists, tables, and searchable text extraction.
- DOCX import converted into EPUB packages with document metadata, headings, paragraphs, lists, tables, and searchable text extraction.
- HTML, HTM, and XHTML import converted into EPUB packages with page metadata, headings, lists, tables, blockquotes, and searchable text extraction.
- MHTML and MHT web archives converted into EPUB packages with decoded HTML roots, embedded image assets including lazy/responsive image references, page metadata, and searchable text extraction.
- Markdown import converted into EPUB packages with front matter metadata, headings, lists, blockquotes, code blocks, and searchable text extraction.
- SAF-based private library imports.
- Batch file and folder import through Android's Storage Access Framework.
- Optional OPDS catalog URL import with Atom feed browsing, navigation links, redirect-aware relative link resolution, supported open-access acquisition downloads, and private-library import integration.
- Duplicate detection by checksum.
- Re-importing a checksum-matched book whose private app-library file is missing restores the stored file and search index without changing the book id.
- Single-book import and duplicate re-import snackbars can open the imported or already-existing title directly.
- Local Room database for books, reading state, sessions, notes, bookmarks, search, and dictionary entries.
- Library organization, metadata editing, favorites, finished state, and progress display.
- Predictable grouped-library ordering: years newest-first and missing series/genre/year buckets last.
- Manual mark-finished and mark-not-finished actions from the compact book action menu, with finished-state-aware progress classification.
- Undoable book removal from the library action menu; books disappear immediately but the destructive delete is deferred through the snackbar undo window.
- Compact author, genre, and series suggestions in the metadata editor to prevent accidental duplicate categories.
- Automatic author, genre, and series canonicalization during import and metadata edits, so variants like `sci-fi` and `Science fiction` collapse into the same library groups.
- Opt-in bulk genre and series cleanup from the metadata editor for matching series books.
- Settings library repair also harmonizes obvious same-series genre drift without adding another maintenance control.
- Persisted library sort and comfortable/compact density controls.
- Quiet first-run import state plus distinct no-results states for search misses and empty library filters.
- Readium locator persistence for resume.
- Manual cover replacement from local image files.
- EPUB cover discovery from OPF guide references and guide XHTML/HTML title pages.
- Unified library search, sorting, density, and grouping controls to reduce duplicate chrome; sorting now applies predictably to grouped library views and in-group book order.
- Library metadata and health UI split into focused components to keep future polish changes lower-risk.
- Per-book library health checks and targeted repair from the metadata dialog.
- In-reader navigation by swipe, calibrated tap zones, hardware keyboard/DPAD keys, TOC, bookmarks, search results, progress scrubber, and Back-based return history after manual jumps.
- Bookmarks now use the exact visible Readium location when available, while still recognizing older unit-level bookmarks.
- Toolbar-created notes now attach to the exact visible reader location instead of only the coarse reading unit.
- The in-reader navigation sheet now separates Contents, Bookmarks, and Notes into compact tabs instead of one mixed scroll.
- Reader navigation UI split into a focused component file to keep future reader polish lower-risk.
- Reader themes, fullscreen mode, typography controls, PDF fit controls, page direction controls, reader-only orientation control, page animation toggle, configurable tap-zone sizing, and real font-family options including OpenDyslexic.
- Compact, comfort, and accessible reader spacing presets for fast typography setup.
- Per-book reader appearance overrides for font size, font weight, line height, margins, font family, hyphenation, publisher styles, alignment, and PDF fit.
- In-reader read-aloud powered by Android TextToSpeech from XReader's page-aligned local indexed book text, anchored to the visible reader position with persisted speed, sleep timer, audio-focus handling, Android media-session transport controls, a media-playback foreground notification, and installed offline voice controls.
- Grouped Settings screen for reader appearance, typography, reading behavior, library display, and maintenance.
- Notes, highlights, bookmarks, global notes view, and in-reader annotation navigation.
- In-reader notes and highlights can be edited from the navigation sheet without leaving the book.
- Human-readable Markdown export for notes, highlights, and bookmarks from the global notes screen.
- Local JSON export/import for notes, highlights, and bookmarks matched back to books by checksum.
- Local JSON export/import for library catalog metadata, favorites, finished state, reading progress, reading sessions, and per-book reader appearance matched back to imported books by checksum.
- Offline English dictionary backed by Princeton WordNet, with phrase, hyphenated-word, common plural, possessive, and inflected-form lookup.
- Local full-text search with fallback from Readium search to the app search index, including cleaned PDF text extraction for wrapped words.
- Library full-text search results identify the source book and author, center snippets on the matched query, support the keyboard search action, and can expand beyond the compact preview without adding another search screen.
- In-reader search dialog polish with keyboard search action, clear affordance, result counts, stale-result clearing, and cancellation of superseded searches.
- Reading analytics for active time, WPM estimate, streaks, range-aware activity, and book/author/genre summaries.
- Reader chrome and the Continue Reading card show conservative time-left estimates when WPM and word-count data are available.
- Reading stats activity chart with 7-day, 30-day, 13-week, and all-time ranges, current/best streaks, and optimized grouped session aggregation.
- Local CSV and JSON export for reading analytics summaries across all stats ranges.
- Manual Settings action to repair covers, metadata, and search indexes from stored library files.
- Modern adaptive app icon.
- Unit and instrumented test coverage for core parsing, indexing, dictionary, analytics, settings persistence, and maintenance repair behavior.
- Public-domain Alice text fixture used by instrumented TXT/EPUB import coverage.

### Known Limits

- AZW3/KF8 conversion is not implemented.
- CBR/DJVU/legacy binary DOC import is not implemented.
- Release APKs are unsigned.
- Play Store packaging, dependency shrinking, and APK/AAB size optimization have not had a final pass.
