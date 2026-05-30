package com.xreader.app.importer

import java.util.Locale

object PublicationMetadataTools {
    data class SeriesTitleInference(
        val series: String,
        val seriesIndex: Double?,
    )

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

    private val weakSeriesGenres = setOf(
        "action",
        "action & adventure",
        "action and adventure",
        "adventure",
        "book",
        "books",
        "children",
        "ebooks",
        "fiction",
        "general",
        "juvenile",
        "juvenile fiction",
        "literature",
        "literature & fiction",
        "literature and fiction",
        "military",
        "war",
        "young adult",
        "ya",
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

    fun canonicalAuthor(value: String?, existing: List<String>): String? =
        canonicalExistingValue(cleanMetadataValue(value), existing)

    fun canonicalGenre(value: String?, existing: List<String>): String? {
        val cleaned = cleanMetadataValue(value) ?: return null
        return cleanGenre(listOf(cleaned)) ?: canonicalExistingValue(cleaned, existing)
    }

    fun seriesGenreConsensus(values: List<String?>): String? {
        if (values.size < 2) return null
        val strongGenres = values
            .mapNotNull { canonicalGenre(it, emptyList()) }
            .filterNot { normalize(it) in weakSeriesGenres }
            .distinctBy(::normalize)
        return strongGenres.singleOrNull()
    }

    fun canonicalSeriesName(value: String?, existing: List<String>): String? =
        canonicalExistingValue(cleanSeriesName(value), existing)

    fun cleanSeriesName(value: String?): String? =
        cleanMetadataValue(value)?.takeIf { it.length <= 160 }

    fun inferSeriesFromTitle(title: String?, knownSeries: List<String>): SeriesTitleInference? {
        val cleanTitle = cleanMetadataValue(title) ?: return null
        val normalizedTitle = normalizeTitleForSeriesInference(cleanTitle)
        if (normalizedTitle.isBlank()) return null

        return knownSeries
            .asSequence()
            .mapNotNull(::cleanSeriesName)
            .distinctBy(::normalizeTitleForSeriesInference)
            .sortedByDescending { normalizeTitleForSeriesInference(it).length }
            .mapNotNull { series ->
                val normalizedSeries = normalizeTitleForSeriesInference(series)
                if (normalizedSeries.length < MIN_TITLE_SERIES_MATCH_LENGTH) return@mapNotNull null
                val range = phraseRange(normalizedTitle, normalizedSeries) ?: return@mapNotNull null
                val exactTitle = normalizedTitle == normalizedSeries
                SeriesTitleInference(
                    series = series,
                    seriesIndex = titleSeriesIndex(normalizedTitle, range)
                        ?: if (exactTitle) 1.0 else null
                )
            }
            .firstOrNull()
    }

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

    private fun cleanMetadataValue(value: String?): String? =
        value
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun canonicalExistingValue(value: String?, existing: List<String>): String? {
        val cleaned = value ?: return null
        val key = normalize(cleaned)
        return existing
            .mapNotNull(::cleanMetadataValue)
            .filter { normalize(it) == key }
            .maxWithOrNull(
                compareBy<String> { metadataDisplayScore(it) }
                    .thenBy { -it.length }
            )
            ?: cleaned
    }

    private fun metadataDisplayScore(value: String): Int {
        val letters = value.filter(Char::isLetter)
        val initialCaps = value
            .split(Regex("""[\s\-_/]+"""))
            .count { word ->
                word.firstOrNull()?.isUpperCase() == true &&
                    word.drop(1).any(Char::isLowerCase)
            }
        val uppercase = value.count(Char::isUpperCase)
        val lowercase = value.count(Char::isLowerCase)
        val hasMixedCase = uppercase > 0 && lowercase > 0
        val caseScore = when {
            letters.isNotEmpty() && letters.all(Char::isUpperCase) -> -40
            letters.isNotEmpty() && letters.all(Char::isLowerCase) -> -20
            hasMixedCase -> 20
            else -> 0
        }
        return initialCaps * 30 + caseScore + lowercase.coerceAtMost(12)
    }

    private fun phraseRange(text: String, phrase: String): IntRange? {
        var start = text.indexOf(phrase)
        while (start >= 0) {
            val endExclusive = start + phrase.length
            val startsAtBoundary = start == 0 || text[start - 1].isWhitespace()
            val endsAtBoundary = endExclusive == text.length || text[endExclusive].isWhitespace()
            if (startsAtBoundary && endsAtBoundary) return start until endExclusive
            start = text.indexOf(phrase, startIndex = start + 1)
        }
        return null
    }

    private fun titleSeriesIndex(normalizedTitle: String, seriesRange: IntRange): Double? {
        val after = normalizedTitle.substring(seriesRange.last + 1)
        parseLeadingSeriesIndex(after)?.let { return it }
        val before = normalizedTitle.substring(0, seriesRange.first)
        return parseTrailingSeriesIndex(before)
    }

    private fun parseLeadingSeriesIndex(value: String): Double? =
        leadingLabeledSeriesIndexRegex.find(value)?.groupValues?.getOrNull(1)?.toTitleSeriesIndex()
            ?: leadingBareSeriesIndexRegex.find(value)?.groupValues?.getOrNull(1)?.toTitleSeriesIndex()

    private fun parseTrailingSeriesIndex(value: String): Double? =
        trailingLabeledSeriesIndexRegex.find(value)?.groupValues?.getOrNull(1)?.toTitleSeriesIndex()

    private fun String.toTitleSeriesIndex(): Double? =
        (titleIndexWords[this] ?: toDoubleOrNull())
            ?.takeIf { it > 0.0 && it <= MAX_TITLE_SERIES_INDEX }

    private fun normalizeTitleForSeriesInference(value: String): String =
        value
            .trim()
            .lowercase(Locale.US)
            .replace("&", " and ")
            .replace(Regex("""[’']"""), "")
            .replace(Regex("""[^\p{L}\p{N}#]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.US).replace(Regex("""\s+"""), " ")

    private const val MIN_TITLE_SERIES_MATCH_LENGTH = 3
    private const val MAX_TITLE_SERIES_INDEX = 200.0
    private const val TITLE_INDEX_TOKEN = """([0-9]+(?:\.[0-9]+)?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"""
    private val leadingLabeledSeriesIndexRegex =
        Regex("""^\s*(?:(?:book|volume|vol|part|no|number)\s+|#\s*)$TITLE_INDEX_TOKEN\b""")
    private val leadingBareSeriesIndexRegex =
        Regex("""^\s*$TITLE_INDEX_TOKEN\b""")
    private val trailingLabeledSeriesIndexRegex =
        Regex("""(?:(?:book|volume|vol|part|no|number)\s+|#\s*)$TITLE_INDEX_TOKEN\s+(?:of|in)\s*$""")
    private val titleIndexWords = mapOf(
        "one" to 1.0,
        "two" to 2.0,
        "three" to 3.0,
        "four" to 4.0,
        "five" to 5.0,
        "six" to 6.0,
        "seven" to 7.0,
        "eight" to 8.0,
        "nine" to 9.0,
        "ten" to 10.0,
        "eleven" to 11.0,
        "twelve" to 12.0,
    )
}
