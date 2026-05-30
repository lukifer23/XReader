package com.xreader.app.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfToolsTest {
    @Test
    fun cleansExtractedTextForSearchAndReadAloud() {
        val raw = "Inter-\nstellar archive\u00AD keeps\r\n file numbers A-17 nearby."

        assertEquals(
            "Interstellar archive keeps file numbers A-17 nearby.",
            cleanPdfExtractedText(raw)
        )
    }

    @Test
    fun blankExtractedTextStaysBlank() {
        assertEquals("", cleanPdfExtractedText(" \r\n\t "))
    }
}
