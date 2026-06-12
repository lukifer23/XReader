package com.xreader.app.tts

internal data class NeuralTtsPreparedBook(
    val segments: List<String>,
    val wordCount: Int,
)

internal object NeuralTtsText {
    fun prepare(chunks: List<ReadAloudChunk>): NeuralTtsPreparedBook {
        val orderedText = chunks
            .sortedBy { it.unitIndex }
            .flatMap { chunk -> normalizeForAudiobook(chunk.text) }
            .dropDuplicatePassages()
            .dropRepeatedShortBoilerplate()
            .filterNot { it.isIsolatedPageMarker() }
            .filterNot { it.isPublisherBoilerplate() }
            .dropLeadingFrontMatter()
        val segments = orderedText
            .flatMap { text -> splitForAudiobook(text) }
            .map { ReadAloudPlanner.cleanSpeechText(it) }
            .filter { it.isNotBlank() }
        val words = segments.sumOf { segment ->
            segment.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }
        }
        return NeuralTtsPreparedBook(
            segments = segments,
            wordCount = words
        )
    }

    private fun normalizeForAudiobook(text: String): List<String> =
        text
            .replace("\u00AD", "")
            .replace(Regex("(?m)-\\s*\\R\\s*"), "")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("\\b\\S+@\\S+\\.\\S+\\b"), " ")
            .replace(Regex("\\bISBN(?:-1[03])?:?\\s*[0-9Xx -]{10,17}\\b"), " ")
            .replace(Regex("[“”]"), "\"")
            .replace(Regex("[‘’]"), "'")
            .replace(Regex("[–—]"), " - ")
            .replace(Regex("\\.{3,}"), "...")
            .split(Regex("\\R{2,}|\\n\\s*\\n"))
            .map { paragraph ->
                ReadAloudPlanner.cleanSpeechText(paragraph)
                    .replace(Regex("\\s+([.,!?;:])"), "$1")
                    .replace(Regex("\\.{2}(?!\\.)"), ".")
                    .replace(Regex("([!?]){2,}"), "$1")
                    .replace(Regex("([,;:]){2,}"), "$1")
            }
            .filter { it.isNotBlank() }

    private fun splitForAudiobook(text: String): List<String> {
        val clean = ReadAloudPlanner.cleanSpeechText(text)
        if (clean.isBlank()) return emptyList()
        if (clean.length <= TARGET_SEGMENT_CHARS) return listOf(clean)

        val result = mutableListOf<String>()
        val sentences = clean.split(AUDIOBOOK_SENTENCE_BOUNDARY)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val current = StringBuilder()

        sentences.forEach { sentence ->
            if (sentence.length > MAX_SEGMENT_CHARS) {
                flushAudiobookSegment(current, result)
                result += sentence.chunkByClauseOrWords()
            } else if (current.isEmpty()) {
                current.append(sentence)
            } else if (current.length + 1 + sentence.length <= TARGET_SEGMENT_CHARS) {
                current.append(' ').append(sentence)
            } else {
                flushAudiobookSegment(current, result)
                current.append(sentence)
            }
        }
        flushAudiobookSegment(current, result)
        return mergeShortAudiobookSegments(result)
    }

    private fun String.chunkByClauseOrWords(): List<String> {
        val clauses = split(Regex("(?<=[,;:])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (clauses.size > 1) {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            clauses.forEach { clause ->
                if (current.isEmpty()) {
                    current.append(clause)
                } else if (current.length + 1 + clause.length <= MAX_SEGMENT_CHARS) {
                    current.append(' ').append(clause)
                } else {
                    flushAudiobookSegment(current, result)
                    current.append(clause)
                }
            }
            flushAudiobookSegment(current, result)
            if (result.all { it.length <= MAX_SEGMENT_CHARS }) return result
        }
        return ReadAloudPlanner.splitForSpeech(this, maxLength = MAX_SEGMENT_CHARS)
    }

    private fun mergeShortAudiobookSegments(segments: List<String>): List<String> {
        if (segments.size < 2) return segments
        val merged = mutableListOf<String>()
        segments.forEach { segment ->
            val previous = merged.lastOrNull()
            if (
                previous != null &&
                previous.length < MIN_SEGMENT_CHARS &&
                previous.length + 1 + segment.length <= TARGET_SEGMENT_CHARS
            ) {
                merged[merged.lastIndex] = "$previous $segment"
            } else {
                merged += segment
            }
        }
        return merged
    }

    private fun flushAudiobookSegment(builder: StringBuilder, result: MutableList<String>) {
        if (builder.isNotBlank()) {
            result += builder.toString()
            builder.clear()
        }
    }

    private fun List<String>.dropRepeatedShortBoilerplate(): List<String> {
        val repeated = groupingBy { it.normalizedBoilerplateKey() }
            .eachCount()
            .filterKeys { it != null }
            .filterValues { it >= 3 }
            .keys
        if (repeated.isEmpty()) return this
        return filterNot { text ->
            val key = text.normalizedBoilerplateKey()
            key != null && key in repeated
        }
    }

    private fun List<String>.dropDuplicatePassages(): List<String> {
        val seen = mutableSetOf<String>()
        return filter { text ->
            val key = text.normalizedPassageKey()
            key == null || seen.add(key)
        }
    }

    private fun String.normalizedPassageKey(): String? {
        val clean = lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return clean.takeIf { it.length >= 80 }
    }

    private fun String.normalizedBoilerplateKey(): String? {
        val clean = lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val words = clean.split(' ').filter { it.isNotBlank() }
        return if (length <= 80 && words.size in 1..8) clean.takeIf { it.isNotBlank() } else null
    }

    private fun String.isIsolatedPageMarker(): Boolean {
        val clean = trim()
        if (clean.length > 24) return false
        return clean.matches(Regex("(?i)(page\\s+)?\\d{1,4}")) ||
            clean.matches(Regex("(?i)[ivxlcdm]{1,8}"))
    }

    private fun List<String>.dropLeadingFrontMatter(): List<String> {
        val firstContent = take(FRONT_MATTER_SCAN_LIMIT).indexOfFirst { it.isNarrativeStartMarker() }
        if (firstContent <= 0) return this
        return drop(firstContent)
    }

    private fun String.isNarrativeStartMarker(): Boolean {
        val clean = trim()
        if (clean.length > 80) return false
        return clean.matches(Regex("(?i)^(prologue|chapter\\s+([0-9]+|[ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)|book\\s+([0-9]+|[ivxlcdm]+)|part\\s+([0-9]+|[ivxlcdm]+)).*"))
    }

    private fun String.isPublisherBoilerplate(): Boolean {
        val clean = lowercase()
            .replace(Regex("[^a-z0-9&' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isBlank()) return true
        val phrases = listOf(
            "all rights reserved",
            "work of fiction",
            "resemblance to actual events",
            "registered trademarks",
            "copyright",
            "published by",
            "printed in",
            "first printing",
            "cover design",
            "designed by",
            "visit us on",
            "world wide web",
            "for information address",
            "library of congress",
            "table of contents",
            "contents",
            "about the author",
            "also by",
        )
        if (phrases.any { it in clean }) return true
        return clean.matches(Regex("""(?:isbn|ebook isbn|print isbn)\s+.*"""))
    }

    private const val MIN_SEGMENT_CHARS = 90
    private const val TARGET_SEGMENT_CHARS = 360
    private const val MAX_SEGMENT_CHARS = 560
    private const val FRONT_MATTER_SCAN_LIMIT = 48
    private val AUDIOBOOK_SENTENCE_BOUNDARY = Regex("(?<=[.!?][\"']?)\\s+")
}
