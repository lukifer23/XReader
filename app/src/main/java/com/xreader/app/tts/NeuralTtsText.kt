package com.xreader.app.tts

internal data class NeuralTtsPreparedBook(
    val segments: List<String>,
    val wordCount: Int,
    val chapters: List<AudiobookChapter> = emptyList(),
    val segmentChapterIndexes: List<Int> = List(segments.size) { 0 },
    val segmentPauseMillis: List<Long> = List(segments.size) { DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS },
)

internal data class AudiobookChapter(
    val index: Int,
    val title: String,
    val firstSegmentIndex: Int,
    val segmentCount: Int,
)

internal fun NeuralTtsPreparedBook.forScope(scope: AudiobookGenerationScope): NeuralTtsPreparedBook {
    val limit = when (scope) {
        AudiobookGenerationScope.FULL_BOOK -> return this
        AudiobookGenerationScope.SAMPLE -> scope.maxSegments
        AudiobookGenerationScope.FIRST_CHAPTER -> firstChapterSegmentLimit(scope)
    } ?: return this
    val scopedSegments = segments.take(limit)
    val scopedChapterIndexes = segmentChapterIndexes.take(scopedSegments.size)
    val scopedPauses = segmentPauseMillis.take(scopedSegments.size)
    return copy(
        segments = scopedSegments,
        wordCount = scopedSegments.sumReadableWords(),
        chapters = chapters.trimToSegmentCount(scopedSegments.size),
        segmentChapterIndexes = scopedChapterIndexes.ifEmpty { List(scopedSegments.size) { 0 } },
        segmentPauseMillis = scopedPauses.ifEmpty { List(scopedSegments.size) { DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS } }
    )
}

private fun Iterable<String>.sumReadableWords(): Int =
    sumOf { segment -> segment.readableWordCount() }

private fun String.readableWordCount(): Int {
    var count = 0
    var inWord = false
    forEach { char ->
        if (char.isLetterOrDigit()) {
            if (!inWord) {
                count += 1
                inWord = true
            }
        } else {
            inWord = false
        }
    }
    return count
}

private fun Iterable<Int>.countByValue(): Map<Int, Int> {
    val counts = LinkedHashMap<Int, Int>()
    forEach { value -> counts[value] = (counts[value] ?: 0) + 1 }
    return counts
}

private fun NeuralTtsPreparedBook.firstChapterSegmentLimit(scope: AudiobookGenerationScope): Int {
    val detected = chapters.firstOrNull()?.segmentCount?.takeIf { it > 0 }
    val fallback = scope.maxSegments ?: segments.size
    return (detected ?: fallback).coerceAtMost(fallback).coerceAtMost(segments.size)
}

internal fun List<ReadAloudChunk>.forAudiobookScope(scope: AudiobookGenerationScope): List<ReadAloudChunk> {
    val ordered = sortedBy { it.unitIndex }
    return when (scope) {
        AudiobookGenerationScope.SAMPLE -> ordered.leadingSampleSourceChunks()
        AudiobookGenerationScope.FIRST_CHAPTER,
        AudiobookGenerationScope.FULL_BOOK -> ordered
    }
}

private fun List<ReadAloudChunk>.leadingSampleSourceChunks(): List<ReadAloudChunk> {
    if (size <= SAMPLE_SOURCE_MAX_CHUNKS) return this
    val result = mutableListOf<ReadAloudChunk>()
    var words = 0
    for (chunk in this) {
        result += chunk
        words += chunk.wordCount.coerceAtLeast(1)
        if (
            result.size >= SAMPLE_SOURCE_MIN_CHUNKS &&
            (words >= SAMPLE_SOURCE_WORD_TARGET || result.size >= SAMPLE_SOURCE_MAX_CHUNKS)
        ) {
            break
        }
    }
    return result.ifEmpty { take(1) }
}

