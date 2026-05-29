package com.xreader.app.importer

import com.xreader.app.core.TextTools
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class CbzToEpubConverter {
    data class ConversionResult(
        val file: File,
        val pageCount: Int,
    )

    fun convert(input: File, output: File, title: String): ConversionResult {
        ZipFile(input).use { archive ->
            val pages = archive.entries().asSequence()
                .filterNot { it.isDirectory }
                .filterNot { it.name.isArchiveMetadataPath() }
                .mapNotNull { entry ->
                    val extension = normalizedImageExtension(entry.name) ?: return@mapNotNull null
                    CbzPage(
                        entry = entry,
                        sortName = entry.name,
                        outputExtension = extension,
                        mediaType = imageMediaType(extension)
                    )
                }
                .sortedWith { left, right -> naturalCompare(left.sortName, right.sortName) }
                .toList()

            require(pages.isNotEmpty()) {
                "CBZ archive contains no supported image pages."
            }

            val safeTitle = TextTools.cleanTitle(title)
            val identifier = "urn:uuid:${UUID.nameUUIDFromBytes(identifierSeed(safeTitle, pages))}"
            output.parentFile?.mkdirs()
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                writeStored(zip, "mimetype", EPUB_MIME_TYPE.toByteArray(Charsets.US_ASCII))
                writeDeflated(zip, "META-INF/container.xml", containerXml.toByteArray(Charsets.UTF_8))
                writeDeflated(zip, "OEBPS/package.opf", packageDocument(safeTitle, identifier, pages).toByteArray(Charsets.UTF_8))
                writeDeflated(zip, "OEBPS/nav.xhtml", navigationDocument(safeTitle, pages.size).toByteArray(Charsets.UTF_8))
                pages.forEachIndexed { index, page ->
                    writeDeflated(
                        zip = zip,
                        name = "OEBPS/pages/${pageName(index)}.xhtml",
                        bytes = pageDocument(safeTitle, index, page).toByteArray(Charsets.UTF_8)
                    )
                    zip.putNextEntry(ZipEntry("OEBPS/images/${pageName(index)}.${page.outputExtension}"))
                    archive.getInputStream(page.entry).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            return ConversionResult(file = output, pageCount = pages.size)
        }
    }

    private fun packageDocument(
        title: String,
        identifier: String,
        pages: List<CbzPage>,
    ): String {
        val imageItems = pages.mapIndexed { index, page ->
            val properties = if (index == 0) """ properties="cover-image"""" else ""
            """<item id="image-${pageNumber(index)}" href="images/${pageName(index)}.${page.outputExtension}" media-type="${page.mediaType}"$properties/>"""
        }
        val pageItems = pages.mapIndexed { index, _ ->
            """<item id="page-${pageNumber(index)}" href="pages/${pageName(index)}.xhtml" media-type="application/xhtml+xml"/>"""
        }
        val spineItems = pages.mapIndexed { index, _ ->
            """<itemref idref="page-${pageNumber(index)}"/>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" prefix="rendition: http://www.idpf.org/vocab/rendition/#">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="book-id">${escapeXml(identifier)}</dc:identifier>
                <dc:title>${escapeXml(title)}</dc:title>
                <dc:language>en</dc:language>
                <meta property="rendition:layout">pre-paginated</meta>
                <meta property="rendition:spread">none</meta>
                <meta property="rendition:orientation">auto</meta>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                ${pageItems.joinToString("\n                ")}
                ${imageItems.joinToString("\n                ")}
              </manifest>
              <spine>
                ${spineItems.joinToString("\n                ")}
              </spine>
            </package>
        """.trimIndent()
    }

    private fun navigationDocument(title: String, pageCount: Int): String {
        val tocItems = (0 until pageCount).joinToString("\n") { index ->
            """<li><a href="pages/${pageName(index)}.xhtml">Page ${index + 1}</a></li>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en">
              <head><title>${escapeXml(title)}</title></head>
              <body>
                <nav epub:type="toc" id="toc">
                  <ol>
                    $tocItems
                  </ol>
                </nav>
              </body>
            </html>
        """.trimIndent()
    }

    private fun pageDocument(title: String, index: Int, page: CbzPage): String =
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en">
              <head>
                <title>${escapeXml(title)} - Page ${index + 1}</title>
                <style>
                  html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    background: #000;
                  }
                  body {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                  }
                  img {
                    display: block;
                    max-width: 100%;
                    max-height: 100vh;
                    width: 100%;
                    height: 100vh;
                    object-fit: contain;
                  }
                </style>
              </head>
              <body>
                <section epub:type="pagebreak">
                  <img src="../images/${pageName(index)}.${page.outputExtension}" alt=""/>
                </section>
              </body>
            </html>
        """.trimIndent()

    private fun identifierSeed(title: String, pages: List<CbzPage>): ByteArray =
        buildString {
            append(title).append('\n')
            pages.forEach { page ->
                append(page.entry.name)
                    .append('\t')
                    .append(page.entry.size)
                    .append('\t')
                    .append(page.entry.crc)
                    .append('\n')
            }
        }.toByteArray(Charsets.UTF_8)

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

    private fun pageName(index: Int): String = "page-${pageNumber(index)}"

    private fun pageNumber(index: Int): String = "%04d".format(Locale.US, index + 1)

    private fun normalizedImageExtension(path: String): String? =
        when (TextTools.extension(path.substringBeforeLast('#'))) {
            "jpg", "jpeg" -> "jpg"
            "png" -> "png"
            "webp" -> "webp"
            else -> null
        }

    private fun imageMediaType(extension: String): String =
        when (extension) {
            "jpg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }

    private fun String.isArchiveMetadataPath(): Boolean {
        val normalized = replace('\\', '/').trimStart('/')
        return normalized.startsWith("__MACOSX/", ignoreCase = true) ||
            normalized.substringAfterLast('/').startsWith(".")
    }

    private fun naturalCompare(left: String, right: String): Int {
        val leftTokens = NATURAL_TOKEN_REGEX.findAll(left.normalizedSortPath()).map { it.value }.toList()
        val rightTokens = NATURAL_TOKEN_REGEX.findAll(right.normalizedSortPath()).map { it.value }.toList()
        val size = minOf(leftTokens.size, rightTokens.size)
        for (index in 0 until size) {
            val comparison = compareToken(leftTokens[index], rightTokens[index])
            if (comparison != 0) return comparison
        }
        return leftTokens.size.compareTo(rightTokens.size)
    }

    private fun compareToken(left: String, right: String): Int {
        val leftNumber = left.takeIf { it.all(Char::isDigit) }
        val rightNumber = right.takeIf { it.all(Char::isDigit) }
        if (leftNumber != null && rightNumber != null) {
            val leftTrimmed = leftNumber.trimStart('0').ifBlank { "0" }
            val rightTrimmed = rightNumber.trimStart('0').ifBlank { "0" }
            return leftTrimmed.length.compareTo(rightTrimmed.length).takeIf { it != 0 }
                ?: leftTrimmed.compareTo(rightTrimmed).takeIf { it != 0 }
                ?: leftNumber.length.compareTo(rightNumber.length)
        }
        return left.compareTo(right)
    }

    private fun String.normalizedSortPath(): String =
        replace('\\', '/').lowercase(Locale.US)

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private data class CbzPage(
        val entry: ZipEntry,
        val sortName: String,
        val outputExtension: String,
        val mediaType: String,
    )

    private val containerXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private companion object {
        private const val EPUB_MIME_TYPE = "application/epub+zip"
        private val NATURAL_TOKEN_REGEX = Regex("\\d+|\\D+")
    }
}
