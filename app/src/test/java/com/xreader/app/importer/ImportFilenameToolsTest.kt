package com.xreader.app.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportFilenameToolsTest {
    @Test
    fun sourceTitleStripsFullKnownExtension() {
        assertEquals("Solar Wind", importSourceTitle("Solar Wind.fb2.zip", "fb2.zip"))
        assertEquals("Station Notes", importSourceTitle("Station Notes.markdown", "markdown"))
    }

    @Test
    fun sourceTitleCleansDownloadedFileNames() {
        assertEquals(
            "The Long Way Home",
            importSourceTitle("The%20Long%20Way%20Home.epub?download=1", "epub")
        )
        assertEquals(
            "Orbital Field Notes",
            importSourceTitle("folder/Orbital_Field_Notes.md#section", "md")
        )
    }

    @Test
    fun sourceTitleFallsBackToDisplayNameWhenNoBasenameExists() {
        assertEquals("Untitled", importSourceTitle(".txt", "txt"))
    }
}
