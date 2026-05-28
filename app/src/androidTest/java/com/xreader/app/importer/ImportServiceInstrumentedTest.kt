package com.xreader.app.importer

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xreader.app.data.BookFormat
import com.xreader.app.data.XReaderDatabase
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
    private val root = File(baseContext.cacheDir, "import-service-test-${System.nanoTime()}")
    private val context = object : ContextWrapper(baseContext) {
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
    }
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
        val source = File(root, "source/xreader_smoke.txt").apply {
            parentFile?.mkdirs()
            writeText(
                """
                XReader smoke test

                This is a real TXT file imported through ImportService.
                The importer should convert it to EPUB and index searchable text.
                """.trimIndent()
            )
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("txt", book.sourceExtension)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val searchResults = db.search().searchBook(result.bookId, "normalizedBody:smoke*")
        assertTrue(searchResults.any { it.body.contains("smoke", ignoreCase = true) })
    }
}
