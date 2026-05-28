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

The manual Settings repair action reuses this parsing/indexing path against stored private-library files. It refreshes covers, metadata fields that are empty or safe to improve, word/page counts, and search rows. It preserves user-edited title and author values. Covers manually replaced from local image files are stored as app-private downsampled JPEGs and are not overwritten by repair.

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
- reader preferences for theme, typography, PDF fit, fullscreen, and page-turn animation behavior

## Settings

Reader and library settings are persisted with DataStore. Settings include:

- theme
- font scale
- line height
- margin scale
- font family
- tap zones
- page animations
- fullscreen
- publisher styles
- alignment
- PDF fit
- idle timeout
- library sort
- library density

Font choices are limited to families that Android/Readium CSS can resolve or fall back from cleanly.

## Dictionary

`tools/build_wordnet_asset.py` converts WordNet 3.0 data files into a compact SQLite asset. On first use, `DictionaryRepository` imports entries into Room and serves normalized local lookup for selected words.

## Analytics

`ReadingAnalyticsTracker` tracks foreground active reading sessions. It uses reading movement and idle timeout rules to estimate active time, words traversed, WPM, completion, streaks, and summary statistics. The reader avoids popups or gamified interruptions during reading.

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
