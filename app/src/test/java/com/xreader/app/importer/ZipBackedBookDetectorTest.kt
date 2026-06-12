package com.xreader.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipBackedBookDetectorTest {
    @Test
    fun detectsZipBackedFormatsFromArchiveStructure() {
        val dir = Files.createTempDirectory("xreader-zip-detector-test").toFile()

        assertEquals("epub", zip(dir, "book.zip", "mimetype" to ascii("application/epub+zip")).detect())
        assertEquals("fb2.zip", zip(dir, "book.zip", "book.fb2" to ascii("<FictionBook/>")).detect())
        assertEquals("odt", zip(dir, "book.zip", "mimetype" to ascii("application/vnd.oasis.opendocument.text")).detect())
        assertEquals(
            "docx",
            zip(
                dir,
                "book.zip",
                "[Content_Types].xml" to ascii("<Types/>"),
                "word/document.xml" to ascii("<w:document/>")
            ).detect()
        )
        assertEquals("cbz", zip(dir, "book.zip", "page001.png" to byteArrayOf(1, 2, 3)).detect())
        assertEquals("cbz", zip(dir, "book.zip", "page001.gif" to byteArrayOf(1, 2, 3)).detect())
    }

    @Test
    fun prefersDocumentStructuresOverImageEntries() {
        val dir = Files.createTempDirectory("xreader-zip-detector-priority-test").toFile()
        val archive = zip(
            dir,
            "book.zip",
            "cover.png" to byteArrayOf(1, 2, 3),
            "META-INF/container.xml" to ascii("<container/>")
        )

        assertEquals("epub", archive.detect())
    }

    @Test
    fun ignoresUnsupportedZipAndOversizedMimetypeMarkers() {
        val dir = Files.createTempDirectory("xreader-zip-detector-unsupported-test").toFile()
        val unsupported = zip(dir, "unsupported.zip", "readme.txt" to ascii("not a book"))
        val oversizedMimetype = zip(
            dir,
            "oversized-mimetype.zip",
            "mimetype" to ascii("application/epub+zip".padEnd(ZipBackedBookDetector.MAX_CLASSIFIER_ENTRIES, 'x'))
        )

        assertNull(unsupported.detect())
        assertNull(oversizedMimetype.detect())
    }

    @Test
    fun capsEntryScanningForPathologicalArchives() {
        val dir = Files.createTempDirectory("xreader-zip-detector-cap-test").toFile()
        val archive = File(dir, "too-many-entries.zip")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            repeat(ZipBackedBookDetector.MAX_CLASSIFIER_ENTRIES) { index ->
                zip.writeEntry("notes/$index.txt", ascii("metadata"))
            }
            zip.writeEntry("page001.png", byteArrayOf(1, 2, 3))
        }

        assertNull(archive.detect())
    }

    private fun File.detect(): String? = ZipBackedBookDetector.detect(this)

    private fun zip(dir: File, name: String, vararg entries: Pair<String, ByteArray>): File =
        File(dir, "${System.nanoTime()}-$name").apply {
            parentFile?.mkdirs()
            ZipOutputStream(outputStream().buffered()).use { zip ->
                entries.forEach { (entryName, bytes) ->
                    zip.writeEntry(entryName, bytes)
                }
            }
        }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)
}
