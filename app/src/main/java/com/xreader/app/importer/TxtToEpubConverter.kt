package com.xreader.app.importer

import com.xreader.app.core.TextTools
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TxtToEpubConverter {
    fun convert(input: File, output: File, title: String): File {
        require(input.length() <= MAX_TXT_BYTES) { "TXT file is too large." }
        val bytes = input.readBytes()
        val raw = decodeText(bytes)
        val parsed = parsePlainText(raw, title)
        require(parsed.chapters.isNotEmpty()) {
            "TXT document contains no readable text."
        }

        val identifier = "urn:uuid:${UUID.nameUUIDFromBytes(bytes)}"

        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            writeStored(zip, "mimetype", "application/epub+zip".toByteArray(Charsets.US_ASCII))
            writeDeflated(zip, "META-INF/container.xml", containerXml.toByteArray(Charsets.UTF_8))
            writeDeflated(zip, "OEBPS/package.opf", packageDocument(parsed.title, identifier, parsed.chapters).toByteArray(Charsets.UTF_8))
            writeDeflated(zip, "OEBPS/nav.xhtml", navigationDocument(parsed.title, parsed.chapters).toByteArray(Charsets.UTF_8))
            parsed.chapters.forEachIndexed { index, chapter ->
                writeDeflated(
                    zip,
                    "OEBPS/chapters/${chapterName(index)}.xhtml",
                    chapterDocument(chapter).toByteArray(Charsets.UTF_8)
                )
            }
        }
        return output
    }

    private fun parsePlainText(raw: String, fallbackTitle: String): ParsedTxt {
        val normalized = raw
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val blocks = normalized
            .split(Regex("\n{2,}"))
            .mapNotNull { block ->
                val lines = block.lines()
                    .map { it.trimEnd() }
                    .dropWhile { it.isBlank() }
                    .dropLastWhile { it.isBlank() }
                val text = lines.joinToString("\n").trim()
                text.takeIf { it.any(Char::isLetterOrDigit) }?.let { TxtBlock(lines = lines, text = it) }
            }
        if (blocks.isEmpty()) return ParsedTxt(TextTools.cleanTitle(fallbackTitle), emptyList())

        val headingIndexes = chapterHeadingIndexes(blocks)
        val chapters = if (headingIndexes.size >= 2) {
            chaptersFromHeadings(blocks, headingIndexes, fallbackTitle)
        } else {
            listOf(
                TxtChapter(
                    title = firstUsefulTitle(blocks, fallbackTitle),
                    blocks = blocks.map { it.text }
                )
            )
        }
        return ParsedTxt(
            title = TextTools.cleanTitle(fallbackTitle),
            chapters = chapters.filter { chapter -> chapter.blocks.any { it.any(Char::isLetterOrDigit) } }
        )
    }

    private fun chapterHeadingIndexes(blocks: List<TxtBlock>): Set<Int> {
        val explicit = blocks
            .mapIndexedNotNull { index, block -> index.takeIf { block.isExplicitChapterHeading() } }
            .toSet()
        if (explicit.size >= 2) return explicit

        val allCaps = blocks
            .mapIndexedNotNull { index, block -> index.takeIf { block.isAllCapsHeadingCandidate() } }
            .toSet()
        return if (allCaps.size >= 2) allCaps else explicit
    }

    private fun chaptersFromHeadings(
        blocks: List<TxtBlock>,
        headingIndexes: Set<Int>,
        fallbackTitle: String,
    ): List<TxtChapter> {
        val chapters = mutableListOf<TxtChapter>()
        var title = TextTools.cleanTitle(fallbackTitle)
        val content = mutableListOf<String>()

        fun flush() {
            if (content.any { it.any(Char::isLetterOrDigit) }) {
                chapters += TxtChapter(title = title, blocks = content.toList())
            }
            content.clear()
        }

        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            if (index in headingIndexes) {
                flush()
                val subtitle = blocks.getOrNull(index + 1)
                    ?.takeIf { (index + 1) !in headingIndexes && it.isSubtitleCandidate() }
                title = listOf(block.titleText(), subtitle?.titleText())
                    .filterNotNull()
                    .joinToString(" - ")
                    .ifBlank { "Chapter ${chapters.size + 1}" }
                index += if (subtitle != null) 2 else 1
            } else {
                content += block.text
                index += 1
            }
        }
        flush()
        return chapters.ifEmpty {
            listOf(TxtChapter(title = firstUsefulTitle(blocks, fallbackTitle), blocks = blocks.map { it.text }))
        }
    }

    private fun firstUsefulTitle(blocks: List<TxtBlock>, fallbackTitle: String): String {
        val first = blocks.firstOrNull()
            ?.takeIf { it.text.length <= 90 && it.text.lineCount() <= 2 }
            ?.titleText()
        return TextTools.cleanTitle(first ?: fallbackTitle)
    }

    private fun TxtBlock.isExplicitChapterHeading(): Boolean {
        val clean = headingText()
        if (clean.length > MAX_HEADING_CHARS || clean.lineCount() > 2) return false
        return CHAPTER_HEADING_REGEX.matches(clean) || STANDALONE_SECTION_REGEX.matches(clean)
    }

    private fun TxtBlock.isAllCapsHeadingCandidate(): Boolean {
        val clean = headingText()
        if (clean.length !in 3..MAX_HEADING_CHARS || clean.lineCount() > 2) return false
        if (TextTools.wordCount(clean) > 10) return false
        if (clean.endsWith(".") || clean.endsWith(",") || clean.endsWith(";")) return false
        val letters = clean.filter(Char::isLetter)
        if (letters.length < 2) return false
        return letters.all { it.isUpperCase() || !it.isLetter() }
    }

    private fun TxtBlock.isSubtitleCandidate(): Boolean {
        val clean = headingText()
        if (clean.length !in 3..MAX_HEADING_CHARS || clean.lineCount() > 2) return false
        if (TextTools.wordCount(clean) > 12) return false
        if (clean.endsWith(".") || clean.endsWith(",") || clean.endsWith(";")) return false
        return clean.any(Char::isLetter)
    }

    private fun TxtBlock.headingText(): String =
        lines.joinToString(" ") { it.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun TxtBlock.titleText(): String =
        lines
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .trim()

    private fun packageDocument(title: String, identifier: String, chapters: List<TxtChapter>): String {
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
                <dc:title>${escapeXml(title)}</dc:title>
                <dc:language>en</dc:language>
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

    private fun navigationDocument(title: String, chapters: List<TxtChapter>): String {
        val tocItems = chapters.mapIndexed { index, chapter ->
            """<li><a href="chapters/${chapterName(index)}.xhtml">${escapeXml(chapter.title)}</a></li>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en">
              <head><title>Contents</title></head>
              <body>
                <nav epub:type="toc">
                  <ol>
                    ${tocItems.joinToString("\n                    ")}
                  </ol>
                </nav>
              </body>
            </html>
        """.trimIndent()
    }

    private fun chapterDocument(chapter: TxtChapter): String {
        val body = chapter.blocks.joinToString("\n") { block ->
            "<p>${escapeXml(block).replace("\n", "<br/>")}</p>"
        }
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

    private fun chapterName(index: Int): String = "chapter-${chapterNumber(index)}"

    private fun chapterNumber(index: Int): String = (index + 1).toString().padStart(4, '0')

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.startsWith(UTF_8_BOM)) return bytes.drop(UTF_8_BOM.size).toByteArray().toString(Charsets.UTF_8)
        if (bytes.startsWith(UTF_16_LE_BOM)) return bytes.drop(UTF_16_LE_BOM.size).toByteArray().toString(Charsets.UTF_16LE)
        if (bytes.startsWith(UTF_16_BE_BOM)) return bytes.drop(UTF_16_BE_BOM.size).toByteArray().toString(Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            bytes.toString(WINDOWS_1252)
        }
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

    private fun String.lineCount(): Int = count { it == '\n' } + 1

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private data class ParsedTxt(
        val title: String,
        val chapters: List<TxtChapter>,
    )

    private data class TxtChapter(
        val title: String,
        val blocks: List<String>,
    )

    private data class TxtBlock(
        val lines: List<String>,
        val text: String,
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
        const val MAX_TXT_BYTES = 32L * 1024L * 1024L
        const val MAX_HEADING_CHARS = 100
        val WINDOWS_1252: Charset = Charset.forName("windows-1252")
        val UTF_8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val UTF_16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val UTF_16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val CHAPTER_HEADING_REGEX = Regex(
            pattern = """(?i)^(chapter|book|part|section)\s+([ivxlcdm]+|\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)(?:[\s.:_-].*)?$"""
        )
        val STANDALONE_SECTION_REGEX = Regex(
            pattern = """(?i)^(prologue|epilogue|preface|introduction|afterword|acknowledgments|acknowledgements)$"""
        )
    }
}
