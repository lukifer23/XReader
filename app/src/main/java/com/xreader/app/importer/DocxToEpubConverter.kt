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

class DocxToEpubConverter {
    fun convert(input: File, output: File, fallbackTitle: String): File {
        require(input.length() <= MAX_DOCX_BYTES) { "DOCX file is too large." }
        val source = DocxSource.read(input)
        val document = parseXml(source.documentXml, "DOCX document")
        val metadata = source.coreXml
            ?.let { parseMetadata(parseXml(it, "DOCX metadata"), fallbackTitle) }
            ?: DocxMetadata(title = TextTools.cleanTitle(fallbackTitle))
        val blocks = parseBlocks(document)
        val chapters = chaptersFromBlocks(blocks, metadata.title)
        require(chapters.isNotEmpty()) {
            "DOCX document contains no readable text."
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
            writeDeflated(
                zip,
                "OEBPS/nav.xhtml",
                navigationDocument(metadata.title, metadata.language, chapters).toByteArray(Charsets.UTF_8)
            )
            chapters.forEachIndexed { index, chapter ->
                writeDeflated(
                    zip,
                    "OEBPS/chapters/${chapterName(index)}.xhtml",
                    chapterDocument(chapter, metadata.language).toByteArray(Charsets.UTF_8)
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

    private fun parseMetadata(document: Document, fallbackTitle: String): DocxMetadata {
        val root = document.documentElement
        val title = TextTools.cleanTitle(root.descendantText("title") ?: fallbackTitle)
        val created = root.descendantText("created")
            ?: root.descendantText("modified")
            ?: root.descendantText("date")
        val subject = root.descendantText("subject")
            ?: root.descendantText("keywords")
        return DocxMetadata(
            title = title,
            author = root.descendantText("creator")?.takeIf { it.isNotBlank() },
            language = root.descendantText("language")?.takeIf { it.isNotBlank() } ?: "en",
            description = root.descendantText("description")?.takeIf { it.isNotBlank() },
            subject = subject?.takeIf { it.isNotBlank() },
            year = created?.let { YEAR_REGEX.find(it)?.value }
        )
    }

    private fun parseBlocks(document: Document): List<DocxBlock> {
        val body = document.documentElement.descendants("body").firstOrNull()
            ?: document.documentElement
        return buildList {
            body.collectBlocks(this)
        }.mapNotNull { block ->
            val clean = block.text
                .replace('\u00A0', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
            clean.takeIf { it.length > 1 && it.any(Char::isLetterOrDigit) }?.let {
                block.copy(text = it)
            }
        }
    }

    private fun Element.collectBlocks(output: MutableList<DocxBlock>) {
        when (local()) {
            "p" -> {
                parseParagraph()?.let { output += it }
                return
            }
            "tbl" -> {
                parseTableRows().forEach { output += it }
                return
            }
        }
        childElements().forEach { child -> child.collectBlocks(output) }
    }

    private fun Element.parseParagraph(): DocxBlock? {
        val properties = childElements().firstOrNull { it.local().equals("pPr", ignoreCase = true) }
        val text = readingText()
        if (text.isBlank()) return null
        val headingLevel = properties?.headingLevel()
        val listItem = properties?.descendants("numPr")?.isNotEmpty() == true
        val normalizedText = if (listItem && !text.trimStart().startsWithAny(LIST_PREFIXES)) {
            "- ${text.trim()}"
        } else {
            text
        }
        return DocxBlock(text = normalizedText, headingLevel = headingLevel)
    }

    private fun Element.parseTableRows(): List<DocxBlock> =
        descendants("tr").mapNotNull { row ->
            val cells = row.childElements()
                .filter { it.local().equals("tc", ignoreCase = true) }
                .mapNotNull { cell ->
                    cell.descendants("p")
                        .joinToString(" ") { it.readingText() }
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .takeIf { it.isNotBlank() }
                }
            cells.joinToString(" | ")
                .takeIf { it.isNotBlank() }
                ?.let { DocxBlock(text = it, headingLevel = null) }
        }

    private fun Element.headingLevel(): Int? {
        val style = descendants("pStyle")
            .firstNotNullOfOrNull { it.attributeValue("val") }
            ?.lowercase(Locale.US)
            ?.replace(Regex("[^a-z0-9]"), "")
        val styleLevel = when {
            style == null -> null
            style == "title" -> 1
            style == "subtitle" -> 2
            style.startsWith("heading") -> style.removePrefix("heading").toIntOrNull()
            else -> null
        }
        val outlineLevel = descendants("outlineLvl")
            .firstNotNullOfOrNull { it.attributeValue("val")?.toIntOrNull() }
            ?.plus(1)
        return (styleLevel ?: outlineLevel)?.coerceIn(1, 6)
    }

    private fun chaptersFromBlocks(blocks: List<DocxBlock>, fallbackTitle: String): List<DocxChapter> {
        if (blocks.isEmpty()) return emptyList()
        val result = mutableListOf<DocxChapter>()
        var title = fallbackTitle.ifBlank { "Document" }
        val content = mutableListOf<DocxBlock>()

        fun flush() {
            val clean = content
                .filter { it.text.length > 1 && it.text.any(Char::isLetterOrDigit) }
            if (clean.isNotEmpty()) {
                result += DocxChapter(
                    title = title.ifBlank { "Chapter ${result.size + 1}" },
                    blocks = clean
                )
            }
            content.clear()
        }

        blocks.forEach { block ->
            if (block.headingLevel == 1) {
                flush()
                title = block.text
            } else {
                content += block
            }
        }
        flush()
        if (result.isEmpty()) {
            blocks.firstOrNull { it.headingLevel != null }?.let { heading ->
                result += DocxChapter(title = heading.text, blocks = emptyList())
            }
        }
        return result.distinctBy { chapter ->
            "${chapter.title}\u0000${chapter.blocks.joinToString("\u0000") { it.text }}"
        }
    }

    private fun packageDocument(
        metadata: DocxMetadata,
        identifier: String,
        chapters: List<DocxChapter>,
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

    private fun navigationDocument(title: String, language: String, chapters: List<DocxChapter>): String {
        val tocItems = chapters.mapIndexed { index, chapter ->
            """<li><a href="chapters/${chapterName(index)}.xhtml">${escapeXml(chapter.title)}</a></li>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="${escapeXml(language)}">
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

    private fun chapterDocument(chapter: DocxChapter, language: String): String {
        val body = chapter.blocks.joinToString("\n") { block ->
            val level = block.headingLevel
            if (level != null && level > 1) {
                "<h${level}>${escapeXml(block.text)}</h${level}>"
            } else {
                "<p>${escapeXml(block.text)}</p>"
            }
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="${escapeXml(language)}">
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
            Node.ELEMENT_NODE -> {
                val element = this as Element
                when (element.local()) {
                    "t" -> element.childNodes.forEachNode { child ->
                        if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                            builder.append(child.nodeValue)
                        }
                    }
                    "tab" -> builder.append('\t')
                    "br", "cr" -> builder.append('\n')
                    "noBreakHyphen" -> builder.append('-')
                    "softHyphen" -> Unit
                    "pPr", "rPr", "tblPr", "trPr", "tcPr", "instrText", "delText", "fldChar", "bookmarkStart", "bookmarkEnd" -> Unit
                    else -> element.childNodes.forEachNode { child -> child.appendReadingText(builder) }
                }
            }
        }
    }

    private fun String.startsWithAny(prefixes: List<String>): Boolean =
        prefixes.any { startsWith(it) }

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
                element.textContent
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

    private data class DocxMetadata(
        val title: String,
        val author: String? = null,
        val language: String = "en",
        val description: String? = null,
        val subject: String? = null,
        val year: String? = null,
    )

    private data class DocxBlock(
        val text: String,
        val headingLevel: Int?,
    )

    private data class DocxChapter(
        val title: String,
        val blocks: List<DocxBlock>,
    )

    private data class DocxSource(
        val documentXml: ByteArray,
        val coreXml: ByteArray?,
        val identityBytes: ByteArray,
    ) {
        companion object {
            fun read(input: File): DocxSource =
                ZipFile(input).use { zip ->
                    val documentXml = zip.readRequiredEntry("word/document.xml", MAX_XML_BYTES)
                    val coreXml = zip.readOptionalEntry("docProps/core.xml", MAX_XML_BYTES)
                    DocxSource(
                        documentXml = documentXml,
                        coreXml = coreXml,
                        identityBytes = documentXml + (coreXml ?: ByteArray(0))
                    )
                }

            private fun ZipFile.readRequiredEntry(name: String, maxBytes: Int): ByteArray {
                val entry = getEntry(name) ?: error("DOCX archive is missing $name.")
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
        private const val MAX_DOCX_BYTES = 96L * 1024L * 1024L
        private const val MAX_XML_BYTES = 32 * 1024 * 1024
        private val YEAR_REGEX = Regex("""\d{4}""")
        private val LIST_PREFIXES = listOf("- ", "• ", "* ", "1.", "1)")
    }
}
