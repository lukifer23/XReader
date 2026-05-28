package com.xreader.app.importer

import com.xreader.app.core.TextTools
import java.io.File
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TxtToEpubConverter {
    fun convert(input: File, output: File, title: String): File {
        val raw = input.readText(Charsets.UTF_8)
        val paragraphs = raw.replace("\r\n", "\n")
            .split(Regex("\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(raw.trim()) }

        val safeTitle = TextTools.cleanTitle(title)
        val identifier = "urn:uuid:${UUID.nameUUIDFromBytes(raw.toByteArray(Charsets.UTF_8))}"
        val body = paragraphs.joinToString("\n") { "<p>${escapeXml(it)}</p>" }
        val xhtml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="en">
              <head><title>${escapeXml(safeTitle)}</title></head>
              <body>
                <h1>${escapeXml(safeTitle)}</h1>
                $body
              </body>
            </html>
        """.trimIndent()

        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="book-id">$identifier</dc:identifier>
                <dc:title>${escapeXml(safeTitle)}</dc:title>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="content" href="content.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="content"/>
              </spine>
            </package>
        """.trimIndent()

        val nav = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="en">
              <head><title>Contents</title></head>
              <body><nav epub:type="toc" xmlns:epub="http://www.idpf.org/2007/ops"><ol><li><a href="content.xhtml">${escapeXml(safeTitle)}</a></li></ol></nav></body>
            </html>
        """.trimIndent()

        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            writeStored(zip, "mimetype", "application/epub+zip".toByteArray(Charsets.US_ASCII))
            writeDeflated(zip, "META-INF/container.xml", containerXml.toByteArray())
            writeDeflated(zip, "OEBPS/package.opf", opf.toByteArray())
            writeDeflated(zip, "OEBPS/nav.xhtml", nav.toByteArray())
            writeDeflated(zip, "OEBPS/content.xhtml", xhtml.toByteArray())
        }
        return output
    }

    private fun writeStored(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeDeflated(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private val containerXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()
}
