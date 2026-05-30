package com.xreader.app.importer

import com.xreader.app.core.TextTools
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Base64
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

class MhtmlToEpubConverter {
    fun convert(input: File, output: File, fallbackTitle: String): File {
        require(input.length() <= MAX_MHTML_BYTES) { "MHTML file is too large." }
        val archiveBytes = input.readBytes()
        val archive = parseArchive(archiveBytes)
        val htmlPart = archive.parts.firstOrNull { it.mediaType in HTML_MEDIA_TYPES }
            ?: error("MHTML archive contains no HTML root part.")
        val htmlBytes = decodeBody(htmlPart)
        val charset = htmlPart.charsetName()
        val document = Jsoup.parse(htmlBytes.inputStream(), charset, htmlPart.contentLocation.orEmpty())
        val assets = archive.parts
            .filter { it.mediaType.startsWith("image/") }
            .take(MAX_ASSET_COUNT)
            .mapIndexedNotNull { index, part -> part.toAsset(index) }
        val assetLookup = buildAssetLookup(assets)
        rewriteImageReferences(document, assetLookup)

        val metadata = parseMetadata(document, fallbackTitle)
        val blocks = parseBlocks(document.body())
        val chapters = chaptersFromBlocks(blocks, metadata.title)
        require(chapters.isNotEmpty()) {
            "MHTML archive contains no readable text."
        }
        val identifier = "urn:uuid:${UUID.nameUUIDFromBytes(archiveBytes)}"

        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            writeStored(zip, "mimetype", EPUB_MIME_TYPE.toByteArray(Charsets.US_ASCII))
            writeDeflated(zip, "META-INF/container.xml", containerXml.toByteArray(Charsets.UTF_8))
            writeDeflated(
                zip,
                "OEBPS/package.opf",
                packageDocument(metadata, identifier, chapters, assets).toByteArray(Charsets.UTF_8)
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
            assets.forEach { asset ->
                writeDeflated(zip, "OEBPS/${asset.href}", asset.bytes)
            }
        }
        return output
    }

    private fun parseArchive(bytes: ByteArray): MhtmlArchive {
        val raw = bytes.toString(Charsets.ISO_8859_1)
        val split = raw.headerBodySplit() ?: error("MHTML archive is missing headers.")
        val headers = parseHeaders(raw.substring(0, split.headerEnd))
        val boundary = parseContentType(headers["content-type"].orEmpty()).params["boundary"]
            ?: error("MHTML archive is missing multipart boundary.")
        val body = raw.substring(split.bodyStart)
        val parts = splitMultipart(body, boundary).mapNotNull { parsePart(it) }
        require(parts.isNotEmpty()) { "MHTML archive contains no MIME parts." }
        return MhtmlArchive(parts = parts)
    }

    private fun splitMultipart(body: String, boundary: String): List<String> {
        val delimiter = Regex("(?m)^--${Regex.escape(boundary)}(--)?[ \\t]*\\r?$")
        val parts = mutableListOf<String>()
        var previousEnd: Int? = null
        delimiter.findAll(body).forEach { match ->
            previousEnd?.let { start ->
                val part = body.substring(start, match.range.first).trimPartBoundaryPadding()
                if (part.isNotBlank()) parts += part
            }
            val closing = match.groups[1]?.value == "--"
            if (closing) return parts
            previousEnd = body.indexAfterLine(match.range.last + 1)
        }
        return parts
    }

    private fun parsePart(rawPart: String): MhtmlPart? {
        val split = rawPart.headerBodySplit() ?: return null
        val headers = parseHeaders(rawPart.substring(0, split.headerEnd))
        val contentType = parseContentType(headers["content-type"].orEmpty())
        return MhtmlPart(
            headers = headers,
            mediaType = contentType.mediaType,
            contentTypeParams = contentType.params,
            contentLocation = headers["content-location"]?.trim()?.takeIf { it.isNotBlank() },
            contentId = headers["content-id"]?.trim()?.trim('<', '>')?.takeIf { it.isNotBlank() },
            body = rawPart.substring(split.bodyStart).toByteArray(Charsets.ISO_8859_1)
        )
    }

