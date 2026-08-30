package com.xreader.app.importer

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPackageToolsTest {
    @Test
    fun storedMimetypeIsFirstAndUncompressed() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeStored(zip, "mimetype", "application/epub+zip".toByteArray())
            writeDeflated(zip, "META-INF/container.xml", EPUB_CONTAINER_BYTES)
        }

        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            val first = zip.nextEntry
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
            assertEquals("application/epub+zip", zip.readBytes().toString(Charsets.UTF_8))
            assertEquals("META-INF/container.xml", zip.nextEntry.name)
            assertTrue(zip.readBytes().toString(Charsets.UTF_8).contains("OEBPS/package.opf"))
        }
    }

    @Test
    fun xmlEscapingCoversAllMarkupCharacters() {
        val escaped = escapeXml("A&B <tag> \"quote\" 'single'")
        assertEquals("A&amp;B &lt;tag&gt; &quot;quote&quot; &apos;single&apos;", escaped)
        assertTrue('<' !in escaped && '>' !in escaped)
    }
}
