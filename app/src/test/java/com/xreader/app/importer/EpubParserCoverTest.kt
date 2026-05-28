package com.xreader.app.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserCoverTest {
    @Test
    fun extractsCoverFromGuideXhtmlPageBeforeArbitraryManifestImages() {
        val interiorBytes = byteArrayOf(1, 2, 3, 4)
        val coverBytes = byteArrayOf(9, 8, 7, 6)
        val epub = epubWithPackage(
            packageXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="2.0" xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <metadata>
                        <dc:title>Guide Cover Test</dc:title>
                        <dc:creator>XReader</dc:creator>
                    </metadata>
                    <manifest>
                        <item id="interior-art" href="Images/interior.png" media-type="image/png"/>
                        <item id="title-page" href="Text/titlepage.xhtml" media-type="application/xhtml+xml"/>
                        <item id="jacket-art" href="Images/jacket.jpg" media-type="image/jpeg"/>
                        <item id="chapter" href="Text/chapter.xhtml" media-type="application/xhtml+xml"/>
                    </manifest>
                    <spine>
                        <itemref idref="chapter"/>
                    </spine>
                    <guide>
                        <reference type="cover" title="Cover" href="Text/titlepage.xhtml"/>
                    </guide>
                </package>
            """.trimIndent(),
            entries = mapOf(
                "OEBPS/Images/interior.png" to interiorBytes,
                "OEBPS/Images/jacket.jpg" to coverBytes,
                "OEBPS/Text/titlepage.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                        <body><img alt="Cover" src="../Images/jacket.jpg"/></body>
                    </html>
                """.trimIndent().toByteArray(Charsets.UTF_8),
                "OEBPS/Text/chapter.xhtml" to "<html><body><p>Body text.</p></body></html>".toByteArray()
            )
        )

        val cover = requireNotNull(EpubParser().parseCover(epub))

        assertEquals("jpg", cover.extension)
        assertArrayEquals(coverBytes, cover.bytes)
    }

    @Test
    fun extractsDirectGuideImageBeforeFirstManifestImage() {
        val interiorBytes = byteArrayOf(10, 11, 12)
        val coverBytes = byteArrayOf(21, 22, 23)
        val epub = epubWithPackage(
            packageXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="2.0" xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <metadata>
                        <dc:title>Direct Guide Cover Test</dc:title>
                        <dc:creator>XReader</dc:creator>
                    </metadata>
                    <manifest>
                        <item id="interior-art" href="Images/interior.png" media-type="image/png"/>
                        <item id="jacket-art" href="Images/jacket.webp" media-type="image/webp"/>
                        <item id="chapter" href="Text/chapter.xhtml" media-type="application/xhtml+xml"/>
                    </manifest>
                    <spine>
                        <itemref idref="chapter"/>
                    </spine>
                    <guide>
                        <reference type="cover" title="Cover" href="Images/jacket.webp"/>
                    </guide>
                </package>
            """.trimIndent(),
            entries = mapOf(
                "OEBPS/Images/interior.png" to interiorBytes,
                "OEBPS/Images/jacket.webp" to coverBytes,
                "OEBPS/Text/chapter.xhtml" to "<html><body><p>Body text.</p></body></html>".toByteArray()
            )
        )

        val cover = requireNotNull(EpubParser().parseCover(epub))

        assertEquals("webp", cover.extension)
        assertArrayEquals(coverBytes, cover.bytes)
    }

    private fun epubWithPackage(
        packageXml: String,
        entries: Map<String, ByteArray>,
    ): File {
        val dir = Files.createTempDirectory("xreader-cover-test").toFile()
        val file = File(dir, "book.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writeEntry(
                "META-INF/container.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.writeEntry("OEBPS/content.opf", packageXml.toByteArray(Charsets.UTF_8))
            entries.forEach { (name, bytes) -> zip.writeEntry(name, bytes) }
        }
        return file
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}
