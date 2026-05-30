package com.xreader.app.importer

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlToEpubConverterTest {
    @Test
    fun convertsHtmlMetadataAndReadingOrderIntoParseableEpub() {
        val dir = Files.createTempDirectory("xreader-html-test").toFile()
        val source = File(dir, "Orbital Report.html")
        val output = File(dir, "Orbital Report.epub")
        source.writeText(htmlDocument())

        HtmlToEpubConverter().convert(source, output, "Fallback Title")

        val parsed = EpubParser().parse(output)
        assertEquals("Orbital Field Report", parsed.metadata.title)
        assertEquals("Mina Patel", parsed.metadata.author)
        assertEquals("en-US", parsed.metadata.language)
        assertEquals("Science Fiction", parsed.metadata.genre)
        assertEquals(2026, parsed.metadata.year)
        assertTrue(parsed.units.any { it.heading == "Arrival" && it.body.contains("The survey ship docked quietly") })
        assertTrue(parsed.units.any { it.body.contains("Checklist complete") })
        assertTrue(parsed.units.any { it.body.contains("Signal | Stable") })
        assertTrue(parsed.units.any { it.body.contains("Keep the lights low") })
    }

    @Test
    fun convertsXhtmlWithFallbackTitle() {
        val dir = Files.createTempDirectory("xreader-xhtml-test").toFile()
        val source = File(dir, "chapter.xhtml")
        val output = File(dir, "chapter.epub")
        source.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <body>
                <h1>Recovered Chapter</h1>
                <p>Standalone XHTML remains readable.</p>
              </body>
            </html>
            """.trimIndent()
        )

        HtmlToEpubConverter().convert(source, output, "Recovered")

        val parsed = EpubParser().parse(output)
        assertEquals("Recovered", parsed.metadata.title)
        assertTrue(parsed.units.any { it.heading == "Recovered Chapter" && it.body.contains("Standalone XHTML") })
    }

    @Test
    fun rejectsHtmlWithoutReadableText() {
        val dir = Files.createTempDirectory("xreader-empty-html-test").toFile()
        val source = File(dir, "Empty.html")
        val output = File(dir, "Empty.epub")
        source.writeText("<html><head><title>Empty</title></head><body><script>hidden()</script></body></html>")

        val error = runCatching {
            HtmlToEpubConverter().convert(source, output, "Empty")
        }.exceptionOrNull()

        assertEquals("HTML document contains no readable text.", error?.message)
        assertFalse(output.exists())
    }

    private fun htmlDocument(): String =
        """
        <!doctype html>
        <html lang="en-US">
          <head>
            <title>Fallback Browser Title</title>
            <meta name="dc.title" content="Orbital Field Report">
            <meta name="author" content="Mina Patel">
            <meta name="keywords" content="Science Fiction">
            <meta name="date" content="2026-05-30">
            <meta name="description" content="A field report fixture.">
          </head>
          <body>
            <nav>Skip this site menu</nav>
            <h1>Arrival</h1>
            <p>The survey ship docked quietly at dawn.</p>
            <ul>
              <li>Checklist complete.</li>
              <li>Greenhouse sealed.</li>
            </ul>
            <table>
              <tr><th>Signal</th><td>Stable</td></tr>
            </table>
            <blockquote>Keep the lights low during first contact.</blockquote>
          </body>
        </html>
        """.trimIndent()
}
