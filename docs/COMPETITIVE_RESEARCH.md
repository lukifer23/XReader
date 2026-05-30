# Competitive Research

Research date: 2026-05-28

This is the working competitive map for XReader. It should drive implementation choices, not become a checklist for clutter. XReader's product line stays local-first, private, ad-free, and DRM-free unless a future pass deliberately changes that scope.

## Current Landscape

| App | What Users Get | Pain To Avoid | XReader Response |
| --- | --- | --- | --- |
| Moon+ Reader | Very broad format support, deep customization, OPDS, cloud backup/sync, annotations, dictionary, translation, TTS, stats, and hardware/headset controls. | Free version has ads; power-user depth can become heavy; all-files access is requested for its file manager path. | Keep the strong reader controls and stats direction, but preserve SAF imports, no ads, and a smaller default UI. |
| ReadEra | Broad format support, no ads, no required registration, offline reading, library grouping by author/series, collections, PDF crop/split, reading history, bookmarks, notes, and flexible settings. | Broad feature set can still leave power-user gaps such as cross-platform sync and advanced metadata workflows. | Match the clean, no-account default while improving local metadata cleanup, backup, and precision resume. |
| Librera | Extremely broad format support, OPDS, TTS, small APK footprint, and many controls. | User feedback highlights control confusion, hidden progress affordances, and missing niceties such as stronger bookmarks or stylus/page-turn input. | Keep navigation predictable: visible controls when needed, tap zones that avoid system gesture conflicts, and conservative progress persistence. |
| FBReader | Fast/lightweight reader, many formats, optional Google Drive based sync, dictionary integration, OPDS, custom fonts/backgrounds, e-ink optimization, and hyphenation. | Optional sync and catalog features add account/workflow complexity. | Local-first stays default; OPDS and sync should remain optional if added. Custom fonts and e-ink tuning are good later candidates. |
| PocketBook Reader | Very broad format support, Adobe DRM, PDF reflow, audiobooks, TTS, cloud sync, OPDS, cloud drives, and minimal design. | Large scope risks turning setup into account/service management. | PDF comfort, TTS, and OPDS are useful candidates; DRM/cloud remain outside v1. |
| KOReader | Open, powerful, highly customizable, strong e-ink heritage, plugins, and advanced reading controls. | Users report overwhelming UI, plugin/customization-related slowdowns, and large-folder lag with thousands of books. | Avoid an exposed-API feel. Advanced controls should be grouped, measured, and off the reading surface by default. |
| Lithium | Simple, Material-style EPUB reader with notes/highlights, themes, page/scroll modes, and ad-free positioning. | Users report fragile file access/book detection and want richer metadata/tags. | Private library copies and checksum identity are good differentiators; metadata cleanup needs to be strong. |
| Google Play Books | Uploaded EPUB/PDF reading across devices with bookmarks, highlights, and notes. | Cloud upload dependency and upload/open failures are common friction points for sideloaded libraries. | XReader should keep direct local import reliable and backup/export user-controlled. |
| BookFusion | Cross-device library, annotations, sync, themes, and integrations. | Sync precision can fail across devices/screen sizes, leading users to rely on manual bookmarks. | If sync ever lands, it needs conflict handling and locator evidence first. Local resume must remain the trusted source. |

## Feature Parity Snapshot

