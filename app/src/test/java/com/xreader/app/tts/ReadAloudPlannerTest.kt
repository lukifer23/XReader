package com.xreader.app.tts

import com.xreader.app.data.SearchIndexEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudPlannerTest {
    @Test
    fun chunksFromRowsCleansAndOrdersIndexedText() {
        val chunks = ReadAloudPlanner.chunksFromRows(
            listOf(
                row(unitIndex = 4, body = "   Later   text .  "),
                row(unitIndex = 2, body = "First  passage , with  spacing."),
                row(unitIndex = 3, body = " ")
            )
        )

        assertEquals(2, chunks.size)
        assertEquals(2, chunks[0].unitIndex)
        assertEquals("First passage, with spacing.", chunks[0].text)
        assertEquals(4, chunks[1].unitIndex)
        assertEquals("Later text.", chunks[1].text)
    }

    @Test
    fun startIndexUsesCurrentOrNearestEarlierChunk() {
        val chunks = listOf(
            ReadAloudChunk(unitIndex = 0, locator = "a", heading = "A", text = "A", wordCount = 1),
            ReadAloudChunk(unitIndex = 5, locator = "b", heading = "B", text = "B", wordCount = 1),
            ReadAloudChunk(unitIndex = 10, locator = "c", heading = "C", text = "C", wordCount = 1)
        )

        assertEquals(0, ReadAloudPlanner.startIndex(chunks, currentUnit = 0))
        assertEquals(0, ReadAloudPlanner.startIndex(chunks, currentUnit = 3))
        assertEquals(1, ReadAloudPlanner.startIndex(chunks, currentUnit = 5))
        assertEquals(1, ReadAloudPlanner.startIndex(chunks, currentUnit = 9))
        assertEquals(2, ReadAloudPlanner.startIndex(chunks, currentUnit = 99))
    }

    @Test
    fun startIndexPrefersExactCurrentLocator() {
        val chunks = listOf(
            ReadAloudChunk(unitIndex = 0, locator = locator(position = 1, progression = 0.1), heading = "A", text = "A", wordCount = 1),
            ReadAloudChunk(unitIndex = 5, locator = locator(position = 2, progression = 0.4), heading = "B", text = "B", wordCount = 1),
            ReadAloudChunk(unitIndex = 10, locator = locator(position = 3, progression = 0.8), heading = "C", text = "C", wordCount = 1)
        )

        assertEquals(
            2,
            ReadAloudPlanner.startIndex(
                chunks = chunks,
                currentUnit = 0,
                currentLocator = chunks[2].locator
            )
        )
    }

    @Test
    fun startIndexFallsBackToNearestLocatorProgression() {
        val chunks = listOf(
            ReadAloudChunk(unitIndex = 0, locator = locator(position = 1, progression = 0.1), heading = "A", text = "A", wordCount = 1),
            ReadAloudChunk(unitIndex = 5, locator = locator(position = 2, progression = 0.4), heading = "B", text = "B", wordCount = 1),
            ReadAloudChunk(unitIndex = 10, locator = locator(position = 3, progression = 0.8), heading = "C", text = "C", wordCount = 1)
        )

        assertEquals(
            1,
            ReadAloudPlanner.startIndex(
                chunks = chunks,
                currentUnit = 0,
                currentLocator = """{"href":"chapter.xhtml","locations":{"totalProgression":0.55}}"""
            )
        )
    }

    @Test
    fun startIndexUsesInChapterProgressionBeforeHrefFallback() {
        val chunks = listOf(
            ReadAloudChunk(unitIndex = 0, locator = locator(position = 1, progression = 0.1, localProgression = 0.1), heading = "A", text = "A", wordCount = 1),
            ReadAloudChunk(unitIndex = 5, locator = locator(position = 2, progression = 0.4, localProgression = 0.4), heading = "B", text = "B", wordCount = 1),
            ReadAloudChunk(unitIndex = 10, locator = locator(position = 3, progression = 0.8, localProgression = 0.8), heading = "C", text = "C", wordCount = 1)
        )

        assertEquals(
            1,
            ReadAloudPlanner.startIndex(
                chunks = chunks,
                currentUnit = 0,
                currentLocator = """{"href":"chapter.xhtml","locations":{"progression":0.55}}"""
            )
        )
    }

    @Test
    fun alignChunksToPositionsUsesReadingProgressInsteadOfSparseParserIds() {
        val sparseChunks = listOf(
            ReadAloudChunk(unitIndex = 0, locator = "epub:chapter-1.xhtml:0", heading = "One", text = "A", wordCount = 100),
            ReadAloudChunk(unitIndex = 10_000, locator = "epub:chapter-2.xhtml:0", heading = "Two", text = "B", wordCount = 100),
            ReadAloudChunk(unitIndex = 10_001, locator = "epub:chapter-2.xhtml:1", heading = "Two", text = "C", wordCount = 100)
        )
        val positions = (1..6).map { page ->
            locator(position = page, progression = (page - 1) / 5.0)
        }

        val aligned = ReadAloudPlanner.alignChunksToPositions(sparseChunks, positions)

        assertEquals(0, aligned[0].unitIndex)
        assertEquals(2, aligned[1].unitIndex)
        assertEquals(3, aligned[2].unitIndex)
        assertEquals(positions[2], aligned[1].locator)
        assertEquals(
            1,
            ReadAloudPlanner.startIndex(
                chunks = aligned,
                currentUnit = 0,
                currentLocator = positions[2]
            )
        )
    }

    @Test
    fun splitForSpeechRespectsMaxLength() {
        val text = (1..80).joinToString(" ") { "sentence$it." }
        val segments = ReadAloudPlanner.splitForSpeech(text, maxLength = 60)

        assertTrue(segments.size > 1)
        assertTrue(segments.all { it.length <= 60 })
        assertEquals(ReadAloudPlanner.cleanSpeechText(text), segments.joinToString(" "))
    }

    private fun row(unitIndex: Int, body: String): SearchIndexEntity =
        SearchIndexEntity(
            bookId = 1L,
            locator = "epub:item:$unitIndex",
            heading = "Heading $unitIndex",
            body = body,
            normalizedBody = body.lowercase(),
            unitIndex = unitIndex
        )

    private fun locator(
        position: Int,
        progression: Double,
        localProgression: Double? = null,
    ): String =
        if (localProgression == null) {
            """{"href":"chapter.xhtml","locations":{"position":$position,"totalProgression":$progression}}"""
        } else {
            """{"href":"chapter.xhtml","locations":{"position":$position,"totalProgression":$progression,"progression":$localProgression}}"""
        }
}
