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
    fun resolvesXmlBaseForFeedAndEntryLinks() {
        val feed = OpdsFeedParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xml:base="https://cdn.example.test/opds/current/">
              <title>Mirror Catalog</title>
              <link rel="subsection" title="Recent" href="recent.xml" type="application/atom+xml;profile=opds-catalog"/>
              <entry xml:base="../downloads/scifi/">
                <title>Mirror Dawn</title>
                <link rel="http://opds-spec.org/acquisition/open-access" href="mirror-dawn.epub" type="application/epub+zip"/>
              </entry>
            </feed>
            """.trimIndent(),
            "https://catalog.example.test/root.xml"
        )

        assertEquals("https://cdn.example.test/opds/current/", feed.url)
        assertEquals("https://cdn.example.test/opds/current/recent.xml", feed.navigationLinks.single().href)
        assertEquals("https://cdn.example.test/opds/downloads/scifi/mirror-dawn.epub", feed.entries.single().acquisitionLinks.single().href)
    }

    @Test
    fun parsesOpdsJsonNavigationAndPublications() {
        val feed = OpdsFeedParser.parse(
            """
            {
              "metadata": { "title": "Modern Catalog" },
              "links": [
                { "rel": "self", "href": "https://example.test/opds/root.json", "type": "application/opds+json" },
                { "rel": ["subsection", "collection"], "href": "new.json", "type": "application/opds+json", "title": "New books" }
              ],
              "navigation": [
                { "href": "science-fiction.json", "title": "Science fiction" }
              ],
              "publications": [
                {
                  "metadata": {
                    "identifier": "urn:book:3",
                    "title": "Station Twelve",
                    "author": [{ "name": "Iris Chen" }],
                    "description": "Orbital maintenance logs."
                  },
                  "links": [
                    { "rel": "http://opds-spec.org/acquisition/open-access", "href": "../books/station-twelve.epub", "type": "application/epub+zip" },
                    { "rel": "preview", "href": "../samples/station-twelve.html", "type": "text/html" }
                  ]
                }
              ]
            }
            """.trimIndent(),
            "https://example.test/opds/root.json"
        )

        assertEquals("Modern Catalog", feed.title)
        assertEquals(2, feed.navigationLinks.size)
        assertEquals("https://example.test/opds/science-fiction.json", feed.navigationLinks[0].href)
        assertEquals("https://example.test/opds/new.json", feed.navigationLinks[1].href)
        assertEquals(1, feed.entries.size)
        assertEquals("Station Twelve", feed.entries.single().title)
        assertEquals("Iris Chen", feed.entries.single().author)
        assertEquals("Orbital maintenance logs.", feed.entries.single().summary)
        assertEquals("https://example.test/books/station-twelve.epub", feed.entries.single().acquisitionLinks.single().href)
    }

    @Test
    fun parsesGroupedOpdsJsonPublicationsAndStringContributors() {
        val feed = OpdsFeedParser.parse(
            """
            {
              "metadata": { "title": "Grouped Catalog" },
              "groups": [
                {
                  "metadata": { "title": "Featured" },
                  "publications": [
                    {
                      "metadata": {
                        "title": "Plain Old Mars",
                        "author": "Nia Okafor",
                        "subtitle": "A field report"
                      },
                      "links": [
                        { "rel": "http://opds-spec.org/acquisition/open-access", "href": "plain-old-mars.pdf", "type": "application/pdf" }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
            "https://example.test/catalogs/root.json"
        )

        assertEquals("Grouped Catalog", feed.title)
        assertEquals("Plain Old Mars", feed.entries.single().title)
        assertEquals("Nia Okafor", feed.entries.single().author)
        assertEquals("A field report", feed.entries.single().summary)
        assertEquals("https://example.test/catalogs/plain-old-mars.pdf", feed.entries.single().acquisitionLinks.single().href)
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