| Capability | XReader Current | Major Competitors | Priority |
| --- | --- | --- | --- |
| EPUB/PDF/TXT/CBZ/FB2/RTF/ODT/DOCX/HTML/Markdown reading | Implemented through Readium, TXT-to-EPUB import, CBZ-to-fixed-layout-EPUB import, FB2-to-EPUB import, RTF-to-EPUB import, ODT-to-EPUB import, DOCX-to-EPUB import, HTML/XHTML-to-EPUB import, and Markdown-to-EPUB import. | All major readers cover EPUB/PDF/TXT; broad readers often include comics/image archives, FB2, RTF, office documents, HTML, and plain markup/document exports. | Keep hardening. |
| MOBI/AZW3 | Not implemented in UI. | Common in Moon+, ReadEra, PocketBook, FBReader. | Later, only with real local conversion. |
| DJVU/CBR/legacy binary DOC | Not implemented. | Broad-format apps cover many of these. | Later; avoid format sprawl before the current reader path is excellent. |
| No ads/no account | Implemented. | ReadEra and Lithium lead here; Moon+ free has ads; sync apps require accounts. | Preserve. |
| Private app library | Implemented with SAF file/folder copies, checksum duplicates, missing private-file recovery on re-import, and per-book save-copy export back through Android's document picker. | Some apps scan folders or request broad file access. | Preserve SAF-only imports and private copies while keeping user-controlled escape hatches. |
| Resume/progress | Implemented with Readium locators and sessions. | Table stakes; sync apps add cloud resume. | Keep testing across restarts and screen sizes. |
| Notes/highlights/bookmarks | Implemented with annotation tags, tag filtering, local JSON backup/import, and human-readable Markdown export. | Table stakes in strong readers; Lithium users specifically ask for richer metadata/tags. | Keep export local and useful outside the app. |
| Reader search | Implemented with in-book search, result jumps, and a compact find bar for previous/next match navigation. | Search is table stakes, but modal-only search interrupts reading. | Keep it temporary, small, and tied to real indexed/Readium results. |
| Library grouping/sorting | Implemented across books, authors, series, genres, years, custom collections, recent, unread, in progress, finished, and favorites; the existing sort control now applies to grouped views and in-group book order, and the Books home can recommend the next unfinished title in a series after a finished book. | Strong readers support flexible organization; cluttered apps often expose too many parallel filters. | Keep the single compact control surface and make it behave predictably. |
| Dictionary | Offline WordNet implemented with common possessive, plural, irregular plural, and inflected-form lookup. | Many rely on external dictionaries or online lookup. | Strong differentiator; keep improving morphology/phrase handling. |
| Search | Local full-text index plus Readium fallback. | Common, but quality varies by format. | Keep hardening PDF/EPUB extraction. |
| Analytics | Active reading, WPM, streaks, range-aware activity, book/author/genre summaries, and local CSV/JSON export. | Moon+ and some ecosystems have stats. | Continue improving trend evidence and long-library performance. |
| TTS | Implemented as in-reader Android TextToSpeech read-aloud from page-aligned local indexed book text, starting from the visible reader position, persisting spoken position as playback advances, and supporting pause/resume, previous/next passage, installed offline device voice selection, speed control, sleep timer, audio-focus handling, Android media-session transport controls, and a media-playback foreground notification for headset/Bluetooth/play-pause/next/previous/stop actions outside the reader. | Moon+, Librera, PocketBook, BookFusion, and others offer it. | Keep hardening device TTS engine differences and background behavior; evaluate optional on-device neural TTS only if quality, latency, battery, position sync, and APK size justify it. |
| OPDS/catalogs | Not implemented. | Moon+, Librera, FBReader, PocketBook support OPDS. | Good optional later feature. |
| PDF reflow/crop | PDF fit and paged/vertical layout are implemented; reflow/crop are not implemented. | PocketBook/ReadEra have PDF comfort features. | Continue adding only real Readium-backed comfort controls. |
| Typography depth | Built-in family choices including OpenDyslexic, spacing presets, font weight, hyphenation, and per-book appearance overrides implemented; user font import not implemented. | FBReader/Moon+ support user fonts, background customization, and hyphenation. | Keep adding only reader-backed controls; user font import remains later until custom files can be served reliably. |
| Bulk metadata cleanup | Automatic author/genre/series canonicalization plus matching-series cleanup implemented in the metadata editor. | Metadata quality is a recurring library pain. | Continue with broader bulk tools later. |

## High-Impact Gaps

The research points to several areas that matter more than raw feature count:

