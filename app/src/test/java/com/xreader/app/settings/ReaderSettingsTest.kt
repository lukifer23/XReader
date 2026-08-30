package com.xreader.app.settings

import org.junit.Assert.assertEquals
import com.xreader.app.settings.LibraryGroup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.xreader.app.tts.NeuralTtsModelCatalog

class ReaderSettingsTest {
    @Test
    fun readerDefaultsKeepFastPageTurnsAvailable() {
        assertTrue(ReaderSettings().pageTurnAnimations)
        assertEquals(ReaderTapZonePreset.BALANCED, ReaderSettings().tapZonePreset)
        assertEquals(1.0f, ReaderSettings().readAloudRate, 0.001f)
        assertNull(ReaderSettings().readAloudEngineName)
        assertNull(ReaderSettings().readAloudVoiceName)
        assertEquals(ReadAloudSleepTimer.OFF, ReaderSettings().readAloudSleepTimer)
        assertEquals(ReadAloudPlaybackMode.DEVICE_TTS, ReaderSettings().readAloudPlaybackMode)
        assertEquals(NeuralTtsModelCatalog.DEFAULT_MODEL_ID, ReaderSettings().neuralTtsModelId)
        assertEquals(0, ReaderSettings().neuralTtsSpeakerId)
        assertEquals(NeuralTtsGender.ANY, ReaderSettings().neuralTtsGender)
        assertEquals(NeuralTtsTone.NATURAL, ReaderSettings().neuralTtsTone)
        assertEquals(NeuralTtsPace.STANDARD, ReaderSettings().neuralTtsPace)
        assertEquals(ReaderHighlightColor.YELLOW.hex, ReaderSettings().highlightColor)
        assertEquals(1.0f, ReaderSettings().fontWeight, 0.001f)
        assertFalse(ReaderSettings().hyphenation)
        assertFalse(ReaderSettings().keepScreenAwake)
        assertFalse(ReaderSettings().volumeKeysTurnPages)
        assertEquals(ReaderPdfFit.AUTO, ReaderSettings().pdfFit)
        assertEquals(ReaderPdfScrollAxis.HORIZONTAL, ReaderSettings().pdfScrollAxis)
        assertEquals(ReaderPageDirection.AUTO, ReaderSettings().pageDirection)
        assertEquals(ReaderOrientation.SYSTEM, ReaderSettings().orientation)
        assertEquals(0f, ReaderSettings().screenDim, 0.001f)
    }

    @Test
    fun libraryDefaultsKeepHomeScreenPredictable() {
        assertEquals(LibrarySort.RECENT, LibrarySettings().sort)
        assertEquals(LibraryDensity.COMFORTABLE, LibrarySettings().density)
        assertEquals(LibraryGroup.BOOKS, LibrarySettings().group)
    }

    @Test
    fun fontFamiliesUseResolvableReadiumNames() {
        assertNull(ReaderFontFamily.DEFAULT.readiumName)
        assertEquals("serif", ReaderFontFamily.SERIF.readiumName)
        assertEquals("sans-serif", ReaderFontFamily.SANS_SERIF.readiumName)
        assertEquals("Trebuchet MS", ReaderFontFamily.HUMANIST.readiumName)
        assertEquals("AccessibleDfA", ReaderFontFamily.ACCESSIBLE.readiumName)
        assertEquals("OpenDyslexic", ReaderFontFamily.DYSLEXIC.readiumName)
        assertEquals("IA Writer Duospace", ReaderFontFamily.DUOSPACE.readiumName)
        assertEquals("monospace", ReaderFontFamily.MONOSPACE.readiumName)
    }

    @Test
    fun pdfControlsUseReaderFriendlyLabels() {
        assertEquals(listOf("Auto", "Page", "Width", "Height"), ReaderPdfFit.entries.map { it.label })
        assertEquals(listOf("Paged", "Scroll"), ReaderPdfScrollAxis.entries.map { it.label })
        assertEquals(listOf("Auto", "Left to right", "Right to left"), ReaderPageDirection.entries.map { it.label })
        assertEquals(listOf("System", "Portrait", "Landscape"), ReaderOrientation.entries.map { it.label })
    }

