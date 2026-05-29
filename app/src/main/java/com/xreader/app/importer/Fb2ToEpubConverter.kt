package com.xreader.app.importer

import com.xreader.app.core.TextTools
import java.io.File
import java.util.Base64
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

class Fb2ToEpubConverter {
    fun convert(input: File, output: File, fallbackTitle: String): File {
        val source = readFb2Bytes(input)
        val document = parseXml(source)
        val root = document.documentElement
        require(root.local().equals("FictionBook", ignoreCase = true)) {
            "File is not a FictionBook document."
        }

        val metadata = parseMetadata(root, fallbackTitle)
        val chapters = parseChapters(root)
        require(chapters.isNotEmpty()) {
            "FictionBook document contains no readable body text."
        }
        val cover = parseCover(root)
        val identifier = "urn:uuid:${UUID.nameUUIDFromBytes(source)}"

        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            writeStored(zip, "mimetype", EPUB_MIME_TYPE.toByteArray(Charsets.US_ASCII))
            writeDeflated(zip, "META-INF/container.xml", containerXml.toByteArray(Charsets.UTF_8))
            writeDeflated(
                zip,
                "OEBPS/package.opf",
                packageDocument(metadata, identifier, chapters, cover).toByteArray(Charsets.UTF_8)
            )
            writeDeflated(zip, "OEBPS/nav.xhtml", navigationDocument(metadata.title, chapters).toByteArray(Charsets.UTF_8))
            chapters.forEachIndexed { index, chapter ->
                writeDeflated(
                    zip,
                    "OEBPS/chapters/${chapterName(index)}.xhtml",
                    chapterDocument(chapter).toByteArray(Charsets.UTF_8)
                )
            }
            cover?.let {
                zip.putNextEntry(ZipEntry("OEBPS/images/cover.${it.extension}"))
                zip.write(it.bytes)
                zip.closeEntry()
            }
        }
        return output
    }

    private fun readFb2Bytes(input: File): ByteArray {
        if (!input.name.lowercase(Locale.US).endsWith(".zip")) {
            require(input.length() <= MAX_FB2_BYTES) { "FictionBook file is too large." }
            return input.readBytes()
        }
        ZipFile(input).use { zip ->
            val entry = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .firstOrNull { TextTools.extension(it.name) == "fb2" }
                ?: error("FB2 ZIP archive contains no .fb2 document.")
            if (entry.size > MAX_FB2_BYTES) error("FictionBook file is too large.")
            return zip.getInputStream(entry).use { stream ->
                val bytes = stream.readBytes()
                require(bytes.size <= MAX_FB2_BYTES) { "FictionBook file is too large." }
                bytes
            }
        }
    }

    private fun parseXml(bytes: ByteArray): Document {
        require(!bytes.toString(Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)) {
            "FictionBook XML doctype declarations are not supported."
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

    private fun parseMetadata(root: Element, fallbackTitle: String): Fb2Metadata {
        val titleInfo = root.directChild("description")?.directChild("title-info")
        val title = TextTools.cleanTitle(
            titleInfo?.directChildText("book-title")
                ?: fallbackTitle
        )
        val author = titleInfo?.directChildren("author")
            ?.firstNotNullOfOrNull { it.authorName() }
            ?: "Unknown Author"
        val genre = titleInfo?.directChildText("genre")?.takeIf { it.isNotBlank() }
        val language = titleInfo?.directChildText("lang")?.takeIf { it.isNotBlank() } ?: "en"
        val description = titleInfo?.directChildText("annotation")?.takeIf { it.isNotBlank() }
        val dateElement = titleInfo?.directChild("date")
        val year = dateElement?.getAttribute("value")
            ?.takeIf { it.isNotBlank() }
            ?: dateElement?.cleanText()
        val sequence = titleInfo?.directChildren("sequence")?.firstOrNull()
        return Fb2Metadata(
            title = title,
            author = author,
            language = language,
            genre = genre,
            description = description,
            year = year?.let { YEAR_REGEX.find(it)?.value },
            series = sequence?.getAttribute("name")?.trim()?.takeIf { it.isNotBlank() },
            seriesIndex = sequence?.getAttribute("number")?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun Element.authorName(): String? {
        val fields = listOf(
            directChildText("first-name"),
            directChildText("middle-name"),
            directChildText("last-name")
        ).filterNotNull()
        return fields.joinToString(" ")
            .takeIf { it.isNotBlank() }
            ?: directChildText("nickname")
            ?: directChildText("email")
    }

    private fun parseChapters(root: Element): List<Fb2Chapter> {
        val bodies = root.directChildren("body")
            .filterNot { it.getAttribute("name").equals("notes", ignoreCase = true) }
        val chapters = bodies.flatMapIndexed { bodyIndex, body ->
            val sections = body.directChildren("section")
            if (sections.isEmpty()) {
                listOfNotNull(body.toChapter("Body ${bodyIndex + 1}"))
            } else {
                sections.flatMap { section -> section.toChapters() }
            }
        }
        return chapters.mapIndexed { index, chapter ->
            chapter.copy(title = chapter.title.ifBlank { "Chapter ${index + 1}" })
        }
    }

    private fun Element.toChapters(): List<Fb2Chapter> {
        val result = mutableListOf<Fb2Chapter>()
        toChapter(null)?.let(result::add)
        directChildren("section").forEach { child ->
            result += child.toChapters()
        }
        return result
    }

    private fun Element.toChapter(fallbackTitle: String?): Fb2Chapter? {
        val title = directChild("title")?.cleanText()
            ?: directChildText("subtitle")
            ?: fallbackTitle.orEmpty()
        val paragraphs = mutableListOf<String>()
        childElements()
            .filterNot { it.local() in setOf("section", "title") }
            .forEach { child -> paragraphs += child.readingBlocks() }
        val cleanParagraphs = paragraphs
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length > 1 }
        if (cleanParagraphs.isEmpty()) return null
        return Fb2Chapter(title = title, paragraphs = cleanParagraphs)
    }

    private fun Element.readingBlocks(): List<String> =
        when (local()) {
            "p", "subtitle", "text-author", "v" -> listOf(cleanText())
            "empty-line", "image" -> emptyList()
            else -> childElements()
                .flatMap { it.readingBlocks() }
                .ifEmpty {
                    cleanText().takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
                }
        }

    private fun parseCover(root: Element): Fb2Cover? {
        val titleInfo = root.directChild("description")?.directChild("title-info")
        val href = titleInfo
            ?.directChild("coverpage")
            ?.descendants("image")
            ?.firstOrNull()
            ?.hrefAttribute()
            ?.removePrefix("#")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val binary = root.directChildren("binary").firstOrNull { it.getAttribute("id") == href }
            ?: return null
        val contentType = binary.getAttribute("content-type")
        val extension = coverExtension(contentType, href) ?: return null
        val bytes = runCatching {
            Base64.getMimeDecoder().decode(binary.textContent)
        }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) return null
        return Fb2Cover(bytes = bytes, extension = extension, mediaType = imageMediaType(extension))
    }

    private fun packageDocument(
        metadata: Fb2Metadata,
        identifier: String,
        chapters: List<Fb2Chapter>,
        cover: Fb2Cover?,
    ): String {
        val coverItem = cover?.let {
            """<item id="cover-image" href="images/cover.${it.extension}" media-type="${it.mediaType}" properties="cover-image"/>"""
        }
        val chapterItems = chapters.mapIndexed { index, _ ->
            """<item id="chapter-${chapterNumber(index)}" href="chapters/${chapterName(index)}.xhtml" media-type="application/xhtml+xml"/>"""
        }
        val spineItems = chapters.mapIndexed { index, _ ->
            """<itemref idref="chapter-${chapterNumber(index)}"/>"""
        }
        val optionalMetadata = buildList {
            metadata.genre?.let { add("<dc:subject>${escapeXml(it)}</dc:subject>") }
            metadata.year?.let { add("<dc:date>${escapeXml(it)}</dc:date>") }
            metadata.description?.let { add("<dc:description>${escapeXml(it)}</dc:description>") }
            metadata.series?.let { add("""<meta name="calibre:series" content="${escapeXml(it)}"/>""") }
            metadata.seriesIndex?.let { add("""<meta name="calibre:series_index" content="${escapeXml(it)}"/>""") }
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="book-id">${escapeXml(identifier)}</dc:identifier>
                <dc:title>${escapeXml(metadata.title)}</dc:title>
                <dc:creator>${escapeXml(metadata.author)}</dc:creator>
                <dc:language>${escapeXml(metadata.language)}</dc:language>
                ${optionalMetadata.joinToString("\n                ")}
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                ${chapterItems.joinToString("\n                ")}
                ${coverItem.orEmpty()}
              </manifest>
              <spine>
                ${spineItems.joinToString("\n                ")}
              </spine>
            </package>
        """.trimIndent()
    }

    private fun navigationDocument(title: String, chapters: List<Fb2Chapter>): String {
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

    private fun chapterDocument(chapter: Fb2Chapter): String {
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

    private fun Element.directChild(localName: String): Element? =
        childElements().firstOrNull { it.local().equals(localName, ignoreCase = true) }

    private fun Element.directChildren(localName: String): List<Element> =
        childElements().filter { it.local().equals(localName, ignoreCase = true) }

    private fun Element.directChildText(localName: String): String? =
        directChild(localName)?.cleanText()?.takeIf { it.isNotBlank() }

    private fun Element.childElements(): List<Element> =
        buildList {
            val children = childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child.nodeType == Node.ELEMENT_NODE) add(child as Element)
            }
        }

    private fun Element.descendants(localName: String): List<Element> =
        buildList {
            val nodes = getElementsByTagName("*")
            for (index in 0 until nodes.length) {
                val item = nodes.item(index) as? Element ?: continue
                if (item.local().equals(localName, ignoreCase = true)) add(item)
            }
        }

    private fun Element.hrefAttribute(): String? =
        buildList {
            val attrs = attributes
            for (index in 0 until attrs.length) {
                val attr = attrs.item(index)
                val name = attr.localName ?: attr.nodeName.substringAfter(':')
                if (name.equals("href", ignoreCase = true)) add(attr.nodeValue)
            }
        }.firstNotNullOfOrNull { it.trim().takeIf(String::isNotBlank) }

    private fun Element.local(): String =
        localName ?: nodeName.substringAfter(':')

    private fun Element.cleanText(): String =
        textContent.replace(Regex("\\s+"), " ").trim()

    private fun chapterName(index: Int): String = "chapter-${chapterNumber(index)}"

    private fun chapterNumber(index: Int): String = "%04d".format(Locale.US, index + 1)

    private fun coverExtension(contentType: String, name: String): String? =
        when (contentType.lowercase(Locale.US)) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> when (TextTools.extension(name)) {
                "jpg", "jpeg" -> "jpg"
                "png" -> "png"
                "webp" -> "webp"
                else -> null
            }
        }

    private fun imageMediaType(extension: String): String =
        when (extension) {
            "jpg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private data class Fb2Metadata(
        val title: String,
        val author: String,
        val language: String,
        val genre: String?,
        val description: String?,
        val year: String?,
        val series: String?,
        val seriesIndex: String?,
    )

    private data class Fb2Chapter(
        val title: String,
        val paragraphs: List<String>,
    )

    private data class Fb2Cover(
        val bytes: ByteArray,
        val extension: String,
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
        private const val MAX_FB2_BYTES = 64L * 1024L * 1024L
        private const val MAX_COVER_BYTES = 10 * 1024 * 1024
        private val YEAR_REGEX = Regex("""\d{4}""")
    }
}
