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

data class BookReaderAppearance(
    val fontScale: Float,
    val lineHeight: Float,
    val marginScale: Float,
    val fontFamily: ReaderFontFamily,
    val publisherStyles: Boolean,
    val textAlign: ReaderTextAlign,
    val pdfFit: ReaderPdfFit,
)

fun ReaderSettings.bookAppearance(): BookReaderAppearance =
    BookReaderAppearance(
        fontScale = fontScale,
        lineHeight = lineHeight,
        marginScale = marginScale,
        fontFamily = fontFamily,
        publisherStyles = publisherStyles,
        textAlign = textAlign,
        pdfFit = pdfFit
    )

fun ReaderSettings.withBookAppearance(appearance: BookReaderAppearance?): ReaderSettings =
    if (appearance == null) {
        this
    } else {
        copy(
            fontScale = appearance.fontScale,
            lineHeight = appearance.lineHeight,
            marginScale = appearance.marginScale,
            fontFamily = appearance.fontFamily,
            publisherStyles = appearance.publisherStyles,
            textAlign = appearance.textAlign,
            pdfFit = appearance.pdfFit
        )
    }
