package com.xreader.app.importer

import com.xreader.app.core.TextTools
import java.io.File
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

class RtfToEpubConverter {
    fun convert(input: File, output: File, fallbackTitle: String): File {
        require(input.length() <= MAX_RTF_BYTES) { "RTF file is too large." }
        val bytes = input.readBytes()
        val rtf = bytes.toString(Charsets.ISO_8859_1)
        require(rtf.trimStart().startsWith("{\\rtf", ignoreCase = true)) {
            "File is not an RTF document."
        }

        val title = extractDestinationText(rtf, "title")
            ?.let(TextTools::cleanTitle)
            ?.takeIf { it.isNotBlank() }
            ?: TextTools.cleanTitle(fallbackTitle)
        val author = extractDestinationText(rtf, "author")?.takeIf { it.isNotBlank() }
        val paragraphs = extractParagraphs(rtf)
        require(paragraphs.isNotEmpty()) {
            "RTF document contains no readable text."
        }
        val identifier = "urn:uuid:${UUID.nameUUIDFromBytes(bytes)}"

        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            writeStored(zip, "mimetype", EPUB_MIME_TYPE.toByteArray(Charsets.US_ASCII))
            writeDeflated(zip, "META-INF/container.xml", containerXml.toByteArray(Charsets.UTF_8))
            writeDeflated(zip, "OEBPS/package.opf", packageDocument(title, author, identifier).toByteArray(Charsets.UTF_8))
            writeDeflated(zip, "OEBPS/nav.xhtml", navigationDocument(title).toByteArray(Charsets.UTF_8))
            writeDeflated(zip, "OEBPS/content.xhtml", contentDocument(title, paragraphs).toByteArray(Charsets.UTF_8))
        }
        return output
    }

    private fun extractParagraphs(rtf: String): List<String> =
        extractText(rtf, skipMetadata = true)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split(Regex("\n+"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length > 1 && it.any(Char::isLetterOrDigit) }

    private fun extractDestinationText(rtf: String, destination: String): String? {
        val group = destinationGroup(rtf, destination) ?: return null
        return extractText(group, skipMetadata = false)
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun destinationGroup(rtf: String, destination: String): String? {
        val marker = "{\\$destination"
        var searchFrom = 0
        var start = -1
        while (searchFrom < rtf.length) {
            val candidate = rtf.indexOf(marker, startIndex = searchFrom, ignoreCase = true)
            if (candidate < 0) return null
            val next = rtf.getOrNull(candidate + marker.length)
            if (next == null || next.isWhitespace() || next == '\\' || next == '{' || next == '}') {
                start = candidate
                break
            }
            searchFrom = candidate + 1
        }
        if (start < 0) return null

        var depth = 0
        var escaped = false
        for (index in start until rtf.length) {
            val char = rtf[index]
            if (escaped) {
                escaped = false
                continue
            }
            when (char) {
                '\\' -> escaped = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return rtf.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun extractText(rtf: String, skipMetadata: Boolean): String {
        val output = StringBuilder()
        val stack = ArrayDeque<RtfState>()
        var state = RtfState()
        var index = 0
        var unicodeSkip = 0

        fun appendText(value: String) {
            if (!state.skippingDestination && unicodeSkip == 0) output.append(value)
        }

        fun consumeUnicodeFallback() {
            if (unicodeSkip > 0) unicodeSkip -= 1
        }

        while (index < rtf.length) {
            val char = rtf[index]
            when (char) {
                '{' -> {
                    stack.addLast(state.copy())
                    index += 1
                }
                '}' -> {
                    state = stack.removeLastOrNull() ?: RtfState()
                    unicodeSkip = 0
                    index += 1
                }
                '\\' -> {
                    val parsed = parseControl(rtf, index + 1)
                    index = parsed.nextIndex
                    if (parsed.isPlainFallback) {
                        consumeUnicodeFallback()
                        continue
                    }
                    if (parsed.symbol != null) {
                        if (unicodeSkip > 0 && parsed.symbol != '*') {
                            consumeUnicodeFallback()
                            continue
                        }
                        when (parsed.symbol) {
                            '*' -> state = state.copy(skippingDestination = true)
                            '\'' -> {
                                parsed.hexChar?.let { value ->
                                    appendText(value.toString())
                                }
                            }
                            '{', '}', '\\' -> appendText(parsed.symbol.toString())
                            '~' -> appendText(" ")
                            '_' -> appendText("-")
                            else -> Unit
                        }
                        continue
                    }

                    val word = parsed.word ?: continue
                    if (word == "bin") {
                        val bytesToSkip = parsed.argument?.coerceAtLeast(0) ?: 0
                        index = (index + bytesToSkip).coerceAtMost(rtf.length)
                        continue
                    }
                    if (skipMetadata && word in METADATA_DESTINATIONS) {
                        state = state.copy(skippingDestination = true)
                        continue
                    }
                    if (word in SKIP_DESTINATIONS) {
                        state = state.copy(skippingDestination = true)
                        continue
                    }
                    if (state.skippingDestination) continue

                    when (word) {
                        "par", "line", "page" -> {
                            if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
                        }
                        "tab" -> output.append('\t')
                        "emdash" -> output.append('\u2014')
                        "endash" -> output.append('\u2013')
                        "emspace", "enspace", "qmspace" -> output.append(' ')
                        "lquote" -> output.append('\u2018')
                        "rquote" -> output.append('\u2019')
                        "ldblquote" -> output.append('\u201C')
                        "rdblquote" -> output.append('\u201D')
                        "bullet" -> output.append('\u2022')
                        "uc" -> state = state.copy(ucSkip = parsed.argument?.coerceIn(0, 16) ?: state.ucSkip)
                        "u" -> {
                            parsed.argument?.let { argument ->
                                val codePoint = if (argument < 0) argument + 65_536 else argument
                                output.append(codePoint.toChar())
                                unicodeSkip = state.ucSkip
                            }
                        }
                    }
                }
                '\u0000' -> index += 1
                '\n', '\r' -> {
                    index += 1
                }
                else -> {
                    if (state.skippingDestination) {
                        index += 1
                    } else if (unicodeSkip > 0) {
                        unicodeSkip -= 1
                        index += 1
                    } else {
                        output.append(char)
                        index += 1
                    }
                }
            }
        }
        return output.toString()
    }

    private fun parseControl(rtf: String, start: Int): RtfControl {
        if (start >= rtf.length) return RtfControl(nextIndex = start)
        val first = rtf[start]
        if (!first.isLetter()) {
            if (first == '\'' && start + 2 < rtf.length) {
                val hex = rtf.substring(start + 1, start + 3)
                val value = hex.toIntOrNull(16)
                    ?.let { byteArrayOf(it.toByte()).toString(WINDOWS_1252) }
                    ?.firstOrNull()
                return RtfControl(symbol = first, hexChar = value, nextIndex = start + 3)
            }
            return RtfControl(symbol = first, nextIndex = start + 1, isPlainFallback = first !in CONTROL_SYMBOLS)
        }

        var index = start
        while (index < rtf.length && rtf[index].isLetter()) index += 1
        val word = rtf.substring(start, index).lowercase()
        var sign = 1
        if (index < rtf.length && rtf[index] == '-') {
            sign = -1
            index += 1
        }
        val numberStart = index
        while (index < rtf.length && rtf[index].isDigit()) index += 1
        val argument = if (index > numberStart) {
            sign * rtf.substring(numberStart, index).toInt()
        } else {
            null
        }
        if (index < rtf.length && rtf[index] == ' ') index += 1
        return RtfControl(word = word, argument = argument, nextIndex = max(index, start + 1))
    }

    private fun packageDocument(title: String, author: String?, identifier: String): String {
        val authorXml = author?.let { "<dc:creator>${escapeXml(it)}</dc:creator>" }.orEmpty()
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="book-id">${escapeXml(identifier)}</dc:identifier>
                <dc:title>${escapeXml(title)}</dc:title>
                $authorXml
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
    }

    private fun navigationDocument(title: String): String =
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en">
              <head><title>${escapeXml(title)}</title></head>
              <body><nav epub:type="toc"><ol><li><a href="content.xhtml">${escapeXml(title)}</a></li></ol></nav></body>
            </html>
        """.trimIndent()

    private fun contentDocument(title: String, paragraphs: List<String>): String {
        val body = paragraphs.joinToString("\n") { "<p>${escapeXml(it)}</p>" }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="en">
              <head><title>${escapeXml(title)}</title></head>
              <body>
                <h1>${escapeXml(title)}</h1>
                $body
              </body>
            </html>
        """.trimIndent()
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

    private data class RtfState(
        val skippingDestination: Boolean = false,
        val ucSkip: Int = 1,
    )

    private data class RtfControl(
        val word: String? = null,
        val argument: Int? = null,
        val symbol: Char? = null,
        val hexChar: Char? = null,
        val nextIndex: Int,
        val isPlainFallback: Boolean = false,
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
        private const val MAX_RTF_BYTES = 64L * 1024L * 1024L
        private val WINDOWS_1252: Charset = Charset.forName("windows-1252")
        private val CONTROL_SYMBOLS = setOf('*', '\'', '{', '}', '\\', '~', '-', '_')
        private val METADATA_DESTINATIONS = setOf("info", "title", "author", "subject", "keywords", "operator", "company", "manager")
        private val SKIP_DESTINATIONS = setOf(
            "fonttbl",
            "colortbl",
            "stylesheet",
            "listtable",
            "listoverridetable",
            "pict",
            "object",
            "shp",
            "shpinst",
            "header",
            "footer",
            "footnote",
            "annotation",
            "field",
            "fldinst",
            "generator",
            "datastore"
        )
    }
}
