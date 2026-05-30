package com.xreader.app.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsFeedParserTest {
    @Test
    fun parsesNavigationAndSupportedOpenAccessAcquisitions() {
        val feed = OpdsFeedParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Public Domain Catalog</title>
              <link rel="self" href="https://example.test/opds/root.xml" type="application/atom+xml;profile=opds-catalog"/>
              <link rel="subsection" title="Science fiction" href="sci-fi.xml" type="application/atom+xml;profile=opds-catalog"/>
              <entry>
                <id>urn:book:1</id>
                <title>Orbital Dawn</title>
                <author><name>Mina Patel</name></author>
                <summary>A short catalog summary.</summary>
                <link rel="http://opds-spec.org/acquisition/open-access" href="../books/orbital-dawn.epub" type="application/epub+zip"/>
                <link rel="http://opds-spec.org/acquisition/buy" href="../store/orbital-dawn" type="text/html"/>
              </entry>
              <entry>
                <id>urn:book:2</id>
                <title>Scanned Manual</title>
                <link rel="enclosure" href="manual.pdf" type="application/pdf"/>
              </entry>
            </feed>
            """.trimIndent(),
            "https://example.test/opds/root.xml"
        )

        assertEquals("Public Domain Catalog", feed.title)
        assertEquals(1, feed.navigationLinks.size)
        assertEquals("Science fiction", feed.navigationLinks.single().displayTitle)
        assertEquals("https://example.test/opds/sci-fi.xml", feed.navigationLinks.single().href)
        assertEquals(2, feed.entries.size)
        assertEquals("Orbital Dawn", feed.entries[0].title)
        assertEquals("Mina Patel", feed.entries[0].author)
        assertEquals("A short catalog summary.", feed.entries[0].summary)
        assertEquals("https://example.test/books/orbital-dawn.epub", feed.entries[0].acquisitionLinks.single().href)
        assertEquals("https://example.test/opds/manual.pdf", feed.entries[1].acquisitionLinks.single().href)
    }

    @Test
    fun acquisitionSupportRequiresBookTypeOrExtension() {
        assertTrue(
            OpdsLink(
                href = "https://example.test/download",
                rel = "http://opds-spec.org/acquisition/open-access",
                type = "application/epub+zip",
                title = null
            ).isSupportedAcquisition()
        )
        assertTrue(
            OpdsLink(
                href = "https://example.test/book.mobi",
                rel = "http://opds-spec.org/acquisition/open-access",
                type = null,
                title = null
            ).isSupportedAcquisition()
        )
        assertFalse(
            OpdsLink(
                href = "https://example.test/page.html",
                rel = "alternate",
                type = "text/html",
                title = null
            ).isSupportedAcquisition()
        )
    }
}
