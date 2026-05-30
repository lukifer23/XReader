package com.xreader.app.importer

import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MhtmlToEpubConverterTest {
    @Test
    fun convertsMhtmlMetadataTextAndEmbeddedImageIntoParseableEpub() {
        val dir = Files.createTempDirectory("xreader-mhtml-test").toFile()
        val source = File(dir, "Archived Report.mhtml")
        val output = File(dir, "Archived Report.epub")
        source.writeText(mhtmlDocument(), Charsets.ISO_8859_1)

        MhtmlToEpubConverter().convert(source, output, "Fallback")

        val parsed = EpubParser().parse(output)
        assertEquals("Archived Field Report", parsed.metadata.title)
        assertEquals("Mina Patel", parsed.metadata.author)
        assertEquals("Science Fiction", parsed.metadata.genre)
        assertEquals(2026, parsed.metadata.year)
        assertTrue(parsed.units.any { it.heading == "Arrival" && it.body.contains("survey ship docked") })
        assertTrue(parsed.units.any { it.body.contains("Signal | Stable") })
        ZipFile(output).use { zip ->
            val image = zip.getEntry("OEBPS/assets/asset-1.png")
            assertNotNull(image)
            val chapter = zip.getInputStream(requireNotNull(zip.getEntry("OEBPS/chapters/chapter-0001.xhtml")))
                .readBytes()
                .toString(Charsets.UTF_8)
            assertTrue(chapter.contains("""src="../assets/asset-1.png""""))
            assertTrue(chapter.contains("Observation sketch"))
        }
    }

    @Test
    fun decodesQuotedPrintableHtmlPart() {
        val dir = Files.createTempDirectory("xreader-mhtml-qp-test").toFile()
        val source = File(dir, "Quoted Printable.mht")
        val output = File(dir, "Quoted Printable.epub")
        source.writeText(quotedPrintableMhtml(), Charsets.ISO_8859_1)

        MhtmlToEpubConverter().convert(source, output, "Fallback")

        val parsed = EpubParser().parse(output)
        assertEquals("Quoted Printable", parsed.metadata.title)
        assertTrue(parsed.units.any { it.body.contains("Café greenhouse", ignoreCase = true) })
    }

    @Test
    fun recoversLazyAndResponsiveImages() {
        val dir = Files.createTempDirectory("xreader-mhtml-responsive-test").toFile()
        val source = File(dir, "Responsive Images.mhtml")
        val output = File(dir, "Responsive Images.epub")
        source.writeText(responsiveImageMhtml(), Charsets.ISO_8859_1)

        MhtmlToEpubConverter().convert(source, output, "Fallback")

        ZipFile(output).use { zip ->
            assertNotNull(zip.getEntry("OEBPS/assets/asset-1.png"))
            assertNotNull(zip.getEntry("OEBPS/assets/asset-2.png"))
            val chapter = zip.getInputStream(requireNotNull(zip.getEntry("OEBPS/chapters/chapter-0001.xhtml")))
                .readBytes()
                .toString(Charsets.UTF_8)
            assertTrue(chapter.contains("""src="../assets/asset-1.png""""))
            assertTrue(chapter.contains("""src="../assets/asset-2.png""""))
            assertTrue(chapter.contains("Lazy observation"))
            assertTrue(chapter.contains("Responsive observation"))
            assertFalse(chapter.contains("srcset="))
        }
    }

    @Test
    fun rejectsArchiveWithoutHtmlRoot() {
        val dir = Files.createTempDirectory("xreader-empty-mhtml-test").toFile()
        val source = File(dir, "No Html.mhtml")
        val output = File(dir, "No Html.epub")
        source.writeText(
            """
            MIME-Version: 1.0
            Content-Type: multipart/related; boundary="xreader-boundary"

            --xreader-boundary
            Content-Type: image/png
            Content-Transfer-Encoding: base64

            ${Base64.getEncoder().encodeToString(PNG_BYTES)}
            --xreader-boundary--
            """.trimIndent(),
            Charsets.ISO_8859_1
        )

        val error = runCatching {
            MhtmlToEpubConverter().convert(source, output, "Fallback")
        }.exceptionOrNull()

        assertEquals("MHTML archive contains no HTML root part.", error?.message)
        assertFalse(output.exists())
    }

    private fun mhtmlDocument(): String =
        """
        MIME-Version: 1.0
        Content-Type: multipart/related; boundary="xreader-boundary"; type="text/html"

        --xreader-boundary
        Content-Type: text/html; charset="utf-8"
        Content-Location: https://example.test/reports/archived.html

        <!doctype html>
        <html lang="en-US">
          <head>
            <title>Browser Title</title>
            <meta name="dc.title" content="Archived Field Report">
            <meta name="author" content="Mina Patel">
            <meta name="keywords" content="Science Fiction">
            <meta name="date" content="2026-05-30">
          </head>
          <body>
            <h1>Arrival</h1>
            <p>The survey ship docked inside the archived page.</p>
            <img src="images/observation.png" alt="Observation sketch">
            <table>
              <tr><th>Signal</th><td>Stable</td></tr>
            </table>
          </body>
        </html>
        --xreader-boundary
        Content-Type: image/png
        Content-Location: https://example.test/reports/images/observation.png
        Content-ID: <observation-image>
        Content-Transfer-Encoding: base64

        ${Base64.getEncoder().encodeToString(PNG_BYTES)}
        --xreader-boundary--
        """.trimIndent()

    private fun quotedPrintableMhtml(): String =
        """
        MIME-Version: 1.0
        Content-Type: multipart/related; boundary="quoted-boundary"; type="text/html"

        --quoted-boundary
        Content-Type: text/html; charset="utf-8"
        Content-Transfer-Encoding: quoted-printable

        <html><head><title>Quoted=20Printable</title></head><body><h1>Chapter</h1><p>Caf=C3=A9=20greenhouse=20sealed.</p></body></html>
        --quoted-boundary--
        """.trimIndent()

    private fun responsiveImageMhtml(): String =
        """
        MIME-Version: 1.0
        Content-Type: multipart/related; boundary="responsive-boundary"; type="text/html"

        --responsive-boundary
        Content-Type: text/html; charset="utf-8"
        Content-Location: https://example.test/articles/responsive.html

        <html>
          <head><title>Responsive Images</title></head>
          <body>
            <h1>Images</h1>
            <p>The archive keeps lazy and responsive image references.</p>
            <img data-src="images/lazy.png" alt="Lazy observation">
            <img srcset="images/small.png 480w, images/responsive-large.png 960w" alt="Responsive observation">
          </body>
        </html>
        --responsive-boundary
        Content-Type: image/png
        Content-Location: https://example.test/articles/images/lazy.png
        Content-Transfer-Encoding: base64

        ${Base64.getEncoder().encodeToString(PNG_BYTES)}
        --responsive-boundary
        Content-Type: image/png
        Content-Location: https://example.test/articles/images/responsive-large.png
        Content-Transfer-Encoding: base64

        ${Base64.getEncoder().encodeToString(PNG_BYTES)}
        --responsive-boundary--
        """.trimIndent()

    private companion object {
        private val PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMB/atJkWcAAAAASUVORK5CYII="
        )
    }
}
