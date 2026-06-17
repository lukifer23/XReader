package com.xreader.app.importer

import org.junit.Assert.assertFalse
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

    @Test
    fun importCandidatesIncludeSupportedNamesAndSniffableExtensionlessGenericFiles() {
        assertTrue(SupportedBookTypes.isPotentialImportCandidate("Book.epub", "application/octet-stream"))
        assertTrue(SupportedBookTypes.isPotentialImportCandidate("Book.fb2.zip", "application/zip"))
        assertTrue(SupportedBookTypes.isPotentialImportCandidate("generic-download", "application/octet-stream"))
        assertTrue(SupportedBookTypes.isPotentialImportCandidate("generic-download", "application/unknown"))
        assertTrue(SupportedBookTypes.isPotentialImportCandidate("generic-download", ""))
    }

    @Test
    fun importCandidatesRejectExplicitUnsupportedExtensionsWithGenericMime() {
        assertFalse(SupportedBookTypes.isPotentialImportCandidate("cover.jpg", "application/octet-stream"))
        assertFalse(SupportedBookTypes.isPotentialImportCandidate("archive.rar", "application/octet-stream"))
        assertFalse(SupportedBookTypes.isPotentialImportCandidate("notes.tmp", ""))
    }

    @Test
    fun modernKindleFormatsStayUnsupportedButExplainWhy() {
        assertFalse(SupportedBookTypes.extensions.contains("azw3"))
        assertFalse(SupportedBookTypes.isPotentialImportCandidate("Novel.azw3", "application/octet-stream"))

        val message = SupportedBookTypes.unsupportedFileTypeMessage("azw3", "Novel.azw3")

        assertTrue(message.startsWith("Unsupported file type: .azw3"))
        assertTrue(message.contains("Modern Kindle AZW/KF8/KFX conversion is not implemented yet"))
        assertTrue(message.contains("DRM-free EPUB"))
    }

    @Test
    fun unsupportedReasonCanBeDetectedFromKindleMimeType() {
        val reason = SupportedBookTypes.unsupportedReasonForName(
            displayName = "download",
            mimeType = "application/vnd.amazon.mobi8-ebook; charset=binary"
        )
        val message = SupportedBookTypes.unsupportedFileTypeMessage(
            sourceExtension = "",
            displayName = "download",
            mimeType = "application/vnd.amazon.mobi8-ebook"
        )

        assertTrue(requireNotNull(reason).contains("Modern Kindle AZW/KF8/KFX"))
        assertTrue(message.contains("Modern Kindle AZW/KF8/KFX"))
    }
}
