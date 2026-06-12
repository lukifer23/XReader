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
- PDFBox Android for PDF metadata/text extraction, sorted by position with wrapped-word cleanup before indexing
- Princeton WordNet as an offline dictionary asset

## Runtime Shape

`XReaderApplication` owns an `AppContainer`. The container constructs the database, repositories, import services, reader services, dictionary repository, analytics repository, and shared application coroutine scope. Expensive reader initialization is kept out of app startup. Readium/PDF service setup is warmed shortly after the first screen, while WebView warmup stays delayed on the main thread to avoid stealing startup frames.

The UI is Compose-first. ViewModels expose immutable state objects and one-shot actions. UI code delegates persistence, file access, parsing, and indexing to repositories and services. Library, Stats, Notes, and Settings are primary destinations with one shared bottom navigation bar and selected state; reader and book-specific flows remain secondary surfaces.

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
- `NeuralTtsModelEntity`
- `BookAudioEntity`

Search uses a normal table plus an FTS table. Book deletion removes search rows and stored files.

## Import Flow

1. Android Storage Access Framework returns one or more document URIs or a folder tree URI; optional OPDS import fetches an Atom catalog URL and downloads a supported open-access acquisition link.
2. `ImportService` copies the selected or downloaded file to a temporary app cache file.
3. The file checksum is calculated to prevent duplicate imports. If the checksum already exists but the app-private stored file is missing, import becomes an in-place recovery path for that existing book id rather than a duplicate no-op.
4. TXT files are converted into a minimal EPUB package, CBZ files are converted into fixed-layout EPUB packages, FB2 / `.fb2.zip` files are converted into EPUB packages, RTF files are converted into EPUB packages with extracted text and basic metadata, DRM-free legacy MOBI/PalmDOC files are converted into EPUB packages with decompressed text and basic metadata, ODT/DOCX files are converted into EPUB packages with document metadata and reading-order text, standalone HTML/HTM/XHTML files are converted into EPUB packages with page metadata and readable block structure, MHTML/MHT web archives are converted into EPUB packages with decoded HTML roots and embedded image assets including lazy/responsive image references, and Markdown files are converted into EPUB packages with front matter metadata and readable block structure.
5. EPUB/PDF files and converted EPUB outputs are copied into app-owned private library storage.
6. Metadata, cover art, reading units, word counts, and searchable text are extracted.
7. Author, genre, and series values are canonicalized against known genre aliases and existing library display values.
8. Book metadata and search rows are persisted in Room.

EPUB cover extraction checks explicit OPF cover metadata, EPUB 3 `cover-image` properties, EPUB 2 guide cover references, guide XHTML/HTML title pages that point at image assets, and conservative manifest-image fallbacks.

The manual Settings repair action and the per-book metadata repair action reuse this parsing/indexing path against stored private-library files. They refresh covers, metadata fields that are empty or safe to improve, word/page counts, and search rows. The Settings repair action also canonicalizes existing metadata and harmonizes obvious same-author/same-series genre drift when one strong genre is mixed with weak labels such as general fiction, adventure, or war. They preserve user-edited title and author values. Covers manually replaced from local image files are stored as app-private downsampled JPEGs and are not overwritten by repair.

Folder imports walk SAF document trees recursively, filter to EPUB, PDF, TXT, CBZ, FB2, `.fb2.zip`, RTF, MOBI, PRC, ODT, DOCX, HTML, HTM, XHTML, MHTML, MHT, MD, and Markdown documents, and summarize imported, restored, duplicate, unsupported, and failed files. Generic ZIP imports are accepted only when their contents prove they are a supported ZIP-backed book or document: EPUB, zipped FB2, ODT, DOCX, or a CBZ-style image archive. Android Open with and Share intents for supported book MIME types route into the same private-copy import flow instead of a separate reader path. The catalog URL flow can also download a direct supported book URL when the server content type or final redirected URL identifies a real supported document, then hands it to the same import service. They do not require broad all-files access.

Single-book imports and duplicate re-imports carry the target book id back to the library UI, where the snackbar exposes a contextual `Open` action. Batch and folder imports keep summary-only feedback unless the completed import set contains exactly one actionable book.