- Library correctness beats file scanning breadth. Lithium-style lost-book reports and Play Books upload friction reinforce XReader's app-private library copy model.
- Reader controls need restraint. Moon+ and KOReader show the ceiling for power, but user pain clusters around too many exposed controls, hidden progress surfaces, and slowdown from heavy customization.
- Metadata cleanup is not optional for real libraries. Series and genre drift makes browse views feel broken, even when every individual book imported successfully.
- OPDS, remaining broad file formats, on-device TTS quality, and PDF comfort tools are the largest remaining parity gaps. They should land only as real optional workflows with device tests, not as visible menu promises.
- Large-library performance needs continuous evidence. Competitors with folder/file-browser models show lag under thousands of books, so XReader should keep indexed Room queries, private copies, and startup/open baselines.

## Patches From This Research

- Opt-in matching-series metadata cleanup. From the metadata editor, changing shared author, genre, or series values can apply them to other books that match the old or new author and series pair. The operation is atomic and keeps per-book fields such as title, year, and series index untouched.
- Metadata canonicalization. Import and metadata edits collapse author casing/spacing variants against existing library values and normalize known genre aliases such as `sci-fi` into stable groups without adding another management screen.
- Reader find bar. After an in-book search, XReader keeps a compact temporary find bar with match count plus previous/next controls, using Readium search positions or the local search index fallback instead of a placeholder overlay.
- Read-aloud passage controls. Active read-aloud now exposes compact previous/next passage controls in the reader bar and uses the same page-aligned chunks that drive spoken locator persistence.
- Annotation tags. Notes and highlights can carry normalized comma-separated tags, visible in reader/global note lists, filterable from the global notes screen, and preserved in JSON/Markdown exports.
- Filterable in-book navigation. The reader Navigate sheet now filters long tables of contents, bookmarks, notes, highlights, and annotation tags without adding permanent chrome, and uses lazy lists for large books.
- Series continuation. The Books home now shows a single compact "Up next" recommendation when the library has the next unfinished title after the most recently finished book in a series, using series index with year/title fallback.
- Save book copy. Each book action menu can export the stored private reader file through SAF, so local-first storage does not trap the user's files or require broad storage permissions.
- Range-aware reading stats. The stats screen now supports 7-day, 30-day, 13-week, and all-time ranges, with grouped analytics and activity buckets recalculated for the selected period.
- Local reading stats export. The stats screen can export all analytics ranges to CSV or JSON through Android's document picker, keeping the workflow local and user-controlled.
- Per-book reader appearance. Books can keep their own font, font weight, spacing, hyphenation, alignment, publisher-style, and PDF fit choices without changing global reading behavior.
- PDF layout comfort. PDFs can use page, width, or height fit and paged or vertical layout, with per-book overrides for manuals, scans, and landscape documents.
- Keep screen awake. Reader sessions can opt into Android's screen-awake flag while the reader is visible, avoiding sleep interruptions without changing global phone settings.
- Reader dimming. Night reading can use a reader-only dim overlay without requesting system brightness permissions or changing the rest of the phone.
- Optional volume-button page turns. Phone volume buttons remain system volume controls by default, but readers can opt into physical page turns from global or in-reader settings without adding permanent chrome.
- Reader spacing presets. Compact, comfort, and accessible presets provide fast setup while still using the same manual font, line-height, and margin controls.
- Typography depth. The reader exposes Readium-backed OpenDyslexic, text weight, and hyphenation controls globally and per book instead of pretending to import custom fonts the WebView cannot serve.
- Read aloud. The reader can speak forward from the visible position through Android TextToSpeech using XReader's private full-text index split into Readium-position-sized speech chunks, persists its spoken locator as it advances, supports pause/resume plus previous/next passage controls, registers Android media-session transport controls, promotes active playback to a media-playback foreground notification, and uses installed offline device voice selection plus sleep timer controls without permanent reader chrome.
- Calibrated tap zones. Reader taps now use compact, balanced, or wide presets with edge guards for gesture-navigation devices, keeping page-turn control predictable without adding permanent reader chrome.
- Batch SAF import. The library can import multiple files or a whole SAF folder without broad storage permission, while preserving checksum duplicate handling.
- Actionable single-book import. When one selected book imports successfully or is already present by checksum, the library snackbar can open that exact title directly instead of leaving the reader to search for it.
- Re-import recovery. If a checksum-matched title is still in the catalog but its app-private reader file is missing, selecting the source file again restores the private copy and search rows without changing progress, notes, bookmarks, or collections.
- CBZ import. Comic/image archives are converted locally into fixed-layout EPUB, sorted by natural page order, and read through the same private-library and Readium path instead of adding a parallel comic-reader surface.
- FB2 import. FictionBook files, including `.fb2.zip`, are converted locally into EPUB with title, author, genre, year, series, chapter text, and embedded cover metadata preserved where available.
- RTF import. Rich Text files convert locally into EPUB with extracted title/author metadata and searchable text, keeping the same private-library and Readium reader path.
- ODT import. OpenDocument text files convert locally into EPUB with metadata, headings, paragraphs, lists, tables, and searchable text preserved in reading order.
- DOCX import. WordprocessingML documents convert locally into EPUB with metadata, headings, paragraphs, lists, tables, and searchable text preserved in reading order.
- HTML import. Standalone HTML, HTM, and XHTML documents convert locally into EPUB with page metadata, headings, lists, tables, blockquotes, and searchable text preserved in reading order.
- Markdown import. Markdown documents convert locally into EPUB with front matter metadata, headings, lists, blockquotes, code blocks, and searchable text preserved in reading order.
- Markdown notes export. Notes, highlights, and bookmarks can leave the app in a readable grouped document, while JSON remains the restore-oriented backup format.
- Manual finished-state control. The book action menu can mark books finished or not finished, and library filters/counts/progress displays use one finished-state-aware classification.
- Custom collections. The book action menu can add or remove a book from user-named collections, and the existing library group control can browse those collections without adding another permanent tab or top-level mode.

