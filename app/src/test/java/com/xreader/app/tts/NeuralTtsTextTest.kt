package com.xreader.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

        assertEquals("First position. Second position. Third position.", prepared.joinedSegments())
        assertEquals(6, prepared.wordCount)
    }

    @Test
    fun prepareSplitsLongPassagesWithoutDroppingTailText() {
        val words = (1..1_200).joinToString(" ") { "word$it" }
        val prepared = NeuralTtsText.prepare(listOf(chunk(text = words)))

        assertTrue(prepared.segments.size > 1)
        assertEquals(1_200, prepared.wordCount)
        assertTrue(prepared.segments.last().contains("word1200"))
        assertTrue(prepared.segments.all { it.length <= 2400 || !it.contains(' ') })
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

        val spokenText = prepared.joinedSegments()
        assertTrue(spokenText.contains("The first real paragraph stays."))
        assertTrue(spokenText.contains("The second real paragraph stays too."))
        assertTrue(!spokenText.contains("XReader Sample Novel"))
        assertTrue(!spokenText.contains("Page 12"))
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

        val spokenText = prepared.joinedSegments()
        assertTrue(spokenText.contains(repeated))
        assertTrue(spokenText.contains("A unique chapter paragraph remains."))
        assertEquals(1, prepared.segments.sumOf { Regex(Regex.escape(repeated)).findAll(it).count() })
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
        assertTrue(prepared.segments.all { it.length <= 1400 })
        assertTrue(prepared.segments.any { it.contains("\"This is a quoted sentence,\" she said.") })
        assertTrue(prepared.segments.any { it.contains("Kokoro rush through the audiobook narration.") })
    }

    @Test
    fun prepareCombinesShortSentencesIntoPracticalAudiobookSegments() {
        val sentences = (1..40).joinToString(" ") { index ->
            "Sentence $index keeps the narration flowing with enough detail for a natural generated audiobook."
        }

        val prepared = NeuralTtsText.prepare(listOf(chunk(text = sentences)))

        assertTrue("Expected far fewer generated prompts than sentences.", prepared.segments.size < 8)
        assertTrue(prepared.segments.all { it.length <= 1400 })
        assertEquals(560, prepared.wordCount)
    }

    @Test
    fun prepareUsesResponsiveBodySegmentsToKeepGenerationCancelable() {
        val sentences = (1..32).joinToString(" ") { index ->
            "The survey ship crossed the dark lane $index while the crew compared the quiet signal with the old maps."
        }

        val prepared = NeuralTtsText.prepare(listOf(chunk(heading = "Chapter 1", text = sentences)))

        assertTrue("Expected responsive prompts so slow generation reports progress sooner.", prepared.segments.size in 3..6)
        assertTrue(prepared.segments.all { it.length <= 1400 })
        assertEquals(listOf(0), prepared.chapters.map { it.firstSegmentIndex })
    }

    @Test
    fun prepareCoalescesTinyExtractorChunksIntoFewerGenerationPrompts() {
        val chunks = (1..100).map { index ->
            chunk(
                unitIndex = index,
                heading = "Chapter 1",
                text = "Line $index keeps the scene moving with useful narration."
            )
        }

        val prepared = NeuralTtsText.prepare(chunks)

        assertTrue("Expected far fewer prompts than extracted chunks.", prepared.segments.size < 25)
        assertTrue(prepared.segments.all { it.length <= 1400 })
        assertEquals(900, prepared.wordCount)
        assertTrue(prepared.joinedSegments().contains("Line 1 keeps the scene moving"))
        assertTrue(prepared.joinedSegments().contains("Line 100 keeps the scene moving"))
    }

    @Test
    fun prepareBuildsChapterMetadataAndSegmentChapterMap() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(unitIndex = 0, heading = "Chapter 1", text = "Opening scene. More opening scene."),
                chunk(unitIndex = 1, heading = "Chapter 2", text = "Second scene. More second scene.")
            )
        )

        assertEquals(listOf("Chapter 1", "Chapter 2"), prepared.chapters.map { it.title })
        assertEquals(listOf(0, 1), prepared.chapters.map { it.index })
        assertEquals(0, prepared.chapters[0].firstSegmentIndex)
        assertTrue(prepared.chapters[0].segmentCount > 0)
        assertEquals(prepared.segments.size, prepared.segmentChapterIndexes.size)
        assertTrue(prepared.segmentChapterIndexes.first() == 0)
        assertTrue(prepared.segmentChapterIndexes.last() == 1)
        assertEquals(prepared.segments.size, prepared.segmentPauseMillis.size)
    }

    @Test
    fun prepareNarratesChapterHeadingsWhenBodyDoesNotRepeatThem() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(
                    unitIndex = 0,
                    heading = "Chapter 7",
                    text = "The room went quiet. Nobody moved."
                )
            )
        )

        assertEquals(
            listOf("Chapter 7", "The room went quiet. Nobody moved."),
            prepared.segments
        )
        assertEquals(CHAPTER_HEADING_AUDIOBOOK_PAUSE_MS, prepared.segmentPauseMillis.first())
        assertEquals(PARAGRAPH_AUDIOBOOK_PAUSE_MS, prepared.segmentPauseMillis.last())
    }

    @Test
    fun prepareCarriesNaturalSegmentPauseMetadata() {
        val text = """
            Chapter 1

            Why now? Because this should pause like a question. Then the paragraph closes.

            Another paragraph follows.
        """.trimIndent()

        val prepared = NeuralTtsText.prepare(listOf(chunk(text = text)))

        assertEquals(prepared.segments.size, prepared.segmentPauseMillis.size)
        assertEquals(CHAPTER_HEADING_AUDIOBOOK_PAUSE_MS, prepared.segmentPauseMillis.first())
        assertTrue(prepared.segmentPauseMillis.any { it == PARAGRAPH_AUDIOBOOK_PAUSE_MS })
    }

    @Test
    fun prepareDoesNotDuplicateChapterHeadingAlreadyInBody() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(
                    unitIndex = 0,
                    heading = "Chapter 7",
                    text = "Chapter 7\n\nThe room went quiet."
                )
            )
        )

        assertEquals(
            listOf("Chapter 7", "The room went quiet."),
            prepared.segments
        )
    }

    @Test
    fun prepareIgnoresGenericExtractorHeadings() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(
                    unitIndex = 0,
                    heading = "Position 12",
                    text = "The actual paragraph should be the only spoken text."
                ),
                chunk(
                    unitIndex = 1,
                    heading = "Heading 1",
                    text = "A second paragraph remains."
                )
            )
        )

        assertEquals(
            "The actual paragraph should be the only spoken text. A second paragraph remains.",
            prepared.joinedSegments()
        )
    }

    @Test
    fun prepareNormalizesCommonLigatures() {
        val prepared = NeuralTtsText.prepare(
            listOf(chunk(text = "The ﬁrst ﬂare stayed readable."))
        )

        assertEquals(listOf("The first flare stayed readable."), prepared.segments)
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
    fun prepareDropsTableOfContentsEntryRows() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(unitIndex = 0, text = "Contents"),
                chunk(unitIndex = 1, text = "Chapter 1 ........ 7"),
                chunk(unitIndex = 2, text = "Chapter 2 - The Signal 19"),
                chunk(unitIndex = 3, text = "Prologue"),
                chunk(unitIndex = 4, text = "The actual story begins here.")
            )
        )

        assertEquals(
            listOf("Prologue", "The actual story begins here."),
            prepared.segments
        )
    }

    @Test
    fun prepareKeepsRealChapterHeadingsWithoutTocPageNumbers() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(unitIndex = 0, text = "Chapter 1"),
                chunk(unitIndex = 1, text = "The actual story begins here.")
            )
        )

        assertEquals(
            listOf("Chapter 1", "The actual story begins here."),
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
            "A quiet opening paragraph remains available. A second paragraph follows without a formal chapter heading.",
            prepared.joinedSegments()
        )
    }

    @Test
    fun sampleScopeKeepsBoundedLeadingSegmentsAndRecomputesWordCount() {
        val prepared = NeuralTtsPreparedBook(
            segments = (1..20).map { index -> "sample segment $index" },
            wordCount = 60
        ).forScope(AudiobookGenerationScope.SAMPLE)

        assertEquals(12, prepared.segments.size)
        assertEquals("sample segment 1", prepared.segments.first())
        assertEquals("sample segment 12", prepared.segments.last())
        assertEquals(36, prepared.wordCount)
        assertEquals(12, prepared.segmentPauseMillis.size)
    }

    @Test
    fun scopeSegmentLimitMatchesGenerationScopes() {
        assertEquals(
            12,
            AudiobookGenerationScope.SAMPLE.segmentLimit(totalSegments = 90)
        )
        assertEquals(
            8,
            AudiobookGenerationScope.FIRST_CHAPTER.segmentLimit(totalSegments = 90, firstChapterSegmentCount = 8)
        )
        assertEquals(
            60,
            AudiobookGenerationScope.FIRST_CHAPTER.segmentLimit(totalSegments = 90)
        )
        assertEquals(
            90,
            AudiobookGenerationScope.FULL_BOOK.segmentLimit(totalSegments = 90)
        )
    }

    @Test
    fun fullBookScopeKeepsPreparedBookUnchanged() {
        val prepared = NeuralTtsPreparedBook(
            segments = listOf("one two", "three four"),
            wordCount = 4
        )

        assertEquals(prepared, prepared.forScope(AudiobookGenerationScope.FULL_BOOK))
    }

    @Test
    fun firstChapterScopeFallsBackToCapWhenNoChapterMetadataExists() {
        val prepared = NeuralTtsPreparedBook(
            segments = (1..80).map { index -> "chapter segment $index" },
            wordCount = 240
        ).forScope(AudiobookGenerationScope.FIRST_CHAPTER)

        assertEquals(60, prepared.segments.size)
        assertEquals("chapter segment 60", prepared.segments.last())
        assertEquals(180, prepared.wordCount)
    }

    @Test
    fun firstChapterScopeUsesPreparedChapterSize() {
        val prepared = NeuralTtsPreparedBook(
            segments = (1..20).map { index -> "chapter segment $index" },
            wordCount = 60,
            chapters = listOf(
                AudiobookChapter(index = 0, title = "Chapter 1", firstSegmentIndex = 0, segmentCount = 3),
                AudiobookChapter(index = 1, title = "Chapter 2", firstSegmentIndex = 3, segmentCount = 17)
            ),
            segmentChapterIndexes = listOf(0, 0, 0) + List(17) { 1 },
            segmentPauseMillis = List(20) { DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS }
        ).forScope(AudiobookGenerationScope.FIRST_CHAPTER)

        assertEquals(
            listOf("chapter segment 1", "chapter segment 2", "chapter segment 3"),
            prepared.segments
        )
        assertEquals(listOf("Chapter 1"), prepared.chapters.map { it.title })
        assertEquals(9, prepared.wordCount)
    }

    @Test
    fun firstChapterScopeNoLongerPreFiltersSourceChunks() {
        val chunks = listOf(
            chunk(unitIndex = 0, heading = "Chapter One", text = "Opening scene."),
            chunk(unitIndex = 1, heading = "The Hill", text = "A subheading scene."),
            chunk(unitIndex = 2, heading = "7:14 P.M.", text = "A time break scene."),
            chunk(unitIndex = 3, heading = "Chapter Two", text = "Second chapter.")
        ).forAudiobookScope(AudiobookGenerationScope.FIRST_CHAPTER)

        assertEquals(
            listOf("Chapter One", "The Hill", "7:14 P.M.", "Chapter Two"),
            chunks.map { it.heading }
        )
    }

    @Test
    fun audiobookScopeKeepsAlreadyOrderedSourceChunksWithoutSorting() {
        val chunks = listOf(
            chunk(unitIndex = 0, text = "First."),
            chunk(unitIndex = 1, text = "Second."),
            chunk(unitIndex = 2, text = "Third.")
        )

        assertSame(chunks, chunks.forAudiobookScope(AudiobookGenerationScope.FULL_BOOK))
        assertSame(chunks, chunks.forAudiobookScope(AudiobookGenerationScope.FIRST_CHAPTER))
    }

    @Test
    fun audiobookScopeStillOrdersUnsortedSourceChunks() {
        val chunks = listOf(
            chunk(unitIndex = 2, text = "Third."),
            chunk(unitIndex = 0, text = "First."),
            chunk(unitIndex = 1, text = "Second.")
        )

        val ordered = chunks.forAudiobookScope(AudiobookGenerationScope.FULL_BOOK)

        assertEquals(listOf(0, 1, 2), ordered.map { it.unitIndex })
    }

    @Test
    fun sampleScopePreFiltersLargeSourceBooksBeforePreparation() {
        val chunks = (0 until 240).map { index ->
            chunk(
                unitIndex = index,
                heading = "Position ${index + 1}",
                text = "sample source chunk $index with enough words for counting"
            )
        }.forAudiobookScope(AudiobookGenerationScope.SAMPLE)

        assertEquals(96, chunks.size)
        assertEquals(0, chunks.first().unitIndex)
        assertEquals(95, chunks.last().unitIndex)
    }

    @Test
    fun sampleScopeKeepsAtLeastMinimumLeadingSourceChunksUntilWordTarget() {
        val chunks = (0 until 160).map { index ->
            chunk(
                unitIndex = index,
                heading = "Position ${index + 1}",
                text = (1..400).joinToString(" ") { word -> "w${index}_$word" }
            )
        }.forAudiobookScope(AudiobookGenerationScope.SAMPLE)

        assertEquals(24, chunks.size)
        assertEquals(23, chunks.last().unitIndex)
    }

    @Test
    fun fullBookScopeDoesNotPreFilterLargeSourceBooks() {
        val chunks = (0 until 140).map { index ->
            chunk(unitIndex = index, text = "full source chunk $index")
        }.forAudiobookScope(AudiobookGenerationScope.FULL_BOOK)

        assertEquals(140, chunks.size)
        assertEquals(139, chunks.last().unitIndex)
    }

    @Test
    fun wordNumberHeadingsBecomeChapters() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(unitIndex = 0, heading = "ONE", text = "Opening scene."),
                chunk(unitIndex = 1, heading = "TWO", text = "Second scene.")
            )
        )

        assertEquals(listOf("Chapter One", "Chapter Two"), prepared.chapters.map { it.title })
        assertEquals(listOf(0, 1), prepared.segmentChapterIndexes.distinct())
    }

    @Test
    fun numericAndRomanExtractorHeadingsSurvivePageMarkerCleanup() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(unitIndex = 0, heading = "IV", text = "The fourth chapter opens."),
                chunk(unitIndex = 1, heading = "12", text = "The twelfth chapter opens.")
            )
        )

        assertEquals(listOf("Chapter IV", "Chapter 12"), prepared.chapters.map { it.title })
        assertEquals(
            listOf("IV", "The fourth chapter opens.", "12", "The twelfth chapter opens."),
            prepared.segments
        )
    }

    @Test
    fun bodyPageMarkersStillDropEvenWhenTheyLookLikeChapterTokens() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(
                    unitIndex = 0,
                    heading = "Position 1",
                    text = "Opening paragraph remains.\n\n12\n\nSecond paragraph remains.\n\niv\n\nThird paragraph remains."
                )
            )
        )

        assertEquals(
            "Opening paragraph remains. Second paragraph remains. Third paragraph remains.",
            prepared.joinedSegments()
        )
    }

    @Test
    fun prepareDoesNotTreatShortBodyMentioningChapterAsHeading() {
        val prepared = NeuralTtsText.prepare(
            listOf(
                chunk(unitIndex = 0, heading = "Chapter 1", text = "Opening scene."),
                chunk(unitIndex = 1, heading = "Position 2", text = "This chapter matters because the signal is hidden.")
            )
        )

        assertEquals(listOf("Chapter 1"), prepared.chapters.map { it.title })
        assertEquals(listOf(0), prepared.segmentChapterIndexes.distinct())
        assertTrue(prepared.segments.any { it.contains("This chapter matters because the signal is hidden.") })
    }

    @Test
    fun prepareUsesLongerPauseForParagraphEndingQuestion() {
        val prepared = NeuralTtsText.prepare(
            listOf(chunk(text = "Chapter 1\n\nWhy did the signal stop?\n\nThe next paragraph starts after a breath."))
        )

        val questionIndex = prepared.segments.indexOf("Why did the signal stop?")
        assertTrue(questionIndex >= 0)
        assertEquals(QUESTION_OR_EXCLAMATION_AUDIOBOOK_PAUSE_MS + 120L, prepared.segmentPauseMillis[questionIndex])
    }

    private fun chunk(unitIndex: Int = 0, heading: String = "Heading $unitIndex", text: String): ReadAloudChunk =
        ReadAloudChunk(
            unitIndex = unitIndex,
            locator = "locator-$unitIndex",
            heading = heading,
            text = text,
            wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
        )

    private fun NeuralTtsPreparedBook.joinedSegments(): String = segments.joinToString(" ")
}
