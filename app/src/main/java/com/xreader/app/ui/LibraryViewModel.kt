package com.xreader.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xreader.app.AppContainer
import com.xreader.app.data.BookEntity
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
    val sort: LibrarySort = LibrarySort.RECENT,
    val density: LibraryDensity = LibraryDensity.COMFORTABLE,
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

    private val bookItems = combine(books, states) { currentBooks, currentStates ->
        val statesByBook = currentStates.associateBy { it.bookId }
        currentBooks.map { BookListItem(it, statesByBook[it.id]) }
    }

    val uiState: StateFlow<LibraryUiState> =
        combine(chromeState, bookItems) { chrome, items ->
            val selection = chrome.selection
            LibraryUiState(
                query = selection.query,
                group = selection.group,
                sort = selection.sort,
                density = selection.density,
                books = items.filteredBy(selection.group).sortedFor(selection.sort),
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

    private fun List<BookListItem>.sortedFor(sort: LibrarySort): List<BookListItem> =
        when (sort) {
            LibrarySort.RECENT -> sortedWith(
                compareByDescending<BookListItem> { it.recentTimestamp() }
                    .thenBy { it.book.sortTitle.lowercase() }
            )
            LibrarySort.TITLE -> sortedWith(
                compareBy<BookListItem> { it.book.sortTitle.lowercase() }
                    .thenBy { it.book.author.lowercase() }
            )
            LibrarySort.AUTHOR -> sortedWith(
                compareBy<BookListItem> { it.book.author.lowercase() }
                    .thenBy { it.book.sortTitle.lowercase() }
            )
            LibrarySort.PROGRESS -> sortedWith(
                compareByDescending<BookListItem> { it.state?.progress ?: 0.0 }
                    .thenBy { it.book.sortTitle.lowercase() }
            )
            LibrarySort.SERIES -> sortedWith(
                compareBy<BookListItem> { it.book.series?.lowercase() ?: it.book.sortTitle.lowercase() }
                    .thenBy { it.book.seriesIndex ?: Double.MAX_VALUE }
                    .thenBy { it.book.year ?: Int.MAX_VALUE }
                    .thenBy { it.book.sortTitle.lowercase() }
            )
        }

    private fun BookListItem.recentTimestamp(): Long =
        state?.lastReadAt ?: book.lastOpenedAt ?: book.importedAt

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(container) as T
            }
    }
}