internal object NeuralTtsText {
    fun prepare(chunks: List<ReadAloudChunk>): NeuralTtsPreparedBook {
        val orderedPassages = chunks
            .sortedBy { it.unitIndex }
            .flatMap { chunk -> chunk.toAudiobookPassages() }
            .dropDuplicatePassages()
            .dropRepeatedShortBoilerplate()
            .dropIsolatedPageMarkers()
            .dropTableOfContentsEntries()
            .filterNot { it.text.isPublisherBoilerplate() }
            .dropLeadingFrontMatter()
            .mapNotNull { passage -> passage.text.toNarrationUnit() }
        val segments = mutableListOf<String>()
        val segmentChapterIndexes = mutableListOf<Int>()
        val segmentPauseMillis = mutableListOf<Long>()
        val chapterBuilders = mutableListOf<ChapterBuilder>()
        var currentChapterIndex = -1
        fun ensureChapter(title: String, firstSegmentIndex: Int): Int {
            val cleanTitle = title.ifBlank { "Beginning" }
            val existing = chapterBuilders.lastOrNull()
            if (existing != null && existing.firstSegmentIndex == firstSegmentIndex) {
                existing.title = cleanTitle
                currentChapterIndex = existing.index
                return currentChapterIndex
            }
            val index = chapterBuilders.size
            chapterBuilders += ChapterBuilder(index = index, title = cleanTitle, firstSegmentIndex = firstSegmentIndex)
            currentChapterIndex = index
            return index
        }

        orderedPassages.forEach { unit ->
            if (unit.kind == NarrationUnitKind.CHAPTER_HEADING || unit.kind == NarrationUnitKind.PART_HEADING) {
                ensureChapter(unit.chapterTitle, segments.size)
            } else if (currentChapterIndex < 0) {
                ensureChapter("Beginning", segments.size)
            }
            val chapterIndex = currentChapterIndex.coerceAtLeast(0)
            val passageIsChapterBoundary = unit.kind == NarrationUnitKind.CHAPTER_HEADING ||
                unit.kind == NarrationUnitKind.PART_HEADING
            splitForAudiobook(unit.text, preferShortSegments = passageIsChapterBoundary)
                .map { it.copy(text = ReadAloudPlanner.cleanSpeechText(it.text)) }
                .filter { it.text.isNotBlank() }
                .forEach { segment ->
                    segments += segment.text
                    segmentChapterIndexes += chapterIndex
                    segmentPauseMillis += if (passageIsChapterBoundary) {
                        CHAPTER_HEADING_AUDIOBOOK_PAUSE_MS
                    } else {
                        segment.pauseAfterMillis
                    }
                    chapterBuilders.getOrNull(chapterIndex)?.segmentCount =
                        (chapterBuilders.getOrNull(chapterIndex)?.segmentCount ?: 0) + 1
                }
        }
        val coalesced = coalesceAdjacentAudiobookSegments(
            segments = segments,
            chapterIndexes = segmentChapterIndexes,
            pauseMillis = segmentPauseMillis
        )
        val words = coalesced.segments.sumReadableWords()
        val chapterSegmentCounts = coalesced.chapterIndexes.countByValue()
        return NeuralTtsPreparedBook(
            segments = coalesced.segments,
            wordCount = words,
            chapters = chapterBuilders
                .mapNotNull { it.toChapter(coalesced.chapterIndexes, chapterSegmentCounts) },
            segmentChapterIndexes = coalesced.chapterIndexes,
            segmentPauseMillis = coalesced.pauseMillis
        )
    }

    private fun ReadAloudChunk.toAudiobookPassages(): List<AudiobookPassage> {
        val body = normalizeForAudiobook(text)
        val heading = normalizeForAudiobookHeading(heading)
        val bodyPassages = body.map { AudiobookPassage(text = it, fromHeading = false) }
        if (heading == null) return bodyPassages
        val firstBody = body.firstOrNull()?.normalizedHeadingComparisonKey()
        return if (firstBody == heading.normalizedHeadingComparisonKey()) {
            bodyPassages
        } else {
            listOf(AudiobookPassage(text = heading, fromHeading = true)) + bodyPassages
        }
    }

