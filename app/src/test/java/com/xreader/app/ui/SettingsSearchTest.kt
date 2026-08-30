package com.xreader.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test
    fun blankQueryIncludesEverySection() {
        assertTrue(settingsSectionMatches("  ", "Appearance", "theme light dark"))
    }

    @Test
    fun searchMatchesTitleAndKeywordsCaseInsensitively() {
        assertTrue(settingsSectionMatches("TYPOGRAPHY", "Typography", "font spacing"))
        assertTrue(settingsSectionMatches("sleep voice", "Reading", "read aloud voice sleep timer"))
    }

    @Test
    fun everySearchTokenMustMatchTheSameSection() {
        assertFalse(settingsSectionMatches("backup theme", "Maintenance", "backup restore import export"))
        assertFalse(settingsSectionMatches("missing", "Library", "sort density list"))
    }
}
