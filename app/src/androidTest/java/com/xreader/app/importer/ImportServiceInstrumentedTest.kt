package com.xreader.app.importer

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.XReaderDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    fun importsDownloadedFileThroughPrivatePipeline() = runBlocking {
        val source = File(root, "downloaded/Remote Book.txt").apply {
            parentFile?.mkdirs()
            writeText(
                """
                Remote Book

                Catalog imports should use the same private pipeline as local files.
                """.trimIndent()
            )
        }

        val result = ImportService(context, db).importFile(
            file = source,
            displayName = "Remote Book.txt",
            mimeType = "text/plain"
        )

        val book = requireNotNull(db.books().getBook(result.bookId))
        assertFalse(result.duplicate)
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("txt", book.sourceExtension)
        assertEquals("Remote Book", book.title)
        assertTrue(File(context.filesDir, book.filePath).exists())
        assertTrue(db.search().searchBook(result.bookId, "normalizedBody:catalog*").isNotEmpty())
    }

    @Test
    fun importsPdfAsPrivatePdfAndIndexesCleanText() = runBlocking {
        val source = File(root, "source/Station Manual.pdf").apply {
            parentFile?.mkdirs()
            writeSearchablePdf(this)
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.PDF, book.format)
        assertEquals("pdf", book.sourceExtension)
        assertEquals("Station Manual", book.title)
        assertEquals(2, book.pageCount)
        assertTrue(book.wordCount > 0)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val results = db.search().searchBook(result.bookId, "normalizedBody:interstellar*")
        assertTrue(results.any { it.body.contains("Interstellar archive", ignoreCase = true) })
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
    fun importsCbzAsPrivateFixedLayoutEpub() = runBlocking {
        val source = File(root, "source/Space Comic.cbz").apply {
            parentFile?.mkdirs()
        }
        ZipOutputStream(source.outputStream().buffered()).use { zip ->
            zip.writeEntry("page2.png", pngBytes(Color.GREEN))
            zip.writeEntry("page1.png", pngBytes(Color.BLUE))
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("cbz", book.sourceExtension)
        assertEquals("Space Comic", book.title)
        assertEquals(2, book.pageCount)
        assertEquals(0, book.wordCount)
        assertTrue(File(context.filesDir, book.filePath).exists())
        assertTrue(book.coverImagePath?.let { File(context.filesDir, it).exists() } == true)
        assertEquals(0, db.search().indexedRowCountForBook(result.bookId))
    }

    @Test
    fun importsFb2ZipAsPrivateEpubAndIndexesText() = runBlocking {
        val source = File(root, "source/Orbital Tales.fb2.zip").apply {
            parentFile?.mkdirs()
        }
        ZipOutputStream(source.outputStream().buffered()).use { zip ->
            zip.writeEntry("Orbital Tales.fb2", fictionBookXml().toByteArray(Charsets.UTF_8))
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("fb2.zip", book.sourceExtension)
        assertEquals("Orbital Dawn", book.title)
        assertEquals("Octavia Butler", book.author)
        assertEquals("Science Fiction", book.genre)
        assertEquals("Patternist", book.series)
        assertEquals(1.0, book.seriesIndex ?: -1.0, 0.001)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val searchResults = db.search().searchBook(result.bookId, "normalizedBody:terraforming*")
        assertTrue(searchResults.any { it.body.contains("terraforming", ignoreCase = true) })
    }

    @Test
    fun importsRtfAsPrivateEpubAndIndexesText() = runBlocking {
        val source = File(root, "source/Field Notes.rtf").apply {
            parentFile?.mkdirs()
            writeText(rtfDocument(), Charsets.ISO_8859_1)
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("rtf", book.sourceExtension)
        assertEquals("Field Notes", book.title)
        assertEquals("Mina Patel", book.author)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val searchResults = db.search().searchBook(result.bookId, "normalizedBody:observatory*")
        assertTrue(searchResults.any { it.body.contains("observatory", ignoreCase = true) })
    }

    @Test
    fun importsMobiAsPrivateEpubAndIndexesText() = runBlocking {
        val source = File(root, "source/Orbital Legacy.mobi").apply {
            parentFile?.mkdirs()
            writeBytes(
                legacyMobiFixture(
                    title = "Orbital Legacy",
                    author = "Mina Patel",
                    body = """
                        <html><body>
                          <h1>Arrival</h1>
                          <p>The courier crossed the quiet orbit.</p>
                          <p>Every docking light stayed green.</p>
                        </body></html>
                    """.trimIndent()
                )
            )
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("mobi", book.sourceExtension)
        assertEquals("Orbital Legacy", book.title)
        assertEquals("Mina Patel", book.author)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val searchResults = db.search().searchBook(result.bookId, "normalizedBody:courier*")
        assertTrue(searchResults.any { it.body.contains("courier crossed", ignoreCase = true) })
    }

    @Test
    fun importsOdtAsPrivateEpubAndIndexesText() = runBlocking {
        val source = File(root, "source/Station Notes.odt").apply {
            parentFile?.mkdirs()
        }
        ZipOutputStream(source.outputStream().buffered()).use { zip ->
            zip.writeEntry("mimetype", "application/vnd.oasis.opendocument.text".toByteArray(Charsets.US_ASCII))
            zip.writeEntry("content.xml", odtContentXml().toByteArray(Charsets.UTF_8))
            zip.writeEntry("meta.xml", odtMetaXml().toByteArray(Charsets.UTF_8))
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("odt", book.sourceExtension)
        assertEquals("Station Notes", book.title)
        assertEquals("Mina Patel", book.author)
        assertEquals(2025, book.year)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val searchResults = db.search().searchBook(result.bookId, "normalizedBody:greenhouse*")
        assertTrue(searchResults.any { it.body.contains("greenhouse", ignoreCase = true) })
    }

    @Test
    fun importsDocxAsPrivateEpubAndIndexesText() = runBlocking {
        val source = File(root, "source/Station Briefing.docx").apply {
            parentFile?.mkdirs()
        }
        ZipOutputStream(source.outputStream().buffered()).use { zip ->
            zip.writeEntry("[Content_Types].xml", docxContentTypesXml().toByteArray(Charsets.UTF_8))
            zip.writeEntry("_rels/.rels", docxRelsXml().toByteArray(Charsets.UTF_8))
            zip.writeEntry("docProps/core.xml", docxCoreXml().toByteArray(Charsets.UTF_8))
            zip.writeEntry("word/document.xml", docxDocumentXml().toByteArray(Charsets.UTF_8))
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("docx", book.sourceExtension)
        assertEquals("Station Briefing", book.title)
        assertEquals("Mina Patel", book.author)
        assertEquals(2026, book.year)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val searchResults = db.search().searchBook(result.bookId, "normalizedBody:airlock*")
        assertTrue(searchResults.any { it.body.contains("airlock", ignoreCase = true) })
    }

    @Test
    fun importsHtmlAsPrivateEpubAndIndexesText() = runBlocking {
        val source = File(root, "source/Orbital Report.html").apply {
            parentFile?.mkdirs()
            writeText(htmlDocument())
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("html", book.sourceExtension)
        assertEquals("Orbital Field Report", book.title)
        assertEquals("Mina Patel", book.author)
        assertEquals("Science Fiction", book.genre)
        assertEquals(2026, book.year)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val searchResults = db.search().searchBook(result.bookId, "normalizedBody:survey*")
        assertTrue(searchResults.any { it.body.contains("survey ship", ignoreCase = true) })
    }

    @Test
    fun importsMhtmlAsPrivateEpubAndIndexesText() = runBlocking {
        val source = File(root, "source/Archived Report.mhtml").apply {
            parentFile?.mkdirs()
            writeText(mhtmlDocument(), Charsets.ISO_8859_1)
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("mhtml", book.sourceExtension)
        assertEquals("Archived Field Report", book.title)
        assertEquals("Mina Patel", book.author)
        assertEquals("Science Fiction", book.genre)
        assertEquals(2026, book.year)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val searchResults = db.search().searchBook(result.bookId, "normalizedBody:archive*")
        assertTrue(searchResults.any { it.body.contains("archived page", ignoreCase = true) })
    }

    @Test
    fun importsMarkdownAsPrivateEpubAndIndexesText() = runBlocking {
        val source = File(root, "source/Orbital Notes.md").apply {
            parentFile?.mkdirs()
            writeText(markdownDocument())
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        assertFalse(result.duplicate)
        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("md", book.sourceExtension)
        assertEquals("Orbital Field Notes", book.title)
        assertEquals("Mina Patel", book.author)
        assertEquals("Science Fiction", book.genre)
        assertEquals(2026, book.year)
        assertTrue(File(context.filesDir, book.filePath).exists())

        val searchResults = db.search().searchBook(result.bookId, "normalizedBody:greenhouse*")
        assertTrue(searchResults.any { it.body.contains("Greenhouse sealed", ignoreCase = true) })
    }

    @Test
    fun importCanonicalizesMetadataAgainstExistingLibrary() = runBlocking {
        db.books().insert(existingBook(author = "Mina Patel", genre = "Science Fiction"))
        val source = File(root, "source/Variant Notes.md").apply {
            parentFile?.mkdirs()
            writeText(
                """
                ---
                title: Variant Notes
                author: mina   patel
                genre: sci-fi
                ---

                # Arrival

                The greenhouse crew cataloged every new leaf.
                """.trimIndent()
            )
        }

        val result = ImportService(context, db).import(Uri.fromFile(source))

        val book = requireNotNull(db.books().getBook(result.bookId))
        assertEquals("Mina Patel", book.author)
        assertEquals("Science Fiction", book.genre)
        assertEquals(listOf("Mina Patel"), db.books().observeAuthors().first())
        assertEquals(listOf("Science Fiction"), db.books().observeGenres().first())
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
        assertEquals(0, imported.recovered)
        assertEquals(0, imported.duplicates)
        assertEquals(0, imported.unsupported)
        assertEquals(0, imported.failed)
        assertEquals(2, db.books().observeBooks("").first().size)

        val repeated = service.importMany(listOf(Uri.fromFile(first), Uri.fromFile(unsupported)))

        assertEquals(2, repeated.scanned)
        assertEquals(0, repeated.imported)
        assertEquals(0, repeated.recovered)
        assertEquals(1, repeated.duplicates)
        assertEquals(1, repeated.unsupported)
        assertEquals(0, repeated.failed)
        assertNull(repeated.primaryBookId)
    }

    @Test
    fun importManySingleBookReportsOpenableBookIdForNewAndDuplicateImports() = runBlocking {
        val source = File(root, "source/single_openable.txt").apply {
            parentFile?.mkdirs()
            writeText("Single openable\n\nThis imported title should be actionable.")
        }
        val service = ImportService(context, db)

        val imported = service.importMany(listOf(Uri.fromFile(source)))
        val bookId = requireNotNull(imported.primaryBookId)

        assertEquals(1, imported.scanned)
        assertEquals(1, imported.imported)
        assertEquals(0, imported.recovered)
        assertEquals(0, imported.duplicates)
        assertEquals(0, imported.unsupported)
        assertEquals(0, imported.failed)

        val duplicate = service.importMany(listOf(Uri.fromFile(source)))

        assertEquals(1, duplicate.scanned)
        assertEquals(0, duplicate.imported)
        assertEquals(0, duplicate.recovered)
        assertEquals(1, duplicate.duplicates)
        assertEquals(0, duplicate.unsupported)
        assertEquals(0, duplicate.failed)
        assertEquals(bookId, duplicate.primaryBookId)
    }

    @Test
    fun reimportRestoresMissingPrivateFileAndSearchRowsWithoutChangingBookId() = runBlocking {
        val source = File(root, "source/recoverable.txt").apply {
            parentFile?.mkdirs()
            writeText("Recoverable title\n\nThe private copy can be rebuilt from the original import.")
        }
        val service = ImportService(context, db)
        val first = service.importMany(listOf(Uri.fromFile(source)))
        val bookId = requireNotNull(first.primaryBookId)
        val original = requireNotNull(db.books().getBook(bookId))
        val edited = original.copy(
            title = "Edited Recoverable Title",
            sortTitle = "edited recoverable title",
            author = "Edited Author"
        )
        db.books().update(edited)
        File(context.filesDir, original.filePath).delete()
        db.search().deleteFtsForBook(bookId.toString())
        db.search().deleteForBook(bookId)
        assertFalse(File(context.filesDir, original.filePath).exists())
        assertEquals(0, db.search().indexedRowCountForBook(bookId))

        val recovered = service.importMany(listOf(Uri.fromFile(source)))

        val restored = requireNotNull(db.books().getBook(bookId))
        assertEquals(1, recovered.scanned)
        assertEquals(0, recovered.imported)
        assertEquals(1, recovered.recovered)
        assertEquals(0, recovered.duplicates)
        assertEquals(0, recovered.unsupported)
        assertEquals(0, recovered.failed)
        assertEquals(bookId, recovered.primaryBookId)
        assertEquals(1, db.books().observeBooks("").first().size)
        assertEquals("Edited Recoverable Title", restored.title)
        assertEquals("Edited Author", restored.author)
        assertTrue(File(context.filesDir, restored.filePath).exists())
        assertTrue(db.search().indexedRowCountForBook(bookId) > 0)
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

    @Test
    fun repairLibraryHarmonizesSameSeriesGenreDrift() = runBlocking {
        val service = ImportService(context, db)
        val imported = listOf(
            "Red Rising" to "Dystopian",
            "Golden Son" to "Adventure",
            "Morning Star" to "War",
        ).mapIndexed { index, (title, genre) ->
            val source = File(root, "source/$title.txt").apply {
                parentFile?.mkdirs()
                writeText("$title\n\nA series cleanup fixture paragraph $index.")
            }
            val bookId = service.import(Uri.fromFile(source)).bookId
            val book = requireNotNull(db.books().getBook(bookId))
            db.books().update(
                book.copy(
                    author = "Pierce Brown",
                    series = "Red Rising",
                    seriesIndex = (index + 1).toDouble(),
                    genre = genre
                )
            )
            bookId
        }

        val result = service.repairLibrary()

        assertEquals(3, result.scanned)
        assertEquals(3, result.metadataUpdated)
        imported.forEach { bookId ->
            assertEquals("Science Fiction", db.books().getBook(bookId)?.genre)
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun pngBytes(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun writeSearchablePdf(target: File) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 16f
        }
        val document = PdfDocument()
        try {
            val firstPage = document.startPage(PdfDocument.PageInfo.Builder(320, 440, 1).create())
            firstPage.canvas.drawText("Inter-", 40f, 80f, paint)
            firstPage.canvas.drawText("stellar archive keeps the docking checklist.", 40f, 104f, paint)
            document.finishPage(firstPage)

            val secondPage = document.startPage(PdfDocument.PageInfo.Builder(320, 440, 2).create())
            secondPage.canvas.drawText("Second page keeps searchable PDF text.", 40f, 80f, paint)
            document.finishPage(secondPage)

            target.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private fun fictionBookXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
          <description>
            <title-info>
              <genre>Science Fiction</genre>
              <author>
                <first-name>Octavia</first-name>
                <last-name>Butler</last-name>
              </author>
              <book-title>Orbital Dawn</book-title>
              <date value="1976-01-01">1976</date>
              <lang>en</lang>
              <sequence name="Patternist" number="1"/>
            </title-info>
          </description>
          <body>
            <section>
              <title><p>First Light</p></title>
              <p>The terraforming crew watched the alien sunrise.</p>
              <p>Every instrument reported a breathable morning.</p>
            </section>
          </body>
        </FictionBook>
        """.trimIndent().trimStart()

    private fun rtfDocument(): String =
        """
        {\rtf1\ansi\deff0
        {\info{\title Field Notes}{\author Mina Patel}}
        {\fonttbl{\f0\fswiss Arial;}}
        \pard\b Field Notes\b0\par
        The observatory window caught the morning light.\par
        A second paragraph kept the import searchable.\par
        }
        """.trimIndent()

    private fun odtContentXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
            xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
          <office:body>
            <office:text>
              <text:h text:outline-level="1">Station Notes</text:h>
              <text:p>The greenhouse crew cataloged every new leaf.</text:p>
              <text:p>OpenDocument import should stay searchable.</text:p>
            </office:text>
          </office:body>
        </office:document-content>
        """.trimIndent().trimStart()

    private fun odtMetaXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
            xmlns:dc="http://purl.org/dc/elements/1.1/">
          <office:meta>
            <dc:title>Station Notes</dc:title>
            <dc:creator>Mina Patel</dc:creator>
            <dc:language>en</dc:language>
            <dc:date>2025-05-29</dc:date>
          </office:meta>
        </office:document-meta>
        """.trimIndent().trimStart()

    private fun htmlDocument(): String =
        """
        <!doctype html>
        <html lang="en-US">
          <head>
            <meta name="dc.title" content="Orbital Field Report">
            <meta name="author" content="Mina Patel">
            <meta name="keywords" content="Science Fiction">
            <meta name="date" content="2026-05-30">
          </head>
          <body>
            <h1>Arrival</h1>
            <p>The survey ship docked quietly at dawn.</p>
            <blockquote>Keep the lights low during first contact.</blockquote>
          </body>
        </html>
        """.trimIndent()

    private fun mhtmlDocument(): String =
        """
        MIME-Version: 1.0
        Content-Type: multipart/related; boundary="xreader-boundary"; type="text/html"

        --xreader-boundary
        Content-Type: text/html; charset="utf-8"
        Content-Location: https://example.test/reports/archived.html

        <!doctype html>
        <html lang="en-US">
          <head>
            <meta name="dc.title" content="Archived Field Report">
            <meta name="author" content="Mina Patel">
            <meta name="keywords" content="Science Fiction">
            <meta name="date" content="2026-05-30">
          </head>
          <body>
            <h1>Arrival</h1>
            <p>The survey ship docked inside the archived page.</p>
            <img src="images/observation.png" alt="Observation sketch">
          </body>
        </html>
        --xreader-boundary
        Content-Type: image/png
        Content-Location: https://example.test/reports/images/observation.png
        Content-ID: <observation-image>
        Content-Transfer-Encoding: base64

        ${Base64.getEncoder().encodeToString(pngBytes(Color.MAGENTA))}
        --xreader-boundary--
        """.trimIndent()

    private fun markdownDocument(): String =
        """
        ---
        title: Orbital Field Notes
        author: Mina Patel
        genre: Science Fiction
        language: en-US
        date: 2026-05-30
        ---

        # Arrival

        The survey ship docked quietly at dawn.

        - Checklist complete.
        - Greenhouse sealed.

        > Keep the lights low during first contact.
        """.trimIndent()

    private fun docxDocumentXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>
            <w:p>
              <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
              <w:r><w:t>Station Briefing</w:t></w:r>
            </w:p>
            <w:p>
              <w:r><w:t>The airlock crew rehearsed every docking step.</w:t></w:r>
            </w:p>
            <w:p>
              <w:pPr><w:numPr><w:numId w:val="1"/></w:numPr></w:pPr>
              <w:r><w:t>Oxygen reserves checked.</w:t></w:r>
            </w:p>
          </w:body>
        </w:document>
        """.trimIndent().trimStart()

    private fun docxCoreXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <cp:coreProperties
            xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
            xmlns:dc="http://purl.org/dc/elements/1.1/"
            xmlns:dcterms="http://purl.org/dc/terms/"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
          <dc:title>Station Briefing</dc:title>
          <dc:creator>Mina Patel</dc:creator>
          <dc:language>en</dc:language>
          <dc:subject>Science Fiction</dc:subject>
          <dcterms:created xsi:type="dcterms:W3CDTF">2026-05-29T10:00:00Z</dcterms:created>
        </cp:coreProperties>
        """.trimIndent().trimStart()

    private fun docxContentTypesXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
          <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
        </Types>
        """.trimIndent().trimStart()

    private fun docxRelsXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
        """.trimIndent().trimStart()

    private fun existingBook(
        author: String,
        genre: String,
    ): BookEntity =
        BookEntity(
            title = "Existing Notes",
            author = author,
            sortTitle = "existing notes",
            series = null,
            seriesIndex = null,
            genre = genre,
            year = 2026,
            description = null,
            language = "en",
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "existing-notes.epub",
            filePath = "library/books/existing-notes.epub",
            coverImagePath = null,
            checksum = "existing-notes-checksum",
            fileSizeBytes = 1024,
            wordCount = 100,
            pageCount = null,
            importedAt = 1,
            updatedAt = 1,
            lastOpenedAt = null,
            favorite = false,
            finished = false
        )
}
