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
- Readium locator persistence for resume.
- In-reader navigation by swipe, tap zones, hardware keys, TOC, bookmarks, search results, and progress scrubber.
- Reader themes, fullscreen mode, typography controls, PDF fit controls, page animation toggle, and real font-family options.
- Notes, highlights, bookmarks, global notes view, and in-reader annotation navigation.
- Offline English dictionary backed by Princeton WordNet.
- Local full-text search with fallback from Readium search to the app search index.
- Reading analytics for active time, WPM estimate, streaks, and book/author/genre summaries.
- Manual Settings action to repair covers, metadata, and search indexes from stored library files.
- Modern adaptive app icon.
- Unit and instrumented test coverage for core parsing, indexing, dictionary, and analytics behavior.

### Known Limits

- MOBI/AZW3 conversion is not implemented.
- Release APKs are unsigned.
- Play Store packaging, dependency shrinking, and APK/AAB size optimization have not had a final pass.