    @Test
    fun automaticPdfFitPrioritizesReadablePhonesAndFullPageLargerViewports() {
        assertEquals(ReaderPdfFit.WIDTH, ReaderPdfFit.AUTO.resolvedForViewport(widthDp = 412, heightDp = 915))
        assertEquals(ReaderPdfFit.CONTAIN, ReaderPdfFit.AUTO.resolvedForViewport(widthDp = 915, heightDp = 412))
        assertEquals(ReaderPdfFit.CONTAIN, ReaderPdfFit.AUTO.resolvedForViewport(widthDp = 700, heightDp = 1100))
        assertEquals(ReaderPdfFit.CONTAIN, ReaderPdfFit.AUTO.resolvedForViewport(widthDp = 0, heightDp = 0))
        assertEquals(ReaderPdfFit.HEIGHT, ReaderPdfFit.HEIGHT.resolvedForViewport(widthDp = 412, heightDp = 915))
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
            readAloudPlaybackMode = ReadAloudPlaybackMode.GENERATED_AUDIO,
            neuralTtsModelId = NeuralTtsModelCatalog.DEFAULT_MODEL_ID,
            neuralTtsSpeakerId = 2,
            neuralTtsGender = NeuralTtsGender.FEMALE,
            neuralTtsTone = NeuralTtsTone.WARM,
            neuralTtsPace = NeuralTtsPace.RELAXED,
            highlightColor = ReaderHighlightColor.BLUE.hex,
            textAlign = ReaderTextAlign.JUSTIFY,
            pdfFit = ReaderPdfFit.HEIGHT,
            pdfScrollAxis = ReaderPdfScrollAxis.VERTICAL,
            pageDirection = ReaderPageDirection.RIGHT_TO_LEFT,
            orientation = ReaderOrientation.LANDSCAPE,
            keepScreenAwake = true,
            volumeKeysTurnPages = true,
            screenDim = 0.3f,
            readAloudEngineName = "com.local.neuraltts",
        )

        val accessible = settings.withSpacingPreset(ReaderSpacingPreset.ACCESSIBLE)