OPDS support stays inside the existing import dialog. `OpdsCatalogService` fetches HTTP/HTTPS OPDS Atom or OPDS 2-style JSON feeds, resolves relative links against the final redirected feed URL plus Atom `xml:base` values where present, follows feed navigation links, filters to supported open-access/enclosure acquisition links, bounds feed and book downloads, and hands the downloaded file to `ImportService.importFile` so checksum identity, conversion, metadata extraction, covers, search indexing, and private-library storage remain identical to SAF imports. It does not add account, DRM, background sync, telemetry, or automatic network scanning.

Book rows expose a save-copy action that launches Android's `CreateDocument` picker and streams the app-private stored reader file to the selected URI. Converted imports such as TXT, CBZ, FB2, RTF, MOBI, ODT, DOCX, HTML, MHTML, and Markdown export as the actual EPUB file XReader stores for reading.

Manual metadata edits canonicalize author, genre, and series values before persistence, then can optionally apply shared author, genre, and series metadata to other books that match the same old or new author and series pair. The bulk cleanup runs in a Room transaction and keeps per-book fields such as title, year, and series index isolated to each book.

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
- reader preferences for theme, typography, adaptive PDF fit/layout, page direction, fullscreen, keep-screen-awake behavior, app-local dimming, tap-zone sizing, page-turn animation behavior, and per-book appearance overrides

Read aloud is handled by `ReadAloudEngine`, a small wrapper around Android `TextToSpeech`. It can initialize against the device default engine or a specific installed TTS engine package from Settings, which lets real local/offline neural Android TTS providers participate. `ReaderViewModel` builds speech chunks from the app's local search index, splits them into Readium-position-sized chunks by reading-order word progress, starts from the visible reader position or nearest earlier position, persists the spoken locator as playback advances, and keeps Compose limited to play/pause/resume/stop, previous/next passage, engine and voice selection, speed, sleep timer countdown, and error feedback. Playback owns Android audio focus while speaking, releases it on pause/stop/shutdown, pauses with a clear message on transient audio interruptions, and stops on permanent audio-focus loss.

Embedded neural audiobook generation is handled separately by `NeuralTtsRepository`. Settings exposes a compact local neural voice downloader backed by the Sherpa-ONNX Android JNI runtime and the Kokoro v1.0 model. Downloads are stored in app-private model storage, progress is persisted in Room, archive size and SHA-256 are verified before extraction, and extraction rejects unsafe archive paths. Startup maintenance removes obsolete voice models that are no longer in the supported catalog. The book action menu can generate cached audiobook audio from XReader's indexed reading-order text. Generation prepares text by removing common extraction noise and repeated boilerplate, then splits full-book text into sentence-aware bounded speech segments tuned for Kokoro cadence. It synthesizes each segment to a WAV file, writes a manifest with the runtime provider, and links the generated audio directory to the book/model/speaker/speed/tone tuple in Room so repeat requests reuse the prior output. The save-audiobook action exports the generated segment set as a ZIP through Android's `CreateDocument` picker.

The current embedded path tries Sherpa-ONNX `xnnpack` first and falls back to `cpu`. NNAPI is not used for OfflineTTS because current Sherpa-ONNX evidence shows it can crash instead of falling back cleanly. Qualcomm QNN/NPU remains future work until it is packaged as a real runtime provider, measured for battery/latency, and kept behind the same no-placeholder UI rule.

Reader search first tries Readium's publication search and falls back to XReader's local search index when needed. Search results carry an approximate reading unit so the compact find bar can jump to the previous or next match from the visible page, then keep the search active until the user closes it. Library full-text search uses the same FTS index joined to book metadata so result rows can show the source title/author and a query-centered snippet before jumping into the matched reading unit. User search text is normalized into bounded FTS terms so punctuation, hyphenated phrases, possessives, and pasted quotes do not break local search. PDF imports sort extracted text by page position and clean soft hyphens/wrapped line-break hyphens before indexing so search and read-aloud do not inherit common PDF extraction artifacts.

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
- read-aloud engine
- read-aloud voice
- read-aloud sleep timer
- local neural voice download
- fullscreen
- reader orientation
- publisher styles
- alignment
- adaptive PDF fit, layout, and page direction
- idle timeout
- library sort
- library density

