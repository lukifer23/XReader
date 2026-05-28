# Architecture

XReader is a single-module native Android app built around a local-first library and a Readium-backed reader.

## Stack

- Kotlin
- Jetpack Compose
- Material 3
- Room
- DataStore Preferences
- Coroutines and Flow
- Navigation Compose
- Readium Kotlin Toolkit
- PDFBox Android for PDF metadata/text extraction
- Princeton WordNet as an offline dictionary asset

## Runtime Shape

`XReaderApplication` owns an `AppContainer`. The container constructs the database, repositories, import services, reader services, dictionary repository, analytics repository, and shared application coroutine scope. Expensive reader initialization is kept out of app startup. Readium/PDF service setup is warmed shortly after the first screen, while WebView warmup stays delayed on the main thread to avoid stealing startup frames.

The UI is Compose-first. ViewModels expose immutable state objects and one-shot actions. UI code delegates persistence, file access, parsing, and indexing to repositories and services.

## Data Model

Room stores:

- `BookEntity`
- `AuthorEntity`
- `SeriesEntity`
- `GenreEntity`
- `ReadingStateEntity`
- `ReadingSessionEntity`
- `AnnotationEntity`
- `BookmarkEntity`
- `SearchIndexEntity`
- `DictionaryEntryEntity`

Search uses a normal table plus an FTS table. Book deletion removes search rows and stored files.

## Import Flow

1. Android Storage Access Framework returns a document URI.
2. `ImportService` copies the selected file to a temporary app cache file.
3. The file checksum is calculated to prevent duplicate imports.
4. TXT files are converted into a minimal EPUB package.
5. EPUB/PDF files are copied into app-owned private library storage.
6. Metadata, cover art, reading units, word counts, and searchable text are extracted.
7. Book metadata and search rows are persisted in Room.

EPUB cover extraction checks explicit OPF cover metadata, EPUB 3 `cover-image` properties, EPUB 2 guide cover references, guide XHTML/HTML title pages that point at image assets, and conservative manifest-image fallbacks.

The manual Settings repair action and the per-book metadata repair action reuse this parsing/indexing path against stored private-library files. They refresh covers, metadata fields that are empty or safe to improve, word/page counts, and search rows. They preserve user-edited title and author values. Covers manually replaced from local image files are stored as app-private downsampled JPEGs and are not overwritten by repair.

Manual metadata edits can optionally apply the edited genre and series name to other books by the same author that match the old or new series name. The bulk cleanup runs in a Room transaction and keeps per-book fields such as title, year, and series index isolated to each book.

## Reader Flow

`PublicationService` opens stored EPUB/PDF publications with Readium and exposes:

- publication metadata
- positions
- table of contents, loaded after the reader is visible
- locators
- search
- reading units for local progress and fallback search

`ReadiumNavigatorHost` embeds the Readium navigator fragment inside Compose. It handles:

- persisted initial locator resume
- tap-zone page navigation
- chrome toggle
- selection actions for highlight, note, and dictionary lookup
- scrollbar cleanup for nested Readium/WebView content
- reader preferences for theme, typography, PDF fit, fullscreen, page-turn animation behavior, and per-book appearance overrides

Read aloud is handled by `ReadAloudEngine`, a small wrapper around Android `TextToSpeech`. `ReaderViewModel` builds speech chunks from the app's local search index, maps those chunks back to nearest Readium positions, starts from the visible reader position or nearest earlier chunk, and keeps Compose limited to a play/stop control, speed setting, and error feedback.

## Settings

Reader and library settings are persisted with DataStore. Settings include:

- theme
- font scale
- line height
- margin scale
- compact, comfort, and accessible spacing presets that write the same typography fields
- font family
- tap zones
- page animations
- read-aloud speed
- fullscreen
- publisher styles
- alignment
- PDF fit
- idle timeout
- library sort
- library density

Per-book reader appearance overrides are also stored in DataStore, keyed by book id. They only override typography, publisher styles, alignment, and PDF fit. Theme, fullscreen, tap zones, page animations, and idle timeout stay global so reading behavior remains predictable across books.

Font choices are limited to families that Android/Readium CSS can resolve or fall back from cleanly.

Settings also exposes local JSON backup and restore through Android's Storage Access Framework. Notes/bookmark backups contain notes, highlights, and bookmarks. Library backups contain catalog metadata, favorites, finished state, reading progress, and reading sessions, but never imported book files or cover image files. Restores match items to already-imported books by file checksum. Items for books that are not in the local library are skipped instead of creating orphan records.

## Dictionary

`tools/build_wordnet_asset.py` converts WordNet 3.0 data files into a compact SQLite asset. On first use, `DictionaryRepository` imports entries into Room and serves normalized local lookup for selected words.

## Analytics

`ReadingAnalyticsTracker` tracks foreground active reading sessions. It uses reading movement and idle timeout rules to estimate active time, words traversed, WPM, and completion. `AnalyticsRepository` aggregates those sessions into selectable 7-day, 30-day, 13-week, and all-time ranges with appropriate daily, weekly, monthly, or yearly activity buckets. `AnalyticsExportService` writes those summaries to local CSV or JSON through Android's Storage Access Framework without including imported book files, private file paths, or checksums. Current/best streaks and book, author, and genre summaries stay quiet, with no popups or gamified interruptions during reading.

## Validation

Primary local gates:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --console=plain
./gradlew :app:lintRelease :app:assembleRelease --console=plain
```

Performance baselines:

```bash
tools/perf_baseline.sh --iterations 7 --reader-tap 400 780
```

Device checks should cover:

- importing EPUB/PDF/TXT
- opening a real EPUB and PDF
- page navigation by swipe, tap, hardware key, TOC, bookmark, search result, and scrubber
- resume after process/app restart
- adding/removing notes, highlights, and bookmarks
- dictionary lookup from selected text
- light, dark, sepia, OLED, fullscreen, and typography settings
- compact phone, tall phone, landscape, tablet-width, and foldable-style layouts
