package com.xreader.app.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadabilityAnalyzerTest {
    @Test
    fun simpleTextProducesEasyReadingMetrics() {
        val text = List(30) {
            "The sun is warm. The path is clear. We walk home."
        }

        val metrics = requireNotNull(ReadabilityAnalyzer.analyze(text))

        assertTrue(metrics.readingEase >= 80.0)
        assertTrue(metrics.gradeLevel <= 5.0)
        assertEquals(330, metrics.words)
        assertEquals(90, metrics.sentences)
        assertTrue(metrics.syllables >= metrics.words)
    }

    @Test
    fun denseTextProducesHarderReadingMetrics() {
        val easy = requireNotNull(ReadabilityAnalyzer.analyze(List(30) {
            "The sun is warm. The path is clear. We walk home."
        }))
        val dense = requireNotNull(ReadabilityAnalyzer.analyze(List(18) {
            "Interdependent institutional architectures complicate interpretation because methodological assumptions accumulate across multidisciplinary frameworks."
        }))

        assertTrue(dense.readingEase < easy.readingEase)
        assertTrue(dense.gradeLevel > easy.gradeLevel)
    }

    @Test
    fun veryShortTextDoesNotProduceMisleadingMetrics() {
        assertNull(ReadabilityAnalyzer.analyze(listOf("Short sample. Too small.")))
    }

    @Test
    fun largeInputsAreBounded() {
        val metrics = requireNotNull(ReadabilityAnalyzer.analyze(List(20_000) {
            "The reader turns the page. "
        }))

        assertEquals(80_000, metrics.words)
        assertNotNull(metrics)
    }
}
