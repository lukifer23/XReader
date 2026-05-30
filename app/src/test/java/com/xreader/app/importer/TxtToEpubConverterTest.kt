package com.xreader.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

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

    @Test
    fun convertsChapterHeadingsIntoSeparateEpubChapters() {
        val dir = Files.createTempDirectory("xreader-epub-test").toFile()
        val source = File(dir, "novel.txt")
        val output = File(dir, "novel.epub")
        source.writeText(
            """
            Project notes before the first chapter.

            CHAPTER I
            The First Door

            The hallway was quiet.

            CHAPTER II
            The Second Door

            The lock finally turned.
            """.trimIndent()
        )

        TxtToEpubConverter().convert(source, output, "Novel")
        val parsed = EpubParser().parse(output)

        assertTrue(parsed.units.any { it.heading == "Novel" && it.body.contains("Project notes") })
        assertTrue(parsed.units.any { it.heading == "CHAPTER I - The First Door" && it.body.contains("hallway") })
        assertTrue(parsed.units.any { it.heading == "CHAPTER II - The Second Door" && it.body.contains("lock") })
        ZipFile(output).use { zip ->
            assertTrue(zip.getEntry("OEBPS/chapters/chapter-0001.xhtml") != null)
            assertTrue(zip.getEntry("OEBPS/chapters/chapter-0002.xhtml") != null)
            assertTrue(zip.getEntry("OEBPS/chapters/chapter-0003.xhtml") != null)
        }
    }

    @Test
    fun fallsBackToWindows1252ForLegacyPlainText() {
        val dir = Files.createTempDirectory("xreader-epub-test").toFile()
        val source = File(dir, "legacy.txt")
        val output = File(dir, "legacy.epub")
        source.writeBytes(
            byteArrayOf(
                0x43, 0x61, 0x66, 0xE9.toByte(), 0x0A, 0x0A,
                0x43, 0x48, 0x41, 0x50, 0x54, 0x45, 0x52, 0x20, 0x31, 0x0A, 0x0A,
                0x41, 0x20, 0x63, 0x69, 0x74, 0x79, 0x20, 0x63, 0x61, 0x66, 0xE9.toByte(), 0x2E
            )
        )

        TxtToEpubConverter().convert(source, output, "Legacy")
        val parsed = EpubParser().parse(output)

        assertTrue(parsed.units.any { it.body.contains("Café") || it.body.contains("café") })
    }

    @Test
    fun rejectsTxtWithoutReadableText() {
        val dir = Files.createTempDirectory("xreader-epub-test").toFile()
        val source = File(dir, "empty.txt")
        val output = File(dir, "empty.epub")
        source.writeText("   \n\n\t  ")

        val error = runCatching { TxtToEpubConverter().convert(source, output, "Empty") }.exceptionOrNull()

        assertEquals("TXT document contains no readable text.", error?.message)
    }
}
