package com.xreader.app.dictionary

import java.util.Locale

object DictionaryLemmatizer {
    fun candidates(rawWord: String): List<String> {
        val tokens = wordTokens(rawWord)
        if (tokens.isEmpty()) return emptyList()

        return buildList {
            if (tokens.size > 1) {
                add(tokens.joinToString(" "))
                if (tokens.any { '-' in it }) {
                    add(tokens.joinToString(" ") { it.replace('-', ' ') }.replace(Regex("\\s+"), " "))
                }
            }
            tokens.firstOrNull()?.let { first ->
                addAll(singleWordCandidates(first))
            }
            tokens.drop(1).forEach { token ->
                addAll(singleWordCandidates(token).take(MAX_FALLBACK_CANDIDATES_PER_TOKEN))
            }
        }.distinct()
    }

    private fun singleWordCandidates(word: String): List<String> =
        buildList {
            add(word)
            if ('-' in word) add(word.replace('-', ' '))
            irregular[word]?.let(::add)
            if (word.endsWith("ies") && word.length > 3) add(word.dropLast(3) + "y")
            if (word.endsWith("ves") && word.length > 3) {
                val stem = word.dropLast(3)
                add(stem + "f")
                add(stem + "fe")
            }
            if (word.endsWith("es") && word.length > 2) add(word.dropLast(2))
            if (word.endsWith("s") && word.length > 1) add(word.dropLast(1))
            if (word.endsWith("ing") && word.length > 4) {
                val stem = word.dropLast(3)
                add(stem)
                add(stem + "e")
                stem.withoutDoubledTerminal()?.let(::add)
            }
            if (word.endsWith("ied") && word.length > 4) add(word.dropLast(3) + "y")
            if (word.endsWith("ed") && word.length > 3) {
                val stem = word.dropLast(2)
                add(stem)
                add(stem + "e")
                if (stem.endsWith("i") && stem.length > 1) add(stem.dropLast(1) + "y")
                stem.withoutDoubledTerminal()?.let(::add)
            }
            if (word.endsWith("ier") && word.length > 4) add(word.dropLast(3) + "y")
            if (word.endsWith("iest") && word.length > 5) add(word.dropLast(4) + "y")
            if (word.endsWith("er") && word.length > 4) {
                val stem = word.dropLast(2)
                add(stem)
                add(stem + "e")
                stem.withoutDoubledTerminal()?.let(::add)
            }
            if (word.endsWith("est") && word.length > 5) {
                val stem = word.dropLast(3)
                add(stem)
                add(stem + "e")
                stem.withoutDoubledTerminal()?.let(::add)
            }
            if (word.endsWith("ally") && word.length > 6) add(word.dropLast(4))
            if (word.endsWith("ly") && word.length > 4) {
                val stem = word.dropLast(2)
                add(stem)
                add(word.dropLast(1) + "e")
                if (stem.endsWith("i") && stem.length > 1) add(stem.dropLast(1) + "y")
                if (stem.endsWith("l") && stem.length > 2) add(stem + "l")
                add(stem + "e")
            }
        }

    private fun wordTokens(rawWord: String): List<String> =
        wordRegex
            .findAll(rawWord.trim())
            .map { match ->
                match.value
                    .lowercase(Locale.US)
                    .removeSuffix("'s")
                    .removeSuffix("’s")
                    .trim('-', '\'', '’')
            }
            .filter { it.isNotBlank() }
            .take(MAX_SELECTION_TOKENS)
            .toList()

    private fun String.withoutDoubledTerminal(): String? {
        if (length < 2) return null
        return if (last() == this[length - 2]) dropLast(1) else null
    }

    private val irregular = mapOf(
        "ate" to "eat",
        "began" to "begin",
        "begun" to "begin",
        "best" to "good",
        "better" to "good",
        "bought" to "buy",
        "brought" to "bring",
        "came" to "come",
        "children" to "child",
        "did" to "do",
        "done" to "do",
        "feet" to "foot",
        "felt" to "feel",
        "found" to "find",
        "farther" to "far",
        "farthest" to "far",
        "further" to "far",
        "furthest" to "far",
        "gave" to "give",
        "given" to "give",
        "geese" to "goose",
        "gone" to "go",
        "got" to "get",
        "held" to "hold",
        "kept" to "keep",
        "knew" to "know",
        "known" to "know",
        "left" to "leave",
        "least" to "little",
        "less" to "little",
        "made" to "make",
        "men" to "man",
        "mice" to "mouse",
        "more" to "much",
        "most" to "much",
        "paid" to "pay",
        "people" to "person",
        "ran" to "run",
        "said" to "say",
        "saw" to "see",
        "seen" to "see",
        "sent" to "send",
        "spoke" to "speak",
        "spoken" to "speak",
        "stood" to "stand",
        "taken" to "take",
        "teeth" to "tooth",
        "thought" to "think",
        "told" to "tell",
        "took" to "take",
        "went" to "go",
        "women" to "woman",
        "worse" to "bad",
        "worst" to "bad",
        "wrote" to "write",
        "written" to "write"
    )

    private val wordRegex = Regex("""[\p{L}\p{N}]+(?:[-'’][\p{L}\p{N}]+)*""")
    private const val MAX_SELECTION_TOKENS = 6
    private const val MAX_FALLBACK_CANDIDATES_PER_TOKEN = 5
}
