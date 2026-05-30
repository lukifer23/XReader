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
    fun keepsSelectedPhraseCandidateBeforeSingleWordFallbacks() {
        val candidates = DictionaryLemmatizer.candidates("“science fiction”")

        assertEquals("science fiction", candidates.first())
        assertTrue("phrase selection should still fall back to the first word", "science" in candidates)
    }

    @Test
    fun handlesHyphenatedSelections() {
        val candidates = DictionaryLemmatizer.candidates("well-being")

        assertEquals("well-being", candidates.first())
        assertTrue("hyphenated words should also try a space form", "well being" in candidates)
    }

    @Test
    fun fallsBackAcrossPhraseTokens() {
        val candidates = DictionaryLemmatizer.candidates("the children")

        assertTrue("later phrase tokens should still be usable", "child" in candidates)
        assertTrue("later progressive tokens should still be usable", "run" in DictionaryLemmatizer.candidates("the running"))
        assertTrue("later comparative tokens should still be usable", "big" in DictionaryLemmatizer.candidates("the bigger"))
    }

    @Test
    fun includesRegularPluralAndProgressiveCandidates() {
        assertTrue("books should include book", "book" in DictionaryLemmatizer.candidates("books"))
        assertTrue("running should include run", "run" in DictionaryLemmatizer.candidates("running"))
    }

    @Test
    fun includesComparativeAndSuperlativeCandidates() {
        assertTrue("happier should include happy", "happy" in DictionaryLemmatizer.candidates("happier"))
        assertTrue("happiest should include happy", "happy" in DictionaryLemmatizer.candidates("happiest"))
        assertTrue("larger should include large", "large" in DictionaryLemmatizer.candidates("larger"))
        assertTrue("fastest should include fast", "fast" in DictionaryLemmatizer.candidates("fastest"))
        assertTrue("bigger should include big", "big" in DictionaryLemmatizer.candidates("bigger"))
        assertTrue("thinnest should include thin", "thin" in DictionaryLemmatizer.candidates("thinnest"))
    }

    @Test
    fun includesAdverbCandidates() {
        assertTrue("quickly should include quick", "quick" in DictionaryLemmatizer.candidates("quickly"))
        assertTrue("happily should include happy", "happy" in DictionaryLemmatizer.candidates("happily"))
        assertTrue("dramatically should include dramatic", "dramatic" in DictionaryLemmatizer.candidates("dramatically"))
        assertTrue("simply should include simple", "simple" in DictionaryLemmatizer.candidates("simply"))
        assertTrue("fully should include full", "full" in DictionaryLemmatizer.candidates("fully"))
        assertTrue("truly should include true", "true" in DictionaryLemmatizer.candidates("truly"))
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
        assertTrue("better should include good", "good" in DictionaryLemmatizer.candidates("better"))
        assertTrue("worst should include bad", "bad" in DictionaryLemmatizer.candidates("worst"))
    }
}
