package com.xreader.app.importer

import com.xreader.app.core.TextTools
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal object ZipBackedBookDetector {
    fun detect(file: File): String? =
        runCatching {
            ZipFile(file).use { zip ->
                val signals = ZipSignals()
                val entries = zip.entries()
                var scanned = 0
                while (entries.hasMoreElements() && scanned < MAX_CLASSIFIER_ENTRIES) {
                    val entry = entries.nextElement()
                    scanned += 1
                    if (entry.isDirectory) continue

                    val path = entry.name.replace('\\', '/').trimStart('/')
                    if (path.isArchiveMetadataPath()) continue

                    if (path.equals("mimetype", ignoreCase = true)) {
                        when (zip.readAsciiEntry(entry, MAX_MIMETYPE_BYTES)?.lowercase(Locale.US)) {
                            "application/epub+zip" -> signals.epubMime = true
                            "application/vnd.oasis.opendocument.text" -> signals.odtMime = true
                        }
                    }

                    when {
                        path.equals("META-INF/container.xml", ignoreCase = true) -> signals.epubContainer = true
                        TextTools.extension(path) == "fb2" -> signals.fb2Document = true
                        path.equals("content.xml", ignoreCase = true) -> signals.odtContent = true
                        path.equals("META-INF/manifest.xml", ignoreCase = true) -> signals.odtManifest = true
                        path.equals("[Content_Types].xml", ignoreCase = true) -> signals.docxContentTypes = true
                        path.equals("word/document.xml", ignoreCase = true) -> signals.docxDocument = true
                        path.supportedCbzImageExtension() != null -> signals.cbzImage = true
                    }
                }
                signals.detectedExtension()
            }
        }.getOrNull()

    private data class ZipSignals(
        var epubMime: Boolean = false,
        var epubContainer: Boolean = false,
        var fb2Document: Boolean = false,
        var odtMime: Boolean = false,
        var odtContent: Boolean = false,
        var odtManifest: Boolean = false,
        var docxContentTypes: Boolean = false,
        var docxDocument: Boolean = false,
        var cbzImage: Boolean = false,
    ) {
        fun detectedExtension(): String? =
            when {
                epubMime || epubContainer -> "epub"
                fb2Document -> "fb2.zip"
                odtMime || (odtContent && odtManifest) -> "odt"
                docxContentTypes && docxDocument -> "docx"
                cbzImage -> "cbz"
                else -> null
            }
    }

    private fun String.isArchiveMetadataPath(): Boolean =
        startsWith("__MACOSX/", ignoreCase = true) ||
            substringAfterLast('/').startsWith(".")

    private fun String.supportedCbzImageExtension(): String? =
        when (TextTools.extension(substringBeforeLast('#'))) {
            "jpg", "jpeg" -> "jpg"
            "png" -> "png"
            "webp" -> "webp"
            "gif" -> "gif"
            else -> null
        }

    private fun ZipFile.readAsciiEntry(entry: ZipEntry, maxBytes: Int): String? {
        if (entry.size > maxBytes) return null
        return getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64)
            while (output.size() <= maxBytes) {
                val read = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
                if (read < 0) break
                output.write(buffer, 0, read)
            }
            val bytes = output.toByteArray()
            if (bytes.size > maxBytes) null else bytes.toString(Charsets.US_ASCII).trim()
        }
    }

    internal const val MAX_CLASSIFIER_ENTRIES = 4_096
    private const val MAX_MIMETYPE_BYTES = 128
}
