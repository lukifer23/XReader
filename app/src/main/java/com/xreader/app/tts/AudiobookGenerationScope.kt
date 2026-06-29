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

internal fun AudiobookGenerationScope.segmentLimit(
    totalSegments: Int,
    firstChapterSegmentCount: Int = 0,
): Int {
    val boundedTotal = totalSegments.coerceAtLeast(0)
    return when (this) {
        AudiobookGenerationScope.FULL_BOOK -> boundedTotal
        AudiobookGenerationScope.SAMPLE -> boundedTotal.coerceAtMost(maxSegments ?: boundedTotal)
        AudiobookGenerationScope.FIRST_CHAPTER -> {
            val detected = firstChapterSegmentCount.takeIf { it > 0 }
            val fallbackCap = maxSegments ?: boundedTotal
            (detected ?: boundedTotal.coerceAtMost(fallbackCap))
                .coerceAtMost(fallbackCap)
                .coerceAtMost(boundedTotal)
        }
    }.coerceAtLeast(0)
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
    return root.contiguousGeneratedAudiobookSegmentFiles(playableSegmentCount())
}

internal fun BookAudioEntity.expectedPlayableSegmentFiles(): List<File> {
    val root = filePath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?: return emptyList()
    val playableCount = playableSegmentCount()
    if (playableCount <= 0 || !root.isDirectory) return emptyList()
    return List(playableCount) { index -> File(root, generatedAudiobookSegmentFileName(index)) }
}

internal fun BookAudioEntity.verifiedGeneratedSegmentFiles(): List<File> {
    val root = filePath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?: return emptyList()
    return root.contiguousGeneratedAudiobookSegmentFiles(segmentCount)
}

internal fun BookAudioEntity.withVerifiedGeneratedProgress(): BookAudioEntity {
    val verified = verifiedGeneratedSegmentCount()
    val boundedVerified = verified.coerceIn(0, segmentCount.coerceAtLeast(0))
    if (boundedVerified == completedSegments) return this
    return copy(completedSegments = boundedVerified)
}

internal fun BookAudioEntity.verifiedPlayableSegmentCount(): Int =
    generatedAudiobookDirectory()?.countContiguousGeneratedAudiobookSegments(playableSegmentCount()) ?: 0

internal fun BookAudioEntity.verifiedGeneratedSegmentCount(): Int =
    generatedAudiobookDirectory()?.countContiguousGeneratedAudiobookSegments(segmentCount) ?: 0

internal fun BookAudioEntity.hasCompletePlayableAudiobook(): Boolean =
    status == BookAudioStatus.GENERATED &&
        segmentCount > 0 &&
        verifiedPlayableSegmentCount() == segmentCount

internal fun BookAudioEntity.withPlaybackBoundedToGeneratedAudio(playableSegments: Int): BookAudioEntity {
    val position = generatedAudiobookPersistedPlaybackPosition(
        requestedSegmentIndex = playbackSegmentIndex,
        positionMs = playbackPositionMs,
        segmentCount = playableSegments
    )
    return copy(
        playbackSegmentIndex = position.segmentIndex,
        playbackPositionMs = position.positionMs
    )
}

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

data class GeneratedAudiobookSegmentMetadata(
    val chapterIndexes: List<Int>,
    val pauseAfterMillis: List<Long>,
    val exportTsv: String,
)

data class GeneratedAudiobookPlaybackMetadata(
    val chapterIndexes: List<Int>,
    val pauseAfterMillis: List<Long>,
)

data class GeneratedAudiobookFileSnapshot(
    val audio: BookAudioEntity,
    val playableSegmentFiles: List<File>,
    val knownPlayableSegmentCount: Int? = null,
    val chapters: List<GeneratedAudiobookChapter>,
) {
    val playableSegmentCount: Int get() = knownPlayableSegmentCount ?: playableSegmentFiles.size
}

