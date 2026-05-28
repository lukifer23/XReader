package com.xreader.app.core

import java.text.Normalizer
import java.util.Locale

object TextTools {
    private val wordRegex = Regex("""[\p{L}\p{N}]+(?:['-][\p{L}\p{N}]+)?""")

    fun normalizeForSearch(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.US)
            .replace(Regex("\\s+"), " ")
            .trim()

    fun wordCount(value: String): Int = wordRegex.findAll(value).count()

    fun words(value: String): List<String> =
        wordRegex.findAll(value).map { it.value }.toList()

    fun cleanTitle(value: String): String =
        value.trim()
            .replace(Regex("\\s+"), " ")
            .ifBlank { "Untitled" }

    fun sortTitle(value: String): String =
        cleanTitle(value)
            .replace(Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE), "")
            .lowercase(Locale.US)

    fun extension(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.US)

    fun snippetAround(body: String, query: String, radius: Int = 90): String {
        val normalizedBody = normalizeForSearch(body)
        val normalizedQuery = normalizeForSearch(query)
        val index = normalizedBody.indexOf(normalizedQuery).takeIf { it >= 0 } ?: 0
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + normalizedQuery.length + radius).coerceAtMost(body.length)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < body.length) "..." else ""
        return prefix + body.substring(start, end).replace(Regex("\\s+"), " ").trim() + suffix
    }
}
