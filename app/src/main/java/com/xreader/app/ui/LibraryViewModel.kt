package com.xreader.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xreader.app.AppContainer
import com.xreader.app.data.BookEntity
import com.xreader.app.data.CollectionEntity
import com.xreader.app.data.LibrarySearchRow
import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.importer.ImportService
import com.xreader.app.opds.OpdsEntry
import com.xreader.app.opds.OpdsFeed
import com.xreader.app.opds.OpdsLink
import com.xreader.app.settings.LibraryDensity
import com.xreader.app.settings.LibrarySort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class LibraryGroup {
    BOOKS,
    AUTHORS,
    SERIES,
    GENRES,
    YEARS,
    COLLECTIONS,
    RECENT,
    UNREAD,
    IN_PROGRESS,
    FINISHED,
    FAVORITES,
}

data class BookListItem(
    val book: BookEntity,
    val state: ReadingStateEntity?,
    val collections: List<CollectionUiItem> = emptyList(),
)

data class CollectionUiItem(
    val id: Long,
    val name: String,
)

data class BookHealthUiState(
    val fileAvailable: Boolean,
    val coverAvailable: Boolean,
    val searchRows: Int,
)

data class LibraryMessage(
    val id: Long,
    val text: String,
    val actionLabel: String? = null,
    val openBookId: Long? = null,
    val undoRemoveBookId: Long? = null,
)

data class LibraryUiState(
    val query: String = "",
    val group: LibraryGroup = LibraryGroup.BOOKS,
    val sort: LibrarySort = LibrarySort.RECENT,
    val density: LibraryDensity = LibraryDensity.COMFORTABLE,
    val books: List<BookListItem> = emptyList(),
    val allBooks: List<BookListItem> = emptyList(),
    val collections: List<CollectionUiItem> = emptyList(),
    val matchedBookCount: Int = 0,
    val totalBookCount: Int = 0,
    val importing: Boolean = false,
    val message: LibraryMessage? = null,
    val librarySearchResults: List<LibrarySearchRow> = emptyList(),
    val bookHealth: Map<Long, BookHealthUiState> = emptyMap(),
    val repairingBookIds: Set<Long> = emptySet(),
    val authorOptions: List<String> = emptyList(),
    val genreOptions: List<String> = emptyList(),
    val seriesOptions: List<String> = emptyList(),
    val opdsCatalog: OpdsCatalogUiState = OpdsCatalogUiState(),
)

data class OpdsCatalogUiState(
    val url: String = "",
    val loading: Boolean = false,
    val importingLink: String? = null,
    val feed: OpdsFeed? = null,
    val error: String? = null,
)

