package com.xreader.app.importer

import java.util.Locale

object PublicationMetadataTools {
    private data class GenreRule(
        val label: String,
        val tokens: Set<String>,
    )

    private val rules = listOf(
        GenreRule("Science Fiction", setOf("science fiction", "sci-fi", "sci fi", "space opera", "cyberpunk", "dystopian")),
        GenreRule("Fantasy", setOf("fantasy", "urban fantasy", "epic fantasy")),
        GenreRule("Mystery", setOf("mystery", "detective")),
        GenreRule("Thriller", setOf("thriller", "suspense")),
        GenreRule("Romance", setOf("romance")),
        GenreRule("Horror", setOf("horror")),
        GenreRule("Historical Fiction", setOf("historical fiction", "historical")),
        GenreRule("Adventure", setOf("adventure", "action & adventure", "action and adventure")),
        GenreRule("Biography", setOf("biography", "autobiography", "memoir")),
        GenreRule("History", setOf("history")),
        GenreRule("Young Adult", setOf("young adult", "juvenile fiction", "ya")),
        GenreRule("Children", setOf("children", "juvenile")),
        GenreRule("Literary Fiction", setOf("literary fiction")),
        GenreRule("Poetry", setOf("poetry")),
        GenreRule("Drama", setOf("drama", "plays")),
        GenreRule("Cooking", setOf("cooking", "cookbooks")),
        GenreRule("Nonfiction", setOf("nonfiction", "non-fiction")),
    )

    private val genericSubjects = setOf(
        "fiction",
        "general",
        "literature",
        "literature & fiction",
        "literature and fiction",
        "ebooks",
        "book",
        "books",
    )

    fun cleanGenre(subjects: List<String>): String? {
        val candidates = subjects
            .flatMap(::subjectCandidates)
            .distinctBy { it.lowercase(Locale.US) }
        val normalizedCandidates = candidates.map(::normalize)
        val normalizedSignals = normalize(subjects.joinToString(" "))
        val matched = rules.firstOrNull { rule ->
            normalizedCandidates.any { candidate -> rule.tokens.any(candidate::contains) }
        }?.label
        if (matched != null) return matched
        if (looksLikeDystopianScienceFiction(normalizedSignals, normalizedCandidates)) return "Science Fiction"
        return candidates.firstOrNull { normalize(it) in genericSubjects }
            ?.let { if (normalize(it).contains("fiction")) "Fiction" else it }
    }

    fun shouldReplaceGenre(existing: String?, parsed: String?): Boolean {
        val current = existing?.trim().orEmpty()
        val candidate = parsed?.trim().orEmpty()
        if (candidate.isBlank()) return false
        if (current.isBlank()) return true
        if (current.equals(candidate, ignoreCase = true)) return false
        val currentGenre = cleanGenre(listOf(current)) ?: return true
        if (normalize(current) in genericSubjects) return true
        val currentPriority = genrePriority(currentGenre)
        val candidatePriority = genrePriority(candidate)
        return current.equals(currentGenre, ignoreCase = true) &&
            currentPriority != null &&
            candidatePriority != null &&
            candidatePriority < currentPriority
    }

    fun cleanSeriesName(value: String?): String? =
        value?.trim()?.takeIf { it.isNotBlank() && it.length <= 160 }

    fun seriesFromDescription(value: String?): String? {
        val text = value
            ?.replace(Regex("""<[^>]+>"""), " ")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            .orEmpty()
        val match = Regex("""([A-Z][A-Za-z0-9'’&:-]+(?:\s+[A-Z][A-Za-z0-9'’&:-]+){0,5})\s+(?:Trilogy|Saga|Series|Cycle|Chronicles)\b""")
            .find(text)
            ?: return null
        return cleanSeriesName(match.groupValues[1])
    }

    fun parseSeriesIndex(value: String?): Double? =
        value
            ?.trim()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.takeIf { it >= 0.0 && it < 10_000.0 }

    private fun genrePriority(label: String): Int? {
        val normalized = normalize(label)
        return rules.indexOfFirst { normalize(it.label) == normalized }
            .takeIf { it >= 0 }
    }

    private fun looksLikeDystopianScienceFiction(text: String, candidates: List<String>): Boolean {
        val hasGenericFiction = candidates.any { it in genericSubjects || it.contains("fiction") }
        if (!hasGenericFiction) return false
        val hasFutureSetting = "future" in text || "generations ago" in text
        val hasSpeculativeSociety = listOf(
            "color-coded society",
            "ruling class",
            "caste",
            "humanity",
            "surface of the planet",
            "sprawling parks spread across the planet",
            "colonization",
            "galactic",
        ).any { it in text }
        return hasFutureSetting && hasSpeculativeSociety
    }

    private fun subjectCandidates(raw: String): List<String> {
        val withoutCode = raw.trim()
            .replace(Regex("""^[A-Z]{3}\d{6}\s+"""), "")
            .replace(Regex("""\s+"""), " ")
        return withoutCode
            .split(Regex("""\s*/\s*|\s*;\s*|\s*\|\s*"""))
            .map { it.trim() }
            .filter { it.length in 2..80 }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.US).replace(Regex("""\s+"""), " ")
}
