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
    fun spacingPresetsApplyOnlyTypographyDensity() {
        val settings = ReaderSettings(
            theme = com.xreader.app.data.ReaderTheme.OLED,
            fontScale = 1.0f,
            lineHeight = 1.2f,
            marginScale = 0.4f,
            fullScreen = true,
            textAlign = ReaderTextAlign.JUSTIFY
        )

        val accessible = settings.withSpacingPreset(ReaderSpacingPreset.ACCESSIBLE)

        assertEquals(com.xreader.app.data.ReaderTheme.OLED, accessible.theme)
        assertTrue(accessible.fullScreen)
        assertEquals(ReaderTextAlign.JUSTIFY, accessible.textAlign)
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
            pageTurnAnimations = false,
            fullScreen = true,
            publisherStyles = true,
            textAlign = ReaderTextAlign.JUSTIFY,
            pdfFit = ReaderPdfFit.CONTAIN,
            idleTimeoutMillis = 30_000L
        )
        val appearance = BookReaderAppearance(
            fontScale = 1.4f,
            lineHeight = 1.7f,
            marginScale = 1.2f,
            fontFamily = ReaderFontFamily.ACCESSIBLE,
            publisherStyles = false,
            textAlign = ReaderTextAlign.START,
            pdfFit = ReaderPdfFit.WIDTH
        )

        val combined = global.withBookAppearance(appearance)

        assertEquals(com.xreader.app.data.ReaderTheme.OLED, combined.theme)
        assertFalse(combined.tapZonesEnabled)
        assertFalse(combined.pageTurnAnimations)
        assertTrue(combined.fullScreen)
        assertEquals(30_000L, combined.idleTimeoutMillis)
        assertEquals(1.4f, combined.fontScale, 0.001f)
        assertEquals(1.7f, combined.lineHeight, 0.001f)
        assertEquals(1.2f, combined.marginScale, 0.001f)
        assertEquals(ReaderFontFamily.ACCESSIBLE, combined.fontFamily)
        assertFalse(combined.publisherStyles)
        assertEquals(ReaderTextAlign.START, combined.textAlign)
        assertEquals(ReaderPdfFit.WIDTH, combined.pdfFit)
    }
}