    private fun normalizeForAudiobook(text: String): List<String> =
        text
            .replace("\u00AD", "")
            .replace("\uFB01", "fi")
            .replace("\uFB02", "fl")
            .replace(AUDIOBOOK_HYPHENATED_LINE_BREAK_REGEX, "")
            .replace(AUDIOBOOK_URL_REGEX, " ")
            .replace(AUDIOBOOK_EMAIL_REGEX, " ")
            .replace(AUDIOBOOK_ISBN_REGEX, " ")
            .replace(AUDIOBOOK_DOUBLE_QUOTE_REGEX, "\"")
            .replace(AUDIOBOOK_SINGLE_QUOTE_REGEX, "'")
            .replace(AUDIOBOOK_DASH_REGEX, " - ")
            .replace(AUDIOBOOK_ELLIPSIS_REGEX, "...")
            .split(AUDIOBOOK_PARAGRAPH_BREAK_REGEX)
            .map { paragraph ->
                ReadAloudPlanner.cleanSpeechText(paragraph)
                    .replace(AUDIOBOOK_SPACE_BEFORE_PUNCTUATION_REGEX, "$1")
                    .replace(AUDIOBOOK_DOUBLE_PERIOD_REGEX, ".")
                    .replace(AUDIOBOOK_REPEATED_QUESTION_EXCLAMATION_REGEX, "$1")
                    .replace(AUDIOBOOK_REPEATED_PUNCTUATION_REGEX, "$1")
            }
            .filter { it.isNotBlank() }

    private fun normalizeForAudiobookHeading(value: String): String? {
        val clean = ReadAloudPlanner.cleanSpeechText(value)
            .replace(AUDIOBOOK_WHITESPACE_REGEX, " ")
            .trim()
        if (clean.length !in 2..96) return null
        if (clean.startsWith("Position ", ignoreCase = true)) return null
        if (clean.isIsolatedPageMarker() && !clean.looksLikeAudiobookChapterHeading()) return null
        return clean.takeIf { it.looksLikeAudiobookChapterHeading() }
    }

    private fun String.normalizedHeadingComparisonKey(): String =
        lowercase()
            .replace(AUDIOBOOK_ALNUM_SPACE_ONLY_REGEX, " ")
            .replace(AUDIOBOOK_WHITESPACE_REGEX, " ")
            .trim()

