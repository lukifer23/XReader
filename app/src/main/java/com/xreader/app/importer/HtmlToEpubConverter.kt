package com.xreader.app.importer

import com.xreader.app.core.TextTools
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

class HtmlToEpubConverter {
    fun convert(input: File, output: File, fallbackTitle: String): File {
        require(input.length() <= MAX_HTML_BYTES) { "HTML file is too large." }
        val bytes = input.readBytes()
        val document = Jsoup.parse(bytes.inputStream(), null, "")
        val metadata = parseMetadata(document, fallbackTitle)
        val blocks = parseBlocks(document.body())
        val chapters = chaptersFromBlocks(blocks, metadata.title)
        require(chapters.isNotEmpty()) {
            "HTML document contains no readable text."
        }
        val identifier = "urn:uuid:${UUID.nameUUIDFromBytes(bytes)}"

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

    private fun parseMetadata(document: Document, fallbackTitle: String): HtmlMetadata {
        val title = TextTools.cleanTitle(
            document.metaContent("dc.title", "dcterms.title", "og:title")
                ?: document.title().takeIf { it.isNotBlank() }
                ?: fallbackTitle
        )
        val date = document.metaContent("date", "dc.date", "dcterms.date", "dcterms.created", "dcterms.issued", "article:published_time")
        return HtmlMetadata(
            title = title,
            author = document.metaContent("author", "creator", "dc.creator", "dcterms.creator", "article:author"),
            language = document.selectFirst("html[lang]")?.attr("lang")?.cleanMetadataValue()
                ?: document.metaContent("language", "dc.language", "content-language")
                ?: "en",
            description = document.metaContent("description", "dc.description", "dcterms.description", "og:description"),
            subject = document.metaContent("keywords", "subject", "dc.subject", "dcterms.subject"),
            year = date?.let { YEAR_REGEX.find(it)?.value }
        )
    }

    private fun parseBlocks(root: Element): List<HtmlBlock> =
        buildList {
            root.collectBlocks(this)
        }.mapNotNull { block ->
            val clean = block.text.cleanBlockText(preformatted = block.kind == HtmlBlockKind.PREFORMATTED)
            clean.takeIf { it.length > 1 && it.any(Char::isLetterOrDigit) }?.let {
                block.copy(text = it)
            }
        }

    private fun Element.collectBlocks(output: MutableList<HtmlBlock>) {
        val tag = normalizedTag()
        if (tag in SKIP_TAGS) return
        when {
            tag.matches(HEADING_TAG_REGEX) -> {
                readingText().toReadableBlock()?.let { text ->
                    output += HtmlBlock(
                        text = text,
                        kind = HtmlBlockKind.HEADING,
                        headingLevel = tag.removePrefix("h").toIntOrNull()?.coerceIn(1, 6) ?: 1
                    )
                }
            }
            tag in PARAGRAPH_TAGS -> {
                readingText().toReadableBlock()?.let { output += HtmlBlock(it, HtmlBlockKind.PARAGRAPH) }
            }
            tag == "blockquote" -> {
                readingText().toReadableBlock()?.let { output += HtmlBlock(it, HtmlBlockKind.QUOTE) }
            }
            tag == "pre" -> {
                wholeText().cleanBlockText(preformatted = true)
                    .takeIf { it.isNotBlank() }
                    ?.let { output += HtmlBlock(it, HtmlBlockKind.PREFORMATTED) }
            }
            tag == "li" -> {
                readingText().toReadableBlock()?.let { output += HtmlBlock(it, HtmlBlockKind.LIST_ITEM) }
            }
            tag == "tr" -> {
                tableRowText().toReadableBlock()?.let { output += HtmlBlock(it, HtmlBlockKind.PARAGRAPH) }
            }
            else -> collectContainerBlocks(output)
        }
    }

    private fun Element.collectContainerBlocks(output: MutableList<HtmlBlock>) {
        val inline = StringBuilder()
        fun flushInline() {
            inline.toString().toReadableBlock()?.let { output += HtmlBlock(it, HtmlBlockKind.PARAGRAPH) }
            inline.clear()
        }

        childNodes().forEach { node ->
            when (node) {
                is TextNode -> inline.appendInline(node.text())
                is Element -> {
                    val tag = node.normalizedTag()
                    when {
                        tag in SKIP_TAGS -> Unit
                        tag == "br" -> inline.append('\n')
                        node.isBlockBoundary() -> {
                            flushInline()
                            node.collectBlocks(output)
                        }
                        else -> inline.appendInline(node.readingText())
                    }
                }
            }
        }
        flushInline()
    }

    private fun Element.isBlockBoundary(): Boolean =
        normalizedTag() in BLOCK_TAGS || normalizedTag().matches(HEADING_TAG_REGEX)

    private fun Element.tableRowText(): String =
        select("> th, > td")
            .mapNotNull { it.readingText().toReadableBlock() }
            .joinToString(" | ")

    private fun Element.readingText(): String {
        val builder = StringBuilder()
        childNodes().forEach { node -> node.appendReadingText(builder) }
        return builder.toString()
    }

    private fun Node.appendReadingText(builder: StringBuilder) {
        when (this) {
            is TextNode -> builder.appendInline(text())
            is Element -> {
                val tag = normalizedTag()
                when {
                    tag in SKIP_TAGS -> Unit
                    tag == "br" -> builder.append('\n')
                    tag == "img" -> builder.appendInline(attr("alt").ifBlank { attr("title") })
                    else -> childNodes().forEach { child -> child.appendReadingText(builder) }
                }
            }
        }
    }

    private fun chaptersFromBlocks(blocks: List<HtmlBlock>, fallbackTitle: String): List<HtmlChapter> {
        if (blocks.isEmpty()) return emptyList()
        val result = mutableListOf<HtmlChapter>()
        var title = fallbackTitle.ifBlank { "Document" }
        val content = mutableListOf<HtmlBlock>()

        fun flush() {
            val clean = content.filter { it.text.length > 1 && it.text.any(Char::isLetterOrDigit) }
            if (clean.isNotEmpty()) {
                result += HtmlChapter(
                    title = title.ifBlank { "Chapter ${result.size + 1}" },
                    blocks = clean
                )
            }
            content.clear()
        }

        blocks.forEach { block ->
            val headingLevel = block.headingLevel ?: Int.MAX_VALUE
            if (block.kind == HtmlBlockKind.HEADING && headingLevel <= 2) {
                flush()
                title = block.text
            } else {
                content += block
            }
        }
        flush()

        if (result.isEmpty()) {
            val first = blocks.first()
            result += HtmlChapter(
                title = first.takeIf { it.kind == HtmlBlockKind.HEADING }?.text ?: title,
                blocks = blocks.filter { it.kind != HtmlBlockKind.HEADING }
                    .ifEmpty { listOf(HtmlBlock(first.text, HtmlBlockKind.PARAGRAPH)) }
            )
        }

        return result.distinctBy { chapter ->
            "${chapter.title}\u0000${chapter.blocks.joinToString("\u0000") { it.text }}"
        }
    }

    private fun packageDocument(
        metadata: HtmlMetadata,
        identifier: String,
        chapters: List<HtmlChapter>,
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

    private fun navigationDocument(title: String, language: String, chapters: List<HtmlChapter>): String {
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

    private fun chapterDocument(chapter: HtmlChapter, language: String): String {
        val body = chapter.blocks.joinToString("\n") { block ->
            when (block.kind) {
                HtmlBlockKind.HEADING -> {
                    val level = (block.headingLevel ?: 2).coerceIn(2, 6)
                    "<h$level>${escapeXml(block.text)}</h$level>"
                }
                HtmlBlockKind.LIST_ITEM -> "<ul><li>${escapeXml(block.text)}</li></ul>"
                HtmlBlockKind.QUOTE -> "<blockquote><p>${escapeXml(block.text)}</p></blockquote>"
                HtmlBlockKind.PREFORMATTED -> "<pre>${escapeXml(block.text)}</pre>"
                HtmlBlockKind.PARAGRAPH -> "<p>${escapeXml(block.text)}</p>"
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

    private fun Document.metaContent(vararg keys: String): String? {
        val normalizedKeys = keys.map { it.lowercase(Locale.US) }.toSet()
        select("meta").forEach { meta ->
            val candidateKeys = listOf("name", "property", "itemprop", "http-equiv")
                .mapNotNull { attribute -> meta.attr(attribute).cleanMetadataValue()?.lowercase(Locale.US) }
            if (candidateKeys.any { it in normalizedKeys }) {
                meta.attr("content").cleanMetadataValue()?.let { return it }
            }
        }
        return null
    }

    private fun Element.normalizedTag(): String =
        tagName().lowercase(Locale.US)

    private fun StringBuilder.appendInline(value: String) {
        val clean = value.cleanBlockText(preformatted = false)
        if (clean.isBlank()) return
        if (isNotEmpty() && last() != '\n' && last() != ' ') append(' ')
        append(clean)
    }

    private fun String.toReadableBlock(): String? =
        cleanBlockText(preformatted = false).takeIf { it.isNotBlank() }

    private fun String.cleanBlockText(preformatted: Boolean): String {
        val normalized = replace('\u00A0', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        return if (preformatted) {
            normalized.trim()
        } else {
            normalized.replace(Regex("\\s+"), " ").trim()
        }
    }

    private fun String.cleanMetadataValue(): String? =
        trim().replace(Regex("\\s+"), " ").takeIf { it.isNotBlank() }

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

    private data class HtmlMetadata(
        val title: String,
        val author: String? = null,
        val language: String = "en",
        val description: String? = null,
        val subject: String? = null,
        val year: String? = null,
    )

    private data class HtmlBlock(
        val text: String,
        val kind: HtmlBlockKind,
        val headingLevel: Int? = null,
    )

    private enum class HtmlBlockKind {
        PARAGRAPH,
        HEADING,
        LIST_ITEM,
        QUOTE,
        PREFORMATTED,
    }

    private data class HtmlChapter(
        val title: String,
        val blocks: List<HtmlBlock>,
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
        private const val MAX_HTML_BYTES = 32L * 1024L * 1024L
        private val YEAR_REGEX = Regex("""\d{4}""")
        private val HEADING_TAG_REGEX = Regex("""h[1-6]""")
        private val SKIP_TAGS = setOf("script", "style", "noscript", "template", "svg", "canvas", "form", "nav")
        private val PARAGRAPH_TAGS = setOf("p", "address", "figcaption")
        private val BLOCK_TAGS = setOf(
            "article",
            "aside",
            "blockquote",
            "body",
            "dd",
            "div",
            "dl",
            "dt",
            "figcaption",
            "figure",
            "footer",
            "header",
            "li",
            "main",
            "ol",
            "p",
            "pre",
            "section",
            "table",
            "tbody",
            "td",
            "tfoot",
            "th",
            "thead",
            "tr",
            "ul"
        )
    }
}
