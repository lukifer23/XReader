package com.xreader.app.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

class CbzToEpubConverterTest {
    @Test
    fun convertsCbzIntoFixedLayoutEpubWithNaturalPageOrder() {
        val dir = Files.createTempDirectory("xreader-cbz-test").toFile()
        val source = File(dir, "space_comic.cbz")
        val output = File(dir, "space_comic.epub")
        val pageOne = pngBytes(0xFFE74C3C.toInt())
        val pageTwo = pngBytes(0xFF2ECC71.toInt())
        val pageTen = pngBytes(0xFF3498DB.toInt())
        writeZip(
            source,
            "chapter/page10.png" to pageTen,
            "chapter/page2.png" to pageTwo,
            "chapter/page1.png" to pageOne,
            "__MACOSX/._page3.png" to pngBytes(0xFFFFFFFF.toInt()),
            "notes.txt" to "not a page".toByteArray()
        )

        val result = CbzToEpubConverter().convert(source, output, "Space Comic")

        assertEquals(3, result.pageCount)
        val parsed = EpubParser().parse(output)
        assertEquals("Space Comic", parsed.metadata.title)
        assertTrue(parsed.units.isEmpty())
        assertArrayEquals(pageOne, requireNotNull(EpubParser().parseCover(output)).bytes)
        ZipFile(output).use { epub ->
            assertArrayEquals(pageOne, epub.readEntry("OEBPS/images/page-0001.png"))
            assertArrayEquals(pageTwo, epub.readEntry("OEBPS/images/page-0002.png"))
            assertArrayEquals(pageTen, epub.readEntry("OEBPS/images/page-0003.png"))
            val opf = epub.readEntry("OEBPS/package.opf").toString(Charsets.UTF_8)
            assertTrue(opf.contains("rendition:layout\">pre-paginated"))
            assertTrue(opf.contains("properties=\"cover-image\""))
        }
    }

    @Test
    fun rejectsCbzWithoutSupportedImages() {
        val dir = Files.createTempDirectory("xreader-empty-cbz-test").toFile()
        val source = File(dir, "empty.cbz")
        val output = File(dir, "empty.epub")
        writeZip(source, "readme.txt" to "no pages".toByteArray())

        val error = runCatching {
            CbzToEpubConverter().convert(source, output, "Empty")
        }.exceptionOrNull()

        assertEquals("CBZ archive contains no supported image pages.", error?.message)
    }

    private fun writeZip(file: File, vararg entries: Pair<String, ByteArray>) {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun ZipFile.readEntry(name: String): ByteArray =
        getInputStream(requireNotNull(getEntry(name)) { "Missing $name" }).use { it.readBytes() }

    private fun pngBytes(argb: Int): ByteArray {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, argb)
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }
}
