package com.xreader.app.importer

import com.xreader.app.core.TextTools
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class OdtToEpubConverter {
    fun convert(input: File, output: File, fallbackTitle: String): File {
        require(input.length() <= MAX_ODT_BYTES) { "ODT file is too large." }
        val source = OdtSource.read(input)
        require(source.mimetype == ODT_MIME_TYPE) {
            "File is not an ODT document."
        }

        val content = parseXml(source.contentXml, "ODT content")
        val metadata = source.metaXml
            ?.let { parseMetadata(parseXml(it, "ODT metadata"), fallbackTitle) }
            ?: OdtMetadata(title = TextTools.cleanTitle(fallbackTitle))
        val blocks = parseBlocks(content)
        val chapters = chaptersFromBlocks(blocks, metadata.title)
        require(chapters.isNotEmpty()) {
            "ODT document contains no readable text."
        }
        val identifier = "urn:uuid:${UUID.nameUUIDFromBytes(source.identityBytes)}"

        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            writeStored(zip, "mimetype", EPUB_MIME_TYPE.toByteArray(Charsets.US_ASCII))
            writeDeflated(zip, "META-INF/container.xml", containerXml.toByteArray(Charsets.UTF_8))
            writeDeflated(
                zip,
                "OEBPS/package.opf",
                packageDocument(metadata, identifier, chapters).toByteArray(Charsets.UTF_8)
            )
            writeDeflated(zip, "OEBPS/nav.xhtml", navigationDocument(metadata.title, chapters).toByteArray(Charsets.UTF_8))
            chapters.forEachIndexed { index, chapter ->
                writeDeflated(
                    zip,
                    "OEBPS/chapters/${chapterName(index)}.xhtml",
                    chapterDocument(chapter).toByteArray(Charsets.UTF_8)
                )
            }
        }
        return output
    }

    private fun parseXml(bytes: ByteArray, label: String): Document {
        val prelude = bytes.decodeToString(endIndex = bytes.size.coerceAtMost(2_048))
        require(!prelude.contains("<!DOCTYPE", ignoreCase = true)) {
            "$label doctype declarations are not supported."
        }
        return DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                trySetFeature("http://xml.org/sax/features/external-general-entities", false)
                trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            .newDocumentBuilder()
            .parse(bytes.inputStream())
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun parseMetadata(document: Document, fallbackTitle: String): OdtMetadata {
        val meta = document.documentElement.descendants("meta").firstOrNull()
            ?: document.documentElement
        val title = TextTools.cleanTitle(
            meta.descendantText("title")
                ?: fallbackTitle
        )
        val author = meta.descendantText("creator")
            ?: meta.descendantText("initial-creator")
        val date = meta.descendantText("date")
            ?: meta.descendantText("creation-date")
        return OdtMetadata(
            title = title,
            author = author?.takeIf { it.isNotBlank() },
            language = meta.descendantText("language")?.takeIf { it.isNotBlank() } ?: "en",
            description = meta.descendantText("description")?.takeIf { it.isNotBlank() },
            subject = meta.descendantText("subject")?.takeIf { it.isNotBlank() },
            year = date?.let { YEAR_REGEX.find(it)?.value }
        )
    }

    private fun parseBlocks(document: Document): List<OdtBlock> {
        val textRoot = document.documentElement.descendants("text").firstOrNull()
            ?: document.documentElement
        return buildList {
            textRoot.collectTextBlocks(this)
        }.mapNotNull { block ->
            val clean = block.text.replace(Regex("\\s+"), " ").trim()
            clean.takeIf { it.length > 1 && it.any(Char::isLetterOrDigit) }?.let {
                block.copy(text = it)
            }
        }
    }

    private fun Element.collectTextBlocks(output: MutableList<OdtBlock>) {
        when (local()) {
            "p" -> {
                output += OdtBlock(text = readingText(), heading = false)
                return
            }
            "h" -> {
                output += OdtBlock(text = readingText(), heading = true)
                return
            }
            "annotation", "notes", "tracked-changes", "change-info" -> return
        }
        childElements().forEach { child -> child.collectTextBlocks(output) }
    }

    private fun chaptersFromBlocks(blocks: List<OdtBlock>, fallbackTitle: String): List<OdtChapter> {
        if (blocks.isEmpty()) return emptyList()
        val result = mutableListOf<OdtChapter>()
        var title = fallbackTitle.ifBlank { "Document" }
        val paragraphs = mutableListOf<String>()

        fun flush() {
            val cleanParagraphs = paragraphs
                .map { it.replace(Regex("\\s+"), " ").trim() }
                .filter { it.length > 1 && it.any(Char::isLetterOrDigit) }
            if (cleanParagraphs.isNotEmpty()) {
                result += OdtChapter(title = title.ifBlank { "Chapter ${result.size + 1}" }, paragraphs = cleanParagraphs)
            }
            paragraphs.clear()
        }

        blocks.forEach { block ->
            if (block.heading) {
                flush()
                title = block.text
            } else {
                paragraphs += block.text
            }
        }
        flush()
        if (result.isEmpty()) {
            val headingOnlyText = blocks.firstOrNull { it.heading }?.text
            headingOnlyText?.let {
                result += OdtChapter(title = it, paragraphs = listOf(it))
            }
        }
        return result
    }

    private fun packageDocument(
        metadata: OdtMetadata,
        identifier: String,
        chapters: List<OdtChapter>,
    ): String {
        val optionalMetadata = buildList {
            metadata.author?.let { add("<dc:creator>${escapeXml(it)}</dc:creator>") }
            metadata.subject?.let { add("<dc:subject>${escapeXml(it)}</dc:subject>") }
            metadata.year?.let { add("<dc:date>${escapeXml(it)}</dc:date>") }
            metadata.description?.let { add("<dc:description>${escapeXml(it)}</dc:description>") }
        }
        val chapterItems = chapters.mapIndexed { index, _ ->
            """<item id="chapter-${chapterNumber(index)}" href="chapters/${chapterName(index)}.xhtml" media-type="application/xhtml+xml"/>"""
        }
        val spineItems = chapters.mapIndexed { index, _ ->
            """<itemref idref="chapter-${chapterNumber(index)}"/>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="book-id">${escapeXml(identifier)}</dc:identifier>
                <dc:title>${escapeXml(metadata.title)}</dc:title>
                <dc:language>${escapeXml(metadata.language)}</dc:language>
                ${optionalMetadata.joinToString("\n                ")}
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                ${chapterItems.joinToString("\n                ")}
              </manifest>
              <spine>
                ${spineItems.joinToString("\n                ")}
              </spine>
            </package>
        """.trimIndent()
    }

    private fun navigationDocument(title: String, chapters: List<OdtChapter>): String {
        val tocItems = chapters.mapIndexed { index, chapter ->
            """<li><a href="chapters/${chapterName(index)}.xhtml">${escapeXml(chapter.title)}</a></li>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en">
              <head><title>${escapeXml(title)}</title></head>
              <body>
                <nav epub:type="toc" id="toc">
                  <ol>
                    ${tocItems.joinToString("\n                    ")}
                  </ol>
                </nav>
              </body>
            </html>
        """.trimIndent()
    }

    private fun chapterDocument(chapter: OdtChapter): String {
        val body = chapter.paragraphs.joinToString("\n") { "<p>${escapeXml(it)}</p>" }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="en">
              <head><title>${escapeXml(chapter.title)}</title></head>
              <body>
                <h1>${escapeXml(chapter.title)}</h1>
                $body
              </body>
            </html>
        """.trimIndent()
    }

    private fun Element.readingText(): String {
        val builder = StringBuilder()
        appendReadingText(builder)
        return builder.toString()
    }

    private fun Node.appendReadingText(builder: StringBuilder) {
        when (nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> builder.append(nodeValue)
            Node.ELEMENT_NODE -> {
                val element = this as Element
                when (element.local()) {
                    "s" -> repeat(element.repeatCount()) { builder.append(' ') }
                    "tab" -> builder.append('\t')
                    "line-break" -> builder.append('\n')
                    "note", "annotation" -> return
                    else -> element.childNodes.forEachNode { child -> child.appendReadingText(builder) }
                }
            }
        }
    }

    private fun Element.repeatCount(): Int =
        attributeValue("c")?.toIntOrNull()?.coerceIn(1, 80) ?: 1

    private fun Element.attributeValue(localName: String): String? {
        val attrs = attributes
        for (index in 0 until attrs.length) {
            val attr = attrs.item(index)
            val attrLocal = attr.localName ?: attr.nodeName.substringAfter(':')
            if (attrLocal.equals(localName, ignoreCase = true)) {
                return attr.nodeValue.trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun Element.descendantText(localName: String): String? =
        descendants(localName)
            .firstNotNullOfOrNull { element ->
                element.readingText()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .takeIf { it.isNotBlank() }
            }

    private fun Element.descendants(localName: String): List<Element> =
        buildList {
            val nodes = getElementsByTagName("*")
            for (index in 0 until nodes.length) {
                val item = nodes.item(index) as? Element ?: continue
                if (item.local().equals(localName, ignoreCase = true)) add(item)
            }
        }

    private fun Element.childElements(): List<Element> =
        buildList {
            childNodes.forEachNode { child ->
                if (child.nodeType == Node.ELEMENT_NODE) add(child as Element)
            }
        }

    private fun org.w3c.dom.NodeList.forEachNode(block: (Node) -> Unit) {
        for (index in 0 until length) block(item(index))
    }

    private fun Element.local(): String =
        localName ?: nodeName.substringAfter(':')

    private fun chapterName(index: Int): String = "chapter-${chapterNumber(index)}"

    private fun chapterNumber(index: Int): String = "%04d".format(Locale.US, index + 1)

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

    private data class OdtMetadata(
        val title: String,
        val author: String? = null,
        val language: String = "en",
        val description: String? = null,
        val subject: String? = null,
        val year: String? = null,
    )

    private data class OdtBlock(
        val text: String,
        val heading: Boolean,
    )

    private data class OdtChapter(
        val title: String,
        val paragraphs: List<String>,
    )

    private data class OdtSource(
        val mimetype: String?,
        val contentXml: ByteArray,
        val metaXml: ByteArray?,
        val identityBytes: ByteArray,
    ) {
        companion object {
            fun read(input: File): OdtSource =
                ZipFile(input).use { zip ->
                    val mimetype = zip.getEntry("mimetype")
                        ?.let { entry ->
                            zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.US_ASCII).trim()
                        }
                    val contentXml = zip.readRequiredEntry("content.xml", MAX_XML_BYTES)
                    val metaXml = zip.readOptionalEntry("meta.xml", MAX_XML_BYTES)
                    OdtSource(
                        mimetype = mimetype,
                        contentXml = contentXml,
                        metaXml = metaXml,
                        identityBytes = contentXml + (metaXml ?: ByteArray(0))
                    )
                }

            private fun ZipFile.readRequiredEntry(name: String, maxBytes: Int): ByteArray {
                val entry = getEntry(name) ?: error("ODT archive is missing $name.")
                return readEntry(entry, maxBytes)
            }

            private fun ZipFile.readOptionalEntry(name: String, maxBytes: Int): ByteArray? {
                val entry = getEntry(name) ?: return null
                return readEntry(entry, maxBytes)
            }

            private fun ZipFile.readEntry(entry: ZipEntry, maxBytes: Int): ByteArray {
                if (entry.size > maxBytes) error("${entry.name} is too large.")
                val bytes = getInputStream(entry).use { it.readBytes() }
                require(bytes.size <= maxBytes) { "${entry.name} is too large." }
                return bytes
            }
        }
    }

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
        private const val ODT_MIME_TYPE = "application/vnd.oasis.opendocument.text"
        private const val MAX_ODT_BYTES = 96L * 1024L * 1024L
        private const val MAX_XML_BYTES = 32 * 1024 * 1024
        private val YEAR_REGEX = Regex("""\d{4}""")
    }
}