internal fun BookAudioEntity.generatedAudiobookFileSnapshot(): GeneratedAudiobookFileSnapshot {
    val playableCount = playableSegmentCount()
    val chapters = if (status == BookAudioStatus.GENERATING) {
        fallbackGeneratedAudiobookChapters(playableCount)
    } else {
        generatedAudiobookChapters(playableCount)
            .ifEmpty { fallbackGeneratedAudiobookChapters(playableCount) }
    }
    return GeneratedAudiobookFileSnapshot(
        audio = this,
        playableSegmentFiles = emptyList(),
        knownPlayableSegmentCount = playableCount,
        chapters = chapters
    )
}

private fun BookAudioEntity.generatedAudiobookDirectory(): File? =
    filePath
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isDirectory }

internal fun BookAudioEntity.generatedAudiobookChapters(playableSegmentCount: Int? = null): List<GeneratedAudiobookChapter> {
    val root = filePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }
        ?: return emptyList()
    val file = File(root, "chapters.tsv")
    val playableCount = playableSegmentCount ?: playableSegmentCount()
    val fallback by lazy { fallbackGeneratedAudiobookChapters(playableCount) }
    if (!file.isFile) return fallback
    return runCatching {
        file.useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                line.generatedAudiobookChapterRow()
            }
                .filter { it.segmentCount > 0 && it.firstSegmentIndex >= 0 }
                .toList()
                .sanitizeGeneratedAudiobookChapters(playableCount)
                .ifEmpty { fallback }
        }
    }.getOrElse { fallback }
}

internal fun BookAudioEntity.fallbackGeneratedAudiobookChapters(playableCount: Int): List<GeneratedAudiobookChapter> {
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
): List<Int> =
    generatedAudiobookPlaybackMetadata(segmentCount = segmentCount, chapters = chapters).chapterIndexes

internal fun BookAudioEntity.generatedAudiobookPlaybackMetadata(
    segmentCount: Int,
    chapters: List<GeneratedAudiobookChapter>,
): GeneratedAudiobookPlaybackMetadata {
    val fallback by lazy { generatedAudiobookFallbackPlaybackMetadata(segmentCount, chapters) }
    if (segmentCount <= 0) return fallback
    val root = filePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }
        ?: return fallback
    val file = File(root, "segments.tsv")
    if (!file.isFile) return fallback
    return file.generatedAudiobookPlaybackMetadata(segmentCount = segmentCount, chapters = chapters)
}

internal fun File.generatedAudiobookPlaybackMetadata(
    segmentCount: Int,
    chapters: List<GeneratedAudiobookChapter>,
): GeneratedAudiobookPlaybackMetadata {
    val fallback by lazy { generatedAudiobookFallbackPlaybackMetadata(segmentCount, chapters) }
    if (segmentCount <= 0 || !isFile) return fallback
    return runCatching {
        parseGeneratedAudiobookSegmentSidecar(
            file = this,
            segmentCount = segmentCount,
            chapters = chapters,
            retainExportRows = false,
            fallbackPlaybackMetadata = fallback
        ).playbackMetadata
    }.getOrElse { fallback }
}

internal fun BookAudioEntity.generatedAudiobookSegmentMetadata(
    segmentCount: Int,
    chapters: List<GeneratedAudiobookChapter>,
): GeneratedAudiobookSegmentMetadata {
    val fallback by lazy { generatedAudiobookFallbackSegmentMetadata(segmentCount, chapters) }
    if (segmentCount <= 0) return fallback
    val root = filePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }
        ?: return fallback
    val file = File(root, "segments.tsv")
    if (!file.isFile) return fallback
    return file.generatedAudiobookSegmentMetadata(segmentCount = segmentCount, chapters = chapters)
}

