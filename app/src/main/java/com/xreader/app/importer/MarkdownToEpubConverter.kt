package com.xreader.app.importer

import com.xreader.app.core.TextTools
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.jsoup.Jsoup

class MarkdownToEpubConverter {
    fun convert(input: File, output: File, fallbackTitle: String): File {
        require(input.length() <= MAX_MARKDOWN_BYTES) { "Markdown file is too large." }
        val bytes = input.readBytes()
        val source = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val parsed = parseSource(source, fallbackTitle)
        require(parsed.chapters.isNotEmpty()) {
            "Markdown document contains no readable text."
        }
        val identifier = "urn:uuid:${UUID.nameUUIDFromBytes(bytes)}"

        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            writeStored(zip, "mimetype", EPUB_MIME_TYPE.toByteArray(Charsets.US_ASCII))
            writeDeflated(zip, "META-INF/container.xml", containerXml.toByteArray(Charsets.UTF_8))
            writeDeflated(
                zip,
                "OEBPS/package.opf",
                packageDocument(parsed.metadata, identifier, parsed.chapters).toByteArray(Charsets.UTF_8)
            )
            writeDeflated(
                zip,
                "OEBPS/nav.xhtml",
                navigationDocument(parsed.metadata.title, parsed.metadata.language, parsed.chapters).toByteArray(Charsets.UTF_8)
            )
            parsed.chapters.forEachIndexed { index, chapter ->
                writeDeflated(
                    zip,
                    "OEBPS/chapters/${chapterName(index)}.xhtml",
                    chapterDocument(chapter, parsed.metadata.language).toByteArray(Charsets.UTF_8)
                )
            }
        }
        return output
    }

    private fun parseSource(source: String, fallbackTitle: String): ParsedMarkdown {
        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val frontMatter = parseFrontMatter(normalized)
        val body = normalized.lines().drop(frontMatter.bodyStartLine)
        val blocks = parseBlocks(body)
        val firstHeading = blocks.firstOrNull { it.kind == MarkdownBlockKind.HEADING }?.text
        val metadata = MarkdownMetadata(
            title = TextTools.cleanTitle(frontMatter.value("title") ?: firstHeading ?: fallbackTitle),
            author = frontMatter.value("author", "authors", "creator"),
            language = frontMatter.value("language", "lang") ?: "en",
            description = frontMatter.value("description", "summary"),
            subject = frontMatter.value("genre", "genres", "subject", "tags"),
            year = frontMatter.value("date", "created", "published", "year")?.let { YEAR_REGEX.find(it)?.value }
        )
        return ParsedMarkdown(
            metadata = metadata,
            chapters = chaptersFromBlocks(blocks, metadata.title)
        )
    }

    private fun parseFrontMatter(source: String): MarkdownFrontMatter {
        val lines = source.lines()
        if (lines.firstOrNull()?.trim() != "---") return MarkdownFrontMatter(emptyMap(), bodyStartLine = 0)
        val values = linkedMapOf<String, String>()
        var index = 1
        while (index < lines.size) {
            val line = lines[index]
            if (line.trim() == "---") return MarkdownFrontMatter(values, bodyStartLine = index + 1)
            val separator = line.indexOf(':')
            if (separator > 0) {
                val key = line.substring(0, separator).trim().lowercase(Locale.US)
                val value = line.substring(separator + 1).cleanFrontMatterValue()
                if (key.isNotBlank() && value.isNotBlank()) values[key] = value
            }
            index += 1
        }
        return MarkdownFrontMatter(emptyMap(), bodyStartLine = 0)
    }

    private fun parseBlocks(lines: List<String>): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            when {
                line.isBlank() -> index += 1
                line.isThematicBreak() -> index += 1
                line.fenceStart() != null -> {
                    val parsed = parseFencedCode(lines, index)
                    blocks += MarkdownBlock(parsed.text, MarkdownBlockKind.CODE)
                    index = parsed.nextIndex
                }
                line.headingMatch() != null -> {
                    val match = requireNotNull(line.headingMatch())
                    blocks += MarkdownBlock(
                        text = match.groupValues[2].trimMarkdownHeading().inlineMarkdownText(),
                        kind = MarkdownBlockKind.HEADING,
                        headingLevel = match.groupValues[1].length.coerceIn(1, 6)
                    )
                    index += 1
                }
                line.blockQuoteText() != null -> {
                    val parsed = parseBlockQuote(lines, index)
                    blocks += MarkdownBlock(parsed.text, MarkdownBlockKind.QUOTE)
                    index = parsed.nextIndex
                }
                line.listItemText() != null -> {
                    val parsed = parseList(lines, index)
                    blocks += parsed.items.map { MarkdownBlock(it, MarkdownBlockKind.LIST_ITEM) }
                    index = parsed.nextIndex
                }
                else -> {
                    val parsed = parseParagraph(lines, index)
                    blocks += MarkdownBlock(parsed.text, MarkdownBlockKind.PARAGRAPH)
                    index = parsed.nextIndex
                }
            }
        }
        return blocks.mapNotNull { block ->
            val clean = block.text.cleanBlockText(preformatted = block.kind == MarkdownBlockKind.CODE)
            clean.takeIf { it.length > 1 && it.any(Char::isLetterOrDigit) }?.let { block.copy(text = it) }
        }
    }

    private fun parseFencedCode(lines: List<String>, start: Int): ParsedTextBlock {
        val fence = requireNotNull(lines[start].fenceStart())
        val content = mutableListOf<String>()
        var index = start + 1
        while (index < lines.size) {
            if (lines[index].trimStart().startsWith(fence.closePrefix)) {
                return ParsedTextBlock(content.joinToString("\n"), index + 1)
            }
            content += lines[index]
            index += 1
        }
        return ParsedTextBlock(content.joinToString("\n"), index)
    }

    private fun parseBlockQuote(lines: List<String>, start: Int): ParsedTextBlock {
        val quote = mutableListOf<String>()
        var index = start
        while (index < lines.size) {
            val text = lines[index].blockQuoteText() ?: break
            quote += text
            index += 1
        }
        return ParsedTextBlock(quote.joinToString("\n").inlineMarkdownText(), index)
    }

    private fun parseList(lines: List<String>, start: Int): ParsedListBlock {
        val items = mutableListOf<String>()
        var index = start
        while (index < lines.size) {
            val firstLine = lines[index].listItemText() ?: break
            val parts = mutableListOf(firstLine)
            index += 1
            while (index < lines.size) {
                val line = lines[index]
                when {
                    line.isBlank() -> {
                        index += 1
                        break
                    }
                    line.listItemText() != null -> break
                    line.isBlockBoundary() -> break
                    else -> {
                        parts += line.trim()
                        index += 1
                    }
                }
            }
            items += parts.joinToString(" ").inlineMarkdownText()
        }
        return ParsedListBlock(items, index)
    }

    private fun parseParagraph(lines: List<String>, start: Int): ParsedTextBlock {
        val parts = mutableListOf<String>()
        var index = start
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.isThematicBreak() || line.isBlockBoundary()) break
            parts += line.trim()
            index += 1
        }
        return ParsedTextBlock(parts.joinToString(" ").inlineMarkdownText(), index)
    }

    private fun chaptersFromBlocks(blocks: List<MarkdownBlock>, fallbackTitle: String): List<MarkdownChapter> {
        if (blocks.isEmpty()) return emptyList()
        val chapters = mutableListOf<MarkdownChapter>()
        var title = fallbackTitle.ifBlank { "Document" }
        val content = mutableListOf<MarkdownBlock>()

        fun flush() {
            val clean = content.filter { it.text.length > 1 && it.text.any(Char::isLetterOrDigit) }
            if (clean.isNotEmpty()) {
                chapters += MarkdownChapter(title = title.ifBlank { "Chapter ${chapters.size + 1}" }, blocks = clean)
            }
            content.clear()
        }

        blocks.forEach { block ->
            val headingLevel = block.headingLevel ?: Int.MAX_VALUE
            if (block.kind == MarkdownBlockKind.HEADING && headingLevel <= 2) {
                flush()
                title = block.text
            } else {
                content += block
            }
        }
        flush()

        if (chapters.isEmpty()) {
            val first = blocks.first()
            chapters += MarkdownChapter(
                title = first.takeIf { it.kind == MarkdownBlockKind.HEADING }?.text ?: title,
                blocks = blocks.filter { it.kind != MarkdownBlockKind.HEADING }
                    .ifEmpty { listOf(MarkdownBlock(first.text, MarkdownBlockKind.PARAGRAPH)) }
            )
        }

        return chapters.distinctBy { chapter ->
            "${chapter.title}\u0000${chapter.blocks.joinToString("\u0000") { it.text }}"
        }
    }

    private fun packageDocument(
        metadata: MarkdownMetadata,
        identifier: String,
        chapters: List<MarkdownChapter>,
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

    private fun navigationDocument(title: String, language: String, chapters: List<MarkdownChapter>): String {
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

    private fun chapterDocument(chapter: MarkdownChapter, language: String): String {
        val body = buildString {
            var listOpen = false
            fun closeList() {
                if (listOpen) {
                    appendLine("</ul>")
                    listOpen = false
                }
            }
            chapter.blocks.forEach { block ->
                when (block.kind) {
                    MarkdownBlockKind.LIST_ITEM -> {
                        if (!listOpen) {
                            appendLine("<ul>")
                            listOpen = true
                        }
                        appendLine("<li>${escapeXml(block.text)}</li>")
                    }
                    MarkdownBlockKind.HEADING -> {
                        closeList()
                        val level = (block.headingLevel ?: 2).coerceIn(2, 6)
                        appendLine("<h$level>${escapeXml(block.text)}</h$level>")
                    }
                    MarkdownBlockKind.QUOTE -> {
                        closeList()
                        appendLine("<blockquote><p>${escapeXml(block.text)}</p></blockquote>")
                    }
                    MarkdownBlockKind.CODE -> {
                        closeList()
                        appendLine("<pre>${escapeXml(block.text)}</pre>")
                    }
                    MarkdownBlockKind.PARAGRAPH -> {
                        closeList()
                        appendLine("<p>${escapeXml(block.text)}</p>")
                    }
                }
            }
            closeList()
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

    private fun String.headingMatch(): MatchResult? =
        HEADING_REGEX.matchEntire(this)

    private fun String.fenceStart(): MarkdownFence? {
        val match = FENCE_REGEX.matchEntire(this) ?: return null
        val marker = match.groupValues[1]
        return MarkdownFence(closePrefix = marker.first().toString().repeat(marker.length))
    }

    private fun String.blockQuoteText(): String? =
        BLOCK_QUOTE_REGEX.matchEntire(this)?.groupValues?.get(1)?.trim()

    private fun String.listItemText(): String? =
        LIST_ITEM_REGEX.matchEntire(this)?.groupValues?.get(2)?.trim()

    private fun String.isBlockBoundary(): Boolean =
        headingMatch() != null || fenceStart() != null || blockQuoteText() != null || listItemText() != null || isThematicBreak()

    private fun String.isThematicBreak(): Boolean {
        val compact = trim().replace(" ", "")
        return compact.length >= 3 && compact.all { it == '-' || it == '*' || it == '_' } && compact.toSet().size == 1
    }

    private fun String.trimMarkdownHeading(): String =
        replace(Regex("""\s+#+\s*$"""), "").trim()

    private fun String.inlineMarkdownText(): String {
        val withoutImages = replace(IMAGE_REGEX) { it.groupValues[1] }
        val withoutLinks = withoutImages
            .replace(LINK_REGEX) { it.groupValues[1] }
            .replace(REFERENCE_LINK_REGEX) { it.groupValues[1] }
        val withoutCode = withoutLinks.replace(INLINE_CODE_REGEX) { it.groupValues[1] }
        val withoutHtml = withoutCode.replace(HTML_TAG_REGEX, "")
        val decoded = Jsoup.parseBodyFragment(withoutHtml).text()
        return decoded
            .replace(STRONG_ASTERISK_REGEX) { it.groupValues[1] }
            .replace(STRONG_UNDERSCORE_REGEX) { it.groupValues[1] }
            .replace(EM_ASTERISK_REGEX) { it.groupValues[1] }
            .replace(EM_UNDERSCORE_REGEX) { it.groupValues[1] }
            .replace(STRIKE_REGEX) { it.groupValues[1] }
            .cleanBlockText(preformatted = false)
    }

    private fun String.cleanBlockText(preformatted: Boolean): String {
        val normalized = replace('\u00A0', ' ')
        return if (preformatted) {
            normalized.trim()
        } else {
            normalized.replace(Regex("\\s+"), " ").trim()
        }
    }

    private fun String.cleanFrontMatterValue(): String =
        trim()
            .trim('"', '\'')
            .trim('[', ']')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun MarkdownFrontMatter.value(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> values[key.lowercase(Locale.US)]?.takeIf { it.isNotBlank() } }

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

    private data class ParsedMarkdown(
        val metadata: MarkdownMetadata,
        val chapters: List<MarkdownChapter>,
    )

    private data class MarkdownMetadata(
        val title: String,
        val author: String? = null,
        val language: String = "en",
        val description: String? = null,
        val subject: String? = null,
        val year: String? = null,
    )

    private data class MarkdownFrontMatter(
        val values: Map<String, String>,
        val bodyStartLine: Int,
    )

    private data class MarkdownBlock(
        val text: String,
        val kind: MarkdownBlockKind,
        val headingLevel: Int? = null,
    )

    private enum class MarkdownBlockKind {
        PARAGRAPH,
        HEADING,
        LIST_ITEM,
        QUOTE,
        CODE,
    }

    private data class MarkdownChapter(
        val title: String,
        val blocks: List<MarkdownBlock>,
    )

    private data class ParsedTextBlock(
        val text: String,
        val nextIndex: Int,
    )

    private data class ParsedListBlock(
        val items: List<String>,
        val nextIndex: Int,
    )

    private data class MarkdownFence(
        val closePrefix: String,
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
        private const val MAX_MARKDOWN_BYTES = 16L * 1024L * 1024L
        private val YEAR_REGEX = Regex("""\d{4}""")
        private val HEADING_REGEX = Regex("""^\s{0,3}(#{1,6})\s+(.+?)\s*$""")
        private val FENCE_REGEX = Regex("""^\s{0,3}(`{3,}|~{3,}).*$""")
        private val BLOCK_QUOTE_REGEX = Regex("""^\s{0,3}>\s?(.*)$""")
        private val LIST_ITEM_REGEX = Regex("""^\s{0,3}([-+*]|\d+[.)])\s+(.+)$""")
        private val IMAGE_REGEX = Regex("""!\[([^\]]*)]\([^)]+\)""")
        private val LINK_REGEX = Regex("""\[([^\]]+)]\([^)]+\)""")
        private val REFERENCE_LINK_REGEX = Regex("""\[([^\]]+)]\[[^\]]*]""")
        private val INLINE_CODE_REGEX = Regex("""`+([^`]+?)`+""")
        private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        private val STRONG_ASTERISK_REGEX = Regex("""\*\*([^*]+)\*\*""")
        private val STRONG_UNDERSCORE_REGEX = Regex("""__([^_]+)__""")
        private val EM_ASTERISK_REGEX = Regex("""\*([^*]+)\*""")
        private val EM_UNDERSCORE_REGEX = Regex("""_([^_]+)_""")
        private val STRIKE_REGEX = Regex("""~~(.+?)~~""")
    }
}
