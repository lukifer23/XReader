package com.xreader.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xreader.app.AppContainer
import com.xreader.app.analytics.AnalyticsSummary
import com.xreader.app.analytics.ReadingAnalyticsTracker
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookmarkEntity
import com.xreader.app.data.DictionaryEntryEntity
import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.data.SearchIndexEntity
import com.xreader.app.data.ReaderTheme
import com.xreader.app.reader.OpenPublication
import com.xreader.app.reader.ReaderSearchResult
import com.xreader.app.reader.ReadingUnit
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.settings.ReaderTextAlign
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import kotlin.math.roundToInt

enum class LibraryGroup {
    BOOKS,
    AUTHORS,
    SERIES,
    GENRES,
    YEARS,
    RECENT,
    UNREAD,
    IN_PROGRESS,
    FINISHED,
    FAVORITES,
}

data class BookListItem(
    val book: BookEntity,
    val state: ReadingStateEntity?,
)

data class LibraryUiState(
    val query: String = "",
    val group: LibraryGroup = LibraryGroup.BOOKS,
    val books: List<BookListItem> = emptyList(),
    val importing: Boolean = false,
    val message: String? = null,
    val librarySearchResults: List<com.xreader.app.data.SearchIndexEntity> = emptyList(),
)

@SuppressLint("LogNotTimber")
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val group = MutableStateFlow(LibraryGroup.BOOKS)
    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val searchResults = MutableStateFlow<List<com.xreader.app.data.SearchIndexEntity>>(emptyList())

    private val books = query.flatMapLatest { container.libraryRepository.observeBooks(it) }
    private val states = container.readingRepository.observeStates()

    private data class LibraryChromeState(
        val query: String,
        val group: LibraryGroup,
        val importing: Boolean,
        val message: String?,
        val searchResults: List<com.xreader.app.data.SearchIndexEntity>,
    )

    private val chromeState = combine(query, group, importing, message, searchResults) {
            currentQuery,
            currentGroup,
            currentImporting,
            currentMessage,
            currentResults ->
        LibraryChromeState(currentQuery, currentGroup, currentImporting, currentMessage, currentResults)
    }

    private val bookItems = combine(books, states) { currentBooks, currentStates ->
        val statesByBook = currentStates.associateBy { it.bookId }
        currentBooks.map { BookListItem(it, statesByBook[it.id]) }
    }

    val uiState: StateFlow<LibraryUiState> =
        combine(chromeState, bookItems) { chrome, items ->
            LibraryUiState(
                query = chrome.query,
                group = chrome.group,
                books = items.filteredBy(chrome.group),
                importing = chrome.importing,
                message = chrome.message,
                librarySearchResults = chrome.searchResults
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) {
        val previous = query.value.trim()
        query.value = value
        if (value.trim() != previous) searchResults.value = emptyList()
    }

    fun setGroup(value: LibraryGroup) {
        group.value = value
    }

    fun clearMessage() {
        message.value = null
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            importing.value = true
            try {
                runCatching { container.libraryRepository.import(uri) }
                    .onSuccess { result ->
                        message.value = if (result.duplicate) "Already in library" else "Imported book"
                    }
                    .onFailure { error ->
                        Log.e("XReader", "Import failed for $uri", error)
                        message.value = error.message ?: "Import failed"
                    }
            } finally {
                importing.value = false
            }
        }
    }

    fun searchLibrary() {
        val value = query.value.trim()
        if (value.isBlank()) {
            searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            searchResults.value = container.libraryRepository.searchLibrary(value)
        }
    }

    fun toggleFavorite(item: BookListItem) {
        viewModelScope.launch {
            container.libraryRepository.setFavorite(item.book.id, !item.book.favorite)
        }
    }

    fun updateMetadata(
        book: BookEntity,
        title: String,
        author: String,
        year: Int?,
        genre: String?,
        series: String?,
        seriesIndex: Double?,
    ) {
        viewModelScope.launch {
            container.libraryRepository.updateMetadata(book, title, author, year, genre, series, seriesIndex)
            message.value = "Updated metadata"
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            container.libraryRepository.deleteBook(book)
            message.value = "Removed book"
        }
    }

    private fun List<BookListItem>.filteredBy(group: LibraryGroup): List<BookListItem> =
        when (group) {
            LibraryGroup.RECENT -> sortedByDescending { it.book.lastOpenedAt ?: it.book.importedAt }
            LibraryGroup.UNREAD -> filter { (it.state?.progress ?: 0.0) <= 0.01 }
            LibraryGroup.IN_PROGRESS -> filter { (it.state?.progress ?: 0.0) in 0.01..0.994 }
            LibraryGroup.FINISHED -> filter { it.book.finished || (it.state?.progress ?: 0.0) >= 0.995 }
            LibraryGroup.FAVORITES -> filter { it.book.favorite }
            else -> this
        }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(container) as T
            }
    }
}

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val book: BookEntity? = null,
    val publication: OpenPublication? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val state: ReadingStateEntity? = null,
    val annotations: List<AnnotationEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
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

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
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
        viewModelScope.launch {
            runCatching {
                val book = requireNotNull(container.libraryRepository.getBook(bookId)) { "Book not found" }
                container.libraryRepository.markOpened(bookId)
                val publication = container.publicationService.open(book)
                val units = publication.units
                val saved = container.readingRepository.getState(bookId)
                val requestedLocator = initialLocatorOverride ?: saved?.locator
                val requestedReadiumLocator = requestedLocator?.toReadiumLocatorOrNull()
                val requestedSearchUnit = requestedLocator?.searchUnitIndexOrNull()
                val initialUnit = requestedReadiumLocator
                    ?.let(publication::positionIndexFor)
                    ?: requestedSearchUnit?.let { searchUnit ->
                        val maxIndexedUnit = container.libraryRepository.maxIndexedUnitForBook(bookId).coerceAtLeast(1)
                        val lastPositionIndex = publication.positions.lastIndex.coerceAtLeast(0)
                        if (lastPositionIndex == 0) {
                            0
                        } else {
                            (lastPositionIndex * (searchUnit.coerceAtLeast(0).toDouble() / maxIndexedUnit.toDouble())).roundToInt()
                        }
                    }
                    ?: requestedLocator?.let { locatorToUnit(it, units) }
                    ?: saved?.currentUnit
                    ?: 0
                val boundedInitialUnit = initialUnit.coerceIn(0, (units.size - 1).coerceAtLeast(0))
                val initialLocator = requestedReadiumLocator?.toJSON()?.toString()
                    ?: publication.positions.getOrNull(boundedInitialUnit)?.toJSON()?.toString()
                    ?: requestedLocator
                val activeTracker = createTracker(publication)
                tracker = activeTracker
                val jumpState = if (initialLocatorOverride != null && initialLocator != null) {
                    activeTracker.record(
                        unit = boundedInitialUnit,
                        locator = initialLocator,
                        progressOverride = if (units.size <= 1) {
                            1.0
                        } else {
                            boundedInitialUnit.toDouble() / (units.size - 1).toDouble()
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
                        currentUnit = boundedInitialUnit,
                        initialLocatorJson = initialLocator,
                        state = jumpState ?: it.state
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(loading = false, error = error.message ?: "Could not open book") }
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

data class AnalyticsUiState(
    val summary: AnalyticsSummary? = null,
)

class AnalyticsViewModel(container: AppContainer) : ViewModel() {
    val uiState: StateFlow<AnalyticsUiState> =
        container.analyticsRepository.observeSummary()
            .combine(MutableStateFlow(Unit)) { summary, _ -> AnalyticsUiState(summary) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AnalyticsViewModel(container) as T
            }
    }
}

data class SettingsMaintenanceUiState(
    val repairingLibrary: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<ReaderSettings> =
        container.settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    private val _maintenance = MutableStateFlow(SettingsMaintenanceUiState())
    val maintenance: StateFlow<SettingsMaintenanceUiState> = _maintenance

    fun setTheme(theme: com.xreader.app.data.ReaderTheme) {
        viewModelScope.launch { container.settingsRepository.setTheme(theme) }
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

    fun setTapZonesEnabled(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setTapZonesEnabled(value) }
    }

    fun setPageTurnAnimations(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setPageTurnAnimations(value) }
    }

    fun setFullScreen(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setFullScreen(value) }
    }

    fun setPublisherStyles(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setPublisherStyles(value) }
    }

    fun setTextAlign(value: ReaderTextAlign) {
        viewModelScope.launch { container.settingsRepository.setTextAlign(value) }
    }

    fun setPdfFit(value: ReaderPdfFit) {
        viewModelScope.launch { container.settingsRepository.setPdfFit(value) }
    }

    fun toggleLightDark() {
        val next = when (settings.value.theme) {
            ReaderTheme.LIGHT, ReaderTheme.SEPIA -> ReaderTheme.DARK
            ReaderTheme.DARK, ReaderTheme.OLED -> ReaderTheme.LIGHT
        }
        viewModelScope.launch { container.settingsRepository.setTheme(next) }
    }

    fun repairLibrary() {
        if (_maintenance.value.repairingLibrary) return
        viewModelScope.launch {
            _maintenance.value = SettingsMaintenanceUiState(repairingLibrary = true)
            val message = runCatching { container.libraryRepository.repairLibrary() }
                .fold(
                    onSuccess = { it.summaryMessage() },
                    onFailure = { it.message ?: "Library repair failed" }
                )
            _maintenance.value = SettingsMaintenanceUiState(message = message)
        }
    }

    fun clearMaintenanceMessage() {
        _maintenance.update { it.copy(message = null) }
    }

    private fun com.xreader.app.importer.ImportService.LibraryRepairResult.summaryMessage(): String {
        val details = buildList {
            if (coversUpdated > 0) add("$coversUpdated covers")
            if (metadataUpdated > 0) add("$metadataUpdated metadata updates")
            if (failed > 0) add("$failed failed")
        }
        val base = "Repaired $scanned ${if (scanned == 1) "book" else "books"}; rebuilt $searchRows search rows"
        return if (details.isEmpty()) base else "$base; ${details.joinToString(", ")}"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(container) as T
            }
    }
}

