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
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.settings.ReaderTextAlign
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val state: ReadingStateEntity? = null,
    val annotations: List<AnnotationEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val tableOfContents: List<ReaderNavigationItem> = emptyList(),
    val tableOfContentsLoading: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ReaderSearchResult> = emptyList(),
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
    private var lastReadingState: ReadingStateEntity? = null
    private var ignoreStoredStateUntilFirstLocator = initialLocatorOverride != null
    private var deferredObserversStarted = false

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
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
        viewModelScope.launch { container.settingsRepository.setFontScale(value) }
    }

    fun setLineHeight(value: Float) {
        viewModelScope.launch { container.settingsRepository.setLineHeight(value) }
    }

    fun setMarginScale(value: Float) {
        viewModelScope.launch { container.settingsRepository.setMarginScale(value) }
    }

    fun setFontFamily(value: ReaderFontFamily) {
        viewModelScope.launch { container.settingsRepository.setFontFamily(value) }
    }

    fun setPublisherStyles(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setPublisherStyles(value) }
    }

    fun setPageTurnAnimations(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setPageTurnAnimations(value) }
    }

    fun setTextAlign(value: ReaderTextAlign) {
        viewModelScope.launch { container.settingsRepository.setTextAlign(value) }
    }

    fun setPdfFit(value: ReaderPdfFit) {
        viewModelScope.launch { container.settingsRepository.setPdfFit(value) }
    }

    fun setSearchQuery(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun runSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), searchRunning = false, searchPerformed = false) }
            return
        }
        viewModelScope.launch {
            val publication = _uiState.value.publication as? OpenPublication.Readium ?: return@launch
            _uiState.update { it.copy(searchRunning = true, searchPerformed = false) }
            val readiumResults = withTimeoutOrNull(1_500L) {
                runCatching { container.publicationService.search(publication, query) }
                    .getOrDefault(emptyList())
            }.orEmpty()
            val results = readiumResults.ifEmpty {
                fallbackSearchResults(publication, query)
            }
            _uiState.update {
                it.copy(searchResults = results, searchRunning = false, searchPerformed = true)
            }
        }
    }

    fun clearSearch() {
        _uiState.update {
            it.copy(searchQuery = "", searchResults = emptyList(), searchRunning = false, searchPerformed = false)
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

    fun openNoteDraft() {
        _uiState.update { it.copy(noteDraftOpen = true, pendingNoteLocator = null, pendingNoteQuote = null) }
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

    fun addSelectedHighlight(locator: Locator, quote: String, color: String = "#F2C94C") {
        val cleanQuote = quote.selectedQuote()
        if (cleanQuote.isBlank()) return
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

    fun addNote(note: String) {
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
                note = note.trim()
            )
            closeNoteDraft()
        }
    }

    fun addHighlight(color: String = "#F2C94C") {
        val unit = currentReadingUnit() ?: return
        viewModelScope.launch {
            container.annotationRepository.addHighlight(
                bookId = bookId,
                locator = unit.locator,
                quote = unit.body.take(300).ifBlank { unit.heading },
                color = color
            )
        }
    }

    fun toggleBookmark() {
        val unit = currentReadingUnit() ?: return
        val publication = _uiState.value.publication ?: return
        val total = publication.units.size.coerceAtLeast(1)
        val progress = (_uiState.value.currentUnit + 1).toDouble() / total.toDouble()
        viewModelScope.launch {
            container.annotationRepository.toggleBookmark(
                bookId = bookId,
                locator = unit.locator,
                label = unit.heading.ifBlank { "Position ${_uiState.value.currentUnit + 1}" },
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
                locatorJson = publication.positions.getOrNull(positionIndex)?.toJSON()?.toString() ?: row.locator
            )
        }
    }

    override fun onCleared() {
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
