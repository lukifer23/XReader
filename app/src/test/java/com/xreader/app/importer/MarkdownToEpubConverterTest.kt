package com.xreader.app.importer

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownToEpubConverterTest {
    @Test
    fun convertsMarkdownMetadataAndReadingOrderIntoParseableEpub() {
        val dir = Files.createTempDirectory("xreader-markdown-test").toFile()
        val source = File(dir, "Orbital Notes.md")
        val output = File(dir, "Orbital Notes.epub")
        source.writeText(markdownDocument())

        MarkdownToEpubConverter().convert(source, output, "Fallback Title")

        val parsed = EpubParser().parse(output)
        assertEquals("Orbital Field Notes", parsed.metadata.title)
        assertEquals("Mina Patel", parsed.metadata.author)
        assertEquals("en-US", parsed.metadata.language)
        assertEquals("Science Fiction", parsed.metadata.genre)
        assertEquals(2026, parsed.metadata.year)
        assertTrue(parsed.units.any { it.heading == "Arrival" && it.body.contains("survey ship docked quietly") })
        assertTrue(parsed.units.any { it.body.contains("Checklist complete") })
        assertTrue(parsed.units.any { it.body.contains("Keep the lights low") })
        assertTrue(parsed.units.any { it.body.contains("oxygen = stable") })
    }

    @Test
    fun fallsBackToFirstHeadingForTitle() {
        val dir = Files.createTempDirectory("xreader-markdown-heading-test").toFile()
        val source = File(dir, "untitled.markdown")
        val output = File(dir, "untitled.epub")
        source.writeText(
            """
            # Recovered Notes

            Standalone Markdown remains readable.
            """.trimIndent()
        )

        MarkdownToEpubConverter().convert(source, output, "Fallback")

        val parsed = EpubParser().parse(output)
        assertEquals("Recovered Notes", parsed.metadata.title)
        assertTrue(parsed.units.any { it.heading == "Recovered Notes" && it.body.contains("Standalone Markdown") })
    }

    @Test
    fun rejectsMarkdownWithoutReadableText() {
        val dir = Files.createTempDirectory("xreader-empty-markdown-test").toFile()
        val source = File(dir, "Empty.md")
        val output = File(dir, "Empty.epub")
        source.writeText(
            """
            ---
            title: Empty
            ---

            ---
            """.trimIndent()
        )

        val error = runCatching {
            MarkdownToEpubConverter().convert(source, output, "Empty")
        }.exceptionOrNull()

        assertEquals("Markdown document contains no readable text.", error?.message)
        assertFalse(output.exists())
    }

    private fun markdownDocument(): String =
        """
        ---
        title: Orbital Field Notes
        author: Mina Patel
        genre: Science Fiction
        language: en-US
        date: 2026-05-30
        description: A compact Markdown fixture.
        ---

        # Arrival

        The **survey ship** docked quietly at dawn.

        - Checklist complete.
        - Greenhouse [sealed](https://example.invalid).

        > Keep the lights low during first contact.

        ```text
        oxygen = stable
        ```
        """.trimIndent()
}
