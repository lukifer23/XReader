package com.xreader.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OdtToEpubConverterTest {
    @Test
    fun convertsOdtMetadataAndBodyIntoParseableEpub() {
        val dir = Files.createTempDirectory("xreader-odt-test").toFile()
        val source = File(dir, "Station Notes.odt")
        val output = File(dir, "Station Notes.epub")
        writeOdt(source, contentXml(), metaXml())

        OdtToEpubConverter().convert(source, output, "Fallback")

        val parsed = EpubParser().parse(output)
        assertEquals("Station Notes", parsed.metadata.title)
        assertEquals("Mina Patel", parsed.metadata.author)
        assertEquals("en", parsed.metadata.language)
        assertEquals(2025, parsed.metadata.year)
        assertTrue(parsed.units.any { it.heading == "Arrival" && it.body.contains("The ship docked quietly") })
        assertTrue(parsed.units.any { it.body.contains("A spaced sentence") })
        assertTrue(parsed.units.any { it.body.contains("Checklist complete") })
    }

    @Test
    fun rejectsOdtWithoutReadableText() {
        val dir = Files.createTempDirectory("xreader-empty-odt-test").toFile()
        val source = File(dir, "Empty.odt")
        val output = File(dir, "Empty.epub")
        writeOdt(
            source,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
              <office:body><office:text><text:p> </text:p></office:text></office:body>
            </office:document-content>
            """.trimIndent(),
            metaXml()
        )

        val error = runCatching {
            OdtToEpubConverter().convert(source, output, "Empty")
        }.exceptionOrNull()

        assertEquals("ODT document contains no readable text.", error?.message)
    }

    @Test
    fun rejectsNonOdtArchive() {
        val dir = Files.createTempDirectory("xreader-non-odt-test").toFile()
        val source = File(dir, "Not ODT.odt")
        val output = File(dir, "Not ODT.epub")
        ZipOutputStream(source.outputStream().buffered()).use { zip ->
            zip.writeEntry("mimetype", "application/zip".toByteArray(Charsets.US_ASCII))
            zip.writeEntry("content.xml", contentXml().toByteArray(Charsets.UTF_8))
        }

        val error = runCatching {
            OdtToEpubConverter().convert(source, output, "Not ODT")
        }.exceptionOrNull()

        assertEquals("File is not an ODT document.", error?.message)
        assertFalse(output.exists())
    }

    private fun writeOdt(source: File, contentXml: String, metaXml: String) {
        ZipOutputStream(source.outputStream().buffered()).use { zip ->
            zip.writeEntry("mimetype", "application/vnd.oasis.opendocument.text".toByteArray(Charsets.US_ASCII))
            zip.writeEntry("content.xml", contentXml.toByteArray(Charsets.UTF_8))
            zip.writeEntry("meta.xml", metaXml.toByteArray(Charsets.UTF_8))
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun contentXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
            xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
            xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0">
          <office:body>
            <office:text>
              <text:h text:outline-level="1">Arrival</text:h>
              <text:p>The ship docked quietly at dawn.</text:p>
              <text:p>A<text:s text:c="2"/>spaced sentence with <text:span>inline emphasis</text:span>.</text:p>
              <text:list>
                <text:list-item><text:p>Checklist complete.</text:p></text:list-item>
              </text:list>
              <table:table>
                <table:table-row>
                  <table:table-cell><text:p>Table cell note.</text:p></table:table-cell>
                </table:table-row>
              </table:table>
            </office:text>
          </office:body>
        </office:document-content>
        """.trimIndent().trimStart()

    private fun metaXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
            xmlns:dc="http://purl.org/dc/elements/1.1/"
            xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0">
          <office:meta>
            <dc:title>Station Notes</dc:title>
            <dc:creator>Mina Patel</dc:creator>
            <dc:language>en</dc:language>
            <dc:date>2025-05-29T10:00:00</dc:date>
            <dc:description>A compact ODT fixture.</dc:description>
            <dc:subject>Science Fiction</dc:subject>
          </office:meta>
        </office:document-meta>
        """.trimIndent().trimStart()
}
