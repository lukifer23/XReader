package com.xreader.app.tts

import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import java.io.File
import kotlin.math.abs

enum class AudiobookGenerationScope(
    val key: String,
    val label: String,
    val maxSegments: Int?,
) {
    FULL_BOOK(
        key = "FULL_BOOK",
        label = "Full book",
        maxSegments = null
    ),
    SAMPLE(
        key = "SAMPLE",
        label = "Sample",
        maxSegments = 12
    ),
    FIRST_CHAPTER(
        key = "FIRST_CHAPTER",
        label = "First chapter",
        maxSegments = 60
    );

    companion object {
        fun fromKey(value: String?): AudiobookGenerationScope =
            entries.firstOrNull { it.key == value } ?: FULL_BOOK
    }
}

internal fun BookAudioEntity.playableSegmentCount(): Int =
    when (status) {
        BookAudioStatus.GENERATED -> segmentCount
        BookAudioStatus.GENERATING,
        BookAudioStatus.CANCELED,
        BookAudioStatus.FAILED -> completedSegments
    }.coerceIn(0, segmentCount.coerceAtLeast(0))

internal fun BookAudioEntity.playableSegmentFiles(): List<File> {
    val root = filePath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?: return emptyList()
    if (!root.isDirectory) return emptyList()
    val playableCount = playableSegmentCount()
    if (playableCount <= 0) return emptyList()
    return (0 until playableCount).mapNotNull { index ->
        File(root, generatedAudiobookSegmentFileName(index))
            .takeIf { it.isFile && it.length() > WAV_HEADER_BYTES }
    }.takeContiguousPrefix()
}

internal fun BookAudioEntity.hasCompletePlayableAudiobook(): Boolean =
    status == BookAudioStatus.GENERATED &&
        segmentCount > 0 &&
        playableSegmentFiles().size == segmentCount

internal fun BookAudioEntity.canDeleteGeneratedAudiobook(): Boolean =
    status != BookAudioStatus.GENERATING

data class GeneratedAudiobookChapter(
    val index: Int,
    val title: String,
    val firstSegmentIndex: Int,
    val segmentCount: Int,
) {
    val lastSegmentIndex: Int get() = firstSegmentIndex + segmentCount - 1
}

internal fun BookAudioEntity.generatedAudiobookChapters(): List<GeneratedAudiobookChapter> {
    val root = filePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }
        ?: return emptyList()
    val file = File(root, "chapters.tsv")
    val playableCount = playableSegmentFiles().size
    if (!file.isFile) return fallbackGeneratedAudiobookChapters(playableCount)
    return runCatching {
        file.readLines()
            .drop(1)
            .mapNotNull { line ->
                val columns = line.split('\t')
                val index = columns.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val firstSegment = columns.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                val count = columns.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                val title = columns.getOrNull(3)?.tsvUnescaped()?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}"
                GeneratedAudiobookChapter(
                    index = index,
                    title = title,
                    firstSegmentIndex = firstSegment,
                    segmentCount = count
                )
            }
            .filter { it.segmentCount > 0 && it.firstSegmentIndex >= 0 }
            .sortedBy { it.firstSegmentIndex }
            .sanitizeGeneratedAudiobookChapters(playableCount)
            .ifEmpty { fallbackGeneratedAudiobookChapters(playableCount) }
    }.getOrDefault(fallbackGeneratedAudiobookChapters(playableCount))
}

private fun BookAudioEntity.fallbackGeneratedAudiobookChapters(playableCount: Int): List<GeneratedAudiobookChapter> {
    if (playableCount <= 0) return emptyList()
    val title = scopeLabel.takeIf { it.isNotBlank() } ?: AudiobookGenerationScope.fromKey(scope).label
    return listOf(
        GeneratedAudiobookChapter(
            index = 0,
            title = title,
            firstSegmentIndex = 0,
            segmentCount = playableCount
        )
    )
}

private fun List<GeneratedAudiobookChapter>.sanitizeGeneratedAudiobookChapters(segmentCount: Int): List<GeneratedAudiobookChapter> {
    if (segmentCount <= 0 || isEmpty()) return emptyList()
    val sanitized = mutableListOf<GeneratedAudiobookChapter>()
    sortedWith(compareBy<GeneratedAudiobookChapter> { it.firstSegmentIndex }.thenBy { it.index })
        .forEach { chapter ->
            if (chapter.firstSegmentIndex !in 0 until segmentCount) return@forEach
            val start = chapter.firstSegmentIndex
            val nextStart = (sanitized.lastOrNull()?.lastSegmentIndex ?: -1) + 1
            val boundedStart = start.coerceAtLeast(nextStart)
            if (boundedStart >= segmentCount) return@forEach
            val boundedEnd = chapter.lastSegmentIndex.coerceAtLeast(boundedStart).coerceAtMost(segmentCount - 1)
            val count = boundedEnd - boundedStart + 1
            if (count <= 0) return@forEach
            sanitized += chapter.copy(
                index = sanitized.size,
                firstSegmentIndex = boundedStart,
                segmentCount = count
            )
        }
    return sanitized
}