    private fun splitForAudiobook(text: String, preferShortSegments: Boolean = false): List<AudiobookPreparedSegment> {
        val clean = ReadAloudPlanner.cleanSpeechText(text)
        if (clean.isBlank()) return emptyList()
        val targetChars = if (preferShortSegments) HEADING_TARGET_SEGMENT_CHARS else TARGET_SEGMENT_CHARS
        if (clean.length <= targetChars) {
            return listOf(AudiobookPreparedSegment(clean, clean.naturalPauseAfterMillis(paragraphEnd = true)))
        }

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
            } else if (current.length + 1 + sentence.length <= targetChars) {
                current.append(' ').append(sentence)
            } else {
                flushAudiobookSegment(current, result)
                current.append(sentence)
            }
        }
        flushAudiobookSegment(current, result)
        val merged = mergeShortAudiobookSegments(result, targetChars)
        return merged.mapIndexed { index, segment ->
            AudiobookPreparedSegment(
                text = segment,
                pauseAfterMillis = segment.naturalPauseAfterMillis(paragraphEnd = index == merged.lastIndex)
            )
        }
    }

    private fun String.chunkByClauseOrWords(): List<String> {
        val clauses = split(AUDIOBOOK_CLAUSE_BOUNDARY_REGEX)
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

    private fun mergeShortAudiobookSegments(segments: List<String>, targetChars: Int): List<String> {
        if (segments.size < 2) return segments
        val merged = mutableListOf<String>()
        segments.forEach { segment ->
            val previous = merged.lastOrNull()
            if (
                previous != null &&
                previous.length < MIN_SEGMENT_CHARS &&
                previous.length + 1 + segment.length <= targetChars
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

    private fun coalesceAdjacentAudiobookSegments(
        segments: List<String>,
        chapterIndexes: List<Int>,
        pauseMillis: List<Long>,
    ): CoalescedAudiobookSegments {
        if (segments.size < 2) {
            return CoalescedAudiobookSegments(segments, chapterIndexes, pauseMillis)
        }
        val mergedSegments = mutableListOf<String>()
        val mergedChapterIndexes = mutableListOf<Int>()
        val mergedPauses = mutableListOf<Long>()
        segments.forEachIndexed { index, segment ->
            val chapterIndex = chapterIndexes.getOrElse(index) { 0 }
            val pause = pauseMillis.getOrElse(index) { DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS }
            val previous = mergedSegments.lastOrNull()
            val previousChapter = mergedChapterIndexes.lastOrNull()
            val previousPause = mergedPauses.lastOrNull()
            val canMerge = previous != null &&
                previousChapter == chapterIndex &&
                previousPause != CHAPTER_HEADING_AUDIOBOOK_PAUSE_MS &&
                previousPause != QUESTION_OR_EXCLAMATION_AUDIOBOOK_PAUSE_MS &&
                previousPause != QUESTION_OR_EXCLAMATION_AUDIOBOOK_PAUSE_MS + 120L &&
                pause != CHAPTER_HEADING_AUDIOBOOK_PAUSE_MS &&
                previous.length + 1 + segment.length <= TARGET_SEGMENT_CHARS
            if (canMerge) {
                mergedSegments[mergedSegments.lastIndex] = "$previous $segment"
                mergedPauses[mergedPauses.lastIndex] = pause
            } else {
                mergedSegments += segment
                mergedChapterIndexes += chapterIndex
                mergedPauses += pause
            }
        }
        return CoalescedAudiobookSegments(
            segments = mergedSegments,
            chapterIndexes = mergedChapterIndexes,
            pauseMillis = mergedPauses
        )
    }

    private fun List<AudiobookPassage>.dropRepeatedShortBoilerplate(): List<AudiobookPassage> {
        val repeated = groupingBy { it.text.normalizedBoilerplateKey() }
            .eachCount()
            .filterKeys { it != null }
            .filterValues { it >= 3 }
            .keys
        if (repeated.isEmpty()) return this
        return filterNot { passage ->
            val key = passage.text.normalizedBoilerplateKey()
            key != null && key in repeated
        }
    }

    private fun List<AudiobookPassage>.dropDuplicatePassages(): List<AudiobookPassage> {
        val seen = mutableSetOf<String>()
        return filter { passage ->
            val key = passage.text.normalizedPassageKey()
            key == null || seen.add(key)
        }
    }

    private fun String.normalizedPassageKey(): String? {
        val clean = lowercase()
            .replace(AUDIOBOOK_ALNUM_SPACE_ONLY_REGEX, " ")
            .replace(AUDIOBOOK_WHITESPACE_REGEX, " ")
            .trim()
        return clean.takeIf { it.length >= 80 }
    }

    private fun String.normalizedBoilerplateKey(): String? {
        val clean = lowercase()
            .replace(AUDIOBOOK_ALNUM_SPACE_ONLY_REGEX, " ")
            .replace(AUDIOBOOK_WHITESPACE_REGEX, " ")
            .trim()
        val words = clean.split(' ').filter { it.isNotBlank() }
        return if (length <= 80 && words.size in 1..8) clean.takeIf { it.isNotBlank() } else null
    }

    private fun String.isIsolatedPageMarker(): Boolean {
        val clean = trim()
        if (clean.length > 24) return false
        return clean.matches(AUDIOBOOK_PAGE_MARKER_REGEX) ||
            clean.matches(AUDIOBOOK_ROMAN_MARKER_REGEX)
    }

    private fun List<AudiobookPassage>.dropIsolatedPageMarkers(): List<AudiobookPassage> =
        filterNot { passage ->
            passage.text.isIsolatedPageMarker() && !(passage.fromHeading && passage.text.looksLikeAudiobookChapterHeading())
        }

    private fun List<AudiobookPassage>.dropTableOfContentsEntries(): List<AudiobookPassage> =
        filterNot { passage -> passage.text.isTableOfContentsEntry() }

    private fun List<AudiobookPassage>.dropLeadingFrontMatter(): List<AudiobookPassage> {
        val firstContent = take(FRONT_MATTER_SCAN_LIMIT).indexOfFirst { passage ->
            passage.text.isNarrativeStartMarker() || (passage.fromHeading && passage.text.looksLikeAudiobookChapterHeading())
        }
        if (firstContent <= 0) return this
        return drop(firstContent)
    }

    private fun String.isNarrativeStartMarker(): Boolean {
        val clean = trim()
        if (clean.length > 80) return false
        return clean.matches(AUDIOBOOK_NARRATIVE_START_REGEX)
    }

    private fun String.isPublisherBoilerplate(): Boolean {
        val clean = lowercase()
            .replace(AUDIOBOOK_PUBLISHER_KEY_REGEX, " ")
            .replace(AUDIOBOOK_WHITESPACE_REGEX, " ")
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
        return clean.matches(AUDIOBOOK_PUBLISHER_ISBN_REGEX)
    }

    private fun String.isTableOfContentsEntry(): Boolean {
        val clean = trim()
        if (clean.length !in 5..140) return false
        if (!clean.any(Char::isLetter)) return false
        return clean.matches(AUDIOBOOK_TOC_LEADER_ENTRY_REGEX) || clean.matches(AUDIOBOOK_TOC_TRAILING_PAGE_ENTRY_REGEX)
    }

    private fun String.toNarrationUnit(): NarrationUnit? {
        val clean = ReadAloudPlanner.cleanSpeechText(this).trim()
        if (clean.isBlank()) return null
        val kind = clean.audiobookHeadingKind()
        val unitKind = when (kind) {
            AudiobookHeadingKind.CHAPTER -> NarrationUnitKind.CHAPTER_HEADING
            AudiobookHeadingKind.PART -> NarrationUnitKind.PART_HEADING
            null -> NarrationUnitKind.BODY
        }
        return NarrationUnit(
            text = clean,
            kind = unitKind,
            chapterTitle = if (unitKind == NarrationUnitKind.BODY) "Beginning" else clean.normalizedChapterTitle()
        )
    }

    private data class ChapterBuilder(
        val index: Int,
        var title: String,
        val firstSegmentIndex: Int,
        var segmentCount: Int = 0,
    ) {
        fun toChapter(coalescedChapterIndexes: List<Int>, chapterSegmentCounts: Map<Int, Int>): AudiobookChapter? {
            val first = coalescedChapterIndexes.indexOf(index).takeIf { it >= 0 } ?: return null
            val count = chapterSegmentCounts[index] ?: 0
            return AudiobookChapter(
                index = index,
                title = title,
                firstSegmentIndex = first,
                segmentCount = count
            ).takeIf { it.segmentCount > 0 }
        }
    }

    private data class CoalescedAudiobookSegments(
        val segments: List<String>,
        val chapterIndexes: List<Int>,
        val pauseMillis: List<Long>,
    )

    private data class AudiobookPreparedSegment(
        val text: String,
        val pauseAfterMillis: Long,
    )

    private data class AudiobookPassage(
        val text: String,
        val fromHeading: Boolean,
    )

    private data class NarrationUnit(
        val text: String,
        val kind: NarrationUnitKind,
        val chapterTitle: String,
    )

    private enum class NarrationUnitKind {
        BODY,
        CHAPTER_HEADING,
        PART_HEADING,
    }

    private const val MIN_SEGMENT_CHARS = 180
    private const val TARGET_SEGMENT_CHARS = 900
    private const val HEADING_TARGET_SEGMENT_CHARS = 220
    private const val MAX_SEGMENT_CHARS = 1400
    private const val FRONT_MATTER_SCAN_LIMIT = 48
    private val AUDIOBOOK_SENTENCE_BOUNDARY = Regex("(?<=[.!?][\"']?)\\s+")
    private val AUDIOBOOK_TOC_LEADER_ENTRY_REGEX = Regex(
        """(?i)^(chapter|section|episode|part|book)?\s*([0-9]{1,3}|[ivxlcdm]{1,8}|$AUDIOBOOK_WORD_NUMBER_PATTERN)(?:[\s.:\-]+.+?)?[\s.·•\-]{2,}[0-9]{1,4}$"""
    )
    private val AUDIOBOOK_TOC_TRAILING_PAGE_ENTRY_REGEX = Regex(
        """(?i)^(chapter|section|episode|part|book)\s+([0-9]{1,3}|[ivxlcdm]{1,8}|$AUDIOBOOK_WORD_NUMBER_PATTERN)(?:\s*[-:]\s+.+?)?\s+[0-9]{1,4}$"""
    )
}

private const val SAMPLE_SOURCE_MIN_CHUNKS = 24
private const val SAMPLE_SOURCE_MAX_CHUNKS = 96
private const val SAMPLE_SOURCE_WORD_TARGET = 8_000
internal const val DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS = 240L
internal const val SHORT_AUDIOBOOK_SEGMENT_PAUSE_MS = 160L
internal const val PARAGRAPH_AUDIOBOOK_PAUSE_MS = 420L
internal const val QUESTION_OR_EXCLAMATION_AUDIOBOOK_PAUSE_MS = 340L
internal const val CHAPTER_HEADING_AUDIOBOOK_PAUSE_MS = 760L

private fun String.naturalPauseAfterMillis(paragraphEnd: Boolean): Long {
    val trimmed = trim()
    if (trimmed.isBlank()) return DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS
    val last = trimmed.last()
    return when {
        last == '?' || last == '!' -> if (paragraphEnd) {
            QUESTION_OR_EXCLAMATION_AUDIOBOOK_PAUSE_MS + 120L
        } else {
            QUESTION_OR_EXCLAMATION_AUDIOBOOK_PAUSE_MS
        }
        last == ',' || last == ';' || last == ':' -> SHORT_AUDIOBOOK_SEGMENT_PAUSE_MS
        paragraphEnd -> PARAGRAPH_AUDIOBOOK_PAUSE_MS
        else -> DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS
    }
}

private fun String.normalizedAudiobookHeading(): String =
    trim()
        .lowercase()
        .replace(AUDIOBOOK_WHITESPACE_REGEX, " ")

private fun String.looksLikeAudiobookChapterHeading(): Boolean {
    return audiobookHeadingKind() != null
}

private enum class AudiobookHeadingKind {
    CHAPTER,
    PART,
}

private fun String.audiobookHeadingKind(): AudiobookHeadingKind? {
    val heading = normalizedAudiobookHeading()
    if (heading.isBlank()) return null
    if (heading.length > 96) return null
    if (heading.startsWith("position ")) return null
    if (AUDIOBOOK_PART_HEADING_REGEX.matches(heading)) return AudiobookHeadingKind.PART
    if (AUDIOBOOK_CHAPTER_HEADING_REGEX.matches(heading)) return AudiobookHeadingKind.CHAPTER
    return null
}

private fun String.normalizedChapterTitle(): String {
    val clean = trim().replace(AUDIOBOOK_WHITESPACE_REGEX, " ")
    val normalized = clean.normalizedAudiobookHeading()
    if (
        normalized.matches(AUDIOBOOK_NUMBER_HEADING_REGEX) ||
        normalized.matches(AUDIOBOOK_ROMAN_MARKER_REGEX) ||
        normalized.matches(AUDIOBOOK_WORD_NUMBER_REGEX)
    ) {
        return "Chapter ${clean.normalizedChapterTokenTitle()}"
    }
    if (clean.length > 2 && clean == clean.uppercase() && clean.any(Char::isLetter)) {
        return clean.lowercase().split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { char -> char.titlecase() }
        }
    }
    return clean
}

private fun String.normalizedChapterTokenTitle(): String {
    if (matches(AUDIOBOOK_ROMAN_MARKER_REGEX)) return uppercase()
    if (length > 2 && this == uppercase() && any(Char::isLetter)) {
        return lowercase().split(AUDIOBOOK_CHAPTER_TOKEN_SPLIT_REGEX).joinToString(" ") { word ->
            word.replaceFirstChar { char -> char.titlecase() }
        }
    }
    return this
}

private const val AUDIOBOOK_WORD_NUMBER_PATTERN =
    """(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|twenty[- ]one|twenty[- ]two|twenty[- ]three|twenty[- ]four|twenty[- ]five|twenty[- ]six|twenty[- ]seven|twenty[- ]eight|twenty[- ]nine|thirty)"""

private val AUDIOBOOK_HYPHENATED_LINE_BREAK_REGEX = Regex("(?m)-\\s*\\R\\s*")
private val AUDIOBOOK_URL_REGEX = Regex("https?://\\S+")
private val AUDIOBOOK_EMAIL_REGEX = Regex("\\b\\S+@\\S+\\.\\S+\\b")
private val AUDIOBOOK_ISBN_REGEX = Regex("\\bISBN(?:-1[03])?:?\\s*[0-9Xx -]{10,17}\\b")
private val AUDIOBOOK_DOUBLE_QUOTE_REGEX = Regex("[“”]")
private val AUDIOBOOK_SINGLE_QUOTE_REGEX = Regex("[‘’]")
private val AUDIOBOOK_DASH_REGEX = Regex("[–—]")
private val AUDIOBOOK_ELLIPSIS_REGEX = Regex("\\.{3,}")
private val AUDIOBOOK_PARAGRAPH_BREAK_REGEX = Regex("\\R{2,}|\\n\\s*\\n")
private val AUDIOBOOK_SPACE_BEFORE_PUNCTUATION_REGEX = Regex("\\s+([.,!?;:])")
private val AUDIOBOOK_DOUBLE_PERIOD_REGEX = Regex("\\.{2}(?!\\.)")
private val AUDIOBOOK_REPEATED_QUESTION_EXCLAMATION_REGEX = Regex("([!?]){2,}")
private val AUDIOBOOK_REPEATED_PUNCTUATION_REGEX = Regex("([,;:]){2,}")
private val AUDIOBOOK_WHITESPACE_REGEX = Regex("\\s+")
private val AUDIOBOOK_ALNUM_SPACE_ONLY_REGEX = Regex("[^a-z0-9 ]")
private val AUDIOBOOK_CLAUSE_BOUNDARY_REGEX = Regex("(?<=[,;:])\\s+")
private val AUDIOBOOK_PAGE_MARKER_REGEX = Regex("(?i)(page\\s+)?\\d{1,4}")
private val AUDIOBOOK_ROMAN_MARKER_REGEX = Regex("(?i)[ivxlcdm]{1,8}")
private val AUDIOBOOK_PUBLISHER_KEY_REGEX = Regex("[^a-z0-9&' ]")
private val AUDIOBOOK_PUBLISHER_ISBN_REGEX = Regex("""(?:isbn|ebook isbn|print isbn)\s+.*""")
private val AUDIOBOOK_NUMBER_HEADING_REGEX = Regex("""\d{1,3}""")
private val AUDIOBOOK_WORD_NUMBER_REGEX = Regex("""(?i)$AUDIOBOOK_WORD_NUMBER_PATTERN""")
private val AUDIOBOOK_CHAPTER_TOKEN_SPLIT_REGEX = Regex("[- ]")

private val AUDIOBOOK_CHAPTER_HEADING_REGEX =
    Regex("""(?i)^(prologue|epilogue|chapter\s+([0-9]+|[ivxlcdm]+|$AUDIOBOOK_WORD_NUMBER_PATTERN)(\b.*)?|section\s+([0-9]+|[ivxlcdm]+|$AUDIOBOOK_WORD_NUMBER_PATTERN)(\b.*)?|episode\s+([0-9]+|[ivxlcdm]+|$AUDIOBOOK_WORD_NUMBER_PATTERN)(\b.*)?|\d{1,3}|[ivxlcdm]{1,8}|$AUDIOBOOK_WORD_NUMBER_PATTERN)$""")

private val AUDIOBOOK_PART_HEADING_REGEX =
    Regex("""(?i)^(part|book)\s+([0-9]+|[ivxlcdm]+|$AUDIOBOOK_WORD_NUMBER_PATTERN)(\b.*)?$""")

private val AUDIOBOOK_NARRATIVE_START_REGEX =
    Regex("(?i)^(prologue|chapter\\s+([0-9]+|[ivxlcdm]+|$AUDIOBOOK_WORD_NUMBER_PATTERN)|book\\s+([0-9]+|[ivxlcdm]+|$AUDIOBOOK_WORD_NUMBER_PATTERN)|part\\s+([0-9]+|[ivxlcdm]+|$AUDIOBOOK_WORD_NUMBER_PATTERN)|$AUDIOBOOK_WORD_NUMBER_PATTERN)(\\b|\\s+.*)")

private fun List<AudiobookChapter>.trimToSegmentCount(segmentCount: Int): List<AudiobookChapter> {
    if (segmentCount <= 0) return emptyList()
    return mapNotNull { chapter ->
        if (chapter.firstSegmentIndex >= segmentCount) return@mapNotNull null
        val nextFirst = firstOrNull { it.firstSegmentIndex > chapter.firstSegmentIndex }?.firstSegmentIndex
            ?: segmentCount
        val boundedCount = (nextFirst.coerceAtMost(segmentCount) - chapter.firstSegmentIndex).coerceAtLeast(0)
        chapter.copy(segmentCount = boundedCount).takeIf { it.segmentCount > 0 }
    }
}
