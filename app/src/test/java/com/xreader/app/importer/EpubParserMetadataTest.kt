package com.xreader.app.importer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserMetadataTest {
    @Test
    fun choosesSpecificGenreInsteadOfFirstSubject() {
        val epub = minimalEpub(
            """
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Morning Star</dc:title>
                <dc:creator>Pierce Brown</dc:creator>
                <dc:subject>United States</dc:subject>
                <dc:subject>Colonization</dc:subject>
                <dc:subject>Adventure</dc:subject>
                <dc:subject>Hard Science Fiction</dc:subject>
                <dc:date>2016-02-08T08:00:00+00:00</dc:date>
            </metadata>
            """.trimIndent()
        )

        val metadata = EpubParser().readMetadata(epub)

        assertEquals("Morning Star", metadata.title)
        assertEquals("Science Fiction", metadata.genre)
        assertEquals(2016, metadata.year)
    }

    @Test
    fun replacesLowerPriorityImportedGenreWithSpecificGenre() {
        assertEquals(
            true,
            PublicationMetadataTools.shouldReplaceGenre("Adventure", "Science Fiction")
        )
    }

    @Test
    fun infersDystopianScienceFictionFromGenericFictionDescription() {
        val epub = minimalEpub(
            """
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Red Rising</dc:title>
                <dc:creator>Pierce Brown</dc:creator>
                <dc:subject>Fiction</dc:subject>
                <dc:description>Darrow is a miner in a color-coded society of the future. Vast cities already cover the surface of the planet, but his caste is enslaved by a ruling class.</dc:description>
            </metadata>
            """.trimIndent()
        )

        val metadata = EpubParser().readMetadata(epub)

        assertEquals("Science Fiction", metadata.genre)
    }

    @Test
    fun extractsCalibreSeriesMetadata() {
        val epub = minimalEpub(
            """
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Book Two</dc:title>
                <dc:creator>Author</dc:creator>
                <meta name="calibre:series" content="The Series"/>
                <meta name="calibre:series_index" content="2.5"/>
            </metadata>
            """.trimIndent()
        )

        val metadata = EpubParser().readMetadata(epub)

        assertEquals("The Series", metadata.series)
        assertEquals(2.5, metadata.seriesIndex ?: -1.0, 0.001)
    }

    @Test
    fun extractsSeriesNameFromDescriptionWhenMetadataIsMissing() {
        val epub = minimalEpub(
            """
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Golden Son</dc:title>
                <dc:creator>Pierce Brown</dc:creator>
                <dc:description>As great as the first book of the Red Rising Trilogy, this sequel continues the story.</dc:description>
            </metadata>
            """.trimIndent()
        )

        val metadata = EpubParser().readMetadata(epub)

        assertEquals("Red Rising", metadata.series)
    }

    private fun minimalEpub(metadata: String): File {
        val dir = Files.createTempDirectory("xreader-metadata-test").toFile()
        val file = File(dir, "book.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writestr(
                "META-INF/container.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
                """.trimIndent()
            )
            zip.writestr(
                "content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="2.0" xmlns="http://www.idpf.org/2007/opf">
                    $metadata
                    <manifest/>
                    <spine/>
                </package>
                """.trimIndent()
            )
        }
        return file
    }

    private fun ZipOutputStream.writestr(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.trim().toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
