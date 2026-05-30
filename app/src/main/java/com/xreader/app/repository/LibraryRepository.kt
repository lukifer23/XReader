package com.xreader.app.repository

import android.net.Uri
import androidx.room.withTransaction
import com.xreader.app.core.TextTools
import com.xreader.app.data.AuthorEntity
import com.xreader.app.data.BookCollectionEntity
import com.xreader.app.data.BookCollectionName
import com.xreader.app.data.BookDao
import com.xreader.app.data.BookEntity
import com.xreader.app.data.CollectionDao
import com.xreader.app.data.CollectionEntity
import com.xreader.app.data.GenreEntity
import com.xreader.app.data.SearchDao
import com.xreader.app.data.SearchIndexEntity
import com.xreader.app.data.SeriesEntity
import com.xreader.app.data.XReaderDatabase
import com.xreader.app.importer.ImportService
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

data class MetadataUpdateResult(
    val updatedBooks: Int,
)

data class CollectionUpdateResult(
    val collectionName: String,
    val changed: Boolean,
)

class LibraryRepository(
    private val database: XReaderDatabase,
    private val importService: ImportService,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val bookDao: BookDao = database.books()
    private val collectionDao: CollectionDao = database.collections()
    private val searchDao: SearchDao = database.search()
    private val libraryDetailsBackfillRunning = AtomicBoolean(false)

    fun observeBooks(query: String): Flow<List<BookEntity>> = bookDao.observeBooks(query)
    fun observeBook(id: Long): Flow<BookEntity?> = bookDao.observeBook(id)
    fun observeAuthors(): Flow<List<String>> = bookDao.observeAuthors()
    fun observeSeries(): Flow<List<String>> = bookDao.observeSeries()
    fun observeGenres(): Flow<List<String>> = bookDao.observeGenres()
    fun observeYears(): Flow<List<Int>> = bookDao.observeYears()
    fun observeCollections(): Flow<List<CollectionEntity>> = collectionDao.observeCollections()
    fun observeBookCollectionNames(): Flow<List<BookCollectionName>> = collectionDao.observeBookCollectionNames()

    suspend fun getBook(id: Long): BookEntity? = bookDao.getBook(id)
    suspend fun import(uri: Uri): ImportService.ImportResult = importService.import(uri)
    suspend fun importMany(uris: List<Uri>): ImportService.ImportBatchResult = importService.importMany(uris)
    suspend fun importFolder(uri: Uri): ImportService.ImportBatchResult = importService.importFolder(uri)
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
    suspend fun exportBook(book: BookEntity, uri: Uri): Long = importService.exportStoredFile(book, uri)

    suspend fun markOpened(bookId: Long) = bookDao.markOpened(bookId, clock.millis())
    suspend fun setFavorite(bookId: Long, favorite: Boolean) =
        bookDao.setFavorite(bookId, favorite, clock.millis())

    suspend fun setFinished(bookId: Long, finished: Boolean) =
        bookDao.setFinished(bookId, finished, clock.millis())

    suspend fun addBookToCollection(bookId: Long, rawName: String): CollectionUpdateResult = database.withTransaction {
        requireNotNull(bookDao.getBook(bookId)) { "Book not found" }
        val name = rawName.cleanCollectionName()
        val now = clock.millis()
        val existing = collectionDao.collectionByName(name)
        val collectionId = existing?.id
            ?: collectionDao.insertCollection(
                CollectionEntity(
                    name = name,
                    createdAt = now,
                    updatedAt = now
                )
            ).takeIf { it > 0 }
            ?: requireNotNull(collectionDao.collectionByName(name)?.id) { "Could not create collection" }
        val changed = collectionDao.insertBookCollection(
            BookCollectionEntity(
                bookId = bookId,
                collectionId = collectionId,
                addedAt = now
            )
        ) > 0
        collectionDao.touchCollection(collectionId, now)
        CollectionUpdateResult(collectionName = existing?.name ?: name, changed = changed)
    }

    suspend fun removeBookFromCollection(bookId: Long, collectionId: Long): CollectionUpdateResult = database.withTransaction {
        val collection = requireNotNull(collectionDao.collectionById(collectionId)) { "Collection not found" }
        val changed = collectionDao.deleteBookCollection(bookId = bookId, collectionId = collectionId) > 0
        if (changed && collectionDao.memberCount(collectionId) == 0) {
            collectionDao.deleteCollection(collectionId)
        } else if (changed) {
            collectionDao.touchCollection(collectionId, clock.millis())
        }
        CollectionUpdateResult(collectionName = collection.name, changed = changed)
    }

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
        applyToSeries: Boolean,
    ): MetadataUpdateResult = database.withTransaction {
        val resolvedTitle = title.trim().ifBlank { book.title }
        val resolvedAuthor = author.cleanMetadataValue() ?: "Unknown Author"
        val resolvedGenre = genre.cleanMetadataValue()
        val resolvedSeries = series.cleanMetadataValue()
        val now = clock.millis()
        val updated = book.copy(
            title = resolvedTitle,
            author = resolvedAuthor,
            sortTitle = TextTools.sortTitle(resolvedTitle),
            year = year,
            genre = resolvedGenre,
            series = resolvedSeries,
            seriesIndex = seriesIndex,
            updatedAt = now
        )
        var updatedBooks = 0
        if (updated.copy(updatedAt = book.updatedAt) != book) {
            bookDao.update(updated)
            updatedBooks += 1
        }

        if (applyToSeries && (book.series.cleanMetadataValue() != null || resolvedSeries != null)) {
            val peers = bookDao.booksForSharedSeriesMetadata(
                bookId = book.id,
                originalAuthor = book.author,
                targetAuthor = resolvedAuthor,
                originalSeries = book.series.cleanMetadataValue().orEmpty(),
                targetSeries = resolvedSeries.orEmpty()
            )
            peers.forEach { peer ->
                val peerUpdated = peer.copy(
                    author = resolvedAuthor,
                    genre = resolvedGenre,
                    series = resolvedSeries,
                    updatedAt = now
                )
                if (peer.author != resolvedAuthor || peer.genre != resolvedGenre || peer.series != resolvedSeries) {
                    bookDao.update(peerUpdated)
                    updatedBooks += 1
                }
            }
        }

        bookDao.insertAuthor(AuthorEntity(name = updated.author))
        updated.genre?.let { bookDao.insertGenre(GenreEntity(name = it)) }
        updated.series?.let { bookDao.insertSeries(SeriesEntity(name = it)) }
        MetadataUpdateResult(updatedBooks = updatedBooks)
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

    private fun String?.cleanMetadataValue(): String? =
        this?.trim()?.ifBlank { null }

    private fun String.cleanCollectionName(): String {
        val cleaned = trim().replace(Regex("\\s+"), " ")
        require(cleaned.isNotBlank()) { "Collection name required" }
        require(cleaned.length <= MAX_COLLECTION_NAME_LENGTH) { "Collection names can be up to $MAX_COLLECTION_NAME_LENGTH characters" }
        return cleaned
    }

    private companion object {
        const val MAX_COLLECTION_NAME_LENGTH = 80
    }
}
