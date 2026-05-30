package com.xreader.app.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class BookCollectionName(
    val bookId: Long,
    val collectionId: Long,
    val name: String,
)

data class LibrarySearchRow(
    @Embedded val row: SearchIndexEntity,
    val bookTitle: String,
    val bookAuthor: String,
)

@Dao
interface BookDao {
    @Query(
        """
        SELECT * FROM books
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'
            OR IFNULL(series, '') LIKE '%' || :query || '%' OR IFNULL(genre, '') LIKE '%' || :query || '%')
        ORDER BY lastOpenedAt DESC, sortTitle ASC
        """
    )
    fun observeBooks(query: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): BookEntity?

    @Query(
        """
        SELECT * FROM books
        WHERE id != :bookId
            AND series IS NOT NULL
            AND series != ''
            AND (
                (:originalSeries != '' AND LOWER(series) = LOWER(:originalSeries))
                OR (:targetSeries != '' AND LOWER(series) = LOWER(:targetSeries))
            )
            AND (
                LOWER(author) = LOWER(:originalAuthor)
                OR LOWER(author) = LOWER(:targetAuthor)
            )
        ORDER BY seriesIndex ASC, year ASC, sortTitle ASC
        """
    )
    suspend fun booksForSharedSeriesMetadata(
        bookId: Long,
        originalAuthor: String,
        targetAuthor: String,
        originalSeries: String,
        targetSeries: String,
    ): List<BookEntity>

