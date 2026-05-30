package com.xreader.app.importer

import com.xreader.app.core.TextTools
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MobiToEpubConverter {
    fun convert(input: File, output: File, fallbackTitle: String): File {
        require(input.length() in 1..MAX_MOBI_BYTES) { "MOBI file is too large." }
        val bytes = input.readBytes()
        val document = parse(bytes, fallbackTitle)
        val blocks = document.readingBlocks()
        require(blocks.isNotEmpty()) {
            "MOBI document contains no readable text."
        }
        val identifier = "urn:uuid:${UUID.nameUUIDFromBytes(bytes)}"

        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            writeStored(zip, "mimetype", EPUB_MIME_TYPE.toByteArray(Charsets.US_ASCII))
            writeDeflated(zip, "META-INF/container.xml", containerXml.toByteArray(Charsets.UTF_8))
            writeDeflated(zip, "OEBPS/package.opf", packageDocument(document, identifier).toByteArray(Charsets.UTF_8))
            writeDeflated(zip, "OEBPS/nav.xhtml", navigationDocument(document.title).toByteArray(Charsets.UTF_8))
            writeDeflated(zip, "OEBPS/content.xhtml", contentDocument(document.title, blocks).toByteArray(Charsets.UTF_8))
        }
        return output
    }

    private fun parse(bytes: ByteArray, fallbackTitle: String): MobiDocument {
        require(bytes.size >= PDB_HEADER_SIZE + RECORD_ENTRY_SIZE) { "File is not a MOBI document." }
        val type = bytes.ascii(PDB_TYPE_OFFSET, PDB_TYPE_OFFSET + 4)
        val creator = bytes.ascii(PDB_CREATOR_OFFSET, PDB_CREATOR_OFFSET + 4)
        require(type == "BOOK" && creator in MOBI_CREATORS) { "File is not a MOBI document." }
        val recordCount = bytes.u16(PDB_RECORD_COUNT_OFFSET)
        require(recordCount >= 2) { "MOBI document has no text records." }
        val recordOffsets = (0 until recordCount).map { index ->
            bytes.u32(PDB_HEADER_SIZE + index * RECORD_ENTRY_SIZE)
        }
        require(recordOffsets.all { it in bytes.indices } && recordOffsets == recordOffsets.sorted()) {
            "MOBI record table is invalid."
        }
        val record0 = bytes.record(recordOffsets, 0)
        require(record0.size >= PALMDOC_HEADER_SIZE) { "MOBI header is missing." }
        val compression = record0.u16(0)
        val textLength = record0.u32(4).coerceAtMost(MAX_DECOMPRESSED_TEXT_BYTES)
        val textRecordCount = record0.u16(8).coerceAtMost(recordCount - 1)
        val encryption = record0.u16(12)
        require(encryption == 0) { "Encrypted MOBI files are not supported." }
        require(compression in SUPPORTED_COMPRESSION) { "Unsupported MOBI compression." }

        val header = record0.parseMobiHeader(fallbackTitle)
        val textBytes = ByteArrayOutputStream()
        for (index in 1..textRecordCount) {
            val record = bytes.record(recordOffsets, index)
            val decoded = when (compression) {
                PALMDOC_COMPRESSION_NONE -> record
                PALMDOC_COMPRESSION -> decompressPalmDoc(record)
                else -> error("Unsupported MOBI compression.")
            }
            val remaining = textLength - textBytes.size()
            if (remaining <= 0) break
            textBytes.write(decoded, 0, decoded.size.coerceAtMost(remaining))
        }
        val text = textBytes.toByteArray().toString(header.charset)
            .replace('\u0000', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        return MobiDocument(
            title = header.title,
            author = header.author,
            language = "en",
            text = text
        )
    }

    private fun ByteArray.parseMobiHeader(fallbackTitle: String): MobiHeader {
        val fallback = TextTools.cleanTitle(fallbackTitle)
        val mobiStart = asciiIndexOf("MOBI", PALMDOC_HEADER_SIZE)
        if (mobiStart < 0 || mobiStart + MOBI_MIN_HEADER_BYTES > size) {
            return MobiHeader(title = fallback, author = null, charset = WINDOWS_1252)
        }
        val headerLength = u32(mobiStart + 4).coerceAtMost(size - mobiStart)
        val charset = when (u32(mobiStart + 12)) {
            65001 -> Charsets.UTF_8
            1252 -> WINDOWS_1252
            else -> WINDOWS_1252
        }
        val fullNameOffset = u32OrNull(mobiStart + 84)
        val fullNameLength = u32OrNull(mobiStart + 88)
        val title = fullNameOffset
            ?.takeIf { offset -> fullNameLength != null && offset >= 0 && offset + fullNameLength <= size }
            ?.let { offset -> copyOfRange(offset, offset + fullNameLength!!).toString(charset) }
            ?.cleanMetadataValue()
            ?.let(TextTools::cleanTitle)
            ?: fallback
        val author = parseExthAuthor(mobiStart + headerLength, charset)
        return MobiHeader(title = title, author = author, charset = charset)
    }

    private fun ByteArray.parseExthAuthor(start: Int, charset: Charset): String? {
        if (start < 0 || start + EXTH_HEADER_SIZE > size || ascii(start, start + 4) != "EXTH") return null
        val length = u32(start + 4)
        val count = u32(start + 8)
        if (length < EXTH_HEADER_SIZE || start + length > size) return null
        var cursor = start + EXTH_HEADER_SIZE
        repeat(count) {
            if (cursor + EXTH_RECORD_HEADER_SIZE > start + length) return null
            val type = u32(cursor)
            val recordLength = u32(cursor + 4)
            if (recordLength < EXTH_RECORD_HEADER_SIZE || cursor + recordLength > start + length) return null
            if (type == EXTH_AUTHOR_TYPE) {
                return copyOfRange(cursor + EXTH_RECORD_HEADER_SIZE, cursor + recordLength)
                    .toString(charset)
                    .cleanMetadataValue()
            }
            cursor += recordLength
        }
        return null
    }

    private fun MobiDocument.readingBlocks(): List<String> {
        val document = Jsoup.parse(text)
        val selected = document.body()
            .select("h1,h2,h3,h4,h5,h6,p,blockquote,li,div")
            .mapNotNull { it.text().cleanReadableBlock() }
            .distinct()
        if (selected.isNotEmpty()) return selected
        val body = document.body().text().ifBlank { text }
        return body
            .split(Regex("""(?:\r?\n){2,}|(?<=[.!?])\s+(?=[A-Z0-9])"""))
            .mapNotNull { it.cleanReadableBlock() }
    }

    private fun decompressPalmDoc(record: ByteArray): ByteArray {
        val output = ArrayList<Byte>(record.size * 2)
        var index = 0
        while (index < record.size) {
            val value = record[index].toInt() and 0xFF
            index += 1
            when (value) {
                0 -> output += 0
                in 1..8 -> {
                    require(index + value <= record.size) { "PalmDOC text record is truncated." }
                    repeat(value) {
                        output += record[index + it]
                    }
                    index += value
                }
                in 9..0x7F -> output += value.toByte()
                in 0x80..0xBF -> {
                    require(index < record.size) { "PalmDOC back-reference is truncated." }
                    val pair = (value shl 8) or (record[index].toInt() and 0xFF)
                    index += 1
                    val distance = (pair shr 3) and 0x07FF
                    val length = (pair and 0x07) + 3
                    require(distance in 1..output.size) { "PalmDOC back-reference is invalid." }
                    repeat(length) {
                        output += output[output.size - distance]
                    }
                }
                else -> {
                    output += ' '.code.toByte()
                    output += (value xor 0x80).toByte()
                }
            }
        }
        return output.toByteArray()
    }

    private fun packageDocument(document: MobiDocument, identifier: String): String {
        val authorXml = document.author?.let { "<dc:creator>${escapeXml(it)}</dc:creator>" }.orEmpty()
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="book-id">${escapeXml(identifier)}</dc:identifier>
                <dc:title>${escapeXml(document.title)}</dc:title>
                $authorXml
                <dc:language>${escapeXml(document.language)}</dc:language>
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
              <head><title>Contents</title></head>
              <body><nav epub:type="toc"><ol><li><a href="content.xhtml">${escapeXml(title)}</a></li></ol></nav></body>
            </html>
        """.trimIndent()

    private fun contentDocument(title: String, blocks: List<String>): String {
        val body = blocks.joinToString("\n") { "<p>${escapeXml(it)}</p>" }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
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

    private fun ByteArray.record(offsets: List<Int>, index: Int): ByteArray {
        val start = offsets[index]
        val end = offsets.getOrNull(index + 1) ?: size
        require(end >= start && end <= size) { "MOBI record table is invalid." }
        return copyOfRange(start, end)
    }

    private fun ByteArray.ascii(start: Int, end: Int): String =
        copyOfRange(start, end.coerceAtMost(size)).toString(Charsets.US_ASCII)

    private fun ByteArray.asciiIndexOf(value: String, start: Int): Int {
        val needle = value.toByteArray(Charsets.US_ASCII)
        for (index in start..(size - needle.size)) {
            if (needle.indices.all { offset -> this[index + offset] == needle[offset] }) return index
        }
        return -1
    }

    private fun ByteArray.u16(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    private fun ByteArray.u32(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private fun ByteArray.u32OrNull(offset: Int): Int? =
        if (offset + 4 <= size) u32(offset) else null

    private fun String.cleanMetadataValue(): String? =
        replace(Regex("""\s+"""), " ").trim().takeIf { it.isNotBlank() }

    private fun String.cleanReadableBlock(): String? =
        replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf { it.length > 1 && it.any(Char::isLetterOrDigit) }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private data class MobiHeader(
        val title: String,
        val author: String?,
        val charset: Charset,
    )

    private data class MobiDocument(
        val title: String,
        val author: String?,
        val language: String,
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
        private const val MAX_MOBI_BYTES = 80L * 1024L * 1024L
        private const val MAX_DECOMPRESSED_TEXT_BYTES = 24 * 1024 * 1024
        private const val EPUB_MIME_TYPE = "application/epub+zip"
        private const val PDB_HEADER_SIZE = 78
        private const val RECORD_ENTRY_SIZE = 8
        private const val PDB_TYPE_OFFSET = 60
        private const val PDB_CREATOR_OFFSET = 64
        private const val PDB_RECORD_COUNT_OFFSET = 76
        private const val PALMDOC_HEADER_SIZE = 16
        private const val MOBI_MIN_HEADER_BYTES = 92
        private const val PALMDOC_COMPRESSION_NONE = 1
        private const val PALMDOC_COMPRESSION = 2
        private const val EXTH_HEADER_SIZE = 12
        private const val EXTH_RECORD_HEADER_SIZE = 8
        private const val EXTH_AUTHOR_TYPE = 100
        private val SUPPORTED_COMPRESSION = setOf(PALMDOC_COMPRESSION_NONE, PALMDOC_COMPRESSION)
        private val MOBI_CREATORS = setOf("MOBI", "REAd")
        private val WINDOWS_1252: Charset = Charset.forName("windows-1252")
    }
}