    private fun parseHeaders(headerText: String): Map<String, String> {
        val unfolded = mutableListOf<String>()
        headerText.lineSequence().forEach { line ->
            if ((line.startsWith(' ') || line.startsWith('\t')) && unfolded.isNotEmpty()) {
                unfolded[unfolded.lastIndex] = unfolded.last() + " " + line.trim()
            } else if (line.isNotBlank()) {
                unfolded += line.trimEnd('\r')
            }
        }
        return unfolded
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) {
                    null
                } else {
                    line.substring(0, separator).trim().lowercase(Locale.US) to
                        line.substring(separator + 1).trim()
                }
            }
            .toMap()
    }

    private fun parseContentType(value: String): ParsedContentType {
        val segments = value.split(';')
        val mediaType = segments.firstOrNull()
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }
            ?: "text/plain"
        val params = segments.drop(1).mapNotNull { segment ->
            val separator = segment.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = segment.substring(0, separator).trim().lowercase(Locale.US)
            val paramValue = segment.substring(separator + 1).trim().trim('"')
            name.takeIf { it.isNotBlank() }?.let { it to paramValue }
        }.toMap()
        return ParsedContentType(mediaType = mediaType, params = params)
    }

    private fun decodeBody(part: MhtmlPart): ByteArray =
        when (part.headers["content-transfer-encoding"]?.lowercase(Locale.US)?.trim()) {
            "base64" -> Base64.getMimeDecoder().decode(part.body.toString(Charsets.US_ASCII))
            "quoted-printable" -> decodeQuotedPrintable(part.body)
            else -> part.body
        }

    private fun decodeQuotedPrintable(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(bytes.size)
        var index = 0
        while (index < bytes.size) {
            val value = bytes[index].toInt() and 0xFF
            if (value == '='.code && index + 1 < bytes.size) {
                val next = bytes[index + 1].toInt() and 0xFF
                val afterNext = bytes.getOrNull(index + 2)?.toInt()?.and(0xFF)
                when {
                    next == '\r'.code && afterNext == '\n'.code -> index += 3
                    next == '\n'.code -> index += 2
                    afterNext != null && next.isHexDigit() && afterNext.isHexDigit() -> {
                        output.write((next.hexValue() shl 4) + afterNext.hexValue())
                        index += 3
                    }
                    else -> {
                        output.write(value)
                        index += 1
                    }
                }
            } else {
                output.write(value)
                index += 1
            }
        }
        return output.toByteArray()
    }

    private fun MhtmlPart.toAsset(index: Int): MhtmlAsset? {
        val extension = assetExtension(mediaType, contentLocation) ?: return null
        val bytes = decodeBody(this).takeIf { it.isNotEmpty() && it.size <= MAX_ASSET_BYTES } ?: return null
        return MhtmlAsset(
            id = "asset-${index + 1}",
            href = "assets/asset-${index + 1}.$extension",
            mediaType = mediaType,
            contentLocation = contentLocation,
            contentId = contentId,
            bytes = bytes
        )
    }

    private fun buildAssetLookup(assets: List<MhtmlAsset>): Map<String, MhtmlAsset> {
        val lookup = linkedMapOf<String, MhtmlAsset>()
        fun add(key: String?, asset: MhtmlAsset) {
            val normalized = key?.normalizedReferenceKey() ?: return
            lookup[normalized] = asset
        }
        assets.forEach { asset ->
            add(asset.contentId, asset)
            add(asset.contentLocation, asset)
            add(asset.contentLocation?.substringAfterLast('/'), asset)
            add(asset.contentLocation?.substringAfterLast('\\'), asset)
        }
        return lookup
    }

    private fun rewriteImageReferences(document: Document, assets: Map<String, MhtmlAsset>) {
        val rootLocation = document.location().takeIf { it.isNotBlank() }
        document.select("img[src]").forEach { image ->
            val source = image.attr("src")
            val asset = documentReferenceVariants(rootLocation, source)
                .asSequence()
                .mapNotNull { assets[it.normalizedReferenceKey()] }
                .firstOrNull()
            if (asset == null) {
                image.removeAttr("src")
            } else {
                image.attr("src", "../${asset.href}")
                image.attr("data-xreader-asset", asset.href)
            }
        }
    }

    private fun documentReferenceVariants(rootLocation: String?, source: String): List<String> =
        buildList {
            if (source.isNotBlank()) {
                add(source)
                if (source.startsWith("cid:", ignoreCase = true)) add(source.removePrefixIgnoreCase("cid:"))
                decodedReference(source)?.let { add(it) }
            }
            if (!rootLocation.isNullOrBlank() && source.isNotBlank()) {
                runCatching { URI(rootLocation).resolve(source).toString() }.getOrNull()?.let { add(it) }
            }
        }

    private fun parseMetadata(document: Document, fallbackTitle: String): MhtmlMetadata {
        val title = TextTools.cleanTitle(
            document.metaContent("dc.title", "dcterms.title", "og:title")
                ?: document.title().takeIf { it.isNotBlank() }
                ?: fallbackTitle
        )
        val date = document.metaContent("date", "dc.date", "dcterms.date", "dcterms.created", "dcterms.issued", "article:published_time")
        return MhtmlMetadata(
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

    private fun parseBlocks(root: Element): List<MhtmlBlock> =
        buildList {
            root.collectBlocks(this)
        }.mapNotNull { block ->
            if (block.kind == MhtmlBlockKind.IMAGE) {
                block
            } else {
                val clean = block.text.cleanBlockText(preformatted = block.kind == MhtmlBlockKind.PREFORMATTED)
                clean.takeIf { it.length > 1 && it.any(Char::isLetterOrDigit) }?.let {
                    block.copy(text = it)
                }
            }
        }

    private fun Element.collectBlocks(output: MutableList<MhtmlBlock>) {
        val tag = normalizedTag()
        if (tag in SKIP_TAGS) return
        when {
            tag == "img" -> {
                attr("data-xreader-asset").takeIf { it.isNotBlank() }?.let { href ->
                    output += MhtmlBlock(
                        text = attr("alt").ifBlank { attr("title") }.cleanBlockText(preformatted = false),
                        kind = MhtmlBlockKind.IMAGE,
                        assetHref = href
                    )
                }
            }
            tag.matches(HEADING_TAG_REGEX) -> {
                readingText().toReadableBlock()?.let { text ->
                    output += MhtmlBlock(
                        text = text,
                        kind = MhtmlBlockKind.HEADING,
                        headingLevel = tag.removePrefix("h").toIntOrNull()?.coerceIn(1, 6) ?: 1
                    )
                }
            }
            tag in PARAGRAPH_TAGS -> readingText().toReadableBlock()?.let { output += MhtmlBlock(it, MhtmlBlockKind.PARAGRAPH) }
            tag == "blockquote" -> readingText().toReadableBlock()?.let { output += MhtmlBlock(it, MhtmlBlockKind.QUOTE) }
            tag == "pre" -> wholeText().cleanBlockText(preformatted = true)
                .takeIf { it.isNotBlank() }
                ?.let { output += MhtmlBlock(it, MhtmlBlockKind.PREFORMATTED) }
            tag == "li" -> readingText().toReadableBlock()?.let { output += MhtmlBlock(it, MhtmlBlockKind.LIST_ITEM) }
            tag == "tr" -> tableRowText().toReadableBlock()?.let { output += MhtmlBlock(it, MhtmlBlockKind.PARAGRAPH) }
            else -> collectContainerBlocks(output)
        }
    }

    private fun Element.collectContainerBlocks(output: MutableList<MhtmlBlock>) {
        val inline = StringBuilder()
        fun flushInline() {
            inline.toString().toReadableBlock()?.let { output += MhtmlBlock(it, MhtmlBlockKind.PARAGRAPH) }
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
                        tag == "img" || node.isBlockBoundary() -> {
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

    private fun chaptersFromBlocks(blocks: List<MhtmlBlock>, fallbackTitle: String): List<MhtmlChapter> {
        if (blocks.isEmpty()) return emptyList()
        val result = mutableListOf<MhtmlChapter>()
        var title = fallbackTitle.ifBlank { "Document" }
        val content = mutableListOf<MhtmlBlock>()
        fun flush() {
            val clean = content.filter { it.kind == MhtmlBlockKind.IMAGE || (it.text.length > 1 && it.text.any(Char::isLetterOrDigit)) }
            if (clean.isNotEmpty()) {
                result += MhtmlChapter(
                    title = title.ifBlank { "Chapter ${result.size + 1}" },
                    blocks = clean
                )
            }
            content.clear()
        }
        blocks.forEach { block ->
            val headingLevel = block.headingLevel ?: Int.MAX_VALUE
            if (block.kind == MhtmlBlockKind.HEADING && headingLevel <= 2) {
                flush()
                title = block.text
            } else {
                content += block
            }
        }
        flush()
        if (result.isEmpty()) {
            val first = blocks.first()
            result += MhtmlChapter(
                title = first.takeIf { it.kind == MhtmlBlockKind.HEADING }?.text ?: title,
                blocks = blocks.filter { it.kind != MhtmlBlockKind.HEADING }
                    .ifEmpty { listOf(MhtmlBlock(first.text, MhtmlBlockKind.PARAGRAPH)) }
            )
        }
        return result.distinctBy { chapter ->
            "${chapter.title}\u0000${chapter.blocks.joinToString("\u0000") { "${it.kind}:${it.text}:${it.assetHref}" }}"
        }
    }

    private fun packageDocument(
        metadata: MhtmlMetadata,
        identifier: String,
        chapters: List<MhtmlChapter>,
        assets: List<MhtmlAsset>,
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
        val assetItems = assets.map {
            """<item id="${it.id}" href="${it.href}" media-type="${it.mediaType}"/>"""
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
                ${assetItems.joinToString("\n                ")}
              </manifest>
              <spine>
                ${spineItems.joinToString("\n                ")}
              </spine>
            </package>
        """.trimIndent()
    }

    private fun navigationDocument(title: String, language: String, chapters: List<MhtmlChapter>): String {
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

    private fun chapterDocument(chapter: MhtmlChapter, language: String): String {
        val body = chapter.blocks.joinToString("\n") { block ->
            when (block.kind) {
                MhtmlBlockKind.HEADING -> {
                    val level = (block.headingLevel ?: 2).coerceIn(2, 6)
                    "<h$level>${escapeXml(block.text)}</h$level>"
                }
                MhtmlBlockKind.IMAGE -> {
                    val src = "../${block.assetHref}"
                    val alt = block.text.takeIf { it.isNotBlank() }
                    buildString {
                        append("<figure><img src=\"${escapeXml(src)}\"")
                        alt?.let { append(" alt=\"${escapeXml(it)}\"") }
                        append("/>")
                        alt?.let { append("<figcaption>${escapeXml(it)}</figcaption>") }
                        append("</figure>")
                    }
                }
                MhtmlBlockKind.LIST_ITEM -> "<ul><li>${escapeXml(block.text)}</li></ul>"
                MhtmlBlockKind.QUOTE -> "<blockquote><p>${escapeXml(block.text)}</p></blockquote>"
                MhtmlBlockKind.PREFORMATTED -> "<pre>${escapeXml(block.text)}</pre>"
                MhtmlBlockKind.PARAGRAPH -> "<p>${escapeXml(block.text)}</p>"
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

    private fun MhtmlPart.charsetName(): String? =
        contentTypeParams["charset"]?.takeIf { runCatching { Charset.forName(it) }.isSuccess }

    private fun Element.normalizedTag(): String = tagName().lowercase(Locale.US)

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
            normalized.replace(WHITESPACE_REGEX, " ").trim()
        }
    }

    private fun String.cleanMetadataValue(): String? =
        trim().replace(WHITESPACE_REGEX, " ").takeIf { it.isNotBlank() }

    private fun String.normalizedReferenceKey(): String? =
        trim()
            .trim('<', '>')
            .removePrefixIgnoreCase("cid:")
            .let { decodedReference(it) ?: it }
            .trim()
            .takeIf { it.isNotBlank() }
            ?.lowercase(Locale.US)

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    private fun decodedReference(value: String): String? =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }
            .getOrNull()
            ?.takeIf { it != value }

    private fun assetExtension(mediaType: String, location: String?): String? =
        when (mediaType.lowercase(Locale.US)) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/svg+xml" -> "svg"
            "image/webp" -> "webp"
            else -> location
                ?.substringAfterLast('/', "")
                ?.substringAfterLast('\\', "")
                ?.substringAfterLast('.', "")
                ?.lowercase(Locale.US)
                ?.takeIf { it in FALLBACK_IMAGE_EXTENSIONS }
        }

    private fun Int.isHexDigit(): Boolean =
        (this in '0'.code..'9'.code) || (this in 'a'.code..'f'.code) || (this in 'A'.code..'F'.code)

    private fun Int.hexValue(): Int =
        when (this) {
            in '0'.code..'9'.code -> this - '0'.code
            in 'a'.code..'f'.code -> this - 'a'.code + 10
            else -> this - 'A'.code + 10
        }

    private fun String.headerBodySplit(): HeaderBodySplit? {
        val crlf = indexOf("\r\n\r\n")
        if (crlf >= 0) return HeaderBodySplit(headerEnd = crlf, bodyStart = crlf + 4)
        val lf = indexOf("\n\n")
        if (lf >= 0) return HeaderBodySplit(headerEnd = lf, bodyStart = lf + 2)
        return null
    }

    private fun String.trimPartBoundaryPadding(): String =
        trimStart('\r', '\n').trimEnd('\r', '\n')

    private fun String.indexAfterLine(index: Int): Int =
        when {
            index < length && this[index] == '\r' && getOrNull(index + 1) == '\n' -> index + 2
            index < length && this[index] == '\n' -> index + 1
            else -> index
        }

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

    private data class HeaderBodySplit(
        val headerEnd: Int,
        val bodyStart: Int,
    )

    private data class ParsedContentType(
        val mediaType: String,
        val params: Map<String, String>,
    )

    private data class MhtmlArchive(val parts: List<MhtmlPart>)

    private data class MhtmlPart(
        val headers: Map<String, String>,
        val mediaType: String,
        val contentTypeParams: Map<String, String>,
        val contentLocation: String?,
        val contentId: String?,
        val body: ByteArray,
    )

    private data class MhtmlAsset(
        val id: String,
        val href: String,
        val mediaType: String,
        val contentLocation: String?,
        val contentId: String?,
        val bytes: ByteArray,
    )

    private data class MhtmlMetadata(
        val title: String,
        val author: String? = null,
        val language: String = "en",
        val description: String? = null,
        val subject: String? = null,
        val year: String? = null,
    )

    private data class MhtmlBlock(
        val text: String,
        val kind: MhtmlBlockKind,
        val headingLevel: Int? = null,
        val assetHref: String? = null,
    )

    private enum class MhtmlBlockKind {
        PARAGRAPH,
        HEADING,
        IMAGE,
        LIST_ITEM,
        QUOTE,
        PREFORMATTED,
    }

    private data class MhtmlChapter(
        val title: String,
        val blocks: List<MhtmlBlock>,
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
        private const val MAX_MHTML_BYTES = 64L * 1024L * 1024L
        private const val MAX_ASSET_BYTES = 24 * 1024 * 1024
        private const val MAX_ASSET_COUNT = 256
        private val HTML_MEDIA_TYPES = setOf("text/html", "application/xhtml+xml")
        private val FALLBACK_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "svg", "webp")
        private val YEAR_REGEX = Regex("""\d{4}""")
        private val WHITESPACE_REGEX = Regex("\\s+")
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
