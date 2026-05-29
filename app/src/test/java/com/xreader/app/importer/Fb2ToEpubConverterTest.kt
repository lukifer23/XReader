package com.xreader.app.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

class Fb2ToEpubConverterTest {
    @Test
    fun convertsFictionBookMetadataChaptersAndCoverIntoParseableEpub() {
        val dir = Files.createTempDirectory("xreader-fb2-test").toFile()
        val source = File(dir, "Solar Wind.fb2")
        val output = File(dir, "Solar Wind.epub")
        val coverBytes = pngBytes(0xFF7FDBFF.toInt())
        source.writeText(fictionBookXml(coverBytes), Charsets.UTF_8)

        Fb2ToEpubConverter().convert(source, output, "Fallback")

        val parsed = EpubParser().parse(output)
        assertEquals("Solar Wind", parsed.metadata.title)
        assertEquals("Ada Lovelace", parsed.metadata.author)
        assertEquals("en", parsed.metadata.language)
        assertEquals("Science Fiction", parsed.metadata.genre)
        assertEquals(2024, parsed.metadata.year)
        assertEquals("Orbital Tales", parsed.metadata.series)
        assertEquals(2.0, parsed.metadata.seriesIndex ?: -1.0, 0.001)
        assertTrue(parsed.units.any { it.heading == "Launch" && it.body.contains("The launch tower hummed") })
        assertTrue(parsed.units.any { it.heading == "Orbit" && it.body.contains("Earth turned slowly") })
        assertArrayEquals(coverBytes, requireNotNull(EpubParser().parseCover(output)).bytes)
    }

    @Test
    fun convertsZippedFictionBookArchives() {
        val dir = Files.createTempDirectory("xreader-fb2-zip-test").toFile()
        val source = File(dir, "Solar Wind.fb2.zip")
        val output = File(dir, "Solar Wind.epub")
        ZipOutputStream(source.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("Solar Wind.fb2"))
            zip.write(fictionBookXml(pngBytes(0xFFECC94B.toInt())).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        Fb2ToEpubConverter().convert(source, output, "Fallback")

        val parsed = EpubParser().parse(output)
        assertEquals("Solar Wind", parsed.metadata.title)
        assertTrue(parsed.units.any { it.body.contains("The launch tower hummed") })
    }

    @Test
    fun rejectsFictionBookWithoutReadableBodyText() {
        val dir = Files.createTempDirectory("xreader-empty-fb2-test").toFile()
        val source = File(dir, "Empty.fb2")
        val output = File(dir, "Empty.epub")
        source.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info><book-title>Empty</book-title></title-info></description>
              <body><section><title><p>Only a title</p></title></section></body>
            </FictionBook>
            """.trimIndent().trimStart(),
            Charsets.UTF_8
        )

        val error = runCatching {
            Fb2ToEpubConverter().convert(source, output, "Empty")
        }.exceptionOrNull()

        assertEquals("FictionBook document contains no readable body text.", error?.message)
    }

    private fun fictionBookXml(coverBytes: ByteArray): String {
        val cover = Base64.getMimeEncoder().encodeToString(coverBytes)
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
              <description>
                <title-info>
                  <genre>Science Fiction</genre>
                  <author>
                    <first-name>Ada</first-name>
                    <last-name>Lovelace</last-name>
                  </author>
                  <book-title>Solar Wind</book-title>
                  <annotation><p>A compact space opera.</p></annotation>
                  <date value="2024-04-05">2024</date>
                  <coverpage><image l:href="#cover.png"/></coverpage>
                  <lang>en</lang>
                  <sequence name="Orbital Tales" number="2"/>
                </title-info>
              </description>
              <body>
                <section>
                  <title><p>Launch</p></title>
                  <p>The launch tower hummed under the dawn.</p>
                  <p>The crew counted every breath.</p>
                </section>
                <section>
                  <title><p>Orbit</p></title>
                  <p>Earth turned slowly below the glass.</p>
                </section>
              </body>
              <binary id="cover.png" content-type="image/png">$cover</binary>
            </FictionBook>
        """.trimIndent().trimStart()
    }

    private fun pngBytes(argb: Int): ByteArray {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, argb)
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }
}
