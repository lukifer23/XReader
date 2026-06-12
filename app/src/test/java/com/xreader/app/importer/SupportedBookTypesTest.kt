package com.xreader.app.importer

import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedBookTypesTest {
    @Test
    fun pickerIncludesAllImporterMimeTypesAndOctetFallback() {
        val pickerTypes = SupportedBookTypes.pickerMimeTypes.toSet()

        assertTrue(pickerTypes.containsAll(SupportedBookTypes.mimeTypes))
        assertTrue(pickerTypes.contains("application/octet-stream"))
    }

    @Test
    fun includesCommonAliasExtensionsAndMimeTypes() {
        assertTrue(SupportedBookTypes.extensions.containsAll(listOf("prc", "htm", "mht", "fb2.zip")))
        assertTrue(SupportedBookTypes.mimeTypes.containsAll(
            listOf(
                "application/prc",
                "application/x-prc",
                "application/x-palm-database",
                "application/vnd.palm",
                "application/fb2",
                "text/fb2"
            )
        ))
    }
}
