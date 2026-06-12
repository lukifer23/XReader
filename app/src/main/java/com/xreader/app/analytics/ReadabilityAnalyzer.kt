package com.xreader.app.analytics

import java.util.Locale

data class ReadabilityMetrics(
    val readingEase: Double,
    val gradeLevel: Double,
    val words: Int,
    val sentences: Int,
    val syllables: Int,
)

object ReadabilityAnalyzer {
    fun analyze(texts: Iterable<String>): ReadabilityMetrics? {
        var words = 0
        var sentences = 0
        var syllables = 0

        loop@ for (text in texts) {
            if (text.isBlank()) continue
            sentences += sentenceRegex.findAll(text).count()
            for (match in wordRegex.findAll(text)) {
                words += 1
                syllables += countSyllables(match.value)
                if (words >= MAX_ANALYZED_WORDS) break@loop
            }
        }

        if (words < MIN_ANALYZED_WORDS) return null
        val boundedSentences = sentences.coerceAtLeast(1)
        val wordsPerSentence = words.toDouble() / boundedSentences.toDouble()
        val syllablesPerWord = syllables.toDouble() / words.toDouble()
        val readingEase = 206.835 - (1.015 * wordsPerSentence) - (84.6 * syllablesPerWord)
        val gradeLevel = (0.39 * wordsPerSentence) + (11.8 * syllablesPerWord) - 15.59
        return ReadabilityMetrics(
            readingEase = readingEase.coerceIn(0.0, 100.0),
            gradeLevel = gradeLevel.coerceIn(0.0, 18.0),
            words = words,
            sentences = boundedSentences,
            syllables = syllables
        )
    }

    private fun countSyllables(raw: String): Int {
        val word = raw
            .lowercase(Locale.US)
            .replace(nonLettersRegex, "")
        if (word.isBlank()) return 1
        var count = 0
        var previousWasVowel = false
        word.forEach { char ->
            val isVowel = char in vowels
            if (isVowel && !previousWasVowel) count += 1
            previousWasVowel = isVowel
        }
        if (word.length > 2 && word.endsWith("e") && !word.endsWith("le") && count > 1) {
            count -= 1
        }
        return count.coerceAtLeast(1)
    }

    private const val MIN_ANALYZED_WORDS = 100
    private const val MAX_ANALYZED_WORDS = 80_000
    private val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')
    private val wordRegex = Regex("[A-Za-z]+(?:['’-][A-Za-z]+)*")
    private val sentenceRegex = Regex("[.!?]+(?:\\s|$)")
    private val nonLettersRegex = Regex("[^a-z]")
}
