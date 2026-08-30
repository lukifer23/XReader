package com.xreader.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationPreparationTest {
    @Test
    fun exactRulesUseLongestMatchFirstAndRespectWordBoundaries() {
        val rules = listOf(
            NarrationPronunciationRule("St. John", "Saint John"),
            NarrationPronunciationRule("St.", "Saint"),
        )

        val result = applyExactPronunciationRules("St. John met St. Johns near St. Louis.", rules)

        assertEquals("Saint John met Saint Johns near Saint Louis.", result)
    }

    @Test
    fun unsupportedLanguageBlocksInsteadOfClaimingMultilingualQuality() {
        val error = runCatching {
            prepareNarrationReport(listOf(chunk("Bonjour tout le monde.")), "fr-FR")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("English narration only"))
    }

    @Test
    fun reportExplainsStructuralExclusionsAndAnIncludeOverrideRestoresThem() {
        val copyright = chunk("Copyright 2026 Example Press. All rights reserved.", unitIndex = 0)
        val chapter = chunk("The retained story begins here.", unitIndex = 1, heading = "Chapter 1")

        val automatic = prepareNarrationReport(listOf(copyright, chapter), "en")
        assertTrue(automatic.exclusions.any { it.sourceKey == narrationSourceKey(copyright) && it.reason.isNotBlank() })
        assertTrue(automatic.prepared.segments.none { it.contains("Copyright") })

        val restored = prepareNarrationReport(
            listOf(copyright, chapter),
            "en-US",
            overrides = listOf(NarrationTextOverride(narrationSourceKey(copyright), include = true)),
        )
        assertTrue(restored.prepared.segments.any { it.contains("Copyright") })
        assertTrue(restored.exclusions.none { it.sourceKey == narrationSourceKey(copyright) })
    }

    @Test
    fun explicitExclusionIsDurableAndEstimatedDurationIsDeterministic() {
        val first = chunk("The first passage stays.", unitIndex = 0)
        val second = chunk("The second passage is excluded.", unitIndex = 1)
        val override = NarrationTextOverride(narrationSourceKey(second), include = false)

        val one = prepareNarrationReport(listOf(first, second), null, overrides = listOf(override))
        val two = prepareNarrationReport(listOf(first, second), null, overrides = listOf(override))

        assertEquals(one.prepared, two.prepared)
        assertEquals(one.estimatedDurationMs, two.estimatedDurationMs)
        assertTrue(one.exclusions.any { it.reason.contains("book narration review") })
    }

    private fun chunk(text: String, unitIndex: Int = 0, heading: String = "Position ${unitIndex + 1}") =
        ReadAloudChunk(unitIndex, "locator-$unitIndex", heading, text, text.split(Regex("\\s+")).size)
}
