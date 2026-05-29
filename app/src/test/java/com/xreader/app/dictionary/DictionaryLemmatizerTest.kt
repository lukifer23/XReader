package com.xreader.app.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryLemmatizerTest {
    @Test
    fun keepsExactCandidateFirst() {
        assertEquals("reader", DictionaryLemmatizer.candidates("reader").first())
    }

    @Test
    fun normalizesPossessiveAndSelectionPunctuation() {
        assertEquals("reader", DictionaryLemmatizer.candidates("“Reader’s”").first())
    }

    @Test
    fun includesRegularPluralAndProgressiveCandidates() {
        assertTrue("books should include book", "book" in DictionaryLemmatizer.candidates("books"))
        assertTrue("running should include run", "run" in DictionaryLemmatizer.candidates("running"))
    }

    @Test
    fun includesIesAndVesCandidates() {
        assertTrue("stories should include story", "story" in DictionaryLemmatizer.candidates("stories"))
        assertTrue("wolves should include wolf", "wolf" in DictionaryLemmatizer.candidates("wolves"))
        assertTrue("wolves should include wolfe", "wolfe" in DictionaryLemmatizer.candidates("wolves"))
    }

    @Test
    fun includesCommonIrregularForms() {
        assertTrue("children should include child", "child" in DictionaryLemmatizer.candidates("children"))
        assertTrue("went should include go", "go" in DictionaryLemmatizer.candidates("went"))
        assertTrue("feet should include foot", "foot" in DictionaryLemmatizer.candidates("feet"))
    }
}
