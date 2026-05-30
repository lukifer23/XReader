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

1. Android Storage Access Framework returns one or more document URIs, or a folder tree URI.
2. `ImportService` copies the selected file to a temporary app cache file.
3. The file checksum is calculated to prevent duplicate imports.
4. TXT files are converted into a minimal EPUB package, CBZ files are converted into fixed-layout EPUB packages, FB2 / `.fb2.zip` files are converted into EPUB packages, RTF files are converted into EPUB packages with extracted text and basic metadata, ODT/DOCX files are converted into EPUB packages with document metadata and reading-order text, and standalone HTML/HTM/XHTML files are converted into EPUB packages with page metadata and readable block structure.
5. EPUB/PDF files and converted EPUB outputs are copied into app-owned private library storage.
6. Metadata, cover art, reading units, word counts, and searchable text are extracted.
7. Book metadata and search rows are persisted in Room.

EPUB cover extraction checks explicit OPF cover metadata, EPUB 3 `cover-image` properties, EPUB 2 guide cover references, guide XHTML/HTML title pages that point at image assets, and conservative manifest-image fallbacks.

The manual Settings repair action and the per-book metadata repair action reuse this parsing/indexing path against stored private-library files. They refresh covers, metadata fields that are empty or safe to improve, word/page counts, and search rows. They preserve user-edited title and author values. Covers manually replaced from local image files are stored as app-private downsampled JPEGs and are not overwritten by repair.

Folder imports walk SAF document trees recursively, filter to EPUB, PDF, TXT, CBZ, FB2, `.fb2.zip`, RTF, ODT, DOCX, HTML, HTM, and XHTML documents, and summarize imported, duplicate, unsupported, and failed files. They do not require broad all-files access.

Book rows expose a save-copy action that launches Android's `CreateDocument` picker and streams the app-private stored reader file to the selected URI. Converted imports such as TXT, CBZ, FB2, RTF, ODT, DOCX, and HTML export as the actual EPUB file XReader stores for reading.

Manual metadata edits can optionally apply shared author, genre, and series metadata to other books that match the same old or new author and series pair. The bulk cleanup runs in a Room transaction and keeps per-book fields such as title, year, and series index isolated to each book.

The Books home derives series continuation recommendations from the already loaded Room library state. It groups books by normalized series name, orders each series by series index with year/title fallback, and surfaces the next unfinished title after the most recently finished series book as a single compact action card.

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
- calibrated tap-zone page navigation
- hardware keyboard and DPAD page navigation, plus opt-in volume-button page turns
- chrome toggle
- lazy, filterable in-book navigation across table of contents, bookmarks, notes, highlights, and annotation tags
- bounded return history for manual TOC, bookmark, note, search-result, and find-next/find-previous jumps
- selection actions for highlight, note, and dictionary lookup
- scrollbar cleanup for nested Readium/WebView content
- reader preferences for theme, typography, PDF fit/layout, fullscreen, keep-screen-awake behavior, app-local dimming, tap-zone sizing, page-turn animation behavior, and per-book appearance overrides

Read aloud is handled by `ReadAloudEngine`, a small wrapper around Android `TextToSpeech`. `ReaderViewModel` builds speech chunks from the app's local search index, splits them into Readium-position-sized chunks by reading-order word progress, starts from the visible reader position or nearest earlier position, persists the spoken locator as playback advances, and keeps Compose limited to play/pause/resume/stop, previous/next passage, speed, sleep timer, installed offline voice selection, and error feedback. Playback owns Android audio focus while speaking, releases it on pause/stop/shutdown, pauses with a clear message on transient audio interruptions, and stops on permanent audio-focus loss.

Reader search first tries Readium's publication search and falls back to XReader's local search index when needed. Search results carry an approximate reading unit so the compact find bar can jump to the previous or next match from the visible page, then keep the search active until the user closes it.

## Settings

Reader and library settings are persisted with DataStore. Settings include:

- theme
- font scale
- line height
- margin scale
- compact, comfort, and accessible spacing presets that write the same typography fields
- font family
- font weight
- hyphenation
- tap zones and tap-zone size preset
- page animations
- keep screen awake
- volume-button page turns
- reader dim amount
- read-aloud speed
- read-aloud sleep timer
- fullscreen
- publisher styles
- alignment
- PDF fit and layout
- idle timeout
- library sort
- library density

Per-book reader appearance overrides are also stored in DataStore, keyed by book id. They only override typography, hyphenation, publisher styles, alignment, and PDF fit/layout. Theme, fullscreen, keep-screen-awake, reader dimming, tap zones, page animations, volume-button page turns, and idle timeout stay global so reading behavior remains predictable across books.

Reader dimming is implemented as a reader-only Compose overlay capped by `MAX_READER_DIM_AMOUNT`; it never writes Android system brightness settings and is cleared naturally when leaving the reader surface.

Font choices are limited to families that Android/Readium CSS can resolve or fall back from cleanly, including Readium's bundled OpenDyslexic asset. XReader does not expose user font import until the reader stack can serve those files reliably.

Settings also exposes local JSON backup and restore through Android's Storage Access Framework. Notes/bookmark backups contain notes, highlights, normalized annotation tags, and bookmarks. The global notes screen supports text, kind, and tag filtering, and it exports human-readable Markdown grouped by book while omitting private file paths and checksums. Library backups contain catalog metadata, favorites, finished state, reading progress, and reading sessions, but never imported book files or cover image files. Restores match items to already-imported books by file checksum. Items for books that are not in the local library are skipped instead of creating orphan records.

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

- importing EPUB/PDF/TXT/CBZ/FB2/RTF/ODT/DOCX/HTML
- opening a real EPUB and PDF
- page navigation by swipe, tap, hardware keyboard/DPAD keys, optional volume buttons, filterable TOC/bookmark/note lists, search result, find-next/find-previous, scrubber, and Back-based return after manual jumps
- resume after process/app restart
- adding/removing notes, highlights, and bookmarks
- dictionary lookup from selected text
- light, dark, sepia, OLED, fullscreen, and typography settings
- compact phone, tall phone, landscape, tablet-width, and foldable-style layouts