@SuppressLint("LogNotTimber")
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val group = MutableStateFlow(LibraryGroup.BOOKS)
    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<LibraryMessage?>(null)
    private var nextMessageId = 0L
    private val searchResults = MutableStateFlow<List<LibrarySearchRow>>(emptyList())
    private val bookHealth = MutableStateFlow<Map<Long, BookHealthUiState>>(emptyMap())
    private val repairingBookIds = MutableStateFlow<Set<Long>>(emptySet())
    private val opdsCatalog = MutableStateFlow(OpdsCatalogUiState())
    private val pendingRemovalIds = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingRemovalBooks = linkedMapOf<Long, BookEntity>()
    private val pendingRemovalJobs = mutableMapOf<Long, Job>()

    private val queriedBooks = query.flatMapLatest { container.libraryRepository.observeBooks(it) }
    private val allBooks = container.libraryRepository.observeBooks("")
    private val states = container.readingRepository.observeStates()
    private val collections = container.libraryRepository.observeCollections()
    private val bookCollectionNames = container.libraryRepository.observeBookCollectionNames()
    private val metadataOptions = combine(
        container.libraryRepository.observeAuthors(),
        container.libraryRepository.observeGenres(),
        container.libraryRepository.observeSeries()
    ) { authors, genres, series ->
        LibraryMetadataOptionsState(
            authorOptions = authors,
            genreOptions = genres,
            seriesOptions = series
        )
    }

    private data class LibrarySelectionState(
        val query: String,
        val group: LibraryGroup,
        val sort: LibrarySort,
        val density: LibraryDensity,
    )

    private data class LibraryChromeState(
        val selection: LibrarySelectionState,
        val importing: Boolean,
        val message: LibraryMessage?,
        val searchResults: List<LibrarySearchRow>,
        val opdsCatalog: OpdsCatalogUiState,
    )

    private val selectionState = combine(query, group, container.settingsRepository.librarySettings) {
            currentQuery,
            currentGroup,
            librarySettings ->
        LibrarySelectionState(currentQuery, currentGroup, librarySettings.sort, librarySettings.density)
    }

    private val chromeState = combine(selectionState, importing, message, searchResults, opdsCatalog) {
            selection,
            currentImporting,
            currentMessage,
            currentResults,
            currentCatalog ->
        LibraryChromeState(selection, currentImporting, currentMessage, currentResults, currentCatalog)
    }

    private data class LibraryBooksState(
        val queriedItems: List<BookListItem>,
        val allItems: List<BookListItem>,
    )

    private data class LibraryMetadataOptionsState(
        val authorOptions: List<String>,
        val genreOptions: List<String>,
        val seriesOptions: List<String>,
    )

    private data class LibrarySupportState(
        val collections: List<CollectionEntity>,
        val bookHealth: Map<Long, BookHealthUiState>,
        val repairingBookIds: Set<Long>,
        val metadataOptions: LibraryMetadataOptionsState,
    )

    private val bookItems = combine(
        queriedBooks,
        allBooks,
        states,
        bookCollectionNames,
        pendingRemovalIds
    ) { currentBooks, currentAllBooks, currentStates, currentBookCollections, removingIds ->
        val statesByBook = currentStates.associateBy { it.bookId }
        val collectionsByBook = currentBookCollections
            .groupBy { it.bookId }
            .mapValues { (_, rows) ->
                rows
                    .distinctBy { it.collectionId }
                    .map { CollectionUiItem(id = it.collectionId, name = it.name) }
            }
        val visibleBooks = currentBooks.withoutPendingRemovalIds(removingIds)
        val visibleAllBooks = currentAllBooks.withoutPendingRemovalIds(removingIds)
        LibraryBooksState(
            queriedItems = visibleBooks.map { BookListItem(it, statesByBook[it.id], collectionsByBook[it.id].orEmpty()) },
            allItems = visibleAllBooks.map { BookListItem(it, statesByBook[it.id], collectionsByBook[it.id].orEmpty()) }
        )
    }

    private val supportState = combine(
        collections,
        bookHealth,
        repairingBookIds,
        metadataOptions
    ) { currentCollections, health, repairing, options ->
        LibrarySupportState(
            collections = currentCollections,
            bookHealth = health,
            repairingBookIds = repairing,
            metadataOptions = options
        )
    }

    val uiState: StateFlow<LibraryUiState> =
        combine(chromeState, bookItems, supportState) { chrome, libraryBooks, support ->
            val selection = chrome.selection
            val visibleBooks = libraryBooks.queriedItems.filteredBy(selection.group).sortedForLibrary(selection.sort)
            LibraryUiState(
                query = selection.query,
                group = selection.group,
                sort = selection.sort,
                density = selection.density,
                books = visibleBooks,
                allBooks = libraryBooks.allItems,
                collections = support.collections.toUiItems(),
                matchedBookCount = libraryBooks.queriedItems.size,
                totalBookCount = libraryBooks.allItems.size,
                importing = chrome.importing,
                message = chrome.message,
                librarySearchResults = chrome.searchResults,
                bookHealth = support.bookHealth,
                repairingBookIds = support.repairingBookIds,
                authorOptions = support.metadataOptions.authorOptions,
                genreOptions = support.metadataOptions.genreOptions,
                seriesOptions = support.metadataOptions.seriesOptions,
                opdsCatalog = chrome.opdsCatalog
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

    fun setSort(value: LibrarySort) {
        viewModelScope.launch {
            container.settingsRepository.setLibrarySort(value)
        }
    }

    fun toggleDensity() {
        val next = if (uiState.value.density == LibraryDensity.COMFORTABLE) {
            LibraryDensity.COMPACT
        } else {
            LibraryDensity.COMFORTABLE
        }
        viewModelScope.launch {
            container.settingsRepository.setLibraryDensity(next)
        }
    }

    fun clearMessage() {
        message.value = null
    }

    private fun postMessage(
        text: String,
        actionLabel: String? = null,
        openBookId: Long? = null,
        undoRemoveBookId: Long? = null,
    ) {
        nextMessageId += 1
        message.value = LibraryMessage(
            id = nextMessageId,
            text = text,
            actionLabel = actionLabel,
            openBookId = openBookId,
            undoRemoveBookId = undoRemoveBookId
        )
    }

    fun import(uri: Uri) {
        importFiles(listOf(uri))
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            try {
                runCatching { container.libraryRepository.importMany(uris) }
                    .onSuccess { result ->
                        postMessage(
                            text = result.summaryMessage(),
                            actionLabel = result.openActionLabel(),
                            openBookId = result.primaryBookId
                        )
                    }
                    .onFailure { error ->
                        Log.e("XReader", "Import failed for ${uris.size} selected files", error)
                        postMessage(error.message ?: "Import failed")
                    }
            } finally {
                importing.value = false
            }
        }
    }

    fun importFolder(uri: Uri) {
        viewModelScope.launch {
            importing.value = true
            try {
                runCatching { container.libraryRepository.importFolder(uri) }
                    .onSuccess { result ->
                        postMessage(
                            text = result.summaryMessage(),
                            actionLabel = result.openActionLabel(),
                            openBookId = result.primaryBookId
                        )
                    }
                    .onFailure { error ->
                        Log.e("XReader", "Folder import failed for $uri", error)
                        postMessage(error.message ?: "Folder import failed")
                    }
            } finally {
                importing.value = false
            }
        }
    }

    fun setOpdsCatalogUrl(value: String) {
        opdsCatalog.value = opdsCatalog.value.copy(url = value, error = null)
    }

    fun loadOpdsCatalog(url: String = opdsCatalog.value.url) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            opdsCatalog.value = opdsCatalog.value.copy(error = "Enter a catalog URL.")
            return
        }
        viewModelScope.launch {
            opdsCatalog.value = opdsCatalog.value.copy(url = trimmed, loading = true, error = null)
            runCatching { container.opdsCatalogService.load(trimmed) }
                .onSuccess { feed ->
                    opdsCatalog.value = opdsCatalog.value.copy(
                        url = feed.url,
                        loading = false,
                        feed = feed,
                        error = null
                    )
                }
                .onFailure { error ->
                    Log.e("XReader", "OPDS catalog load failed for $trimmed", error)
                    opdsCatalog.value = opdsCatalog.value.copy(
                        loading = false,
                        error = error.message ?: "Catalog load failed"
                    )
                }
        }
    }

    fun openOpdsCatalogLink(link: OpdsLink) {
        setOpdsCatalogUrl(link.href)
        loadOpdsCatalog(link.href)
    }

    fun importOpdsEntry(entry: OpdsEntry) {
        val link = entry.acquisitionLinks.preferredCatalogDownload()
        if (link == null) {
            opdsCatalog.value = opdsCatalog.value.copy(error = "No supported download link for this book.")
            return
        }
        importOpdsLink(link)
    }

    private fun importOpdsLink(link: OpdsLink) {
        viewModelScope.launch {
            importing.value = true
            opdsCatalog.value = opdsCatalog.value.copy(importingLink = link.href, error = null)
            try {
                runCatching { container.opdsCatalogService.importLink(link) }
                    .onSuccess { result ->
                        postMessage(
                            text = result.summaryMessage(),
                            actionLabel = "Open",
                            openBookId = result.bookId
                        )
                    }
                    .onFailure { error ->
                        Log.e("XReader", "OPDS import failed for ${link.href}", error)
                        opdsCatalog.value = opdsCatalog.value.copy(error = error.message ?: "Catalog import failed")
                    }
            } finally {
                importing.value = false
                opdsCatalog.value = opdsCatalog.value.copy(importingLink = null)
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

    fun setFinished(item: BookListItem, finished: Boolean) {
        viewModelScope.launch {
            container.libraryRepository.setFinished(item.book.id, finished)
            postMessage(when {
                finished -> "Marked finished"
                item.rawLibraryProgress() >= 0.995 -> "Removed manual finish mark; progress is still complete"
                else -> "Marked not finished"
            })
        }
    }

    fun addToCollection(item: BookListItem, name: String) {
        viewModelScope.launch {
            runCatching { container.libraryRepository.addBookToCollection(item.book.id, name) }
                .onSuccess { result ->
                    postMessage(if (result.changed) {
                        "Added to ${result.collectionName}"
                    } else {
                        "Already in ${result.collectionName}"
                    })
                }
                .onFailure { error ->
                    Log.e("XReader", "Add to collection failed for ${item.book.id}", error)
                    postMessage(error.message ?: "Could not update collection")
                }
        }
    }

    fun removeFromCollection(item: BookListItem, collection: CollectionUiItem) {
        viewModelScope.launch {
            runCatching { container.libraryRepository.removeBookFromCollection(item.book.id, collection.id) }
                .onSuccess { result ->
                    postMessage(if (result.changed) {
                        "Removed from ${result.collectionName}"
                    } else {
                        "Collection already updated"
                    })
                }
                .onFailure { error ->
                    Log.e("XReader", "Remove from collection failed for ${item.book.id}", error)
                    postMessage(error.message ?: "Could not update collection")
                }
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
        applyToSeries: Boolean,
    ) {
        viewModelScope.launch {
            val result = container.libraryRepository.updateMetadata(
                book = book,
                title = title,
                author = author,
                year = year,
                genre = genre,
                series = series,
                seriesIndex = seriesIndex,
                applyToSeries = applyToSeries
            )
            postMessage(when {
                result.updatedBooks > 1 -> "Updated metadata for ${result.updatedBooks} books"
                result.updatedBooks == 1 -> "Updated metadata"
                else -> "Metadata already up to date"
            })
        }
    }

    fun replaceCover(book: BookEntity, uri: Uri) {
        viewModelScope.launch {
            runCatching { container.libraryRepository.replaceCover(book, uri) }
                .onSuccess {
                    refreshBookHealth(book.id)
                    postMessage("Updated cover")
                }
                .onFailure { error ->
                    Log.e("XReader", "Cover replacement failed for ${book.id}", error)
                    postMessage(error.message ?: "Cover update failed")
                }
        }
    }

    fun exportBook(book: BookEntity, uri: Uri) {
        viewModelScope.launch {
            runCatching { container.libraryRepository.exportBook(book, uri) }
                .onSuccess { bytes ->
                    postMessage("Saved ${book.title} (${bytes.toReadableFileSize()})")
                }
                .onFailure { error ->
                    Log.e("XReader", "Book export failed for ${book.id}", error)
                    postMessage(error.message ?: "Book export failed")
                }
        }
    }

    fun refreshBookHealth(bookId: Long) {
        viewModelScope.launch {
            runCatching { container.libraryRepository.bookHealth(bookId) }
                .onSuccess { health ->
                    bookHealth.value = bookHealth.value + (
                        bookId to BookHealthUiState(
                            fileAvailable = health.fileAvailable,
                            coverAvailable = health.coverAvailable,
                            searchRows = health.searchRows
                        )
                    )
                }
                .onFailure { error ->
                    Log.e("XReader", "Book health check failed for $bookId", error)
                    postMessage(error.message ?: "Book health check failed")
                }
        }
    }

    fun repairBook(book: BookEntity) {
        if (book.id in repairingBookIds.value) return
        viewModelScope.launch {
            repairingBookIds.value = repairingBookIds.value + book.id
            val result = runCatching { container.libraryRepository.repairBook(book.id) }
                .onSuccess { refreshBookHealth(book.id) }
                .getOrElse { error ->
                    Log.e("XReader", "Book repair failed for ${book.id}", error)
                    postMessage(error.message ?: "Book repair failed")
                    repairingBookIds.value = repairingBookIds.value - book.id
                    return@launch
                }
            postMessage(result.summaryMessage(book))
            repairingBookIds.value = repairingBookIds.value - book.id
        }
    }

    fun deleteBook(book: BookEntity) {
        if (book.id in pendingRemovalIds.value) return
        pendingRemovalBooks[book.id] = book
        pendingRemovalIds.update { it + book.id }
        searchResults.update { results -> results.filterNot { it.row.bookId == book.id } }
        pendingRemovalJobs.remove(book.id)?.cancel()
        pendingRemovalJobs[book.id] = viewModelScope.launch {
            delay(UNDO_REMOVE_TIMEOUT_MS)
            commitPendingBookRemoval(book.id, cancelJob = false)
        }
        postMessage(
            text = "Removed ${book.title}",
            actionLabel = "Undo",
            undoRemoveBookId = book.id
        )
    }

    fun undoPendingBookRemoval(bookId: Long) {
        val book = pendingRemovalBooks.remove(bookId) ?: return
        pendingRemovalJobs.remove(bookId)?.cancel()
        pendingRemovalIds.update { it - bookId }
        postMessage("Restored ${book.title}")
    }

    fun finalizePendingBookRemoval(bookId: Long) {
        commitPendingBookRemoval(bookId)
    }

    private fun commitPendingBookRemoval(bookId: Long, cancelJob: Boolean = true) {
        val book = pendingRemovalBooks.remove(bookId) ?: return
        val job = pendingRemovalJobs.remove(bookId)
        if (cancelJob) job?.cancel()
        pendingRemovalIds.update { it - bookId }
        viewModelScope.launch {
            runCatching { container.libraryRepository.deleteBook(book) }
                .onFailure { error ->
                    Log.e("XReader", "Book removal failed for ${book.id}", error)
                    postMessage(error.message ?: "Could not remove ${book.title}")
                }
        }
    }

    override fun onCleared() {
        pendingRemovalJobs.values.forEach { it.cancel() }
        val removals = pendingRemovalBooks.values.toList()
        pendingRemovalBooks.clear()
        pendingRemovalIds.value = emptySet()
        removals.forEach { book ->
            container.applicationScope.launch {
                runCatching { container.libraryRepository.deleteBook(book) }
                    .onFailure { error -> Log.e("XReader", "Book removal failed for ${book.id}", error) }
            }
        }
        super.onCleared()
    }

    private fun List<BookListItem>.filteredBy(group: LibraryGroup): List<BookListItem> =
        when (group) {
            LibraryGroup.RECENT -> sortedByDescending { it.book.lastOpenedAt ?: it.book.importedAt }
            LibraryGroup.UNREAD -> filter { it.isLibraryUnread() }
            LibraryGroup.IN_PROGRESS -> filter { it.isLibraryInProgress() }
            LibraryGroup.FINISHED -> filter { it.isLibraryFinished() }
            LibraryGroup.FAVORITES -> filter { it.book.favorite }
            LibraryGroup.COLLECTIONS -> filter { it.collections.isNotEmpty() }
            else -> this
        }

    private fun com.xreader.app.importer.ImportService.BookRepairResult.summaryMessage(book: BookEntity): String {
        if (failed) return "Could not repair ${book.title}"
        val details = buildList {
            if (coverUpdated) add("cover")
            if (metadataUpdated) add("metadata")
        }
        val base = "Repaired ${book.title}; rebuilt $searchRows search rows"
        return if (details.isEmpty()) base else "$base; updated ${details.joinToString(", ")}"
    }

    private fun com.xreader.app.importer.ImportService.ImportBatchResult.openActionLabel(): String? =
        if (primaryBookId != null) "Open" else null

    private fun ImportService.ImportResult.summaryMessage(): String =
        when {
            duplicate -> "Already in library"
            recovered -> "Restored book"
            else -> "Imported book"
        }

    private fun com.xreader.app.importer.ImportService.ImportBatchResult.summaryMessage(): String {
        if (scanned == 0) return "No supported book files found"
        if (imported == 1 && duplicates == 0 && unsupported == 0 && failed == 0) return "Imported book"
        if (recovered == 1 && imported == 0 && duplicates == 0 && unsupported == 0 && failed == 0) {
            return "Restored book file"
        }
        if (imported == 0 && recovered == 0 && duplicates == 1 && unsupported == 0 && failed == 0) {
            return "Already in library"
        }
        val parts = buildList {
            if (imported > 0) add("Imported ${bookCount(imported)}")
            if (recovered > 0) add("Restored ${bookCount(recovered)}")
            if (duplicates > 0) add("${bookCount(duplicates)} already in library")
            if (unsupported > 0) add("$unsupported unsupported")
            if (failed > 0) add("$failed failed")
        }
        return parts.ifEmpty { listOf("No books imported") }.joinToString("; ")
    }

    private fun bookCount(count: Int): String =
        if (count == 1) "1 book" else "$count books"

    private fun Long.toReadableFileSize(): String =
        when {
            this >= 1_048_576L -> "%.1f MB".format(Locale.US, this / 1_048_576.0)
            this >= 1024L -> "%.1f KB".format(Locale.US, this / 1024.0)
            else -> "$this B"
        }

    private fun List<CollectionEntity>.toUiItems(): List<CollectionUiItem> =
        map { CollectionUiItem(id = it.id, name = it.name) }

    companion object {
        private const val UNDO_REMOVE_TIMEOUT_MS = 8_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(container) as T
            }
    }
}

internal fun List<BookEntity>.withoutPendingRemovalIds(pendingIds: Set<Long>): List<BookEntity> =
    if (pendingIds.isEmpty()) this else filterNot { it.id in pendingIds }

private fun List<OpdsLink>.preferredCatalogDownload(): OpdsLink? =
    minWithOrNull(
        compareBy<OpdsLink> { link ->
            when (link.type?.substringBefore(';')?.lowercase(Locale.US)) {
                "application/epub+zip" -> 0
                "application/pdf" -> 1
                else -> 2
            }
        }.thenBy { it.displayTitle.lowercase(Locale.US) }
    )
