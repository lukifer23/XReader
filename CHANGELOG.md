# Changelog

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
- MHTML and MHT web archives converted into EPUB packages with decoded HTML roots, embedded image assets, page metadata, and searchable text extraction.
- Markdown import converted into EPUB packages with front matter metadata, headings, lists, blockquotes, code blocks, and searchable text extraction.
- SAF-based private library imports.
- Batch file and folder import through Android's Storage Access Framework.
- Optional OPDS catalog URL import with Atom feed browsing, navigation links, supported open-access acquisition downloads, and private-library import integration.
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
- Reader themes, fullscreen mode, typography controls, PDF fit controls, page direction controls, page animation toggle, configurable tap-zone sizing, and real font-family options including OpenDyslexic.
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
- Local full-text search with fallback from Readium search to the app search index.
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
