package com.xreader.app.audiobook

import com.xreader.app.data.AudiobookChapterEntity
import com.xreader.app.data.AudiobookTrackEntity
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.ImportedAudiobookEntity

sealed interface AudiobookSource {
    data class Imported(val audiobookId: Long) : AudiobookSource
    data class Generated(val bookAudioId: Long, val bookId: Long) : AudiobookSource
}

sealed interface AudiobookLocator {
    data class Imported(val trackIndex: Int, val positionMs: Long) : AudiobookLocator
    data class Generated(val segmentIndex: Int, val positionMs: Int) : AudiobookLocator
}

data class AudiobookItem(
    val source: AudiobookSource,
    val title: String,
    val author: String,
    val narrator: String?,
    val coverImagePath: String?,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val locator: AudiobookLocator,
    val playable: Boolean,
    val generating: Boolean,
    val failedMessage: String?,
)

data class ImportedAudiobookPackage(
    val audiobook: ImportedAudiobookEntity,
    val tracks: List<AudiobookTrackEntity>,
    val chapters: List<AudiobookChapterEntity>,
)

internal fun ImportedAudiobookPackage.toAudiobookItem(): AudiobookItem = AudiobookItem(
    source = AudiobookSource.Imported(audiobook.id),
    title = audiobook.title,
    author = audiobook.author,
    narrator = audiobook.narrator,
    coverImagePath = audiobook.coverImagePath,
    durationMs = audiobook.durationMs,
    fileSizeBytes = audiobook.fileSizeBytes,
    locator = AudiobookLocator.Imported(audiobook.playbackTrackIndex, audiobook.playbackPositionMs.toLong()),
    playable = tracks.isNotEmpty(),
    generating = false,
    failedMessage = null,
)

internal fun BookAudioEntity.toGeneratedAudiobookItem(
    title: String,
    author: String,
    coverImagePath: String?,
): AudiobookItem = AudiobookItem(
    source = AudiobookSource.Generated(id, bookId),
    title = title,
    author = author,
    narrator = modelDisplayName,
    coverImagePath = coverImagePath,
    durationMs = generationAudioMillis,
    fileSizeBytes = fileSizeBytes,
    locator = AudiobookLocator.Generated(playbackSegmentIndex, playbackPositionMs),
    playable = completedSegments > 0 && !filePath.isNullOrBlank(),
    generating = status.name == "GENERATING",
    failedMessage = error,
)
