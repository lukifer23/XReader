package com.xreader.app.importer

import com.xreader.app.core.TextTools
import com.xreader.app.reader.PublicationMetadata
import com.xreader.app.reader.ReadingUnit
import org.jsoup.Jsoup
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubParser {
    data class ParsedEpub(
        val metadata: PublicationMetadata,
        val units: List<ReadingUnit>,
        val coverImage: CoverImage?,
    )

    data class CoverImage(
        val bytes: ByteArray,
        val extension: String,
    )

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: Set<String>,
    )

    fun parse(file: File): ParsedEpub {
        ZipFile(file).use { zip ->
            val opfPath = findPackagePath(zip)
            val opf = zip.getInputStream(zip.getEntry(opfPath)).use { parseXml(it.readBytes()) }
            val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            val metadata = parseMetadata(opf)
            val manifest = parseManifest(opf)
            val spine = parseSpine(opf)
            val units = spine.flatMapIndexed { chapterIndex, idRef ->
                val href = manifest[idRef]?.href ?: return@flatMapIndexed emptyList()
                val entryName = normalizeZipPath(basePath, href)
                val entry = zip.getEntry(entryName) ?: return@flatMapIndexed emptyList()
                zip.getInputStream(entry).use { htmlToUnits(it, chapterIndex, entryName) }
            }

            return ParsedEpub(
                metadata = metadata,
                coverImage = parseCoverImage(zip, opf, basePath, manifest),
                units = units.ifEmpty {
                    zip.entries().asSequence()
                        .filter { it.name.endsWith(".xhtml", true) || it.name.endsWith(".html", true) }
                        .flatMapIndexed { index, entry ->
                            zip.getInputStream(entry).use {
                                htmlToUnits(it, index, entry.name).asSequence()
                            }
                        }
                        .toList()
                }
            )
        }
    }

    fun parseCover(file: File): CoverImage? {
        ZipFile(file).use { zip ->
            val opfPath = findPackagePath(zip)
            val opf = zip.getInputStream(zip.getEntry(opfPath)).use { parseXml(it.readBytes()) }
            val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            return parseCoverImage(zip, opf, basePath, parseManifest(opf))
        }
    }

    fun readMetadata(file: File): PublicationMetadata {
        ZipFile(file).use { zip ->
            val opfPath = findPackagePath(zip)
            val opf = zip.getInputStream(zip.getEntry(opfPath)).use { parseXml(it.readBytes()) }
            return parseMetadata(opf)
        }
    }

    private fun findPackagePath(zip: ZipFile): String {
        val container = zip.getEntry("META-INF/container.xml")
            ?: error("EPUB is missing META-INF/container.xml")
        val document = zip.getInputStream(container).use { parseXml(it.readBytes()) }
        val rootfile = document.getElementsByTagName("rootfile").item(0) as? Element
            ?: error("EPUB container has no rootfile")
        return rootfile.getAttribute("full-path")
            .ifBlank { error("EPUB rootfile has no full-path") }
    }

    private fun parseXml(bytes: ByteArray): Document {
        require(!bytes.toString(Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)) {
            "EPUB XML doctype declarations are not supported"
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

    private fun parseMetadata(opf: Document): PublicationMetadata {
        val title = firstText(opf, "title")
        val author = firstText(opf, "creator")
        val language = firstText(opf, "language")
        val description = firstText(opf, "description")
        val genre = PublicationMetadataTools.cleanGenre(allText(opf, "subject") + listOfNotNull(description))
        val year = firstText(opf, "date")?.let { Regex("""\d{4}""").find(it)?.value?.toIntOrNull() }
        val epub3Series = epub3Series(opf)
        val series = PublicationMetadataTools.cleanSeriesName(
            metaContent(opf, "calibre:series") ?: epub3Series?.name ?: PublicationMetadataTools.seriesFromDescription(description)
        )
        val seriesIndex = PublicationMetadataTools.parseSeriesIndex(
            metaContent(opf, "calibre:series_index") ?: epub3Series?.position
        )
        return PublicationMetadata(title, author, language, description, genre, year, series, seriesIndex)
    }

    private fun firstText(document: Document, localName: String): String? {
        return allText(document, localName).firstOrNull()
    }

    private fun allText(document: Document, localName: String): List<String> {
        val result = mutableListOf<String>()
        val nodes = document.getElementsByTagName("*")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index)
            val actual = element.localName ?: element.nodeName.substringAfter(':')
            if (actual.equals(localName, ignoreCase = true)) {
                val text = element.textContent.trim()
                if (text.isNotBlank()) result.add(text)
            }
        }
        return result
    }

    private fun metaContent(document: Document, name: String): String? {
        val nodes = document.getElementsByTagName("meta")
        for (index in 0 until nodes.length) {
            val meta = nodes.item(index) as? Element ?: continue
            if (meta.getAttribute("name").equals(name, ignoreCase = true)) {
                meta.getAttribute("content").trim().takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    private data class Epub3Series(val name: String, val position: String?)

    private fun epub3Series(document: Document): Epub3Series? {
        val metas = document.getElementsByTagName("meta")
        for (index in 0 until metas.length) {
            val meta = metas.item(index) as? Element ?: continue
            if (!meta.getAttribute("property").equals("belongs-to-collection", ignoreCase = true)) continue
            val name = meta.textContent.trim().takeIf { it.isNotBlank() } ?: continue
            val id = meta.getAttribute("id").takeIf { it.isNotBlank() }
            val type = id?.let { refinedMeta(document, it, "collection-type") }
            if (type != null && !type.equals("series", ignoreCase = true)) continue
            return Epub3Series(name = name, position = id?.let { refinedMeta(document, it, "group-position") })
        }
        return null
    }

    private fun refinedMeta(document: Document, id: String, property: String): String? {
        val metas = document.getElementsByTagName("meta")
        for (index in 0 until metas.length) {
            val meta = metas.item(index) as? Element ?: continue
            if (
                meta.getAttribute("refines").equals("#$id", ignoreCase = true) &&
                meta.getAttribute("property").equals(property, ignoreCase = true)
            ) {
                return meta.textContent.trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun parseManifest(opf: Document): Map<String, ManifestItem> {
        val result = linkedMapOf<String, ManifestItem>()
        val nodes = opf.getElementsByTagName("item")
        for (index in 0 until nodes.length) {
            val item = nodes.item(index) as? Element ?: continue
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            val mediaType = item.getAttribute("media-type")
            val properties = item.getAttribute("properties")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .toSet()
            if (id.isNotBlank() && href.isNotBlank()) {
                result[id] = ManifestItem(id, href, mediaType, properties)
            }
        }
        return result
    }

    private fun parseCoverImage(
        zip: ZipFile,
        opf: Document,
        basePath: String,
        manifest: Map<String, ManifestItem>,
    ): CoverImage? {
        val explicitCoverId = coverMetaId(opf)
        listOfNotNull(
            explicitCoverId?.let(manifest::get),
            manifest.values.firstOrNull { it.properties.any { property -> property.equals("cover-image", ignoreCase = true) } },
        ).firstNotNullOfOrNull { coverFromManifestItem(zip, basePath, it) }?.let { return it }

        guideCoverImage(zip, opf, basePath, manifest)?.let { return it }

        return listOfNotNull(
            manifest.values.firstOrNull {
                it.isSupportedImage() && (it.id.contains("cover", ignoreCase = true) || it.href.contains("cover", ignoreCase = true))
            },
            manifest.values.firstOrNull { it.isSupportedImage() }
        ).firstNotNullOfOrNull { coverFromManifestItem(zip, basePath, it) }
    }

    private fun guideCoverImage(
        zip: ZipFile,
        opf: Document,
        basePath: String,
        manifest: Map<String, ManifestItem>,
    ): CoverImage? {
        val nodes = opf.getElementsByTagName("reference")
        for (index in 0 until nodes.length) {
            val reference = nodes.item(index) as? Element ?: continue
            if (!reference.getAttribute("type").equals("cover", ignoreCase = true)) continue
            val href = reference.getAttribute("href").takeIf { it.isNotBlank() } ?: continue
            val entryName = normalizeZipPath(basePath, href)
            val manifestItem = manifest.values.firstOrNull { normalizeZipPath(basePath, it.href) == entryName }
            if (manifestItem?.isSupportedImage() == true) {
                coverFromManifestItem(zip, basePath, manifestItem)?.let { return it }
            }
            if (coverExtension(manifestItem?.mediaType.orEmpty(), href) != null) {
                coverFromEntry(zip, entryName, manifestItem?.mediaType.orEmpty(), href)?.let { return it }
            }
            if (!isHtmlReference(manifestItem, href)) continue
            val pageEntry = zip.getEntry(entryName) ?: continue
            val imageHref = zip.getInputStream(pageEntry).use { coverImageHrefFromPage(it) } ?: continue
            val pageBasePath = entryName.substringBeforeLast('/', missingDelimiterValue = "")
            val imageEntryName = normalizeZipPath(pageBasePath, imageHref)
            val imageManifestItem = manifest.values.firstOrNull { normalizeZipPath(basePath, it.href) == imageEntryName }
            coverFromEntry(
                zip = zip,
                entryName = imageEntryName,
                mediaType = imageManifestItem?.mediaType.orEmpty(),
                href = imageManifestItem?.href ?: imageHref
            )?.let { return it }
        }
        return null
    }

    private fun coverFromManifestItem(zip: ZipFile, basePath: String, item: ManifestItem): CoverImage? {
        if (!item.isSupportedImage()) return null
        val entryName = normalizeZipPath(basePath, item.href)
        return coverFromEntry(zip, entryName, item.mediaType, item.href)
    }

    private fun coverFromEntry(
        zip: ZipFile,
        entryName: String,
        mediaType: String,
        href: String,
    ): CoverImage? {
        val entry = zip.getEntry(entryName) ?: return null
        val size = entry.size
        if (size > MAX_COVER_BYTES) return null
        val bytes = zip.getInputStream(entry).use { input ->
            val data = input.readBytes()
            if (data.size > MAX_COVER_BYTES) return null
            data
        }
        if (bytes.isEmpty()) return null
        val extension = coverExtension(mediaType, href) ?: return null
        return CoverImage(bytes, extension)
    }

    private fun isHtmlReference(item: ManifestItem?, href: String): Boolean {
        val mediaType = item?.mediaType.orEmpty().lowercase(Locale.US)
        return mediaType in HTML_MEDIA_TYPES || TextTools.extension(href.substringBefore('#')) in HTML_EXTENSIONS
    }

    private fun coverImageHrefFromPage(input: InputStream): String? {
        val document = Jsoup.parse(input, null, "")
        document.select("img[src]").firstOrNull()
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        for (element in document.getElementsByTag("image")) {
            listOf("href", "xlink:href").firstNotNullOfOrNull { attribute ->
                element.attr(attribute).trim().takeIf { it.isNotBlank() }
            }?.let { return it }
        }
        return null
    }

    private fun coverMetaId(opf: Document): String? {
        val nodes = opf.getElementsByTagName("meta")
        for (index in 0 until nodes.length) {
            val item = nodes.item(index) as? Element ?: continue
            if (item.getAttribute("name").equals("cover", ignoreCase = true)) {
                return item.getAttribute("content").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun ManifestItem.isSupportedImage(): Boolean =
        mediaType.lowercase(Locale.US) in SUPPORTED_COVER_MEDIA_TYPES || coverExtension(mediaType, href) != null

    private fun coverExtension(mediaType: String, href: String): String? =
        when (mediaType.lowercase(Locale.US)) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> when (TextTools.extension(href.substringBefore('#'))) {
                "jpg", "jpeg" -> "jpg"
                "png" -> "png"
                "webp" -> "webp"
                else -> null
            }
        }

    private fun parseSpine(opf: Document): List<String> {
        val ids = mutableListOf<String>()
        val nodes = opf.getElementsByTagName("itemref")
        for (index in 0 until nodes.length) {
            val itemRef = nodes.item(index) as? Element ?: continue
            itemRef.getAttribute("idref").takeIf { it.isNotBlank() }?.let(ids::add)
        }
        return ids
    }

    private fun normalizeZipPath(basePath: String, href: String): String {
        val decoded = href.substringBefore('#').replace("%20", " ")
        val combined = if (basePath.isBlank()) decoded else "$basePath/$decoded"
        val parts = ArrayDeque<String>()
        combined.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun htmlToUnits(input: InputStream, chapterIndex: Int, entryName: String): List<ReadingUnit> {
        val document = Jsoup.parse(input, null, "")
        document.select("script,style,nav[epub|type=toc],nav[type=toc]").remove()
        val heading = document.select("h1,h2,h3,title").firstOrNull()?.text()?.trim().orEmpty()
        val blocks = document.select("h1,h2,h3,h4,p,li,blockquote")
            .map { it.text().trim() }
            .filter { it.length > 1 }
            .ifEmpty {
                document.body().text().trim()
                    .takeIf { it.isNotBlank() }
                    ?.chunked(1200)
                    ?: emptyList()
            }

        return blocks.mapIndexed { blockIndex, text ->
            val clean = text.replace(Regex("\\s+"), " ").trim()
            ReadingUnit(
                index = chapterIndex * 10_000 + blockIndex,
                locator = "epub:$entryName:$blockIndex",
                heading = heading.ifBlank { "Chapter ${chapterIndex + 1}" },
                body = clean,
                wordCount = TextTools.wordCount(clean)
            )
        }
    }

    companion object {
        private const val MAX_COVER_BYTES = 10 * 1024 * 1024L
        private val HTML_EXTENSIONS = setOf("xhtml", "html", "htm")
        private val HTML_MEDIA_TYPES = setOf("application/xhtml+xml", "text/html")
        private val SUPPORTED_COVER_MEDIA_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

        fun extensionMediaType(extension: String): String =
            when (extension.lowercase(Locale.US)) {
                "xhtml", "html", "htm" -> "application/xhtml+xml"
                "css" -> "text/css"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "svg" -> "image/svg+xml"
                else -> "application/octet-stream"
            }
    }
}