data class NoteListItem(
    val annotation: AnnotationEntity,
    val book: BookEntity?,
)

data class NotesUiState(
    val query: String = "",
    val kind: AnnotationKind? = null,
    val notes: List<NoteListItem> = emptyList(),
)

class NotesViewModel(container: AppContainer) : ViewModel() {
    private val annotationRepository = container.annotationRepository
    private val query = MutableStateFlow("")
    private val kind = MutableStateFlow<AnnotationKind?>(null)

    val uiState: StateFlow<NotesUiState> =
        container.annotationRepository.observeAllAnnotations()
            .combine(container.libraryRepository.observeBooks("")) { annotations, books ->
                annotations to books.associateBy { it.id }
            }
            .combine(query) { (annotations, booksById), currentQuery ->
                Triple(annotations, booksById, currentQuery.trim())
            }
            .combine(kind) { (annotations, booksById, currentQuery), currentKind ->
                val filtered = annotations
                    .asSequence()
                    .filter { currentKind == null || it.kind == currentKind }
                    .filter { annotation ->
                        if (currentQuery.isBlank()) {
                            true
                        } else {
                            val haystack = listOf(
                                annotation.quote,
                                annotation.note,
                                annotation.tags,
                                booksById[annotation.bookId]?.title.orEmpty(),
                                booksById[annotation.bookId]?.author.orEmpty()
                            ).joinToString(" ")
                            haystack.contains(currentQuery, ignoreCase = true)
                        }
                    }
                    .map { NoteListItem(it, booksById[it.bookId]) }
                    .toList()
                NotesUiState(query = currentQuery, kind = currentKind, notes = filtered)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setKind(value: AnnotationKind?) {
        kind.value = value
    }

    fun updateNote(annotation: AnnotationEntity, note: String) {
        viewModelScope.launch {
            annotationRepository.updateNote(annotation, note)
        }
    }

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch {
            annotationRepository.deleteAnnotation(id)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NotesViewModel(container) as T
            }
    }
}

