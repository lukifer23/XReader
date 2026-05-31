package com.xreader.app.repository

import com.xreader.app.core.TextTools

object SearchFtsQuery {
    fun build(query: String): String? {
        val normalized = TextTools.normalizeForSearch(query)
            .replace(POSSESSIVE_SUFFIX, " ")
        val rawTerms = SEARCH_TERM
            .findAll(normalized)
            .map { it.value }
            .filter { it.isNotBlank() }
            .toList()
        if (rawTerms.isEmpty()) return null

        val usefulTerms = rawTerms
            .filter { it.length > 1 || it.any(Char::isDigit) }
            .ifEmpty { rawTerms }
            .distinct()
            .take(MAX_SEARCH_TERMS)
        if (usefulTerms.isEmpty()) return null

        return usefulTerms.joinToString(" ") { term ->
            "normalizedBody:$term*"
        }
    }

    private val SEARCH_TERM = Regex("""[\p{L}\p{N}]+""")
    private val POSSESSIVE_SUFFIX = Regex("""['’]s\b""")
    private const val MAX_SEARCH_TERMS = 8
}
