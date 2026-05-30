package com.xreader.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xreader.app.AppContainer
import com.xreader.app.analytics.ReadingAnalyticsTracker
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookmarkEntity
import com.xreader.app.data.DictionaryEntryEntity
import com.xreader.app.data.ReaderTheme
import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.data.SearchIndexEntity
import com.xreader.app.reader.OpenPublication
import com.xreader.app.reader.ReaderNavigationItem
import com.xreader.app.reader.ReaderSearchResult
import com.xreader.app.reader.ReadingUnit
import com.xreader.app.settings.ReadAloudSleepTimer
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderHighlightColor
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.settings.ReaderSpacingPreset
import com.xreader.app.settings.ReaderTapZonePreset
import com.xreader.app.settings.ReaderTextAlign
import com.xreader.app.settings.withBookAppearance
import com.xreader.app.tts.ReadAloudChunk
import com.xreader.app.tts.ReadAloudPlanner
import com.xreader.app.tts.ReadAloudState
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.shared.publication.Locator

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val book: BookEntity? = null,
    val publication: OpenPublication? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val bookAppearanceEnabled: Boolean = false,
    val state: ReadingStateEntity? = null,
    val annotations: List<AnnotationEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val tableOfContents: List<ReaderNavigationItem> = emptyList(),
    val tableOfContentsLoading: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ReaderSearchResult> = emptyList(),
    val activeSearchResultIndex: Int? = null,
    val searchRunning: Boolean = false,
    val searchPerformed: Boolean = false,
    val dictionaryWord: String? = null,
    val dictionaryEntries: List<DictionaryEntryEntity> = emptyList(),
    val chromeVisible: Boolean = false,
    val currentUnit: Int = 0,
    val initialLocatorJson: String? = null,
    val noteDraftOpen: Boolean = false,
    val pendingNoteLocator: String? = null,
    val pendingNoteQuote: String? = null,
    val readAloud: ReadAloudState = ReadAloudState(),
)