        assertEquals(com.xreader.app.data.ReaderTheme.OLED, accessible.theme)
        assertTrue(accessible.fullScreen)
        assertEquals(1.25f, accessible.readAloudRate, 0.001f)
        assertEquals("com.local.neuraltts", accessible.readAloudEngineName)
        assertEquals("local-voice", accessible.readAloudVoiceName)
        assertEquals(ReadAloudSleepTimer.THIRTY_MINUTES, accessible.readAloudSleepTimer)
        assertEquals(ReadAloudPlaybackMode.GENERATED_AUDIO, accessible.readAloudPlaybackMode)
        assertEquals(NeuralTtsModelCatalog.DEFAULT_MODEL_ID, accessible.neuralTtsModelId)
        assertEquals(2, accessible.neuralTtsSpeakerId)
        assertEquals(NeuralTtsGender.FEMALE, accessible.neuralTtsGender)
        assertEquals(NeuralTtsTone.WARM, accessible.neuralTtsTone)
        assertEquals(NeuralTtsPace.RELAXED, accessible.neuralTtsPace)
        assertEquals(ReaderHighlightColor.BLUE.hex, accessible.highlightColor)
        assertEquals(ReaderTextAlign.JUSTIFY, accessible.textAlign)
        assertEquals(ReaderPdfFit.HEIGHT, accessible.pdfFit)
        assertEquals(ReaderPdfScrollAxis.VERTICAL, accessible.pdfScrollAxis)
        assertEquals(ReaderPageDirection.RIGHT_TO_LEFT, accessible.pageDirection)
        assertEquals(ReaderOrientation.LANDSCAPE, accessible.orientation)
        assertTrue(accessible.keepScreenAwake)
        assertTrue(accessible.volumeKeysTurnPages)
        assertEquals(0.3f, accessible.screenDim, 0.001f)
        assertEquals(1.0f, accessible.fontWeight, 0.001f)
        assertFalse(accessible.hyphenation)
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
            fontWeight = 1.1f,
            hyphenation = true,
            tapZonesEnabled = false,
            tapZonePreset = ReaderTapZonePreset.COMPACT,
            pageTurnAnimations = false,
            keepScreenAwake = true,
            volumeKeysTurnPages = true,
            screenDim = 0.35f,
            readAloudRate = 1.3f,
            readAloudEngineName = "com.local.neuraltts",
            readAloudVoiceName = "local-voice",
            readAloudSleepTimer = ReadAloudSleepTimer.FORTY_FIVE_MINUTES,
            readAloudPlaybackMode = ReadAloudPlaybackMode.GENERATED_AUDIO,
            neuralTtsModelId = NeuralTtsModelCatalog.DEFAULT_MODEL_ID,
            neuralTtsSpeakerId = 3,
            neuralTtsGender = NeuralTtsGender.FEMALE,
            neuralTtsTone = NeuralTtsTone.CALM,
            neuralTtsPace = NeuralTtsPace.BRISK,
            highlightColor = ReaderHighlightColor.PURPLE.hex,
            fullScreen = true,
            publisherStyles = true,
            textAlign = ReaderTextAlign.JUSTIFY,
            pdfFit = ReaderPdfFit.CONTAIN,
            pdfScrollAxis = ReaderPdfScrollAxis.VERTICAL,
            pageDirection = ReaderPageDirection.LEFT_TO_RIGHT,
            orientation = ReaderOrientation.PORTRAIT,
            idleTimeoutMillis = 30_000L
        )
        val appearance = BookReaderAppearance(
            fontScale = 1.4f,
            lineHeight = 1.7f,
            marginScale = 1.2f,
            fontFamily = ReaderFontFamily.ACCESSIBLE,
            fontWeight = 1.35f,
            hyphenation = false,
            publisherStyles = false,
            textAlign = ReaderTextAlign.START,
            pdfFit = ReaderPdfFit.HEIGHT,
            pdfScrollAxis = ReaderPdfScrollAxis.HORIZONTAL,
            pageDirection = ReaderPageDirection.RIGHT_TO_LEFT
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
        assertEquals("com.local.neuraltts", combined.readAloudEngineName)
        assertEquals("local-voice", combined.readAloudVoiceName)
        assertEquals(ReadAloudSleepTimer.FORTY_FIVE_MINUTES, combined.readAloudSleepTimer)
        assertEquals(ReadAloudPlaybackMode.GENERATED_AUDIO, combined.readAloudPlaybackMode)
        assertEquals(NeuralTtsModelCatalog.DEFAULT_MODEL_ID, combined.neuralTtsModelId)
        assertEquals(3, combined.neuralTtsSpeakerId)
        assertEquals(NeuralTtsGender.FEMALE, combined.neuralTtsGender)
        assertEquals(NeuralTtsTone.CALM, combined.neuralTtsTone)
        assertEquals(NeuralTtsPace.BRISK, combined.neuralTtsPace)
        assertEquals(ReaderHighlightColor.PURPLE.hex, combined.highlightColor)
        assertTrue(combined.fullScreen)
        assertEquals(ReaderOrientation.PORTRAIT, combined.orientation)
        assertEquals(30_000L, combined.idleTimeoutMillis)
        assertEquals(1.4f, combined.fontScale, 0.001f)
        assertEquals(1.7f, combined.lineHeight, 0.001f)
        assertEquals(1.2f, combined.marginScale, 0.001f)
        assertEquals(ReaderFontFamily.ACCESSIBLE, combined.fontFamily)
        assertEquals(1.35f, combined.fontWeight, 0.001f)
        assertFalse(combined.hyphenation)
        assertFalse(combined.publisherStyles)
        assertEquals(ReaderTextAlign.START, combined.textAlign)
        assertEquals(ReaderPdfFit.HEIGHT, combined.pdfFit)
        assertEquals(ReaderPdfScrollAxis.HORIZONTAL, combined.pdfScrollAxis)
        assertEquals(ReaderPageDirection.RIGHT_TO_LEFT, combined.pageDirection)
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

    @Test
    fun readerFontWeightIsBoundedForLegibility() {
        assertEquals(MIN_READER_FONT_WEIGHT, normalizedReaderFontWeight(-1f), 0.001f)
        assertEquals(1.15f, normalizedReaderFontWeight(1.15f), 0.001f)
        assertEquals(MAX_READER_FONT_WEIGHT, normalizedReaderFontWeight(5f), 0.001f)
    }
}
