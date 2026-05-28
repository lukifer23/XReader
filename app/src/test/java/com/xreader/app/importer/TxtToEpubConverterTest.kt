package com.xreader.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TxtToEpubConverterTest {
    @Test
    fun convertsTxtIntoParseableEpub() {
        val dir = Files.createTempDirectory("xreader-epub-test").toFile()
        val source = File(dir, "essay.txt")
        val output = File(dir, "essay.epub")
        source.writeText(
            """
            First paragraph has useful words.

            Second paragraph keeps its own block.
            """.trimIndent()
        )

        TxtToEpubConverter().convert(source, output, "Essay")
        val parsed = EpubParser().parse(output)

        assertEquals("Essay", parsed.metadata.title)
        assertTrue(parsed.units.any { it.body.contains("First paragraph") })
        assertTrue(parsed.units.any { it.body.contains("Second paragraph") })
        assertTrue(parsed.units.sumOf { it.wordCount } >= 10)
    }
}
