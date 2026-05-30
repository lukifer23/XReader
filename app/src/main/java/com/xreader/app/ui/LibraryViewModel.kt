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
import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.data.SearchIndexEntity
import com.xreader.app.settings.LibraryDensity
import com.xreader.app.settings.LibrarySort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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
    val message: String? = null,
    val librarySearchResults: List<com.xreader.app.data.SearchIndexEntity> = emptyList(),
    val bookHealth: Map<Long, BookHealthUiState> = emptyMap(),
    val repairingBookIds: Set<Long> = emptySet(),
)

@SuppressLint("LogNotTimber")
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val group = MutableStateFlow(LibraryGroup.BOOKS)
    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val searchResults = MutableStateFlow<List<com.xreader.app.data.SearchIndexEntity>>(emptyList())
    private val bookHealth = MutableStateFlow<Map<Long, BookHealthUiState>>(emptyMap())
    private val repairingBookIds = MutableStateFlow<Set<Long>>(emptySet())

    private val queriedBooks = query.flatMapLatest { container.libraryRepository.observeBooks(it) }
    private val allBooks = container.libraryRepository.observeBooks("")
    private val states = container.readingRepository.observeStates()
    private val collections = container.libraryRepository.observeCollections()
    private val bookCollectionNames = container.libraryRepository.observeBookCollectionNames()

    private data class LibrarySelectionState(
        val query: String,
        val group: LibraryGroup,
        val sort: LibrarySort,
        val density: LibraryDensity,
    )

    private data class LibraryChromeState(
        val selection: LibrarySelectionState,
        val importing: Boolean,
        val message: String?,
        val searchResults: List<com.xreader.app.data.SearchIndexEntity>,
    )

    private val selectionState = combine(query, group, container.settingsRepository.librarySettings) {
            currentQuery,
            currentGroup,
            librarySettings ->
        LibrarySelectionState(currentQuery, currentGroup, librarySettings.sort, librarySettings.density)
    }

    private val chromeState = combine(selectionState, importing, message, searchResults) {
            selection,
            currentImporting,
            currentMessage,
            currentResults ->
        LibraryChromeState(selection, currentImporting, currentMessage, currentResults)
    }

    private data class LibraryBooksState(
        val queriedItems: List<BookListItem>,
        val allItems: List<BookListItem>,
    )

    private val bookItems = combine(
        queriedBooks,
        allBooks,
        states,
        bookCollectionNames
    ) { currentBooks, currentAllBooks, currentStates, currentBookCollections ->
        val statesByBook = currentStates.associateBy { it.bookId }
        val collectionsByBook = currentBookCollections
            .groupBy { it.bookId }
            .mapValues { (_, rows) ->
                rows
                    .distinctBy { it.collectionId }
                    .map { CollectionUiItem(id = it.collectionId, name = it.name) }
            }
        LibraryBooksState(
            queriedItems = currentBooks.map { BookListItem(it, statesByBook[it.id], collectionsByBook[it.id].orEmpty()) },
            allItems = currentAllBooks.map { BookListItem(it, statesByBook[it.id], collectionsByBook[it.id].orEmpty()) }
        )
    }

    val uiState: StateFlow<LibraryUiState> =
        combine(chromeState, bookItems, collections, bookHealth, repairingBookIds) { chrome, libraryBooks, currentCollections, health, repairing ->
            val selection = chrome.selection
            val visibleBooks = libraryBooks.queriedItems.filteredBy(selection.group).sortedForLibrary(selection.sort)
            LibraryUiState(
                query = selection.query,
                group = selection.group,
                sort = selection.sort,
                density = selection.density,
                books = visibleBooks,
                allBooks = libraryBooks.allItems,
                collections = currentCollections.toUiItems(),
                matchedBookCount = libraryBooks.queriedItems.size,
                totalBookCount = libraryBooks.allItems.size,
                importing = chrome.importing,
                message = chrome.message,
                librarySearchResults = chrome.searchResults,
                bookHealth = health,
                repairingBookIds = repairing
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
                        message.value = result.summaryMessage()
                    }
                    .onFailure { error ->
                        Log.e("XReader", "Import failed for ${uris.size} selected files", error)
                        message.value = error.message ?: "Import failed"
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
                        message.value = result.summaryMessage()
                    }
                    .onFailure { error ->
                        Log.e("XReader", "Folder import failed for $uri", error)
                        message.value = error.message ?: "Folder import failed"
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

    fun setFinished(item: BookListItem, finished: Boolean) {
        viewModelScope.launch {
            container.libraryRepository.setFinished(item.book.id, finished)
            message.value = when {
                finished -> "Marked finished"
                item.rawLibraryProgress() >= 0.995 -> "Removed manual finish mark; progress is still complete"
                else -> "Marked not finished"
            }
        }
    }

    fun addToCollection(item: BookListItem, name: String) {
        viewModelScope.launch {
            runCatching { container.libraryRepository.addBookToCollection(item.book.id, name) }
                .onSuccess { result ->
                    message.value = if (result.changed) {
                        "Added to ${result.collectionName}"
                    } else {
                        "Already in ${result.collectionName}"
                    }
                }
                .onFailure { error ->
                    Log.e("XReader", "Add to collection failed for ${item.book.id}", error)
                    message.value = error.message ?: "Could not update collection"
                }
        }
    }

    fun removeFromCollection(item: BookListItem, collection: CollectionUiItem) {
        viewModelScope.launch {
            runCatching { container.libraryRepository.removeBookFromCollection(item.book.id, collection.id) }
                .onSuccess { result ->
                    message.value = if (result.changed) {
                        "Removed from ${result.collectionName}"
                    } else {
                        "Collection already updated"
                    }
                }
                .onFailure { error ->
                    Log.e("XReader", "Remove from collection failed for ${item.book.id}", error)
                    message.value = error.message ?: "Could not update collection"
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
            message.value = when {
                result.updatedBooks > 1 -> "Updated metadata for ${result.updatedBooks} books"
                result.updatedBooks == 1 -> "Updated metadata"
                else -> "Metadata already up to date"
            }
        }
    }

    fun replaceCover(book: BookEntity, uri: Uri) {
        viewModelScope.launch {
            runCatching { container.libraryRepository.replaceCover(book, uri) }
                .onSuccess {
                    refreshBookHealth(book.id)
                    message.value = "Updated cover"
                }
                .onFailure { error ->
                    Log.e("XReader", "Cover replacement failed for ${book.id}", error)
                    message.value = error.message ?: "Cover update failed"
                }
        }
    }

    fun exportBook(book: BookEntity, uri: Uri) {
        viewModelScope.launch {
            runCatching { container.libraryRepository.exportBook(book, uri) }
                .onSuccess { bytes ->
                    message.value = "Saved ${book.title} (${bytes.toReadableFileSize()})"
                }
                .onFailure { error ->
                    Log.e("XReader", "Book export failed for ${book.id}", error)
                    message.value = error.message ?: "Book export failed"
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
                    message.value = error.message ?: "Book health check failed"
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
                    message.value = error.message ?: "Book repair failed"
                    repairingBookIds.value = repairingBookIds.value - book.id
                    return@launch
                }
            message.value = result.summaryMessage(book)
            repairingBookIds.value = repairingBookIds.value - book.id
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

    private fun com.xreader.app.importer.ImportService.ImportBatchResult.summaryMessage(): String {
        if (scanned == 0) return "No EPUB, PDF, TXT, CBZ, FB2, RTF, or ODT files found"
        if (imported == 1 && duplicates == 0 && unsupported == 0 && failed == 0) return "Imported book"
        if (imported == 0 && duplicates == 1 && unsupported == 0 && failed == 0) return "Already in library"
        val parts = buildList {
            if (imported > 0) add("Imported ${bookCount(imported)}")
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
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(container) as T
            }
    }
}
