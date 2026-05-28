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
}
