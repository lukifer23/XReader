package com.xreader.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxToEpubConverterTest {
    @Test
    fun convertsDocxMetadataAndReadingOrderIntoParseableEpub() {
        val dir = Files.createTempDirectory("xreader-docx-test").toFile()
        val source = File(dir, "Station Notes.docx")
        val output = File(dir, "Station Notes.epub")
        writeDocx(source, documentXml(), coreXml())

        DocxToEpubConverter().convert(source, output, "Fallback")

        val parsed = EpubParser().parse(output)
        assertEquals("Station Notes", parsed.metadata.title)
        assertEquals("Mina Patel", parsed.metadata.author)
        assertEquals("en", parsed.metadata.language)
        assertEquals("Science Fiction", parsed.metadata.genre)
        assertEquals(2026, parsed.metadata.year)
        assertTrue(parsed.units.any { it.heading == "Arrival" && it.body.contains("The ship docked quietly") })
        assertTrue(parsed.units.any { it.body.contains("Greenhouse notes") })
        assertTrue(parsed.units.any { it.body.contains("- Checklist complete") })
        assertTrue(parsed.units.any { it.body.contains("Table cell note | EVA ready") })
    }

    @Test
    fun rejectsDocxWithoutReadableText() {
        val dir = Files.createTempDirectory("xreader-empty-docx-test").toFile()
        val source = File(dir, "Empty.docx")
        val output = File(dir, "Empty.epub")
        writeDocx(
            source,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p><w:r><w:t> </w:t></w:r></w:p></w:body>
            </w:document>
            """.trimIndent(),
            coreXml()
        )

        val error = runCatching {
            DocxToEpubConverter().convert(source, output, "Empty")
        }.exceptionOrNull()

        assertEquals("DOCX document contains no readable text.", error?.message)
    }

    @Test
    fun rejectsArchiveWithoutDocumentXml() {
        val dir = Files.createTempDirectory("xreader-non-docx-test").toFile()
        val source = File(dir, "Not DOCX.docx")
        val output = File(dir, "Not DOCX.epub")
        ZipOutputStream(source.outputStream().buffered()).use { zip ->
            zip.writeEntry("[Content_Types].xml", "<Types/>".toByteArray(Charsets.UTF_8))
        }

        val error = runCatching {
            DocxToEpubConverter().convert(source, output, "Not DOCX")
        }.exceptionOrNull()

        assertEquals("DOCX archive is missing word/document.xml.", error?.message)
        assertFalse(output.exists())
    }

    private fun writeDocx(source: File, documentXml: String, coreXml: String) {
        ZipOutputStream(source.outputStream().buffered()).use { zip ->
            zip.writeEntry("[Content_Types].xml", contentTypesXml().toByteArray(Charsets.UTF_8))
            zip.writeEntry("_rels/.rels", relsXml().toByteArray(Charsets.UTF_8))
            zip.writeEntry("docProps/core.xml", coreXml.toByteArray(Charsets.UTF_8))
            zip.writeEntry("word/document.xml", documentXml.toByteArray(Charsets.UTF_8))
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun documentXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>
            <w:p>
              <w:pPr><w:pStyle w:val="Title"/></w:pPr>
              <w:r><w:t>Station Notes</w:t></w:r>
            </w:p>
            <w:p>
              <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
              <w:r><w:t>Arrival</w:t></w:r>
            </w:p>
            <w:p>
              <w:r><w:t>The ship docked quietly at dawn.</w:t></w:r>
            </w:p>
            <w:p>
              <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
              <w:r><w:t>Greenhouse notes</w:t></w:r>
            </w:p>
            <w:p>
              <w:pPr><w:numPr><w:numId w:val="1"/></w:numPr></w:pPr>
              <w:r><w:t>Checklist complete.</w:t></w:r>
            </w:p>
            <w:tbl>
              <w:tr>
                <w:tc><w:p><w:r><w:t>Table cell note</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>EVA ready</w:t></w:r></w:p></w:tc>
              </w:tr>
            </w:tbl>
          </w:body>
        </w:document>
        """.trimIndent().trimStart()

    private fun coreXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <cp:coreProperties
            xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
            xmlns:dc="http://purl.org/dc/elements/1.1/"
            xmlns:dcterms="http://purl.org/dc/terms/"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
          <dc:title>Station Notes</dc:title>
          <dc:creator>Mina Patel</dc:creator>
          <dc:language>en</dc:language>
          <dc:subject>Science Fiction</dc:subject>
          <dcterms:created xsi:type="dcterms:W3CDTF">2026-05-29T10:00:00Z</dcterms:created>
          <dc:description>A compact DOCX fixture.</dc:description>
        </cp:coreProperties>
        """.trimIndent().trimStart()

    private fun contentTypesXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
          <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
        </Types>
        """.trimIndent().trimStart()

    private fun relsXml(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
        """.trimIndent().trimStart()
}
