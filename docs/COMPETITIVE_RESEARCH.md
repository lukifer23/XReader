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
| EPUB/PDF/TXT reading | Implemented through Readium and TXT-to-EPUB import. | All major readers cover EPUB/PDF/TXT; many cover more. | Keep hardening. |
| MOBI/AZW3 | Not implemented in UI. | Common in Moon+, ReadEra, PocketBook, FBReader. | Later, only with real local conversion. |
| DJVU/FB2/CBZ/CBR/DOC/RTF/ODT | Not implemented. | Broad-format apps cover many of these. | Later; avoid format sprawl before EPUB/PDF/TXT are excellent. |
| No ads/no account | Implemented. | ReadEra and Lithium lead here; Moon+ free has ads; sync apps require accounts. | Preserve. |
| Private app library | Implemented with SAF copies and checksum duplicates. | Some apps scan folders or request broad file access. | Preserve; add folder import only through SAF. |
| Resume/progress | Implemented with Readium locators and sessions. | Table stakes; sync apps add cloud resume. | Keep testing across restarts and screen sizes. |
| Notes/highlights/bookmarks | Implemented with local export/import. | Table stakes in strong readers. | Add richer export targets later. |
| Dictionary | Offline WordNet implemented. | Many rely on external dictionaries or online lookup. | Strong differentiator; keep improving morphology/phrase handling. |
| Search | Local full-text index plus Readium fallback. | Common, but quality varies by format. | Keep hardening PDF/EPUB extraction. |
| Analytics | Active reading, WPM, streaks, range-aware activity, book/author/genre summaries, and local CSV/JSON export. | Moon+ and some ecosystems have stats. | Continue improving trend evidence and long-library performance. |
| TTS | Implemented as in-reader Android TextToSpeech read-aloud from local indexed book text, starting from the visible reader position with persisted speed control. | Moon+, Librera, PocketBook, BookFusion, and others offer it. | Keep hardening voice behavior and device lifecycle handling; evaluate optional on-device neural TTS only if quality, latency, battery, and APK size justify it. |
| OPDS/catalogs | Not implemented. | Moon+, Librera, FBReader, PocketBook support OPDS. | Good optional later feature. |
| PDF reflow/crop | PDF fit implemented; reflow/crop not implemented. | PocketBook/ReadEra have PDF comfort features. | Evaluate after reader polish. |
| Custom user fonts | Built-in family choices, spacing presets, and per-book appearance overrides implemented; user font import not implemented. | FBReader/Moon+ support user fonts. | User font import is a later reader-polish candidate. |
| Bulk metadata cleanup | Matching-series genre/series cleanup implemented in the metadata editor. | Metadata quality is a recurring library pain. | Continue with broader bulk tools later. |

## High-Impact Gaps

The research points to several areas that matter more than raw feature count:

- Library correctness beats file scanning breadth. Lithium-style lost-book reports and Play Books upload friction reinforce XReader's app-private library copy model.
- Reader controls need restraint. Moon+ and KOReader show the ceiling for power, but user pain clusters around too many exposed controls, hidden progress surfaces, and slowdown from heavy customization.
- Metadata cleanup is not optional for real libraries. Series and genre drift makes browse views feel broken, even when every individual book imported successfully.
- OPDS, broader file formats, voice selection/on-device TTS quality, and PDF comfort tools are the largest remaining parity gaps. They should land only as real optional workflows with device tests, not as visible menu promises.
- Large-library performance needs continuous evidence. Competitors with folder/file-browser models show lag under thousands of books, so XReader should keep indexed Room queries, private copies, and startup/open baselines.

## Patches From This Research

- Opt-in matching-series metadata cleanup. From the metadata editor, changing a book's genre or series can now apply those two fields to other books by the same author that match the old or new series name. The operation is atomic and keeps per-book fields such as title, year, and series index untouched.
- Range-aware reading stats. The stats screen now supports 7-day, 30-day, 13-week, and all-time ranges, with grouped analytics and activity buckets recalculated for the selected period.
- Local reading stats export. The stats screen can export all analytics ranges to CSV or JSON through Android's document picker, keeping the workflow local and user-controlled.
- Per-book reader appearance. Books can keep their own font, spacing, alignment, publisher-style, and PDF fit choices without changing global reading behavior.
- Reader spacing presets. Compact, comfort, and accessible presets provide fast setup while still using the same manual font, line-height, and margin controls.
- Read aloud. The reader can speak forward from the visible position through Android TextToSpeech using XReader's private full-text index, with persisted speed control and no permanent reader chrome.

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
