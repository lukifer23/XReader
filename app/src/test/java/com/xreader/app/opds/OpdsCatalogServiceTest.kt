package com.xreader.app.opds

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsCatalogServiceTest {
    @Test
    fun loadUsesFinalRedirectUrlForRelativeCatalogLinks() = runBlocking {
        val cacheDir = Files.createTempDirectory("xreader-opds-test").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/opds/root.xml") { exchange ->
            exchange.responseHeaders.add("Location", "/catalogs/current/root.xml")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/catalogs/current/root.xml") { exchange ->
            val body = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Redirected Catalog</title>
                  <link rel="subsection" title="Science fiction" href="sci-fi.xml" type="application/atom+xml;profile=opds-catalog"/>
                  <entry>
                    <title>Redirected Dawn</title>
                    <link rel="http://opds-spec.org/acquisition/open-access" href="../books/redirected-dawn.epub" type="application/epub+zip"/>
                  </entry>
                </feed>
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/atom+xml; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val feed = OpdsCatalogService(importService = null, cacheDir = cacheDir).load("$base/opds/root.xml")

            assertEquals("$base/catalogs/current/root.xml", feed.url)
            assertEquals("$base/catalogs/current/sci-fi.xml", feed.navigationLinks.single().href)
            assertEquals("$base/catalogs/books/redirected-dawn.epub", feed.entries.single().acquisitionLinks.single().href)
        } finally {
            server.stop(0)
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun loadParsesOpdsJsonCatalogs() = runBlocking {
        val cacheDir = Files.createTempDirectory("xreader-opds-json-test").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/opds/root.json") { exchange ->
            val body = """
                {
                  "metadata": { "title": "JSON Catalog" },
                  "navigation": [
                    { "href": "sections/scifi.json", "title": "Science fiction" }
                  ],
                  "publications": [
                    {
                      "metadata": { "title": "Europa Packet", "author": "Rae Kim" },
                      "links": [
                        { "rel": "http://opds-spec.org/acquisition/open-access", "href": "../books/europa-packet.epub", "type": "application/epub+zip" }
                      ]
                    }
                  ]
                }
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/opds+json; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val feed = OpdsCatalogService(importService = null, cacheDir = cacheDir).load("$base/opds/root.json")

            assertEquals("JSON Catalog", feed.title)
            assertEquals("$base/opds/sections/scifi.json", feed.navigationLinks.single().href)
            assertEquals("Europa Packet", feed.entries.single().title)
            assertEquals("$base/books/europa-packet.epub", feed.entries.single().acquisitionLinks.single().href)
        } finally {
            server.stop(0)
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun loadOrImportParsesCatalogFeeds() = runBlocking {
        val cacheDir = Files.createTempDirectory("xreader-opds-load-or-import-feed-test").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/opds/root.xml") { exchange ->
            val body = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Load Or Import Catalog</title>
                  <entry>
                    <title>Catalog Book</title>
                    <link rel="http://opds-spec.org/acquisition/open-access" href="../books/catalog-book.epub" type="application/epub+zip"/>
                  </entry>
                </feed>
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/atom+xml; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val result = OpdsCatalogService(importService = null, cacheDir = cacheDir).loadOrImport("$base/opds/root.xml")

            assertTrue(result is OpdsCatalogLoadResult.Feed)
            val feed = (result as OpdsCatalogLoadResult.Feed).feed
            assertEquals("Load Or Import Catalog", feed.title)
            assertEquals("$base/books/catalog-book.epub", feed.entries.single().acquisitionLinks.single().href)
        } finally {
            server.stop(0)
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun loadOrImportDownloadsDirectBookUrlsThroughImporter() = runBlocking {
        val cacheDir = Files.createTempDirectory("xreader-opds-direct-download-test").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/books/direct.txt") { exchange ->
            val body = """
                Direct Book

                A pasted book URL should import through the same private pipeline.
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val imported = mutableListOf<DownloadedBook>()
        val importer = RemoteBookImporter { file, displayName, mimeType ->
            imported += DownloadedBook(
                displayName = displayName,
                mimeType = mimeType,
                body = file.readText(StandardCharsets.UTF_8),
                existsDuringImport = file.exists()
            )
            com.xreader.app.importer.ImportService.ImportResult(bookId = 42L, duplicate = false)
        }

        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val result = OpdsCatalogService(importer, cacheDir).loadOrImport("$base/books/direct.txt")

            assertTrue(result is OpdsCatalogLoadResult.Imported)
            assertEquals(42L, (result as OpdsCatalogLoadResult.Imported).result.bookId)
            assertEquals(1, imported.size)
            assertTrue(imported.single().existsDuringImport)
            assertEquals("text/plain", imported.single().mimeType)
            assertTrue(imported.single().displayName.endsWith("direct.txt"))
            assertTrue(imported.single().body.contains("pasted book URL"))
            assertFalse(cacheDir.walkTopDown().any { it.isFile })
        } finally {
            server.stop(0)
            cacheDir.deleteRecursively()
        }
    }

    private data class DownloadedBook(
        val displayName: String,
        val mimeType: String,
        val body: String,
        val existsDuringImport: Boolean,
    )
}