internal fun File.generatedAudiobookSegmentMetadata(
    segmentCount: Int,
    chapters: List<GeneratedAudiobookChapter>,
): GeneratedAudiobookSegmentMetadata {
    val fallback by lazy { generatedAudiobookFallbackSegmentMetadata(segmentCount, chapters) }
    val fallbackPlayback by lazy { generatedAudiobookFallbackPlaybackMetadata(segmentCount, chapters) }
    if (segmentCount <= 0 || !isFile) return fallback
    return runCatching {
        val parsed = parseGeneratedAudiobookSegmentSidecar(
            file = this,
            segmentCount = segmentCount,
            chapters = chapters,
            retainExportRows = true,
            fallbackPlaybackMetadata = fallbackPlayback
        )
        GeneratedAudiobookSegmentMetadata(
            chapterIndexes = parsed.playbackMetadata.chapterIndexes,
            pauseAfterMillis = parsed.playbackMetadata.pauseAfterMillis,
            exportTsv = generatedAudiobookSegmentsTsvFromRows(
                segmentCount = segmentCount,
                chapterIndexes = parsed.playbackMetadata.chapterIndexes,
                rowsByIndex = parsed.rowsByIndex.orEmpty()
            )
        )
    }.getOrElse { fallback }
}

internal fun List<GeneratedAudiobookChapter>.toGeneratedAudiobookChaptersTsv(): String {
    val chapters = this
    return buildString {
        appendLine("index\tfirstSegment\tsegmentCount\ttitle")
        chapters.forEach { chapter ->
            appendGeneratedAudiobookChapterRow(chapter)
        }
    }
}

private fun String.generatedAudiobookChapterRow(): GeneratedAudiobookChapter? {
    val firstTab = indexOf('\t')
    if (firstTab <= 0) return null
    val secondTab = indexOf('\t', firstTab + 1)
    if (secondTab <= firstTab) return null
    val thirdTab = indexOf('\t', secondTab + 1)
    if (thirdTab <= secondTab) return null
    val index = substring(0, firstTab).toIntOrNull() ?: return null
    val firstSegment = substring(firstTab + 1, secondTab).toIntOrNull() ?: return null
    val count = substring(secondTab + 1, thirdTab).toIntOrNull() ?: return null
    val title = substring(thirdTab + 1).tsvUnescaped().takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}"
    return GeneratedAudiobookChapter(
        index = index,
        title = title,
        firstSegmentIndex = firstSegment,
        segmentCount = count
    )
}

private fun StringBuilder.appendGeneratedAudiobookChapterRow(chapter: GeneratedAudiobookChapter) {
    append(chapter.index)
    append('\t')
    append(chapter.firstSegmentIndex)
    append('\t')
    append(chapter.segmentCount)
    append('\t')
    appendLine(chapter.title.tsvEscaped())
}

internal fun generatedAudiobookFallbackSegmentsTsv(
    segmentCount: Int,
    chapterIndexes: List<Int> = List(segmentCount.coerceAtLeast(0)) { 0 },
): String =
    generatedAudiobookSegmentsTsvFromRows(
        segmentCount = segmentCount,
        chapterIndexes = chapterIndexes,
        rowsByIndex = emptyMap()
    )

private fun generatedAudiobookFallbackSegmentMetadata(
    segmentCount: Int,
    chapters: List<GeneratedAudiobookChapter>,
): GeneratedAudiobookSegmentMetadata {
    val fallback = generatedAudiobookFallbackPlaybackMetadata(segmentCount, chapters)
    return GeneratedAudiobookSegmentMetadata(
        chapterIndexes = fallback.chapterIndexes,
        pauseAfterMillis = fallback.pauseAfterMillis,
        exportTsv = generatedAudiobookFallbackSegmentsTsv(
            segmentCount = fallback.chapterIndexes.size,
            chapterIndexes = fallback.chapterIndexes
        )
    )
}

private fun generatedAudiobookFallbackPlaybackMetadata(
    segmentCount: Int,
    chapters: List<GeneratedAudiobookChapter>,
): GeneratedAudiobookPlaybackMetadata {
    val boundedCount = segmentCount.coerceAtLeast(0)
    val chapterIndexes = List(boundedCount) { index -> chapters.chapterForSegment(index)?.index ?: 0 }
    return GeneratedAudiobookPlaybackMetadata(
        chapterIndexes = chapterIndexes,
        pauseAfterMillis = List(boundedCount) { DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS }
    )
}

