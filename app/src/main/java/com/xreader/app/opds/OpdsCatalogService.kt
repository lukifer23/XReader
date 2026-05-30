package com.xreader.app.opds

import com.xreader.app.importer.ImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.Charset
import java.time.Clock
import java.util.Locale

data class OpdsFeed(
    val title: String,
    val url: String,
    val entries: List<OpdsEntry>,
    val navigationLinks: List<OpdsLink>,
)

data class OpdsEntry(
    val id: String,
    val title: String,
    val author: String?,
    val summary: String?,
    val acquisitionLinks: List<OpdsLink>,
)

data class OpdsLink(
    val href: String,
    val rel: String?,
    val type: String?,
    val title: String?,
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: href.substringAfterLast('/').ifBlank { href }
}

class OpdsCatalogService(
    private val importService: ImportService,
    private val cacheDir: File,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun load(url: String): OpdsFeed = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeCatalogUrl(url)
        val response = openConnection(normalizedUrl).useResponse { connection ->
            val charset = connection.contentType.charsetFromContentType() ?: Charsets.UTF_8
            val bytes = connection.inputStream.buffered().use { input ->
                input.readBounded(MAX_FEED_BYTES)
            }
            bytes.toString(charset)
        }
        OpdsFeedParser.parse(response, normalizedUrl.toString())
    }

    suspend fun importLink(link: OpdsLink): ImportService.ImportResult = withContext(Dispatchers.IO) {
        require(link.isSupportedAcquisition()) { "This catalog item is not a supported book download." }
        val url = normalizeCatalogUrl(link.href)
        var target: File? = null
        try {
            openConnection(url).useResponse { connection ->
                val responseType = connection.contentType?.substringBefore(';')?.trim().orEmpty()
                val importType = responseType.ifBlank { link.type.orEmpty() }
                val download = File(cacheDir, "opds-${clock.millis()}-${url.safeDownloadName(importType)}")
                target = download
                download.parentFile?.mkdirs()
                connection.inputStream.buffered().use { input ->
                    download.outputStream().buffered().use { output ->
                        input.copyBoundedTo(output, MAX_BOOK_BYTES)
                    }
                }
                importService.importFile(download, download.name, importType)
            }
        } finally {
            target?.delete()
        }
    }

    private fun normalizeCatalogUrl(value: String): URL {
        val trimmed = value.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: throw IllegalArgumentException("Enter a valid catalog URL.")
        require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            "Catalog URLs must start with http:// or https://."
        }
        return uri.toURL()
    }

    private fun openConnection(url: URL): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", OPDS_ACCEPT_HEADER)
            setRequestProperty("User-Agent", "XReader/0.1")
        }

    private inline fun <T> HttpURLConnection.useResponse(block: (HttpURLConnection) -> T): T {
        try {
            val code = responseCode
            require(code in 200..299) { "Catalog request failed with HTTP $code." }
            return block(this)
        } finally {
            disconnect()
        }
    }

    companion object {
        internal const val MAX_FEED_BYTES = 4L * 1024L * 1024L
        internal const val MAX_BOOK_BYTES = 512L * 1024L * 1024L
        private const val HTTP_TIMEOUT_MS = 15_000
        private const val OPDS_ACCEPT_HEADER =
            "application/atom+xml;profile=opds-catalog, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.1"
    }
}

object OpdsFeedParser {
    fun parse(xml: String, baseUrl: String): OpdsFeed {
        val document = Jsoup.parse(xml, baseUrl, Parser.xmlParser())
        val feedTitle = document.selectFirst("feed > title")?.cleanText()
            ?: document.selectFirst("title")?.cleanText()
            ?: "Catalog"
        val entries = document.select("feed > entry, entry")
            .mapNotNull { it.toEntry(baseUrl) }
            .distinctBy { it.id }
        val navigationLinks = document.select("feed > link[href]")
            .mapNotNull { it.toLink(baseUrl) }
            .filter { it.isNavigationLink() }
            .distinctBy { "${it.rel.orEmpty()}\u0000${it.href}" }
        return OpdsFeed(
            title = feedTitle,
            url = baseUrl,
            entries = entries,
            navigationLinks = navigationLinks
        )
    }

    private fun Element.toEntry(baseUrl: String): OpdsEntry? {
        val title = selectFirst("> title")?.cleanText()?.takeIf { it.isNotBlank() } ?: return null
        val id = selectFirst("> id")?.cleanText()?.takeIf { it.isNotBlank() }
            ?: selectFirst("> link[href]")?.absHref(baseUrl)
            ?: title
        val links = select("> link[href]")
            .mapNotNull { it.toLink(baseUrl) }
            .filter { it.isSupportedAcquisition() }
            .distinctBy { it.href }
        return OpdsEntry(
            id = id,
            title = title,
            author = selectFirst("> author > name, > contributor > name")?.cleanText(),
            summary = selectFirst("> summary, > content")?.cleanText(),
            acquisitionLinks = links
        )
    }

    private fun Element.toLink(baseUrl: String): OpdsLink? {
        val href = absHref(baseUrl) ?: return null
        return OpdsLink(
            href = href,
            rel = attr("rel").trim().ifBlank { null },
            type = attr("type").trim().ifBlank { null },
            title = attr("title").trim().ifBlank { null }
        )
    }

