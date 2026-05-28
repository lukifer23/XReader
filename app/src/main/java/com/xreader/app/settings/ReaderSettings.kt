package com.xreader.app.settings

import com.xreader.app.data.ReaderTheme

enum class ReaderTextAlign {
    START,
    JUSTIFY,
}

enum class ReaderPdfFit {
    CONTAIN,
    WIDTH,
}

enum class ReaderFontFamily(
    val label: String,
    val readiumName: String?,
) {
    DEFAULT("Default", null),
    SERIF("Serif", "serif"),
    SANS_SERIF("Sans", "sans-serif"),
    HUMANIST("Humanist", "Trebuchet MS"),
    ACCESSIBLE("Accessible", "AccessibleDfA"),
    DUOSPACE("Duospace", "IA Writer Duospace"),
    MONOSPACE("Mono", "monospace"),
}

data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val fontScale: Float = 1.18f,
    val lineHeight: Float = 1.42f,
    val marginScale: Float = 0.52f,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.DEFAULT,
    val tapZonesEnabled: Boolean = true,
    val pageTurnAnimations: Boolean = true,
    val fullScreen: Boolean = false,
    val publisherStyles: Boolean = false,
    val textAlign: ReaderTextAlign = ReaderTextAlign.START,
    val pdfFit: ReaderPdfFit = ReaderPdfFit.WIDTH,
    val idleTimeoutMillis: Long = 90_000L,
)