Per-book reader appearance overrides are also stored in DataStore, keyed by book id. They only override typography, hyphenation, publisher styles, alignment, PDF fit/layout, and page direction. Theme, fullscreen, reader orientation, keep-screen-awake, reader dimming, tap zones, page animations, volume-button page turns, and idle timeout stay global so reading behavior remains predictable across books.

Reader orientation is applied only while the reader surface is visible, using Android's Activity orientation request for system, sensor portrait, or sensor landscape. Leaving the reader restores the previous Activity orientation request instead of changing the rest of the app or the device's global rotation setting.

Reader dimming is implemented as a reader-only Compose overlay capped by `MAX_READER_DIM_AMOUNT`; it never writes Android system brightness settings and is cleared naturally when leaving the reader surface.

Font choices are limited to families that Android/Readium CSS can resolve or fall back from cleanly, including Readium's bundled OpenDyslexic asset. XReader does not expose user font import until the reader stack can serve those files reliably.

Settings also exposes local JSON backup and restore through Android's Storage Access Framework. Notes/bookmark backups contain notes, highlights, normalized annotation tags, and bookmarks. The global notes screen supports text, kind, and tag filtering, and it exports human-readable Markdown grouped by book while omitting private file paths and checksums. Library backups contain catalog metadata, favorites, finished state, reading progress, reading sessions, custom collections, collection membership, global reader/library settings, and per-book reader appearance, but never imported book files or cover image files. Restores match book-scoped items to already-imported books by file checksum. Items for books that are not in the local library are skipped instead of creating orphan records.

## Dictionary

`tools/build_wordnet_asset.py` converts WordNet 3.0 data files into a compact SQLite asset. On first use, `DictionaryRepository` opens the bundled database and serves normalized local lookup for selected words, including phrase, hyphenated-word, possessive, plural, comparative, superlative, adverb, and common irregular candidates before falling back to web/share actions for local misses.

## Analytics

`ReadingAnalyticsTracker` tracks foreground active reading sessions. It uses reading movement and idle timeout rules to estimate active time, words traversed, WPM, and completion. Normal page movement and small rereading moves count toward words traversed, while large search, TOC, bookmark, or scrubber jumps reset the traversal anchor so skipped pages do not inflate WPM or ETA. Resumed locations seed the tracker without counting the already-visible page again, and short or implausibly fast/slow samples are excluded from pace calculations while still preserving total reading time and words. Import and repair also run `ReadabilityAnalyzer` over extracted text and persist Flesch reading-ease plus grade-level estimates on `BookEntity`, so readability is available offline in existing book health and stats surfaces without adding a new screen. `AnalyticsRepository` aggregates sessions into selectable 7-day, 30-day, 13-week, and all-time ranges with appropriate daily, weekly, monthly, or yearly activity buckets. Finished-book counts use the same persisted manual flag, `finishedAt`, and 99.5% completion threshold as the library filters so restored progress and near-end books do not disagree across Home, Stats, and exports. `AnalyticsExportService` writes those summaries to local CSV or JSON through Android's Storage Access Framework without including imported book files, private file paths, or checksums. Current/best streaks and book, author, genre, and reading-level summaries stay quiet, with no popups or gamified interruptions during reading. Per-book ETA is derived from persisted progress, imported word count, and the current reliable WPM estimate, and is shown only on existing reader/home surfaces when enough data is available.

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

- importing EPUB/PDF/TXT/CBZ/FB2/RTF/MOBI/ODT/DOCX/HTML/MHTML/Markdown
- opening a real EPUB and PDF
- page navigation by swipe, tap, hardware keyboard/DPAD keys, optional volume buttons, filterable TOC/bookmark/note lists, search result, find-next/find-previous, scrubber, and Back-based return after manual jumps
- resume after process/app restart
- adding/removing notes, highlights, and bookmarks
- dictionary lookup from selected text
- light, dark, sepia, OLED, fullscreen, and typography settings
- compact phone, tall phone, landscape, tablet-width, and foldable-style layouts
