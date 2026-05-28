package com.xreader.app.repository

import android.net.Uri
import com.xreader.app.data.BookDao
import com.xreader.app.data.BookEntity
import com.xreader.app.data.SearchDao
import com.xreader.app.data.SearchIndexEntity
import com.xreader.app.importer.ImportService
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

class LibraryRepository(
    private val bookDao: BookDao,
    private val searchDao: SearchDao,
    private val importService: ImportService,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val libraryDetailsBackfillRunning = AtomicBoolean(false)

    fun observeBooks(query: String): Flow<List<BookEntity>> = bookDao.observeBooks(query)
    fun observeBook(id: Long): Flow<BookEntity?> = bookDao.observeBook(id)
    fun observeAuthors(): Flow<List<String>> = bookDao.observeAuthors()
    fun observeSeries(): Flow<List<String>> = bookDao.observeSeries()
    fun observeGenres(): Flow<List<String>> = bookDao.observeGenres()
    fun observeYears(): Flow<List<Int>> = bookDao.observeYears()

    suspend fun getBook(id: Long): BookEntity? = bookDao.getBook(id)
    suspend fun import(uri: Uri): ImportService.ImportResult = importService.import(uri)
    suspend fun backfillMissingCovers() = importService.backfillMissingCovers()
    suspend fun backfillLibraryDetails() {
        if (!libraryDetailsBackfillRunning.compareAndSet(false, true)) return
        try {
            importService.backfillLibraryDetails()
        } finally {
            libraryDetailsBackfillRunning.set(false)
        }
    }
    suspend fun repairLibrary(): ImportService.LibraryRepairResult = importService.repairLibrary()
    suspend fun repairBook(bookId: Long): ImportService.BookRepairResult = importService.repairBook(bookId)
    suspend fun bookHealth(bookId: Long): ImportService.BookHealth = importService.bookHealth(bookId)
    suspend fun replaceCover(book: BookEntity, uri: Uri): String = importService.replaceCover(book, uri)

    suspend fun markOpened(bookId: Long) = bookDao.markOpened(bookId, clock.millis())
    suspend fun setFavorite(bookId: Long, favorite: Boolean) =
        bookDao.setFavorite(bookId, favorite, clock.millis())

    suspend fun setFinished(bookId: Long, finished: Boolean) =
        bookDao.setFinished(bookId, finished, clock.millis())

    suspend fun deleteBook(book: BookEntity) {
        searchDao.deleteFtsForBook(book.id.toString())
        searchDao.deleteForBook(book.id)
        bookDao.deleteBook(book.id)
        importService.deleteStoredFile(book)
    }

    suspend fun updateMetadata(
        book: BookEntity,
        title: String,
        author: String,
        year: Int?,
        genre: String?,
        series: String?,
        seriesIndex: Double?,
    ) {
        val resolvedTitle = title.trim().ifBlank { book.title }
        val updated = book.copy(
            title = resolvedTitle,
            author = author.trim().ifBlank { "Unknown Author" },
            sortTitle = com.xreader.app.core.TextTools.sortTitle(resolvedTitle),
            year = year,
            genre = genre?.trim()?.ifBlank { null },
            series = series?.trim()?.ifBlank { null },
            seriesIndex = seriesIndex,
            updatedAt = clock.millis()
        )
        bookDao.update(updated)
        bookDao.insertAuthor(com.xreader.app.data.AuthorEntity(name = updated.author))
        updated.genre?.let { bookDao.insertGenre(com.xreader.app.data.GenreEntity(name = it)) }
        updated.series?.let { bookDao.insertSeries(com.xreader.app.data.SeriesEntity(name = it)) }
    }

    suspend fun searchBook(bookId: Long, query: String): List<SearchIndexEntity> =
        ftsQuery(query)?.let { searchDao.searchBook(bookId, it) }.orEmpty()

    suspend fun indexedRowsForBook(bookId: Long): List<SearchIndexEntity> =
        searchDao.indexedRowsForBook(bookId)

    suspend fun maxIndexedUnitForBook(bookId: Long): Int =
        searchDao.maxUnitIndexForBook(bookId) ?: 0

    suspend fun searchLibrary(query: String): List<SearchIndexEntity> =
        ftsQuery(query)?.let { searchDao.searchLibrary(it) }.orEmpty()

    private fun ftsQuery(query: String): String? {
        val terms = com.xreader.app.core.TextTools.words(
            com.xreader.app.core.TextTools.normalizeForSearch(query)
        )
            .map { it.filter(Char::isLetterOrDigit) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { "normalizedBody:${it}*" }
    }
}
