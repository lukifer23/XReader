package com.xreader.app.dictionary

import java.util.Locale

object DictionaryLemmatizer {
    fun candidates(rawWord: String): List<String> {
        val word = wordToken(rawWord)
            .lowercase(Locale.US)
            .removeSuffix("'s")
            .removeSuffix("’s")
        if (word.isBlank()) return emptyList()

        return buildList {
            add(word)
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
        }.distinct()
    }

    private fun wordToken(rawWord: String): String =
        Regex("""[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)?""")
            .find(rawWord.trim())
            ?.value
            .orEmpty()

    private fun String.withoutDoubledTerminal(): String? {
        if (length < 2) return null
        return if (last() == this[length - 2]) dropLast(1) else null
    }

    private val irregular = mapOf(
        "ate" to "eat",
        "began" to "begin",
        "begun" to "begin",
        "bought" to "buy",
        "brought" to "bring",
        "came" to "come",
        "children" to "child",
        "did" to "do",
        "done" to "do",
        "feet" to "foot",
        "felt" to "feel",
        "found" to "find",
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
        "made" to "make",
        "men" to "man",
        "mice" to "mouse",
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
        "wrote" to "write",
        "written" to "write"
    )
}
