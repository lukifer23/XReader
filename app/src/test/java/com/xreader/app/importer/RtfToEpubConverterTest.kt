package com.xreader.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RtfToEpubConverterTest {
    @Test
    fun convertsRtfMetadataAndBodyIntoParseableEpub() {
        val dir = Files.createTempDirectory("xreader-rtf-test").toFile()
        val source = File(dir, "Solar Memoirs.rtf")
        val output = File(dir, "Solar Memoirs.epub")
        source.writeText(
            """
            {\rtf1\ansi\deff0
            {\info{\title Solar Memoirs}{\author Jane Reader}}
            {\fonttbl{\f0\fswiss Arial;}}
            \pard\b Solar Memoirs\b0\par
            First paragraph with \'e9 and \u8217? punctuation.\par
            Second line\tab indented.\par
            {\pict\pngblip abcdef}\par
            }
            """.trimIndent(),
            Charsets.ISO_8859_1
        )

        RtfToEpubConverter().convert(source, output, "Fallback")

        val parsed = EpubParser().parse(output)
        assertEquals("Solar Memoirs", parsed.metadata.title)
        assertEquals("Jane Reader", parsed.metadata.author)
        assertTrue(parsed.units.any { it.body.contains("First paragraph with é and ’ punctuation") })
        assertTrue(parsed.units.any { it.body.contains("Second line indented") })
        assertFalse(parsed.units.any { it.body.contains("abcdef") })
    }

    @Test
    fun rejectsRtfWithoutReadableText() {
        val dir = Files.createTempDirectory("xreader-empty-rtf-test").toFile()
        val source = File(dir, "Empty.rtf")
        val output = File(dir, "Empty.epub")
        source.writeText("""{\rtf1\ansi{\fonttbl{\f0 Arial;}}}""", Charsets.ISO_8859_1)

        val error = runCatching {
            RtfToEpubConverter().convert(source, output, "Empty")
        }.exceptionOrNull()

        assertEquals("RTF document contains no readable text.", error?.message)
    }

    @Test
    fun rejectsNonRtfInput() {
        val dir = Files.createTempDirectory("xreader-non-rtf-test").toFile()
        val source = File(dir, "Not RTF.rtf")
        val output = File(dir, "Not RTF.epub")
        source.writeText("not an rtf document", Charsets.ISO_8859_1)

        val error = runCatching {
            RtfToEpubConverter().convert(source, output, "Not RTF")
        }.exceptionOrNull()

        assertEquals("File is not an RTF document.", error?.message)
    }
}