    @Query("SELECT * FROM books ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun booksForMaintenance(limit: Int): List<BookEntity>

    @Query("SELECT * FROM books")
    suspend fun booksForBackup(): List<BookEntity>

    @Query("SELECT * FROM books WHERE coverImagePath IS NULL OR coverImagePath = ''")
    suspend fun booksMissingCovers(): List<BookEntity>

    @Query("SELECT * FROM books WHERE format = 'EPUB' ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun epubBooksForMetadataAudit(limit: Int): List<BookEntity>

    @Query(
        """
        SELECT * FROM books
        WHERE series IS NOT NULL AND series != '' AND year IS NOT NULL
        ORDER BY author COLLATE NOCASE, series COLLATE NOCASE, year ASC, sortTitle ASC
        """
    )
    suspend fun booksWithSeriesForNormalization(): List<BookEntity>

    @Query("SELECT * FROM books ORDER BY author COLLATE NOCASE, year ASC, sortTitle ASC")
    suspend fun booksForSeriesInference(): List<BookEntity>

    @Query("SELECT * FROM books WHERE checksum = :checksum LIMIT 1")
    suspend fun getByChecksum(checksum: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: Long)

    @Query("UPDATE books SET lastOpenedAt = :openedAt WHERE id = :bookId")
    suspend fun markOpened(bookId: Long, openedAt: Long)

    @Query("UPDATE books SET favorite = :favorite, updatedAt = :updatedAt WHERE id = :bookId")
    suspend fun setFavorite(bookId: Long, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE books SET finished = :finished, updatedAt = :updatedAt WHERE id = :bookId")
    suspend fun setFinished(bookId: Long, finished: Boolean, updatedAt: Long)

    @Query("UPDATE books SET coverImagePath = :coverImagePath, updatedAt = :updatedAt WHERE id = :bookId")
    suspend fun setCoverImagePath(bookId: Long, coverImagePath: String?, updatedAt: Long)

    @Query("SELECT DISTINCT author FROM books ORDER BY author")
    fun observeAuthors(): Flow<List<String>>

    @Query("SELECT DISTINCT series FROM books WHERE series IS NOT NULL AND series != '' ORDER BY series")
    fun observeSeries(): Flow<List<String>>

    @Query("SELECT DISTINCT genre FROM books WHERE genre IS NOT NULL AND genre != '' ORDER BY genre")
    fun observeGenres(): Flow<List<String>>

    @Query("SELECT DISTINCT author FROM books WHERE author != '' ORDER BY author COLLATE NOCASE")
    suspend fun authorNames(): List<String>

    @Query("SELECT DISTINCT series FROM books WHERE series IS NOT NULL AND series != '' ORDER BY series COLLATE NOCASE")
    suspend fun seriesNames(): List<String>

    @Query("SELECT DISTINCT genre FROM books WHERE genre IS NOT NULL AND genre != '' ORDER BY genre COLLATE NOCASE")
    suspend fun genreNames(): List<String>

    @Query("SELECT DISTINCT year FROM books WHERE year IS NOT NULL ORDER BY year DESC")
    fun observeYears(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAuthor(author: AuthorEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeries(series: SeriesEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenre(genre: GenreEntity): Long
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE ASC")
    suspend fun allCollections(): List<CollectionEntity>

    @Query("SELECT * FROM book_collections")
    suspend fun allBookCollections(): List<BookCollectionEntity>

    @Query(
        """
        SELECT book_collections.bookId AS bookId, collections.id AS collectionId, collections.name AS name
        FROM book_collections
        INNER JOIN collections ON collections.id = book_collections.collectionId
        ORDER BY collections.name COLLATE NOCASE ASC
        """
    )
    fun observeBookCollectionNames(): Flow<List<BookCollectionName>>

    @Query("SELECT * FROM collections WHERE id = :collectionId")
    suspend fun collectionById(collectionId: Long): CollectionEntity?

    @Query("SELECT * FROM collections WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun collectionByName(name: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Query("UPDATE collections SET updatedAt = :updatedAt WHERE id = :collectionId")
    suspend fun touchCollection(collectionId: Long, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookCollection(entity: BookCollectionEntity): Long

    @Query("DELETE FROM book_collections WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun deleteBookCollection(bookId: Long, collectionId: Long): Int

    @Query("SELECT COUNT(*) FROM book_collections WHERE collectionId = :collectionId")
    suspend fun memberCount(collectionId: Long): Int

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: Long): Int
}

@Dao
interface ReadingDao {
    @Query("SELECT * FROM reading_states WHERE bookId = :bookId")
    fun observeState(bookId: Long): Flow<ReadingStateEntity?>

    @Query("SELECT * FROM reading_states")
    fun observeStates(): Flow<List<ReadingStateEntity>>

    @Query("SELECT * FROM reading_states")
    suspend fun allStates(): List<ReadingStateEntity>

    @Query("SELECT * FROM reading_states WHERE bookId = :bookId")
    suspend fun getState(bookId: Long): ReadingStateEntity?

    @Upsert
    suspend fun upsertState(state: ReadingStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSessionEntity): Long

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    suspend fun allSessions(): List<ReadingSessionEntity>

    @Query(
        """
        SELECT * FROM reading_sessions
        WHERE bookId = :bookId AND startedAt = :startedAt AND endedAt = :endedAt
            AND startUnit = :startUnit AND endUnit = :endUnit
        LIMIT 1
        """
    )
    suspend fun getSessionForImport(
        bookId: Long,
        startedAt: Long,
        endedAt: Long,
        startUnit: Int,
        endUnit: Int,
    ): ReadingSessionEntity?

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startedAt DESC")
    fun observeSessionsForBook(bookId: Long): Flow<List<ReadingSessionEntity>>
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations ORDER BY updatedAt DESC")
    fun observeAllAnnotations(): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations ORDER BY updatedAt DESC")
    suspend fun allAnnotations(): List<AnnotationEntity>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observeAnnotations(bookId: Long): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Update
    suspend fun updateAnnotation(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotation(id: Long)

    @Query(
        """
        SELECT * FROM annotations
        WHERE bookId = :bookId AND kind = :kind AND locator = :locator AND quote = :quote
        LIMIT 1
        """
    )
    suspend fun getAnnotationForImport(
        bookId: Long,
        kind: AnnotationKind,
        locator: String,
        quote: String,
    ): AnnotationEntity?

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY progress ASC")
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun allBookmarks(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND locator = :locator LIMIT 1")
    suspend fun getBookmarkAt(bookId: Long, locator: String): BookmarkEntity?
}

@Dao
interface SearchDao {
    @Query("DELETE FROM search_index WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM search_index_fts WHERE bookId = :bookId")
    suspend fun deleteFtsForBook(bookId: String)

    @Query("SELECT * FROM search_index WHERE bookId = :bookId ORDER BY unitIndex ASC")
    suspend fun indexedRowsForBook(bookId: Long): List<SearchIndexEntity>

    @Query("SELECT COUNT(*) FROM search_index WHERE bookId = :bookId")
    suspend fun indexedRowCountForBook(bookId: Long): Int

    @Query("SELECT MAX(unitIndex) FROM search_index WHERE bookId = :bookId")
    suspend fun maxUnitIndexForBook(bookId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRows(chunks: List<SearchIndexEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(chunks: List<SearchIndexFtsEntity>)

    @Transaction
    suspend fun replaceForBook(bookId: Long, chunks: List<SearchIndexEntity>) {
        deleteFtsForBook(bookId.toString())
        deleteForBook(bookId)
        if (chunks.isNotEmpty()) {
            val ids = insertRows(chunks)
            insertFts(
                chunks.zip(ids).map { (chunk, id) ->
                    SearchIndexFtsEntity(
                        rowId = id,
                        bookId = chunk.bookId.toString(),
                        locator = chunk.locator,
                        heading = chunk.heading,
                        body = chunk.body,
                        normalizedBody = chunk.normalizedBody,
                        unitIndex = chunk.unitIndex.toString()
                    )
                }
            )
        }
    }

    @Query(
        """
        SELECT search_index.* FROM search_index
        INNER JOIN search_index_fts ON search_index.id = search_index_fts.rowid
        WHERE search_index.bookId = :bookId AND search_index_fts MATCH :query
        ORDER BY unitIndex ASC
        LIMIT :limit
        """
    )
    suspend fun searchBook(bookId: Long, query: String, limit: Int = 80): List<SearchIndexEntity>

    @Query(
        """
        SELECT search_index.* FROM search_index
        INNER JOIN search_index_fts ON search_index.id = search_index_fts.rowid
        INNER JOIN books ON books.id = search_index.bookId
        WHERE search_index_fts MATCH :query
        ORDER BY books.lastOpenedAt DESC, search_index.unitIndex ASC
        LIMIT :limit
        """
    )
    suspend fun searchLibrary(query: String, limit: Int = 120): List<SearchIndexEntity>

    @Query(
        """
        SELECT search_index.*, books.title AS bookTitle, books.author AS bookAuthor
        FROM search_index
        INNER JOIN search_index_fts ON search_index.id = search_index_fts.rowid
        INNER JOIN books ON books.id = search_index.bookId
        WHERE search_index_fts MATCH :query
        ORDER BY books.lastOpenedAt DESC, search_index.unitIndex ASC
        LIMIT :limit
        """
    )
    suspend fun searchLibraryWithBooks(query: String, limit: Int = 120): List<LibrarySearchRow>
}

@Dao
interface DictionaryDao {
    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<DictionaryEntryEntity>)

    @Query(
        """
        SELECT * FROM dictionary_entries
        WHERE lemma = :lemma
        ORDER BY partOfSpeech ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun lookup(lemma: String, limit: Int = 24): List<DictionaryEntryEntity>
}
