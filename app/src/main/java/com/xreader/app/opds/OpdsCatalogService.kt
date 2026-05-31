package com.xreader.app.opds

import com.xreader.app.importer.ImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

sealed interface OpdsCatalogLoadResult {
    data class Feed(val feed: OpdsFeed) : OpdsCatalogLoadResult
    data class Imported(val result: ImportService.ImportResult) : OpdsCatalogLoadResult
}

internal fun interface RemoteBookImporter {
    suspend fun importFile(file: File, displayName: String, mimeType: String): ImportService.ImportResult
}

class OpdsCatalogService internal constructor(
    private val bookImporter: RemoteBookImporter?,
    private val cacheDir: File,
    private val clock: Clock = Clock.systemUTC(),
) {
    constructor(
        importService: ImportService?,
        cacheDir: File,
        clock: Clock = Clock.systemUTC(),
    ) : this(
        bookImporter = importService?.let { importer ->
            RemoteBookImporter { file, displayName, mimeType ->
                importer.importFile(file, displayName, mimeType)
            }
        },
        cacheDir = cacheDir,
        clock = clock
    )

    suspend fun load(url: String): OpdsFeed = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeCatalogUrl(url)
        openConnection(normalizedUrl).useResponse { connection ->
            val charset = connection.contentType.charsetFromContentType() ?: Charsets.UTF_8
            val bytes = connection.inputStream.buffered().use { input ->
                input.readBounded(MAX_FEED_BYTES)
            }
            OpdsFeedParser.parse(bytes.toString(charset), connection.url.toString())
        }
    }

    suspend fun loadOrImport(url: String): OpdsCatalogLoadResult = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeCatalogUrl(url)
        openConnection(normalizedUrl).useResponse { connection ->
            val responseType = connection.contentType.mediaType()
            if (connection.isSupportedDirectDownload(responseType)) {
                OpdsCatalogLoadResult.Imported(downloadAndImport(connection, responseType))
            } else {
                val charset = connection.contentType.charsetFromContentType() ?: Charsets.UTF_8
                val bytes = connection.inputStream.buffered().use { input ->
                    input.readBounded(MAX_FEED_BYTES)
                }
                OpdsCatalogLoadResult.Feed(OpdsFeedParser.parse(bytes.toString(charset), connection.url.toString()))
            }
        }
    }

    suspend fun importLink(link: OpdsLink): ImportService.ImportResult = withContext(Dispatchers.IO) {
        require(link.isSupportedAcquisition()) { "This catalog item is not a supported book download." }
        val url = normalizeCatalogUrl(link.href)
        openConnection(url).useResponse { connection ->
            val responseType = connection.contentType.mediaType().ifBlank { link.type.mediaType() }
            require(connection.isSupportedDirectDownload(responseType) || link.isSupportedAcquisition()) {
                "This catalog item is not a supported book download."
            }
            downloadAndImport(connection, responseType)
        }
    }

    private suspend fun downloadAndImport(connection: HttpURLConnection, mediaType: String): ImportService.ImportResult {
        val importer = requireNotNull(bookImporter) { "Book import is unavailable." }
        var target: File? = null
        try {
            val download = File(cacheDir, "opds-${clock.millis()}-${connection.url.safeDownloadName(mediaType)}")
            target = download
            download.parentFile?.mkdirs()
            connection.inputStream.buffered().use { input ->
                download.outputStream().buffered().use { output ->
                    input.copyBoundedTo(output, MAX_BOOK_BYTES)
                }
            }
            return importer.importFile(download, download.name, mediaType)
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

    private suspend inline fun <T> HttpURLConnection.useResponse(block: suspend (HttpURLConnection) -> T): T {
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
            "application/opds+json, application/atom+xml;profile=opds-catalog, application/atom+xml, application/json;q=0.9, application/xml;q=0.8, text/xml;q=0.8, */*;q=0.1"
    }
}

object OpdsFeedParser {
    fun parse(documentText: String, baseUrl: String): OpdsFeed {
        val trimmed = documentText.trimStart()
        if (trimmed.startsWith("{")) return parseJson(trimmed, baseUrl)

        val document = Jsoup.parse(documentText, baseUrl, Parser.xmlParser())
        val feed = document.selectFirst("feed") ?: document
        val feedBaseUrl = feed.effectiveBaseUrl(baseUrl)
        val feedTitle = document.selectFirst("feed > title")?.cleanText()
            ?: document.selectFirst("title")?.cleanText()
            ?: "Catalog"
        val entries = document.select("feed > entry, entry")
            .mapNotNull { it.toEntry(feedBaseUrl) }
            .distinctBy { it.id }
        val navigationLinks = document.select("feed > link[href]")
            .mapNotNull { it.toLink(feedBaseUrl) }
            .filter { it.isNavigationLink() }
            .distinctBy { "${it.rel.orEmpty()}\u0000${it.href}" }
        return OpdsFeed(
            title = feedTitle,
            url = feedBaseUrl,
            entries = entries,
            navigationLinks = navigationLinks
        )
    }

    private fun parseJson(json: String, baseUrl: String): OpdsFeed {
        val root = JSONObject(json)
        val feedTitle = root.optJSONObject("metadata")?.optCleanString("title")
            ?: root.optCleanString("title")
            ?: "Catalog"
        val topLevelLinks = root.optJSONArray("links")
            .toJsonObjects()
            .mapNotNull { it.toLink(baseUrl) }
            .filter { it.isNavigationLink() }
        val navigationLinks = root.optJSONArray("navigation")
            .toJsonObjects()
            .mapNotNull { it.toLink(baseUrl) }
            .filterNot { it.isSupportedAcquisition() }
            .plus(topLevelLinks)
            .distinctBy { "${it.rel.orEmpty()}\u0000${it.href}" }
        val entries = root.collectPublicationObjects()
            .mapNotNull { it.toJsonEntry(baseUrl) }
            .distinctBy { it.id }
        return OpdsFeed(
            title = feedTitle,
            url = baseUrl,
            entries = entries,
            navigationLinks = navigationLinks
        )
    }

    private fun Element.toEntry(baseUrl: String): OpdsEntry? {
        val entryBaseUrl = effectiveBaseUrl(baseUrl)
        val title = selectFirst("> title")?.cleanText()?.takeIf { it.isNotBlank() } ?: return null
        val id = selectFirst("> id")?.cleanText()?.takeIf { it.isNotBlank() }
            ?: selectFirst("> link[href]")?.absHref(entryBaseUrl)
            ?: title
        val links = select("> link[href]")
            .mapNotNull { it.toLink(entryBaseUrl) }
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
        return resolveUrl(effectiveBaseUrl(baseUrl), href)
    }

    private fun Element.effectiveBaseUrl(baseUrl: String): String {
        val xmlBase = attr("xml:base").trim().takeIf { it.isNotBlank() } ?: return baseUrl
        return resolveUrl(baseUrl, xmlBase)
    }

    private fun Element.cleanText(): String =
        text().replace(Regex("\\s+"), " ").trim()

    private fun JSONObject.toJsonEntry(baseUrl: String): OpdsEntry? {
        val metadata = optJSONObject("metadata") ?: this
        val title = metadata.optCleanString("title") ?: return null
        val links = optJSONArray("links")
            .toJsonObjects()
            .mapNotNull { it.toLink(baseUrl) }
            .filter { it.isSupportedAcquisition() }
            .distinctBy { it.href }
        val id = metadata.optCleanString("identifier")
            ?: metadata.optCleanString("@id")
            ?: metadata.optCleanString("id")
            ?: links.firstOrNull()?.href
            ?: title
        return OpdsEntry(
            id = id,
            title = title,
            author = metadata.optContributorName("author") ?: metadata.optContributorName("creator"),
            summary = metadata.optCleanString("description") ?: metadata.optCleanString("subtitle"),
            acquisitionLinks = links
        )
    }

    private fun JSONObject.toLink(baseUrl: String): OpdsLink? {
        val href = optCleanString("href") ?: return null
        return OpdsLink(
            href = resolveUrl(baseUrl, href),
            rel = optRelString(),
            type = optCleanString("type"),
            title = optCleanString("title")
        )
    }

    private fun JSONObject.collectPublicationObjects(): List<JSONObject> =
        buildList {
            addAll(optJSONArray("publications").toJsonObjects())
            optJSONArray("groups").toJsonObjects().forEach { group ->
                addAll(group.collectPublicationObjects())
            }
        }

    private fun JSONArray?.toJsonObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun JSONObject.optCleanString(name: String): String? =
        optString(name, "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }

    private fun JSONObject.optRelString(): String? {
        val value = opt("rel") ?: return null
        return when (value) {
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    value.optString(index, "").trim().takeIf { it.isNotBlank() }?.let(::add)
                }
            }.joinToString(" ")
            else -> value.toString().trim()
        }.takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optContributorName(name: String): String? {
        val value = opt(name) ?: return null
        return when (value) {
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    val candidate = value.optContributorValue(index)
                    if (!candidate.isNullOrBlank()) return candidate
                }
                null
            }
            is JSONObject -> value.optCleanString("name") ?: value.optCleanString("sortAs")
            else -> value.toString().replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }
        }
    }

    private fun JSONArray.optContributorValue(index: Int): String? =
        optJSONObject(index)?.let { it.optCleanString("name") ?: it.optCleanString("sortAs") }
            ?: optString(index, "").replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }
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
    val relationTokens = relation.relationTokens()
    val mediaType = type?.substringBefore(';')?.trim()?.lowercase(Locale.US).orEmpty()
    if ("self" in relationTokens || relation.contains("acquisition")) return false
    return mediaType.contains("atom") || mediaType.contains("opds") ||
        relationTokens.any { it in NAVIGATION_RELATIONS } ||
        relation.startsWith("http://opds-spec.org/sort/") ||
        relation.startsWith("https://opds-spec.org/sort/")
}

private fun resolveUrl(baseUrl: String, value: String): String =
    runCatching { URI(baseUrl).resolve(value).toString() }.getOrDefault(value)

private fun String.relationTokens(): Set<String> =
    split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

private fun String.supportedBookExtension(): String? =
    substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringAfterLast('.', "")
        .lowercase(Locale.US)
        .takeIf { it in SUPPORTED_ACQUISITION_EXTENSIONS }

private fun HttpURLConnection.isSupportedDirectDownload(mediaType: String): Boolean =
    mediaType in SUPPORTED_ACQUISITION_TYPES ||
        url.toString().supportedBookExtension() != null

private fun String?.mediaType(): String =
    this
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.US)
        .orEmpty()

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
