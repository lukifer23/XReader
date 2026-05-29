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

enum class ReaderTapZonePreset(
    val label: String,
    val sideFraction: Float,
    val edgeGuardDp: Int,
) {
    COMPACT("Compact", 0.28f, 64),
    BALANCED("Balanced", 0.34f, 44),
    WIDE("Wide", 0.42f, 24),
}

enum class ReadAloudSleepTimer(
    val label: String,
    val durationMillis: Long?,
) {
    OFF("Off", null),
    FIFTEEN_MINUTES("15 min", 15 * 60_000L),
    THIRTY_MINUTES("30 min", 30 * 60_000L),
    FORTY_FIVE_MINUTES("45 min", 45 * 60_000L),
    SIXTY_MINUTES("60 min", 60 * 60_000L),
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

enum class ReaderSpacingPreset(
    val label: String,
    val fontScale: Float,
    val lineHeight: Float,
    val marginScale: Float,
) {
    COMPACT("Compact", 1.08f, 1.30f, 0.42f),
    COMFORT("Comfort", 1.18f, 1.42f, 0.52f),
    ACCESSIBLE("Accessible", 1.35f, 1.65f, 0.85f);

    fun matches(settings: ReaderSettings): Boolean =
        settings.fontScale.closeTo(fontScale) &&
            settings.lineHeight.closeTo(lineHeight) &&
            settings.marginScale.closeTo(marginScale)
}

data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val fontScale: Float = 1.18f,
    val lineHeight: Float = 1.42f,
    val marginScale: Float = 0.52f,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.DEFAULT,
    val tapZonesEnabled: Boolean = true,
    val tapZonePreset: ReaderTapZonePreset = ReaderTapZonePreset.BALANCED,
    val pageTurnAnimations: Boolean = true,
    val readAloudRate: Float = 1.0f,
    val readAloudVoiceName: String? = null,
    val readAloudSleepTimer: ReadAloudSleepTimer = ReadAloudSleepTimer.OFF,
    val fullScreen: Boolean = false,
    val publisherStyles: Boolean = false,
    val textAlign: ReaderTextAlign = ReaderTextAlign.START,
    val pdfFit: ReaderPdfFit = ReaderPdfFit.WIDTH,
    val idleTimeoutMillis: Long = 90_000L,
)

fun ReaderSettings.withSpacingPreset(preset: ReaderSpacingPreset): ReaderSettings =
    copy(
        fontScale = preset.fontScale,
        lineHeight = preset.lineHeight,
        marginScale = preset.marginScale
    )

fun ReaderSettings.spacingPresetOrNull(): ReaderSpacingPreset? =
    ReaderSpacingPreset.entries.firstOrNull { it.matches(this) }

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

private fun Float.closeTo(other: Float): Boolean =
    kotlin.math.abs(this - other) < 0.001f
