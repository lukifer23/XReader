package com.xreader.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralTtsTextTest {
    @Test
    fun preparePreservesAllChunksInReadingOrder() {
        val chunks = listOf(
            chunk(unitIndex = 2, text = "Third position."),
            chunk(unitIndex = 0, text = "First position."),
            chunk(unitIndex = 1, text = "Second position.")
        )

        val prepared = NeuralTtsText.prepare(chunks)

        assertEquals(
            listOf("First position.", "Second position.", "Third position."),
            prepared.segments
        )
        assertEquals(6, prepared.wordCount)
    }

    @Test
    fun prepareSplitsLongPassagesWithoutDroppingTailText() {
        val words = (1..1_200).joinToString(" ") { "word$it" }
        val prepared = NeuralTtsText.prepare(listOf(chunk(text = words)))

        assertTrue(prepared.segments.size > 1)
        assertEquals(1_200, prepared.wordCount)
        assertTrue(prepared.segments.last().contains("word1200"))
        assertTrue(prepared.segments.all { it.length <= 560 || !it.contains(' ') })
    }

    @Test
    fun prepareDropsRepeatedShortHeadersAndIsolatedPageMarkers() {
        val chunks = listOf(
            chunk(unitIndex = 0, text = "XReader Sample Novel"),
            chunk(unitIndex = 1, text = "The first real paragraph stays."),
            chunk(unitIndex = 2, text = "Page 12"),
            chunk(unitIndex = 3, text = "XReader Sample Novel"),
            chunk(unitIndex = 4, text = "The second real paragraph stays too."),
            chunk(unitIndex = 5, text = "iv"),
            chunk(unitIndex = 6, text = "XReader Sample Novel"),
        )

        val prepared = NeuralTtsText.prepare(chunks)

        assertEquals(
            listOf("The first real paragraph stays.", "The second real paragraph stays too."),
            prepared.segments
        )
        assertEquals(11, prepared.wordCount)
    }

    @Test
    fun prepareDropsRepeatedLongPassagesThatWouldInflateAudiobookLength() {
        val repeated = "This paragraph is a full body passage that should only be spoken once even if an extractor indexes it repeatedly across sections."
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(unitIndex = 0, text = repeated),
                chunk(unitIndex = 1, text = "A unique chapter paragraph remains."),
                chunk(unitIndex = 2, text = repeated)
            )
        )

        assertEquals(
            listOf(repeated, "A unique chapter paragraph remains."),
            prepared.segments
        )
        assertEquals(27, prepared.wordCount)
    }

    @Test
    fun prepareRemovesUrlsEmailAddressesAndIsbnNoise() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(
                    text = "Read more at https://example.com or contact books@example.com. ISBN 978-1-23456-789-7. Actual sentence remains."
                )
            )
        )

        assertEquals(listOf("Read more at or contact. Actual sentence remains."), prepared.segments)
    }

    @Test
    fun prepareSplitsChapterTextIntoNaturalBoundedSegments() {
        val text = """
            Chapter 7

            “This is a quoted sentence,” she said. The room went quiet. Nobody moved for a long moment.

            This next paragraph has enough material to be grouped with adjacent sentences, but it should not become one enormous model prompt that makes Kokoro rush through the audiobook narration.
        """.trimIndent()

        val prepared = NeuralTtsText.prepare(listOf(chunk(text = text)))

        assertTrue(prepared.segments.size >= 2)
        assertTrue(prepared.segments.all { it.length <= 560 })
        assertTrue(prepared.segments.any { it.contains("\"This is a quoted sentence,\" she said.") })
        assertTrue(prepared.segments.any { it.contains("Kokoro rush through the audiobook narration.") })
    }

    @Test
    fun prepareSkipsPublisherFrontMatterBeforePrologue() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(unitIndex = 0, text = "THE LIONS OF LUCERNE"),
                chunk(unitIndex = 1, text = "This book is a work of fiction. Any resemblance to actual events is coincidental."),
                chunk(unitIndex = 2, text = "All rights reserved, including the right to reproduce this book."),
                chunk(unitIndex = 3, text = "Visit us on the World Wide Web:"),
                chunk(unitIndex = 4, text = "Prologue"),
                chunk(unitIndex = 5, text = "\"Senators,\" said Fawcett as he strode across the polished floor.")
            )
        )

        assertEquals(
            listOf(
                "Prologue",
                "\"Senators,\" said Fawcett as he strode across the polished floor."
            ),
            prepared.segments
        )
    }

    @Test
    fun prepareKeepsBookWithoutEarlyChapterMarker() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(unitIndex = 0, text = "A quiet opening paragraph remains available."),
                chunk(unitIndex = 1, text = "A second paragraph follows without a formal chapter heading.")
            )
        )

        assertEquals(
            listOf(
                "A quiet opening paragraph remains available.",
                "A second paragraph follows without a formal chapter heading."
            ),
            prepared.segments
        )
    }

    private fun chunk(unitIndex: Int = 0, text: String): ReadAloudChunk =
        ReadAloudChunk(
            unitIndex = unitIndex,
            locator = "locator-$unitIndex",
            heading = "Heading $unitIndex",
            text = text,
            wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
        )
}
