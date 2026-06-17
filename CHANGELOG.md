# Changelog

## Unreleased

### Added

- Embedded Kokoro v1.0 audiobook generation with model download/install/delete controls, narrator selection, narration style, pacing, scan summaries, sample/first-chapter/full-book scopes, persisted progress/ETA, partial playback, generated-audio resume, chapter picker/jump controls, delete, and ZIP export.
- Global Audiobooks screen for completed, partial, active, failed, and generated audio rows, with compact playback controls and chapter navigation.

### Changed

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
- Audiobook text preparation now drops standalone table-of-contents entry rows such as chapter/page listings before narration, while preserving real chapter headings.
- Generated-audio rows now separate metadata from playback/export/delete controls and allow the control strip to wrap, avoiding cramped audiobook dialogs on phone-width screens.
- Audiobook generation controls now explain why generation is disabled when the selected voice is missing, installing, failed, or already generating audio.
- Reader navigation filtering now matches across punctuation boundaries, so queries like `chapter-1`, `landing-sequence`, or `#character-later` find the expected TOC entries, bookmarks, notes, and highlights.
- The Audiobooks screen now has local search across book title, author, voice/profile, scope, and generation status so large generated-audio libraries stay navigable without extra clutter.
- Library grouping now includes a Formats view, using original import extensions such as EPUB, PDF, TXT, MOBI, CBZ, and document conversions so mixed libraries are easier to audit.
- Empty grouped-library views now give specific guidance for missing author, series, genre, format, and year groups instead of a generic empty message.
- Library search now matches custom collections, file format, original extension, file name, and publication year in addition to title, author, series, and genre.
- Library filtering now trims and normalizes pasted whitespace, so searches such as `Red   Rising` or `  sci-fi  ` still match expected books and collections.
- Library sorting now includes `Longest first`, using extracted word count when available and file size as a fallback for formats without reliable text counts.
- Library repair now clearly backfills readability metrics, search rows, covers, metadata, and series order, and its result messages call out readability updates and missing private book files.
- Markdown notes export now includes readable percent positions for highlights, notes, and bookmarks, with safer Markdown escaping for book titles, authors, tags, and bookmark labels.
- Notes/bookmarks restore now counts malformed backup rows as invalid items instead of silently ignoring them.
- Full-book neural generation prefers WebGPU with isolated-process runtime rotation, then XNNPACK/CPU fallback; preview generation stays on XNNPACK/CPU for stability.

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
