package com.xreader.app.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.XReaderDatabase
import com.xreader.app.importer.ImportService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class LibraryRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, XReaderDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC)
    private val repository = LibraryRepository(
        database = db,
        importService = ImportService(context, db, clock),
        clock = clock
    )

    @After
    fun cleanUp() {
        db.close()
    }

    @Test
    fun updateMetadataCanApplySharedMetadataToMatchingSeriesBooks() = runBlocking {
        val firstId = db.books().insert(
            book(
                title = "Red Rising",
                author = "P. Brown",
                series = "Red Rising Saga",
                seriesIndex = 1.0,
                genre = "Dystopian"
            )
        )
        val secondId = db.books().insert(
            book(
                title = "Golden Son",
                author = "P. Brown",
                series = "Red Rising Saga",
                seriesIndex = 2.0,
                genre = "Adventure"
            )
        )
        val alreadyRenamedId = db.books().insert(
            book(
                title = "Morning Star",
                author = "Pierce Brown",
                series = "Red Rising",
                seriesIndex = 3.0,
                genre = "War"
            )
        )
        val differentAuthorId = db.books().insert(
            book(
                title = "Other Red Rising",
                author = "Other Author",
                series = "Red Rising Saga",
                seriesIndex = 1.0,
                genre = "Fantasy"
            )
        )
        val first = requireNotNull(db.books().getBook(firstId))

        val result = repository.updateMetadata(
            book = first,
            title = first.title,
            author = "Pierce Brown",
            year = first.year,
            genre = "Science Fiction",
            series = "Red Rising",
            seriesIndex = first.seriesIndex,
            applyToSeries = true
        )

        assertEquals(3, result.updatedBooks)
        assertEquals("Pierce Brown", db.books().getBook(firstId)?.author)
        assertEquals("Pierce Brown", db.books().getBook(secondId)?.author)
        assertEquals("Pierce Brown", db.books().getBook(alreadyRenamedId)?.author)
        assertEquals("Other Author", db.books().getBook(differentAuthorId)?.author)
        assertEquals("Science Fiction", db.books().getBook(firstId)?.genre)
        assertEquals("Science Fiction", db.books().getBook(secondId)?.genre)
        assertEquals("Science Fiction", db.books().getBook(alreadyRenamedId)?.genre)
        assertEquals("Red Rising", db.books().getBook(firstId)?.series)
        assertEquals("Red Rising", db.books().getBook(secondId)?.series)
        assertEquals("Red Rising", db.books().getBook(alreadyRenamedId)?.series)
        assertEquals("Fantasy", db.books().getBook(differentAuthorId)?.genre)
        assertEquals("Red Rising Saga", db.books().getBook(differentAuthorId)?.series)
        assertEquals(2.0, db.books().getBook(secondId)?.seriesIndex)
        assertEquals(2026, db.books().getBook(secondId)?.year)
        assertEquals(listOf("Other Author", "Pierce Brown"), db.books().observeAuthors().first())
        assertEquals(listOf("Fantasy", "Science Fiction"), db.books().observeGenres().first())
        assertEquals(listOf("Red Rising", "Red Rising Saga"), db.books().observeSeries().first())
    }

    @Test
    fun updateMetadataWithoutBulkOnlyChangesSelectedBook() = runBlocking {
        val firstId = db.books().insert(
            book(
                title = "Red Rising",
                author = "Pierce Brown",
                series = "Red Rising Saga",
                seriesIndex = 1.0,
                genre = "Dystopian"
            )
        )
        val secondId = db.books().insert(
            book(
                title = "Golden Son",
                author = "Pierce Brown",
                series = "Red Rising Saga",
                seriesIndex = 2.0,
                genre = "Adventure"
            )
        )
        val first = requireNotNull(db.books().getBook(firstId))

        val result = repository.updateMetadata(
            book = first,
            title = first.title,
            author = first.author,
            year = first.year,
            genre = "Science Fiction",
            series = "Red Rising",
            seriesIndex = first.seriesIndex,
            applyToSeries = false
        )

        assertEquals(1, result.updatedBooks)
        assertEquals("Science Fiction", db.books().getBook(firstId)?.genre)
        assertEquals("Red Rising", db.books().getBook(firstId)?.series)
        assertEquals("Adventure", db.books().getBook(secondId)?.genre)
        assertEquals("Red Rising Saga", db.books().getBook(secondId)?.series)
    }

    @Test
    fun updateMetadataReturnsZeroWhenNothingChanged() = runBlocking {
        val id = db.books().insert(
            book(
                title = "Red Rising",
                author = "Pierce Brown",
                series = "Red Rising",
                seriesIndex = 1.0,
                genre = "Science Fiction"
            )
        )
        val original = requireNotNull(db.books().getBook(id))

        val result = repository.updateMetadata(
            book = original,
            title = original.title,
            author = original.author,
            year = original.year,
            genre = original.genre,
            series = original.series,
            seriesIndex = original.seriesIndex,
            applyToSeries = true
        )

        assertEquals(0, result.updatedBooks)
        assertEquals(original, db.books().getBook(id))
    }

    @Test
    fun collectionsAreCaseInsensitiveAndRemovedWhenEmpty() = runBlocking {
        val id = db.books().insert(
            book(
                title = "Red Rising",
                author = "Pierce Brown",
                series = "Red Rising",
                seriesIndex = 1.0,
                genre = "Science Fiction"
            )
        )

        val first = repository.addBookToCollection(id, "  Sci-Fi  ")
        val duplicate = repository.addBookToCollection(id, "sci-fi")
        val memberships = repository.observeBookCollectionNames().first()

        assertEquals("Sci-Fi", first.collectionName)
        assertEquals(true, first.changed)
        assertEquals("Sci-Fi", duplicate.collectionName)
        assertEquals(false, duplicate.changed)
        assertEquals(1, memberships.size)
        assertEquals("Sci-Fi", memberships.single().name)
        assertEquals(listOf("Sci-Fi"), repository.observeCollections().first().map { it.name })

        val removed = repository.removeBookFromCollection(id, memberships.single().collectionId)

        assertEquals(true, removed.changed)
        assertEquals(emptyList<String>(), repository.observeCollections().first().map { it.name })
        assertEquals(emptyList<String>(), repository.observeBookCollectionNames().first().map { it.name })
    }

    private fun book(
        title: String,
        author: String,
        series: String,
        seriesIndex: Double,
        genre: String,
    ): BookEntity =
        BookEntity(
            title = title,
            author = author,
            sortTitle = title.lowercase(),
            series = series,
            seriesIndex = seriesIndex,
            genre = genre,
            year = 2026,
            description = null,
            language = "en",
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "${title.lowercase().replace(' ', '-')}.epub",
            filePath = "library/books/${title.lowercase().replace(' ', '-')}.epub",
            coverImagePath = null,
            checksum = "checksum-${title.lowercase()}",
            fileSizeBytes = 1024,
            wordCount = 100_000,
            pageCount = null,
            importedAt = 100,
            updatedAt = 100,
            lastOpenedAt = null,
            favorite = false,
            finished = false
        )
}