internal fun List<GeneratedAudiobookChapter>.chapterForSegment(segmentIndex: Int): GeneratedAudiobookChapter? =
    lastOrNull { chapter -> segmentIndex >= chapter.firstSegmentIndex }
        ?.takeIf { segmentIndex <= it.lastSegmentIndex }

internal fun BookAudioEntity.generatedAudiobookSegmentChapterIndexes(
    segmentCount: Int,
    chapters: List<GeneratedAudiobookChapter>,
): List<Int> {
    if (segmentCount <= 0) return emptyList()
    val fallback = List(segmentCount) { index -> chapters.chapterForSegment(index)?.index ?: 0 }
    val root = filePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }
        ?: return fallback
    val file = File(root, "segments.tsv")
    if (!file.isFile) return fallback
    val validChapterIndexes = chapters.mapTo(mutableSetOf()) { it.index }
    if (validChapterIndexes.isEmpty()) return fallback
    return runCatching {
        val parsed = fallback.toMutableList()
        file.readLines()
            .drop(1)
            .forEach { line ->
                val columns = line.split('\t')
                val index = columns.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val chapter = columns.getOrNull(1)?.toIntOrNull() ?: return@forEach
                if (index in parsed.indices && chapter in validChapterIndexes) {
                    parsed[index] = chapter
                }
            }
        parsed.toList()
    }.getOrDefault(fallback)
}

internal fun List<GeneratedAudiobookChapter>.toGeneratedAudiobookChaptersTsv(): String {
    val chapters = this
    return buildString {
        appendLine("index\tfirstSegment\tsegmentCount\ttitle")
        chapters.forEach { chapter ->
            appendLine(
                listOf(
                    chapter.index.toString(),
                    chapter.firstSegmentIndex.toString(),
                    chapter.segmentCount.toString(),
                    chapter.title.tsvEscaped()
                ).joinToString("\t")
            )
        }
    }
}

internal fun generatedAudiobookFallbackSegmentsTsv(
    segmentCount: Int,
    chapterIndexes: List<Int> = List(segmentCount.coerceAtLeast(0)) { 0 },
): String =
    buildString {
        appendLine("index\tchapterIndex\tpauseAfterMs\ttext")
        repeat(segmentCount.coerceAtLeast(0)) { index ->
            appendLine(
                listOf(
                    index.toString(),
                    chapterIndexes.getOrElse(index) { 0 }.toString(),
                    DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS.toString(),
                    ""
                ).joinToString("\t")
            )
        }
    }

internal fun List<GeneratedAudiobookChapter>.nextChapterStart(currentSegmentIndex: Int): Int? =
    firstOrNull { it.firstSegmentIndex > currentSegmentIndex }?.firstSegmentIndex

internal fun List<GeneratedAudiobookChapter>.previousChapterStart(currentSegmentIndex: Int): Int? {
    val currentChapter = chapterForSegment(currentSegmentIndex)
    return if (currentChapter != null && currentSegmentIndex > currentChapter.firstSegmentIndex) {
        currentChapter.firstSegmentIndex
    } else {
        lastOrNull { it.firstSegmentIndex < currentSegmentIndex }?.firstSegmentIndex
    }
}

internal fun Iterable<BookAudioEntity>.bestPlayableAudiobookForProfile(
    modelId: String,
    speakerId: Int,
    speed: Float,
    tone: String,
): BookAudioEntity? =
    filter { audio ->
        audio.modelId == modelId &&
            audio.speakerId == speakerId &&
            abs(audio.speed - speed) < 0.001f &&
            audio.tone == tone &&
            audio.playableSegmentFiles().isNotEmpty()
    }
        .sortedWith(
            compareByDescending<BookAudioEntity> { it.status == BookAudioStatus.GENERATED }
                .thenBy { it.audiobookScopeRank() }
                .thenByDescending { it.playableSegmentCount() }
                .thenByDescending { it.updatedAt }
        )
        .firstOrNull()

private fun BookAudioEntity.audiobookScopeRank(): Int =
    when (AudiobookGenerationScope.fromKey(scope)) {
        AudiobookGenerationScope.FULL_BOOK -> 0
        AudiobookGenerationScope.FIRST_CHAPTER -> 1
        AudiobookGenerationScope.SAMPLE -> 2
    }

private fun List<File>.takeContiguousPrefix(): List<File> {
    val contiguous = ArrayList<File>(size)
    forEachIndexed { index, file ->
        if (file.name != generatedAudiobookSegmentFileName(index)) return contiguous
        contiguous += file
    }
    return contiguous
}

internal const val WAV_HEADER_BYTES = 44L

internal fun String.tsvUnescaped(): String {
    val result = StringBuilder(length)
    var escaping = false
    forEach { char ->
        if (escaping) {
            result.append(
                when (char) {
                    't' -> '\t'
                    'n' -> '\n'
                    'r' -> '\r'
                    '\\' -> '\\'
                    else -> char
                }
            )
            escaping = false
        } else if (char == '\\') {
            escaping = true
        } else {
            result.append(char)
        }
    }
    if (escaping) result.append('\\')
    return result.toString()
}
