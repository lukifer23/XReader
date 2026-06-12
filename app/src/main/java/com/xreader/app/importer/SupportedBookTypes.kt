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
}
