# Changelog

## 0.1.0 - 2026-05-28

Initial public repository snapshot.

### Added

- Native Android app scaffold for XReader.
- EPUB and PDF reading through Readium Kotlin.
- TXT import converted into minimal EPUB packages.
- SAF-based private library imports.
- Duplicate detection by checksum.
- Local Room database for books, reading state, sessions, notes, bookmarks, search, and dictionary entries.
- Library organization, metadata editing, favorites, finished state, and progress display.
- Persisted library sort and comfortable/compact density controls.
- Readium locator persistence for resume.
- Manual cover replacement from local image files.
- EPUB cover discovery from OPF guide references and guide XHTML/HTML title pages.
- Unified library search, sorting, density, and grouping controls to reduce duplicate chrome.
- Per-book library health checks and targeted repair from the metadata dialog.
- In-reader navigation by swipe, tap zones, hardware keys, TOC, bookmarks, search results, and progress scrubber.
- Reader themes, fullscreen mode, typography controls, PDF fit controls, page animation toggle, and real font-family options.
- Grouped Settings screen for reader appearance, typography, reading behavior, library display, and maintenance.
- Notes, highlights, bookmarks, global notes view, and in-reader annotation navigation.
- Local JSON export/import for notes, highlights, and bookmarks matched back to books by checksum.
- Local JSON export/import for library catalog metadata, favorites, finished state, reading progress, and reading sessions matched back to imported books by checksum.
- Offline English dictionary backed by Princeton WordNet.
- Local full-text search with fallback from Readium search to the app search index.
- Reading analytics for active time, WPM estimate, streaks, and book/author/genre summaries.
- Manual Settings action to repair covers, metadata, and search indexes from stored library files.
- Modern adaptive app icon.
- Unit and instrumented test coverage for core parsing, indexing, dictionary, analytics, settings persistence, and maintenance repair behavior.

### Known Limits

- MOBI/AZW3 conversion is not implemented.
- Release APKs are unsigned.
- Play Store packaging, dependency shrinking, and APK/AAB size optimization have not had a final pass.
