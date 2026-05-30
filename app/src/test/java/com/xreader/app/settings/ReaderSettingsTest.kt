package com.xreader.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSettingsTest {
    @Test
    fun readerDefaultsKeepFastPageTurnsAvailable() {
        assertTrue(ReaderSettings().pageTurnAnimations)
        assertEquals(ReaderTapZonePreset.BALANCED, ReaderSettings().tapZonePreset)
        assertEquals(1.0f, ReaderSettings().readAloudRate, 0.001f)
        assertNull(ReaderSettings().readAloudVoiceName)
        assertEquals(ReadAloudSleepTimer.OFF, ReaderSettings().readAloudSleepTimer)
        assertEquals(ReaderHighlightColor.YELLOW.hex, ReaderSettings().highlightColor)
        assertFalse(ReaderSettings().keepScreenAwake)
        assertFalse(ReaderSettings().volumeKeysTurnPages)
        assertEquals(ReaderPdfFit.WIDTH, ReaderSettings().pdfFit)
        assertEquals(ReaderPdfScrollAxis.HORIZONTAL, ReaderSettings().pdfScrollAxis)
        assertEquals(0f, ReaderSettings().screenDim, 0.001f)
    }

    @Test
    fun libraryDefaultsKeepHomeScreenPredictable() {
        assertEquals(LibrarySort.RECENT, LibrarySettings().sort)
        assertEquals(LibraryDensity.COMFORTABLE, LibrarySettings().density)
    }

    @Test
    fun fontFamiliesUseResolvableReadiumNames() {
        assertNull(ReaderFontFamily.DEFAULT.readiumName)
        assertEquals("serif", ReaderFontFamily.SERIF.readiumName)
        assertEquals("sans-serif", ReaderFontFamily.SANS_SERIF.readiumName)
        assertEquals("Trebuchet MS", ReaderFontFamily.HUMANIST.readiumName)
        assertEquals("AccessibleDfA", ReaderFontFamily.ACCESSIBLE.readiumName)
        assertEquals("IA Writer Duospace", ReaderFontFamily.DUOSPACE.readiumName)
        assertEquals("monospace", ReaderFontFamily.MONOSPACE.readiumName)
        assertFalse(ReaderFontFamily.entries.any { it.readiumName == "OpenDyslexic" })
    }

    @Test
    fun pdfControlsUseReaderFriendlyLabels() {
        assertEquals(listOf("Page", "Width", "Height"), ReaderPdfFit.entries.map { it.label })
        assertEquals(listOf("Paged", "Scroll"), ReaderPdfScrollAxis.entries.map { it.label })
    }

    @Test
    fun spacingPresetsApplyOnlyTypographyDensity() {
        val settings = ReaderSettings(
            theme = com.xreader.app.data.ReaderTheme.OLED,
            fontScale = 1.0f,
            lineHeight = 1.2f,
            marginScale = 0.4f,
            fullScreen = true,
            readAloudRate = 1.25f,
            readAloudVoiceName = "local-voice",
            readAloudSleepTimer = ReadAloudSleepTimer.THIRTY_MINUTES,
            highlightColor = ReaderHighlightColor.BLUE.hex,
            textAlign = ReaderTextAlign.JUSTIFY,
            pdfFit = ReaderPdfFit.HEIGHT,
            pdfScrollAxis = ReaderPdfScrollAxis.VERTICAL,
            keepScreenAwake = true,
            volumeKeysTurnPages = true,
            screenDim = 0.3f
        )

        val accessible = settings.withSpacingPreset(ReaderSpacingPreset.ACCESSIBLE)

        assertEquals(com.xreader.app.data.ReaderTheme.OLED, accessible.theme)
        assertTrue(accessible.fullScreen)
        assertEquals(1.25f, accessible.readAloudRate, 0.001f)
        assertEquals("local-voice", accessible.readAloudVoiceName)
        assertEquals(ReadAloudSleepTimer.THIRTY_MINUTES, accessible.readAloudSleepTimer)
        assertEquals(ReaderHighlightColor.BLUE.hex, accessible.highlightColor)
        assertEquals(ReaderTextAlign.JUSTIFY, accessible.textAlign)
        assertEquals(ReaderPdfFit.HEIGHT, accessible.pdfFit)
        assertEquals(ReaderPdfScrollAxis.VERTICAL, accessible.pdfScrollAxis)
        assertTrue(accessible.keepScreenAwake)
        assertTrue(accessible.volumeKeysTurnPages)
        assertEquals(0.3f, accessible.screenDim, 0.001f)
        assertEquals(ReaderSpacingPreset.ACCESSIBLE.fontScale, accessible.fontScale, 0.001f)
        assertEquals(ReaderSpacingPreset.ACCESSIBLE.lineHeight, accessible.lineHeight, 0.001f)
        assertEquals(ReaderSpacingPreset.ACCESSIBLE.marginScale, accessible.marginScale, 0.001f)
        assertEquals(ReaderSpacingPreset.ACCESSIBLE, accessible.spacingPresetOrNull())
        assertNull(accessible.copy(fontScale = accessible.fontScale + 0.02f).spacingPresetOrNull())
    }

    @Test
    fun bookAppearanceOverridesOnlyReaderAppearanceFields() {
        val global = ReaderSettings(
            theme = com.xreader.app.data.ReaderTheme.OLED,
            fontScale = 1.0f,
            lineHeight = 1.2f,
            marginScale = 0.7f,
            fontFamily = ReaderFontFamily.SERIF,
            tapZonesEnabled = false,
            tapZonePreset = ReaderTapZonePreset.COMPACT,
            pageTurnAnimations = false,
            keepScreenAwake = true,
            volumeKeysTurnPages = true,
            screenDim = 0.35f,
            readAloudRate = 1.3f,
            readAloudVoiceName = "local-voice",
            readAloudSleepTimer = ReadAloudSleepTimer.FORTY_FIVE_MINUTES,
            highlightColor = ReaderHighlightColor.PURPLE.hex,
            fullScreen = true,
            publisherStyles = true,
            textAlign = ReaderTextAlign.JUSTIFY,
            pdfFit = ReaderPdfFit.CONTAIN,
            pdfScrollAxis = ReaderPdfScrollAxis.VERTICAL,
            idleTimeoutMillis = 30_000L
        )
        val appearance = BookReaderAppearance(
            fontScale = 1.4f,
            lineHeight = 1.7f,
            marginScale = 1.2f,
            fontFamily = ReaderFontFamily.ACCESSIBLE,
            publisherStyles = false,
            textAlign = ReaderTextAlign.START,
            pdfFit = ReaderPdfFit.HEIGHT,
            pdfScrollAxis = ReaderPdfScrollAxis.HORIZONTAL
        )

        val combined = global.withBookAppearance(appearance)

        assertEquals(com.xreader.app.data.ReaderTheme.OLED, combined.theme)
        assertFalse(combined.tapZonesEnabled)
        assertEquals(ReaderTapZonePreset.COMPACT, combined.tapZonePreset)
        assertFalse(combined.pageTurnAnimations)
        assertTrue(combined.keepScreenAwake)
        assertTrue(combined.volumeKeysTurnPages)
        assertEquals(0.35f, combined.screenDim, 0.001f)
        assertEquals(1.3f, combined.readAloudRate, 0.001f)
        assertEquals("local-voice", combined.readAloudVoiceName)
        assertEquals(ReadAloudSleepTimer.FORTY_FIVE_MINUTES, combined.readAloudSleepTimer)
        assertEquals(ReaderHighlightColor.PURPLE.hex, combined.highlightColor)
        assertTrue(combined.fullScreen)
        assertEquals(30_000L, combined.idleTimeoutMillis)
        assertEquals(1.4f, combined.fontScale, 0.001f)
        assertEquals(1.7f, combined.lineHeight, 0.001f)
        assertEquals(1.2f, combined.marginScale, 0.001f)
        assertEquals(ReaderFontFamily.ACCESSIBLE, combined.fontFamily)
        assertFalse(combined.publisherStyles)
        assertEquals(ReaderTextAlign.START, combined.textAlign)
        assertEquals(ReaderPdfFit.HEIGHT, combined.pdfFit)
        assertEquals(ReaderPdfScrollAxis.HORIZONTAL, combined.pdfScrollAxis)
    }

    @Test
    fun readAloudSleepTimerPresetsExposeRealDurations() {
        assertNull(ReadAloudSleepTimer.OFF.durationMillis)
        assertEquals(15 * 60_000L, ReadAloudSleepTimer.FIFTEEN_MINUTES.durationMillis)
        assertEquals(30 * 60_000L, ReadAloudSleepTimer.THIRTY_MINUTES.durationMillis)
        assertEquals(45 * 60_000L, ReadAloudSleepTimer.FORTY_FIVE_MINUTES.durationMillis)
        assertEquals(60 * 60_000L, ReadAloudSleepTimer.SIXTY_MINUTES.durationMillis)
    }

    @Test
    fun highlightColorNormalizesToPalette() {
        assertEquals(ReaderHighlightColor.GREEN.hex, ReaderHighlightColor.normalized("#6fcf97"))
        assertEquals(ReaderHighlightColor.YELLOW.hex, ReaderHighlightColor.normalized("#123456"))
        assertEquals(ReaderHighlightColor.YELLOW, ReaderHighlightColor.optionFor(null))
    }

    @Test
    fun readerDimAmountIsBoundedForOverlaySafety() {
        assertEquals(0f, normalizedReaderDimAmount(-0.5f), 0.001f)
        assertEquals(0.25f, normalizedReaderDimAmount(0.25f), 0.001f)
        assertEquals(MAX_READER_DIM_AMOUNT, normalizedReaderDimAmount(2.0f), 0.001f)
    }
}