    private fun Element.absHref(baseUrl: String): String? {
        val href = attr("href").trim().takeIf { it.isNotBlank() } ?: return null
        return runCatching { URI(baseUrl).resolve(href).toString() }.getOrDefault(href)
    }

    private fun Element.cleanText(): String =
        text().replace(Regex("\\s+"), " ").trim()
}

fun OpdsLink.isSupportedAcquisition(): Boolean {
    val relation = rel.orEmpty().lowercase(Locale.US)
    if (!relation.contains("acquisition") && relation != "enclosure") return false
    if (UNSUPPORTED_ACQUISITION_RELATIONS.any { it in relation }) return false
    val mediaType = type?.substringBefore(';')?.trim()?.lowercase(Locale.US).orEmpty()
    return mediaType in SUPPORTED_ACQUISITION_TYPES || href.supportedBookExtension() != null
}

private fun OpdsLink.isNavigationLink(): Boolean {
    val relation = rel.orEmpty().lowercase(Locale.US)
    val mediaType = type?.substringBefore(';')?.trim()?.lowercase(Locale.US).orEmpty()
    if (relation == "self" || relation.contains("acquisition")) return false
    return mediaType.contains("atom") || mediaType.contains("opds") ||
        relation in NAVIGATION_RELATIONS ||
        relation.startsWith("http://opds-spec.org/sort/") ||
        relation.startsWith("https://opds-spec.org/sort/")
}

private fun String.supportedBookExtension(): String? =
    substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringAfterLast('.', "")
        .lowercase(Locale.US)
        .takeIf { it in SUPPORTED_ACQUISITION_EXTENSIONS }

private fun String?.charsetFromContentType(): Charset? {
    val charset = this
        ?.split(';')
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim('"')
        ?: return null
    return runCatching { Charset.forName(charset) }.getOrNull()
}

private fun URL.safeDownloadName(contentType: String?): String {
    val pathName = URLDecoder.decode(path.substringAfterLast('/'), Charsets.UTF_8.name())
        .replace(Regex("""[^\w.\- ]"""), "_")
        .takeIf { it.isNotBlank() && it.contains('.') }
    if (pathName != null) return pathName
    val extension = contentType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.US)
        ?.let(MIME_EXTENSION::get)
        ?: path.supportedBookExtension()
        ?: "epub"
    return "catalog-book.$extension"
}

private fun java.io.InputStream.readBounded(limitBytes: Long): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    copyBoundedTo(output, limitBytes)
    return output.toByteArray()
}

private fun java.io.InputStream.copyBoundedTo(output: java.io.OutputStream, limitBytes: Long): Long {
    var copied = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) return copied
        copied += read
        require(copied <= limitBytes) { "Download is too large." }
        output.write(buffer, 0, read)
    }
}

private val SUPPORTED_ACQUISITION_EXTENSIONS = setOf(
    "epub",
    "pdf",
    "txt",
    "cbz",
    "fb2",
    "rtf",
    "mobi",
    "prc",
    "odt",
    "docx",
    "html",
    "htm",
    "xhtml",
    "mhtml",
    "mht",
    "md",
    "markdown",
)

private val SUPPORTED_ACQUISITION_TYPES = setOf(
    "application/epub+zip",
    "application/pdf",
    "text/plain",
    "application/x-cbz",
    "application/vnd.comicbook+zip",
    "application/x-fictionbook+xml",
    "application/fb2+xml",
    "text/fb2+xml",
    "application/rtf",
    "text/rtf",
    "application/x-rtf",
    "application/x-mobipocket-ebook",
    "application/vnd.amazon.ebook",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/html",
    "application/xhtml+xml",
    "multipart/related",
    "application/x-mimearchive",
    "application/mhtml",
    "message/rfc822",
    "text/markdown",
    "text/x-markdown",
    "application/markdown",
    "application/x-markdown",
)

private val MIME_EXTENSION = mapOf(
    "application/epub+zip" to "epub",
    "application/pdf" to "pdf",
    "text/plain" to "txt",
    "application/x-cbz" to "cbz",
    "application/vnd.comicbook+zip" to "cbz",
    "application/x-fictionbook+xml" to "fb2",
    "application/fb2+xml" to "fb2",
    "text/fb2+xml" to "fb2",
    "application/rtf" to "rtf",
    "text/rtf" to "rtf",
    "application/x-rtf" to "rtf",
    "application/x-mobipocket-ebook" to "mobi",
    "application/vnd.amazon.ebook" to "mobi",
    "application/vnd.oasis.opendocument.text" to "odt",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
    "text/html" to "html",
    "application/xhtml+xml" to "xhtml",
    "multipart/related" to "mhtml",
    "application/x-mimearchive" to "mhtml",
    "application/mhtml" to "mhtml",
    "message/rfc822" to "mhtml",
    "text/markdown" to "md",
    "text/x-markdown" to "md",
    "application/markdown" to "md",
    "application/x-markdown" to "md",
)

private val NAVIGATION_RELATIONS = setOf(
    "alternate",
    "collection",
    "contents",
    "first",
    "last",
    "next",
    "previous",
    "search",
    "start",
    "subsection",
    "up",
)

private val UNSUPPORTED_ACQUISITION_RELATIONS = setOf(
    "buy",
    "borrow",
    "sample",
    "subscribe",
)
