package com.xreader.app.importer

object SupportedBookTypes {
    val extensions: Set<String> = setOf(
        "epub",
        "pdf",
        "txt",
        "cbz",
        "fb2",
        "fb2.zip",
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
        "markdown"
    )

    val mimeTypes: Set<String> = setOf(
        "application/epub+zip",
        "application/pdf",
        "text/plain",
        "application/rtf",
        "text/rtf",
        "application/x-rtf",
        "application/x-mobipocket-ebook",
        "application/vnd.amazon.ebook",
        "application/prc",
        "application/x-prc",
        "application/x-palm-database",
        "application/vnd.palm",
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
        "application/vnd.oasis.opendocument.text",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/zip",
        "application/x-zip-compressed",
        "application/x-cbz",
        "application/vnd.comicbook+zip",
        "application/x-fictionbook+xml",
        "application/fb2+xml",
        "application/fb2",
        "text/fb2+xml",
        "text/fb2"
    )

    val pickerMimeTypes: Array<String> =
        (mimeTypes + "application/octet-stream").toTypedArray()

    fun extensionForMimeType(mimeType: String): String =
        when (mimeType.substringBefore(';').trim().lowercase()) {
            "application/epub+zip" -> "epub"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "application/x-cbz", "application/vnd.comicbook+zip" -> "cbz"
            "application/zip", "application/x-zip-compressed" -> "zip"
            "application/rtf", "text/rtf", "application/x-rtf" -> "rtf"
            "application/x-mobipocket-ebook", "application/vnd.amazon.ebook" -> "mobi"
            "application/prc", "application/x-prc", "application/x-palm-database", "application/vnd.palm" -> "prc"
            "application/vnd.oasis.opendocument.text" -> "odt"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "text/html" -> "html"
            "application/xhtml+xml" -> "xhtml"
            "multipart/related", "application/x-mimearchive", "application/mhtml", "message/rfc822" -> "mhtml"
            "text/markdown", "text/x-markdown", "application/markdown", "application/x-markdown" -> "md"
            "application/x-fictionbook+xml", "application/fb2+xml", "application/fb2", "text/fb2+xml", "text/fb2" -> "fb2"
            else -> ""
        }

    fun isPotentialImportCandidate(displayName: String, mimeType: String): Boolean {
        val extension = when {
            displayName.lowercase().endsWith(".fb2.zip") -> "fb2.zip"
            else -> displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        }
        if (extension in extensions) return true
        val normalizedMimeType = mimeType.substringBefore(';').trim().lowercase()
        if (normalizedMimeType in mimeTypes) return true
        return extension.isBlank() && normalizedMimeType in sniffableGenericMimeTypes
    }

    fun unsupportedFileTypeMessage(
        sourceExtension: String,
        displayName: String = "",
        mimeType: String = "",
    ): String {
        val extension = sourceExtension.trim().lowercase().ifBlank {
            displayName.substringAfterLast('.', missingDelimiterValue = "").trim().lowercase()
        }
        val prefix = if (extension.isBlank()) {
            "Unsupported file type"
        } else {
            "Unsupported file type: .$extension"
        }
        val detail = unsupportedReasonForExtension(extension) ?: unsupportedReasonForMimeType(mimeType)
        return if (detail == null) prefix else "$prefix. $detail"
    }

    fun unsupportedReasonForName(displayName: String, mimeType: String = ""): String? {
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").trim().lowercase()
        return unsupportedReasonForExtension(extension)
            ?: unsupportedReasonForMimeType(mimeType)
    }

    private fun unsupportedReasonForExtension(extension: String): String? =
        when (extension) {
            "azw", "azw3", "kf8", "kfx" ->
                "Modern Kindle AZW/KF8/KFX conversion is not implemented yet; import a DRM-free EPUB, PDF, TXT, MOBI/PRC, CBZ, FB2, RTF, ODT, DOCX, HTML, MHTML, or Markdown file."
            "cbr", "djvu", "djv", "doc" ->
                "This legacy format is not implemented yet; convert it to EPUB/PDF or import another supported DRM-free format."
            "acsm" ->
                "An ACSM file is an Adobe license instruction, not an ebook. Open it with an authorized Adobe-compatible app to fulfill the loan."
            else -> null
        }

    private fun unsupportedReasonForMimeType(mimeType: String): String? =
        when (mimeType.substringBefore(';').trim().lowercase()) {
            "application/vnd.amazon.mobi8-ebook",
            "application/x-kindle-application",
            "application/vnd.amazon.ebook-kf8" ->
                "Modern Kindle AZW/KF8/KFX conversion is not implemented yet; import a DRM-free EPUB, PDF, TXT, MOBI/PRC, CBZ, FB2, RTF, ODT, DOCX, HTML, MHTML, or Markdown file."
            "application/vnd.adobe.adept+xml" ->
                "An ACSM file is an Adobe license instruction, not an ebook. Open it with an authorized Adobe-compatible app to fulfill the loan."
            else -> null
        }

    private val sniffableGenericMimeTypes: Set<String> = setOf(
        "",
        "application/octet-stream",
        "binary/octet-stream",
        "application/unknown"
    )
}
