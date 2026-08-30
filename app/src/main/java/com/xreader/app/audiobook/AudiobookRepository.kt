package com.xreader.app.audiobook

import com.xreader.app.data.AudiobookBookmarkEntity
import com.xreader.app.data.XReaderDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Clock

class AudiobookRepository(
    private val database: XReaderDatabase,
    private val importer: AudiobookImportService,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun observeImported(query: String = ""): Flow<List<ImportedAudiobookPackage>> =
        database.audiobooks().observeAudiobooks(query.trim()).map { rows ->
            rows.map { ImportedAudiobookPackage(it.audiobook, it.tracks.sortedBy { track -> track.trackIndex }, it.chapters.sortedBy { chapter -> chapter.chapterIndex }) }
        }

    suspend fun importUris(uris: List<android.net.Uri>): AudiobookImportService.ImportResult = importer.importUris(uris)

    suspend fun addBookmark(audiobookId: Long, trackIndex: Int, positionMs: Long, label: String, note: String): Long {
        require(positionMs >= 0L) { "Bookmark position cannot be negative." }
        val now = clock.millis()
        return database.audiobooks().insertBookmark(
            AudiobookBookmarkEntity(
                audiobookId = audiobookId,
                trackIndex = trackIndex.coerceAtLeast(0),
                positionMs = positionMs,
                label = label.trim().take(200),
                note = note.trim().take(10_000),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun updatePlayback(audiobookId: Long, trackIndex: Int, positionMs: Int): Boolean =
        database.audiobooks().updatePlayback(audiobookId, trackIndex.coerceAtLeast(0), positionMs.coerceAtLeast(0), clock.millis()) > 0

    suspend fun linkBook(audiobookId: Long, bookId: Long?): Boolean {
        if (bookId != null) requireNotNull(database.books().getBook(bookId)) { "Book no longer exists." }
        return database.audiobooks().linkBook(audiobookId, bookId, clock.millis()) > 0
    }

    suspend fun delete(audiobookId: Long): Boolean {
        val row = database.audiobooks().audiobook(audiobookId) ?: return false
        database.audiobooks().deleteAudiobook(audiobookId)
        File(row.audiobook.filePath).deleteRecursively()
        return true
    }
}
