package com.xreader.app.importer

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xreader.app.data.BookFormat
import com.xreader.app.data.XReaderDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImportServiceInstrumentedTest {
    private val baseContext: Context = ApplicationProvider.getApplicationContext()
    private val instrumentationContext: Context = InstrumentationRegistry.getInstrumentation().context
    private val root = File(baseContext.cacheDir, "import-service-test-${System.nanoTime()}")
    private val context = object : ContextWrapper(baseContext) {
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
    }
    private val fixtures = PublicDomainBookFixtures(instrumentationContext, root)
    private val db = Room.inMemoryDatabaseBuilder(baseContext, XReaderDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun cleanUp() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun importsTxtAsPrivateEpubAndIndexesText() = runBlocking {
        val source = fixtures.aliceTxt()

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("txt", book.sourceExtension)
        assertEquals("Alice Public Domain Excerpt", book.title)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val searchResults = db.search().searchBook(result.bookId, "normalizedBody:rabbit*")
        assertTrue(searchResults.any { it.body.contains("Rabbit", ignoreCase = true) })
    }

    @Test
    fun importsPublicDomainEpubFixtureAndIndexesText() = runBlocking {
        val source = fixtures.aliceEpub()

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("epub", book.sourceExtension)
        assertEquals("Alice Public Domain Excerpt", book.title)
        assertTrue(book.wordCount > 80)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val aliceResults = db.search().searchBook(result.bookId, "normalizedBody:alice*")
        val rabbitResults = db.search().searchBook(result.bookId, "normalizedBody:rabbit*")
        assertTrue(aliceResults.any { it.body.contains("Alice", ignoreCase = true) })
        assertTrue(rabbitResults.any { it.body.contains("White Rabbit", ignoreCase = true) })
    }

    @Test
    fun importsManyBooksAndReportsDuplicatesAndUnsupportedFiles() = runBlocking {
        val first = File(root, "source/batch_one.txt").apply {
            parentFile?.mkdirs()
            writeText("Batch one\n\nA real text import.")
        }
        val second = File(root, "source/batch_two.txt").apply {
            parentFile?.mkdirs()
            writeText("Batch two\n\nAnother real text import.")
        }
        val unsupported = File(root, "source/not_a_book.jpg").apply {
            parentFile?.mkdirs()
            writeText("not an image or a book")
        }
        val service = ImportService(context, db)

        val imported = service.importMany(listOf(Uri.fromFile(first), Uri.fromFile(second)))

        assertEquals(2, imported.scanned)
        assertEquals(2, imported.imported)
        assertEquals(0, imported.duplicates)
        assertEquals(0, imported.unsupported)
        assertEquals(0, imported.failed)
        assertEquals(2, db.books().observeBooks("").first().size)

        val repeated = service.importMany(listOf(Uri.fromFile(first), Uri.fromFile(unsupported)))

        assertEquals(2, repeated.scanned)
        assertEquals(0, repeated.imported)
        assertEquals(1, repeated.duplicates)
        assertEquals(1, repeated.unsupported)
        assertEquals(0, repeated.failed)
    }

    @Test
    fun reportsHealthAndRepairsSingleBookSearchIndex() = runBlocking {
        val source = File(root, "source/xreader_repair.txt").apply {
            parentFile?.mkdirs()
            writeText(
                """
                XReader repair test

                The repair action should rebuild searchable private book text.
                """.trimIndent()
            )
        }
        val service = ImportService(context, db)
        val result = service.import(Uri.fromFile(source))

        val initialHealth = service.bookHealth(result.bookId)
        assertTrue(initialHealth.fileAvailable)
        assertTrue(initialHealth.searchRows > 0)

        db.search().deleteFtsForBook(result.bookId.toString())
        db.search().deleteForBook(result.bookId)
        assertEquals(0, service.bookHealth(result.bookId).searchRows)

        val repair = service.repairBook(result.bookId)

        assertFalse(repair.failed)
        assertTrue(repair.searchRows > 0)
        assertEquals(repair.searchRows, service.bookHealth(result.bookId).searchRows)
    }
}