private data class GeneratedAudiobookSegmentSidecarParse(
    val playbackMetadata: GeneratedAudiobookPlaybackMetadata,
    val rowsByIndex: Map<Int, String>?,
)

private fun parseGeneratedAudiobookSegmentSidecar(
    file: File,
    segmentCount: Int,
    chapters: List<GeneratedAudiobookChapter>,
    retainExportRows: Boolean,
    fallbackPlaybackMetadata: GeneratedAudiobookPlaybackMetadata = generatedAudiobookFallbackPlaybackMetadata(segmentCount, chapters),
): GeneratedAudiobookSegmentSidecarParse {
    val validChapterIndexes = chapters.mapTo(mutableSetOf()) { it.index }
    val chapterIndexes = fallbackPlaybackMetadata.chapterIndexes.toMutableList()
    val pauses = fallbackPlaybackMetadata.pauseAfterMillis.toMutableList()
    val rowsByIndex = if (retainExportRows) mutableMapOf<Int, String>() else null
    file.useLines { lines ->
        lines.drop(1).forEach { line ->
            val row = line.segmentSidecarRowPrefix() ?: return@forEach
            if (row.index !in 0 until segmentCount) return@forEach
            if (row.chapterIndex != null && (validChapterIndexes.isEmpty() || row.chapterIndex in validChapterIndexes)) {
                chapterIndexes[row.index] = row.chapterIndex
            }
            row.pauseAfterMillis?.let { pause -> pauses[row.index] = pause }
            rowsByIndex?.put(row.index, line)
        }
    }
    return GeneratedAudiobookSegmentSidecarParse(
        playbackMetadata = GeneratedAudiobookPlaybackMetadata(
            chapterIndexes = chapterIndexes,
            pauseAfterMillis = pauses
        ),
        rowsByIndex = rowsByIndex
    )
}

private data class GeneratedAudiobookSegmentSidecarRowPrefix(
    val index: Int,
    val chapterIndex: Int?,
    val pauseAfterMillis: Long?,
)

private fun String.segmentSidecarRowPrefix(): GeneratedAudiobookSegmentSidecarRowPrefix? {
    val firstTab = indexOf('\t')
    if (firstTab <= 0) return null
    val secondTab = indexOf('\t', firstTab + 1)
    if (secondTab <= firstTab) return null
    val thirdTab = indexOf('\t', secondTab + 1).let { if (it == -1) length else it }
    val index = substring(0, firstTab).toIntOrNull() ?: return null
    val chapterIndex = substring(firstTab + 1, secondTab).toIntOrNull()
    val pauseAfterMillis = substring(secondTab + 1, thirdTab).toLongOrNull()
    return GeneratedAudiobookSegmentSidecarRowPrefix(
        index = index,
        chapterIndex = chapterIndex,
        pauseAfterMillis = pauseAfterMillis
    )
}

private fun generatedAudiobookSegmentsTsvFromRows(
    segmentCount: Int,
    chapterIndexes: List<Int>,
    rowsByIndex: Map<Int, String>,
): String =
    buildString {
        appendLine("index\tchapterIndex\tpauseAfterMs\ttext")
        repeat(segmentCount.coerceAtLeast(0)) { index ->
            rowsByIndex[index]?.let { row ->
                appendLine(row)
            } ?: appendGeneratedAudiobookFallbackSegmentRow(
                index = index,
                chapterIndex = chapterIndexes.getOrElse(index) { 0 }
            )
        }
    }

private fun StringBuilder.appendGeneratedAudiobookFallbackSegmentRow(
    index: Int,
    chapterIndex: Int,
) {
    append(index)
    append('\t')
    append(chapterIndex)
    append('\t')
    append(DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS)
    append('\t')
    appendLine()
}