fun OpenPublication.positionIndexFor(locator: Locator): Int {
    locator.locations.position?.let { position ->
        if (position > 0) return position - 1
    }
    val href = locator.href.toString()
    val exact = positions.indexOfFirst { it.href.toString() == href && it.locations.fragments == locator.locations.fragments }
    if (exact >= 0) return exact
    locator.locations.totalProgression?.let { progression ->
        if (positions.isNotEmpty()) return ((positions.size - 1).coerceAtLeast(0) * progression).toInt()
    }
    val byHref = positions.indexOfFirst { it.href.toString() == href }
    if (byHref >= 0) return byHref
    locator.locations.progression?.let { progression ->
        if (positions.isNotEmpty()) return ((positions.size - 1).coerceAtLeast(0) * progression).toInt()
    }
    val unit = units.indexOfFirst { it.locator == locator.toJSON().toString() }
    return unit.takeIf { it >= 0 } ?: 0
}

fun locatorToUnit(locator: String, units: List<ReadingUnit>): Int =
    units.indexOfFirst { it.locator == locator }.takeIf { it >= 0 }
        ?: locator.substringAfter(':', "").substringBefore(':').toIntOrNull()
        ?: 0

internal const val SEARCH_UNIT_LOCATOR_PREFIX = "xreader-search:"

internal fun String.searchUnitIndexOrNull(): Int? =
    takeIf { it.startsWith(SEARCH_UNIT_LOCATOR_PREFIX) }
        ?.removePrefix(SEARCH_UNIT_LOCATOR_PREFIX)
        ?.toIntOrNull()

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

private fun String.toReadiumLocatorOrNull(): Locator? {
    if (isBlank() || !trimStart().startsWith("{")) return null
    return runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()
}