class ReaderViewModel(
    private val bookId: Long,
    private val initialLocatorOverride: String?,
    private val container: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    private var tracker: ReadingAnalyticsTracker? = null
    private var readerClosed = false
    private var saveJob: Job? = null
    private var searchJob: Job? = null
    private var lastReadingState: ReadingStateEntity? = null
    private var ignoreStoredStateUntilFirstLocator = initialLocatorOverride != null
    private var deferredObserversStarted = false
    private var lastReadAloudLocator: String? = null

    init {
        viewModelScope.launch {
            combine(
                container.settingsRepository.settings,
                container.settingsRepository.bookAppearance(bookId)
            ) { global, bookAppearance ->
                global.withBookAppearance(bookAppearance) to (bookAppearance != null)
            }.collect { (settings, bookAppearanceEnabled) ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        bookAppearanceEnabled = bookAppearanceEnabled
                    )
                }
                val readAloud = container.readAloudEngine.state.value
                if (readAloud.activeBookId == bookId && (readAloud.playing || readAloud.initializing)) {
                    container.readAloudEngine.setSpeechRate(settings.readAloudRate)
                    container.readAloudEngine.setVoice(settings.readAloudVoiceName)
                }
            }
        }
        viewModelScope.launch {
            container.readAloudEngine.state.collect { readAloud ->
                val relevantReadAloud = if (readAloud.activeBookId == null || readAloud.activeBookId == bookId) {
                    readAloud
                } else {
                    ReadAloudState()
                }
                _uiState.update {
                    it.copy(readAloud = relevantReadAloud)
                }
                val spokenLocator = relevantReadAloud.currentLocator
                if (relevantReadAloud.playing && spokenLocator != null) {
                    if (spokenLocator != lastReadAloudLocator) {
                        lastReadAloudLocator = spokenLocator
                        recordReadAloudProgress(
                            unit = relevantReadAloud.currentUnit,
                            locator = spokenLocator
                        )
                    }
                } else if (!relevantReadAloud.initializing) {
                    lastReadAloudLocator = null
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                val book = requireNotNull(container.libraryRepository.getBook(bookId)) { "Book not found" }
                val savedState = async { container.readingRepository.getState(bookId) }
                val publication = container.publicationService.open(book)
                val units = publication.units
                val saved = savedState.await()
                val requestedLocator = initialLocatorOverride ?: saved?.locator
                val maxIndexedUnit = if (requestedLocator?.searchUnitIndexOrNull() != null) {
                    container.libraryRepository.maxIndexedUnitForBook(bookId).coerceAtLeast(1)
                } else {
                    1
                }
                val initialPosition = resolveInitialReaderPosition(
                    initialLocatorOverride = initialLocatorOverride,
                    saved = saved,
                    positions = publication.positions,
                    units = units,
                    maxIndexedUnit = maxIndexedUnit
                )
                val activeTracker = createTracker(publication)
                tracker = activeTracker
                val jumpState = if (initialPosition.fromInitialOverride && initialPosition.locatorJson != null) {
                    activeTracker.record(
                        unit = initialPosition.unitIndex,
                        locator = initialPosition.locatorJson,
                        progressOverride = if (units.size <= 1) {
                            1.0
                        } else {
                            initialPosition.unitIndex.toDouble() / (units.size - 1).toDouble()
                        }
                    )
                } else {
                    saved
                }
                lastReadingState = jumpState
                _uiState.update {
                    it.copy(
                        loading = false,
                        book = book,
                        publication = publication,
                        currentUnit = initialPosition.unitIndex,
                        initialLocatorJson = initialPosition.locatorJson,
                        state = jumpState ?: it.state
                    )
                }
                startDeferredObservers()
                loadTableOfContents(publication)
                container.applicationScope.launch { container.libraryRepository.markOpened(bookId) }
            }.onFailure { error ->
                _uiState.update { it.copy(loading = false, error = error.message ?: "Could not open book") }
            }
        }
    }

    private fun startDeferredObservers() {
        if (deferredObserversStarted) return
        deferredObserversStarted = true
        viewModelScope.launch {
            container.annotationRepository.observeAnnotations(bookId).collect { annotations ->
                _uiState.update { it.copy(annotations = annotations) }
            }
        }
        viewModelScope.launch {
            container.annotationRepository.observeBookmarks(bookId).collect { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }
        viewModelScope.launch {
            container.readingRepository.observeState(bookId).collect { state ->
                if (ignoreStoredStateUntilFirstLocator) return@collect
                if (lastReadingState == null && state != null) lastReadingState = state
                _uiState.update { it.copy(state = state, currentUnit = state?.currentUnit ?: it.currentUnit) }
            }
        }
    }

    private fun loadTableOfContents(publication: OpenPublication.Readium) {
        _uiState.update { it.copy(tableOfContentsLoading = true) }
        viewModelScope.launch {
            val tableOfContents = runCatching {
                container.publicationService.tableOfContents(publication)
            }.getOrDefault(emptyList())
            _uiState.update { current ->
                if (current.publication !== publication) {
                    current
                } else {
                    current.copy(
                        tableOfContents = tableOfContents,
                        tableOfContentsLoading = false
                    )
                }
            }
        }
    }

    fun toggleChrome() {
        _uiState.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    fun toggleLightDark() {
        val next = when (_uiState.value.settings.theme) {
            ReaderTheme.LIGHT, ReaderTheme.SEPIA -> ReaderTheme.DARK
            ReaderTheme.DARK, ReaderTheme.OLED -> ReaderTheme.LIGHT
        }
        viewModelScope.launch { container.settingsRepository.setTheme(next) }
    }

    fun toggleFullScreen() {
        val next = !_uiState.value.settings.fullScreen
        viewModelScope.launch { container.settingsRepository.setFullScreen(next) }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch {
            if (_uiState.value.bookAppearanceEnabled) {
                container.settingsRepository.setBookFontScale(bookId, value)
            } else {
                container.settingsRepository.setFontScale(value)
            }
        }
    }

    fun setLineHeight(value: Float) {
        viewModelScope.launch {
            if (_uiState.value.bookAppearanceEnabled) {
                container.settingsRepository.setBookLineHeight(bookId, value)
            } else {
                container.settingsRepository.setLineHeight(value)
            }
        }
    }

    fun setMarginScale(value: Float) {
        viewModelScope.launch {
            if (_uiState.value.bookAppearanceEnabled) {
                container.settingsRepository.setBookMarginScale(bookId, value)
            } else {
                container.settingsRepository.setMarginScale(value)
            }
        }
    }

    fun setSpacingPreset(value: ReaderSpacingPreset) {
        viewModelScope.launch {
            if (_uiState.value.bookAppearanceEnabled) {
                container.settingsRepository.setBookSpacingPreset(bookId, value)
            } else {
                container.settingsRepository.setSpacingPreset(value)
            }
        }
    }

    fun setFontFamily(value: ReaderFontFamily) {
        viewModelScope.launch {
            if (_uiState.value.bookAppearanceEnabled) {
                container.settingsRepository.setBookFontFamily(bookId, value)
            } else {
                container.settingsRepository.setFontFamily(value)
            }
        }
    }

    fun setPublisherStyles(value: Boolean) {
        viewModelScope.launch {
            if (_uiState.value.bookAppearanceEnabled) {
                container.settingsRepository.setBookPublisherStyles(bookId, value)
            } else {
                container.settingsRepository.setPublisherStyles(value)
            }
        }
    }

    fun setPageTurnAnimations(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setPageTurnAnimations(value) }
    }

    fun setKeepScreenAwake(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setKeepScreenAwake(value) }
    }

    fun setTapZonesEnabled(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setTapZonesEnabled(value) }
    }

    fun setTapZonePreset(value: ReaderTapZonePreset) {
        viewModelScope.launch { container.settingsRepository.setTapZonePreset(value) }
    }

    fun setReadAloudRate(value: Float) {
        viewModelScope.launch { container.settingsRepository.setReadAloudRate(value) }
        container.readAloudEngine.setSpeechRate(value)
    }

    fun setReadAloudSleepTimer(value: ReadAloudSleepTimer) {
        viewModelScope.launch { container.settingsRepository.setReadAloudSleepTimer(value) }
        container.readAloudEngine.setSleepTimer(value.durationMillis)
    }

    fun setTextAlign(value: ReaderTextAlign) {
        viewModelScope.launch {
            if (_uiState.value.bookAppearanceEnabled) {
                container.settingsRepository.setBookTextAlign(bookId, value)
            } else {
                container.settingsRepository.setTextAlign(value)
            }
        }
    }

    fun setPdfFit(value: ReaderPdfFit) {
        viewModelScope.launch {
            if (_uiState.value.bookAppearanceEnabled) {
                container.settingsRepository.setBookPdfFit(bookId, value)
            } else {
                container.settingsRepository.setPdfFit(value)
            }
        }
    }

    fun setHighlightColor(value: String) {
        viewModelScope.launch {
            container.settingsRepository.setHighlightColor(value)
        }
    }

    fun setBookAppearanceEnabled(value: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setBookAppearanceEnabled(
                bookId = bookId,
                enabled = value,
                seed = _uiState.value.settings
            )
        }
    }

    fun toggleReadAloud(
        visibleUnit: Int? = null,
        visibleLocator: String? = null,
    ) {
        val readAloud = _uiState.value.readAloud
        if (readAloud.playing || readAloud.initializing) {
            container.readAloudEngine.stop(bookId)
            return
        }
        viewModelScope.launch {
            val publication = _uiState.value.publication ?: return@launch
            val rows = container.libraryRepository.indexedRowsForBook(bookId)
            val chunks = readAloudChunks(publication, rows)
            val startPosition = resolveReadAloudStartPosition(
                visibleUnit = visibleUnit,
                visibleLocatorJson = visibleLocator,
                storedLocatorJson = _uiState.value.state?.locator,
                fallbackUnit = _uiState.value.currentUnit,
                positions = publication.positions,
                units = publication.units
            )
            container.readAloudEngine.play(
                bookId = bookId,
                chunks = chunks,
                currentUnit = startPosition?.unitIndex ?: _uiState.value.currentUnit.coerceAtLeast(0),
                currentLocator = startPosition?.locatorJson ?: _uiState.value.state?.locator?.takeIf { it.isNotBlank() },
                speechRate = _uiState.value.settings.readAloudRate,
                voiceName = _uiState.value.settings.readAloudVoiceName,
                sleepTimerDurationMillis = _uiState.value.settings.readAloudSleepTimer.durationMillis
            )
        }
    }

    fun clearReadAloudMessage() {
        container.readAloudEngine.clearMessage(bookId)
    }

    fun skipReadAloudPrevious() {
        container.readAloudEngine.skipToPrevious(bookId)
    }

    fun skipReadAloudNext() {
        container.readAloudEngine.skipToNext(bookId)
    }

    fun setSearchQuery(value: String) {
        val previousQuery = _uiState.value.searchQuery.trim()
        val nextQuery = value.trim()
        if (previousQuery != nextQuery) {
            searchJob?.cancel()
            searchJob = null
        }
        _uiState.update {
            if (previousQuery == nextQuery) {
                it.copy(searchQuery = value)
            } else {
                it.copy(
                    searchQuery = value,
                    searchResults = emptyList(),
                    activeSearchResultIndex = null,
                    searchRunning = false,
                    searchPerformed = false
                )
            }
        }
    }

    fun runSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            searchJob?.cancel()
            searchJob = null
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    activeSearchResultIndex = null,
                    searchRunning = false,
                    searchPerformed = false
                )
            }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val publication = _uiState.value.publication as? OpenPublication.Readium
            if (publication == null) {
                _uiState.update { current ->
                    if (current.searchQuery.trim() == query) {
                        current.copy(
                            searchResults = emptyList(),
                            activeSearchResultIndex = null,
                            searchRunning = false,
                            searchPerformed = true
                        )
                    } else {
                        current
                    }
                }
                return@launch
            }
            _uiState.update { it.copy(searchRunning = true, searchPerformed = false) }
            val readiumResults = withTimeoutOrNull(1_500L) {
                runCatching { container.publicationService.search(publication, query) }
                    .getOrDefault(emptyList())
            }.orEmpty()
            val results = readiumResults.ifEmpty {
                fallbackSearchResults(publication, query)
            }
            _uiState.update { current ->
                if (current.searchQuery.trim() == query) {
                    current.copy(
                        searchResults = results,
                        activeSearchResultIndex = null,
                        searchRunning = false,
                        searchPerformed = true
                    )
                } else {
                    current
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                activeSearchResultIndex = null,
                searchRunning = false,
                searchPerformed = false
            )
        }
    }

    fun setActiveSearchResult(index: Int?) {
        _uiState.update { state ->
            val safeIndex = index?.takeIf { it in state.searchResults.indices }
            state.copy(activeSearchResultIndex = safeIndex)
        }
    }

    fun recordLocator(locator: Locator) {
        ignoreStoredStateUntilFirstLocator = false
        val publication = _uiState.value.publication ?: return
        val total = publication.units.size.coerceAtLeast(1)
        val unit = publication.positionIndexFor(locator).coerceIn(0, total - 1)
        val progress = locator.locations.totalProgression
            ?: if (total <= 1) 1.0 else unit.toDouble() / (total - 1).toDouble()
        val locatorJson = locator.toJSON().toString()
        val previous = lastReadingState ?: _uiState.value.state
        val previousFinishedAt = previous?.finishedAt
        val previousActiveMillis = previous?.activeMillis ?: 0L
        val sameLocator = _uiState.value.currentUnit == unit && previous?.locator == locatorJson
        val state = (tracker ?: createTracker(publication).also { tracker = it }).record(
            unit = unit,
            locator = locatorJson,
            progressOverride = progress.coerceIn(0.0, 1.0)
        )
        lastReadingState = state
        _uiState.update { it.copy(currentUnit = unit, state = state) }
        val shouldSave = !sameLocator ||
            state.finishedAt != previousFinishedAt ||
            state.activeMillis - previousActiveMillis >= 10_000L
        if (shouldSave) scheduleStateSave(state)
    }

    private fun recordReadAloudProgress(unit: Int, locator: String) {
        val publication = _uiState.value.publication ?: return
        val total = publication.units.size.coerceAtLeast(1)
        val readiumLocator = locator.toReadiumLocatorOrNull()
        val boundedUnit = readiumLocator
            ?.let { publication.positionIndexFor(it) }
            ?: unit
        val currentUnit = boundedUnit.coerceIn(0, total - 1)
        val progress = readiumLocator?.locations?.totalProgression
            ?: if (total <= 1) 1.0 else currentUnit.toDouble() / (total - 1).toDouble()
        val state = (tracker ?: createTracker(publication).also { tracker = it }).record(
            unit = currentUnit,
            locator = readiumLocator?.toJSON()?.toString() ?: locator,
            progressOverride = progress.coerceIn(0.0, 1.0)
        )
        lastReadingState = state
        _uiState.update { it.copy(currentUnit = currentUnit, state = state) }
        scheduleStateSave(state)
    }

    fun persistReadingState() {
        if (readerClosed) return
        val state = snapshotReadingState() ?: return
        persistInApplicationScope(state = state, session = null)
    }

    fun pauseReadingSession() {
        if (readerClosed) return
        val flush = tracker?.flush()
        val state = flush?.state ?: lastReadingState ?: _uiState.value.state
        if (flush?.state != null) {
            lastReadingState = flush.state
            _uiState.update { it.copy(state = flush.state, currentUnit = flush.state.currentUnit) }
        }
        tracker = null
        if (state != null || flush?.session != null) {
            persistInApplicationScope(state = state, session = flush?.session)
        }
    }

    fun resumeReadingSession() {
        if (readerClosed || tracker != null) return
        val publication = _uiState.value.publication ?: return
        tracker = createTracker(publication)
    }

    fun lookupWord(word: String) {
        viewModelScope.launch {
            val entries = container.dictionaryRepository.lookup(word)
            _uiState.update { it.copy(dictionaryWord = word, dictionaryEntries = entries) }
        }
    }

    fun closeDictionary() {
        _uiState.update { it.copy(dictionaryWord = null, dictionaryEntries = emptyList()) }
    }

    fun openNoteDraft(
        visibleUnit: Int? = null,
        visibleLocator: String? = null,
    ) {
        val target = visibleReadingTarget(visibleUnit, visibleLocator)
        _uiState.update {
            it.copy(
                noteDraftOpen = true,
                pendingNoteLocator = target?.position?.locatorJson,
                pendingNoteQuote = target?.noteQuote()
            )
        }
    }

    fun openSelectedNote(locator: Locator, quote: String) {
        val cleanQuote = quote.selectedQuote()
        if (cleanQuote.isBlank()) return
        _uiState.update {
            it.copy(
                noteDraftOpen = true,
                pendingNoteLocator = locator.toJSON().toString(),
                pendingNoteQuote = cleanQuote
            )
        }
    }

    fun addSelectedHighlight(locator: Locator, quote: String) {
        val cleanQuote = quote.selectedQuote()
        if (cleanQuote.isBlank()) return
        val color = ReaderHighlightColor.normalized(_uiState.value.settings.highlightColor)
        viewModelScope.launch {
            container.annotationRepository.addHighlight(
                bookId = bookId,
                locator = locator.toJSON().toString(),
                quote = cleanQuote,
                color = color
            )
        }
    }

    fun closeNoteDraft() {
        _uiState.update { it.copy(noteDraftOpen = false, pendingNoteLocator = null, pendingNoteQuote = null) }
    }

    fun addNote(note: String, tags: String) {
        if (note.isBlank()) return
        val snapshot = _uiState.value
        val unit = if (snapshot.pendingNoteLocator == null) currentReadingUnit() else null
        val locator = snapshot.pendingNoteLocator ?: unit?.locator ?: return
        val quote = snapshot.pendingNoteQuote ?: unit?.body?.take(300)?.ifBlank { unit.heading } ?: ""
        viewModelScope.launch {
            container.annotationRepository.addNote(
                bookId = bookId,
                locator = locator,
                quote = quote,
                note = note.trim(),
                tags = tags
            )
            closeNoteDraft()
        }
    }

    fun updateAnnotationNote(annotation: AnnotationEntity, note: String, color: String, tags: String) {
        viewModelScope.launch {
            container.annotationRepository.updateNote(
                annotation = annotation,
                note = note,
                color = if (annotation.kind == com.xreader.app.data.AnnotationKind.HIGHLIGHT) {
                    ReaderHighlightColor.normalized(color)
                } else {
                    annotation.color
                },
                tags = tags
            )
        }
    }

    fun addHighlight() {
        val unit = currentReadingUnit() ?: return
        val color = ReaderHighlightColor.normalized(_uiState.value.settings.highlightColor)
        viewModelScope.launch {
            container.annotationRepository.addHighlight(
                bookId = bookId,
                locator = unit.locator,
                quote = unit.body.take(300).ifBlank { unit.heading },
                color = color
            )
        }
    }

    fun toggleBookmark(
        visibleUnit: Int? = null,
        visibleLocator: String? = null,
    ) {
        val target = visibleReadingTarget(visibleUnit, visibleLocator) ?: return
        val publication = _uiState.value.publication ?: return
        val total = publication.units.size.coerceAtLeast(1)
        val existing = _uiState.value.bookmarks.bookmarkAtReaderLocation(
            visibleLocatorJson = target.position.locatorJson,
            fallbackUnitLocator = target.unit.locator
        )
        if (existing != null) {
            viewModelScope.launch { container.annotationRepository.deleteBookmark(existing.id) }
            return
        }
        val progress = target.position.readiumLocator?.locations?.totalProgression
            ?: if (total <= 1) 1.0 else target.position.unitIndex.toDouble() / (total - 1).toDouble()
        val label = target.position.readiumLocator?.title
            ?: target.unit.heading.ifBlank { "Position ${target.position.unitIndex + 1}" }
        viewModelScope.launch {
            container.annotationRepository.toggleBookmark(
                bookId = bookId,
                locator = target.position.locatorJson,
                label = label,
                progress = progress.coerceIn(0.0, 1.0)
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            container.annotationRepository.deleteBookmark(id)
        }
    }

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch {
            container.annotationRepository.deleteAnnotation(id)
        }
    }

    fun flushSession() {
        if (readerClosed) return
        readerClosed = true
        container.readAloudEngine.stop(bookId)
        saveJob?.cancel()
        val flush = tracker?.flush()
        val finalState = flush?.state ?: lastReadingState ?: _uiState.value.state
        tracker = null
        if (finalState != null || flush?.session != null) {
            persistInApplicationScope(state = finalState, session = flush?.session)
        }
    }

    private fun currentReadingUnit(): ReadingUnit? =
        _uiState.value.publication?.units?.getOrNull(_uiState.value.currentUnit)

    private fun visibleReadingTarget(
        visibleUnit: Int?,
        visibleLocator: String?,
    ): VisibleReadingTarget? {
        val publication = _uiState.value.publication ?: return null
        val position = resolveVisibleReaderPosition(
            visibleUnit = visibleUnit,
            visibleLocatorJson = visibleLocator,
            fallbackUnit = _uiState.value.currentUnit,
            positions = publication.positions,
            units = publication.units
        ) ?: return null
        val unit = publication.units.getOrNull(position.unitIndex) ?: return null
        return VisibleReadingTarget(position = position, unit = unit)
    }

    private fun createTracker(publication: OpenPublication): ReadingAnalyticsTracker =
        ReadingAnalyticsTracker(
            bookId = bookId,
            totalUnits = publication.units.size.coerceAtLeast(1),
            wordsForUnit = { index -> publication.units.getOrNull(index)?.wordCount ?: 0 },
            idleTimeoutMillis = _uiState.value.settings.idleTimeoutMillis
        )

    private fun snapshotReadingState(): ReadingStateEntity? {
        val state = tracker?.snapshot() ?: lastReadingState ?: _uiState.value.state
        if (state != null) {
            lastReadingState = state
            _uiState.update { it.copy(state = state, currentUnit = state.currentUnit) }
        }
        return state
    }

    private fun scheduleStateSave(state: ReadingStateEntity) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { persistState(state) }
    }

    private suspend fun persistState(state: ReadingStateEntity) {
        container.readingRepository.saveState(state)
        if (state.finishedAt != null) container.libraryRepository.setFinished(bookId, true)
    }

    private fun persistInApplicationScope(
        state: ReadingStateEntity?,
        session: com.xreader.app.data.ReadingSessionEntity?,
    ) {
        container.applicationScope.launch {
            state?.let { persistState(it) }
            session?.let { container.readingRepository.insertSession(it) }
        }
    }

    private suspend fun fallbackSearchResults(
        publication: OpenPublication.Readium,
        query: String,
    ): List<ReaderSearchResult> {
        val matches = container.libraryRepository.searchBook(bookId, query)
        if (matches.isEmpty()) return emptyList()
        val lastIndexedUnit = container.libraryRepository.maxIndexedUnitForBook(bookId).coerceAtLeast(1)
        val lastPositionIndex = (publication.positions.size - 1).coerceAtLeast(0)
        return matches.map { row ->
            val positionIndex = if (lastPositionIndex == 0) {
                0
            } else {
                (lastPositionIndex * (row.unitIndex.toDouble() / lastIndexedUnit.toDouble())).roundToInt()
            }
            ReaderSearchResult(
                title = row.heading,
                snippet = row.snippet(query),
                locatorJson = publication.positions.getOrNull(positionIndex)?.toJSON()?.toString() ?: row.locator,
                unitIndex = positionIndex
            )
        }
    }

    private fun readAloudChunks(
        publication: OpenPublication,
        rows: List<SearchIndexEntity>,
    ): List<ReadAloudChunk> {
        val chunks = if (rows.isEmpty()) {
            ReadAloudPlanner.chunksFromUnits(publication.units)
        } else {
            ReadAloudPlanner.chunksFromRows(rows)
        }
        return ReadAloudPlanner.pageAlignedChunks(
            chunks = chunks,
            positionLocators = publication.positions.map { it.toJSON().toString() }
        )
    }

    override fun onCleared() {
        searchJob?.cancel()
        flushSession()
        _uiState.value.publication?.close()
        super.onCleared()
    }

    companion object {
        fun factory(bookId: Long, initialLocatorOverride: String?, container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReaderViewModel(bookId, initialLocatorOverride, container) as T
            }
    }
}

private fun SearchIndexEntity.snippet(query: String): String {
    val normalizedBody = body.replace(Regex("\\s+"), " ").trim()
    val firstTerm = query.split(Regex("\\s+")).firstOrNull { it.isNotBlank() }
        ?: return normalizedBody.take(220)
    val index = normalizedBody.indexOf(firstTerm, ignoreCase = true)
    if (index < 0) return normalizedBody.take(220)
    val start = (index - 80).coerceAtLeast(0)
    val end = (index + firstTerm.length + 140).coerceAtMost(normalizedBody.length)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < normalizedBody.length) "..." else ""
    return prefix + normalizedBody.substring(start, end) + suffix
}

private fun String.selectedQuote(): String =
    replace(Regex("\\s+"), " ").trim().take(800)

private data class VisibleReadingTarget(
    val position: ResolvedVisibleReaderPosition,
    val unit: ReadingUnit,
)

private fun VisibleReadingTarget.noteQuote(): String =
    position.readiumLocator?.text?.highlight?.selectedQuote()?.takeIf { it.isNotBlank() }
        ?: unit.body.selectedQuote().takeIf { it.isNotBlank() }
        ?: position.readiumLocator?.title?.takeIf { it.isNotBlank() }
        ?: unit.heading