internal fun File.generatedAudiobookExportSegmentsTsv(
    segmentCount: Int,
    chapterIndexes: List<Int> = List(segmentCount.coerceAtLeast(0)) { 0 },
): String {
    if (segmentCount <= 0) return generatedAudiobookFallbackSegmentsTsv(segmentCount = 0, chapterIndexes = emptyList())
    return generatedAudiobookSegmentMetadata(
        segmentCount = segmentCount,
        chapters = chapterIndexes.toFallbackGeneratedAudiobookChapters(segmentCount)
    ).exportTsv
}

private fun List<Int>.toFallbackGeneratedAudiobookChapters(segmentCount: Int): List<GeneratedAudiobookChapter> {
    if (segmentCount <= 0 || isEmpty()) return emptyList()
    val chapters = mutableListOf<GeneratedAudiobookChapter>()
    var start = 0
    var current = getOrElse(0) { 0 }
    for (index in 1 until segmentCount) {
        val chapter = getOrElse(index) { current }
        if (chapter != current) {
            chapters += GeneratedAudiobookChapter(current, "Chapter ${current + 1}", start, index - start)
            start = index
            current = chapter
        }
    }
    chapters += GeneratedAudiobookChapter(current, "Chapter ${current + 1}", start, segmentCount - start)
    return chapters
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
    verifyFiles: Boolean = true,
): BookAudioEntity? =
    playableAudiobooksForProfile(
        modelId = modelId,
        speakerId = speakerId,
        speed = speed,
        tone = tone,
        verifyFiles = verifyFiles
    ).firstOrNull()

internal fun Iterable<BookAudioEntity>.playableAudiobooksForProfile(
    modelId: String,
    speakerId: Int,
    speed: Float,
    tone: String,
    verifyFiles: Boolean = true,
): List<BookAudioEntity> =
    filter { audio ->
        audio.modelId == modelId &&
            audio.speakerId == speakerId &&
            abs(audio.speed - speed) < 0.001f &&
            audio.tone == tone &&
            if (verifyFiles) {
                audio.playableSegmentFiles().isNotEmpty()
            } else {
                audio.playableSegmentCount() > 0
            }
    }
        .sortedWith(
            compareByDescending<BookAudioEntity> { it.status == BookAudioStatus.GENERATED }
                .thenBy { it.audiobookScopeRank() }
                .thenByDescending { it.playableSegmentCount() }
                .thenByDescending { it.updatedAt }
        )

private fun BookAudioEntity.audiobookScopeRank(): Int =
    when (AudiobookGenerationScope.fromKey(scope)) {
        AudiobookGenerationScope.FULL_BOOK -> 0
        AudiobookGenerationScope.FIRST_CHAPTER -> 1
        AudiobookGenerationScope.SAMPLE -> 2
    }

internal fun File.contiguousGeneratedAudiobookSegmentFiles(expectedSegments: Int): List<File> {
    val result = scanContiguousGeneratedAudiobookSegments(expectedSegments, collectFiles = true)
    return result.files ?: emptyList()
}

internal fun File.countContiguousGeneratedAudiobookSegments(expectedSegments: Int): Int =
    scanContiguousGeneratedAudiobookSegments(expectedSegments, collectFiles = false).count

private data class ContiguousGeneratedAudiobookSegmentScan(
    val count: Int,
    val files: List<File>?,
)

private fun File.scanContiguousGeneratedAudiobookSegments(
    expectedSegments: Int,
    collectFiles: Boolean,
): ContiguousGeneratedAudiobookSegmentScan {
    if (expectedSegments <= 0 || !isDirectory) {
        return ContiguousGeneratedAudiobookSegmentScan(count = 0, files = if (collectFiles) emptyList() else null)
    }
    val files = if (collectFiles) ArrayList<File>(expectedSegments) else null
    repeat(expectedSegments) { index ->
        val file = File(this, generatedAudiobookSegmentFileName(index))
        if (!file.isFile || file.length() <= WAV_HEADER_BYTES) {
            return ContiguousGeneratedAudiobookSegmentScan(count = index, files = files)
        }
        files?.add(file)
    }
    return ContiguousGeneratedAudiobookSegmentScan(count = expectedSegments, files = files)
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
