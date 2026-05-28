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
    fun startIndexUsesNearestChunkAtOrAfterCurrentUnit() {
        val chunks = listOf(
            ReadAloudChunk(unitIndex = 0, locator = "a", heading = "A", text = "A", wordCount = 1),
            ReadAloudChunk(unitIndex = 5, locator = "b", heading = "B", text = "B", wordCount = 1),
            ReadAloudChunk(unitIndex = 10, locator = "c", heading = "C", text = "C", wordCount = 1)
        )

        assertEquals(0, ReadAloudPlanner.startIndex(chunks, currentUnit = 0))
        assertEquals(1, ReadAloudPlanner.startIndex(chunks, currentUnit = 3))
        assertEquals(2, ReadAloudPlanner.startIndex(chunks, currentUnit = 99))
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
}