## Product Rules From The Research

- Default to reading, not configuring. Advanced controls belong in grouped settings or contextual dialogs, not permanent reader chrome.
- Never allow a hidden scrubber or ambiguous gesture to make a reader lose their place.
- Avoid all-files access unless there is no scoped alternative. SAF imports and app-private copies are a product feature, not a compromise.
- Keep sync, catalogs, and conversion optional. They are useful, but they can easily become account friction, unreliable state, or bloat.
- Measure startup and reader-open changes on device. Debug-build class loading can obscure app-owned work, so performance claims need artifacts.
- Treat metadata consistency as a first-class library feature. Users with real series libraries need cleanup tools, not only one-book forms.

## Sources

- [Moon+ Reader on Google Play](https://play.google.com/store/apps/details?hl=en&id=com.flyersoft.moonreader)
- [ReadEra on Google Play](https://play.google.com/store/apps/details?id=org.readera)
- [Librera on Google Play](https://play.google.com/store/apps/details?id=com.foobnix.pdf.reader)
- [FBReader on Google Play](https://play.google.com/store/apps/details?hl=en_US&id=org.geometerplus.zlibrary.ui.android)
- [PocketBook Reader on Google Play](https://play.google.com/store/apps/details/?hl=en-US&id=com.obreey.reader)
- [Lithium on Google Play](https://play.google.com/store/apps/details?id=com.faultexception.reader)
- [Google Play Books upload support](https://support.google.com/googleplay/answer/11012086?co=GENIE.Platform%3DDesktop&hl=en)
- [KOReader large-library lag discussion](https://www.reddit.com/r/koreader/comments/1tch3i2/koreader_is_slow_within_simple_ui_library/)
- [KOReader slowdown discussion](https://www.reddit.com/r/koreader/comments/1slf9xu/koreader_is_slow/)
- [Lithium file-access/book detection discussion](https://www.reddit.com/r/androidapps/comments/1cok5dn/lithium_reader_lost_all_books_and_doesnt_find/)
- [BookFusion sync issues discussion](https://www.reddit.com/r/BookFusion/comments/1jr6icd/sync_issues/)
