package com.xreader.app.opds

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
