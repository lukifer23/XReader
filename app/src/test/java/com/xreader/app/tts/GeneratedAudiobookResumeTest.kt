package com.xreader.app.tts

import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeneratedAudiobookResumeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun segmentNamesAreOneBasedAndStable() {
        assertEquals("segment-00001.wav", generatedAudiobookSegmentFileName(0))
        assertEquals("segment-00012.wav", generatedAudiobookSegmentFileName(11))
    }

    @Test
    fun reusableSegmentsCountsOnlyConsecutiveAudioFiles() {
        val dir = temporaryFolder.newFolder()
        writeSegment(dir, index = 0)
        writeSegment(dir, index = 1)
        writeSegment(dir, index = 3)

        assertEquals(2, reusableGeneratedAudiobookSegments(dir, expectedSegments = 5))
    }

    @Test
    fun reusableSegmentsRejectsHeaderOnlyFiles() {
        val dir = temporaryFolder.newFolder()
        File(dir, generatedAudiobookSegmentFileName(0)).writeBytes(ByteArray(44))

        assertEquals(0, reusableGeneratedAudiobookSegments(dir, expectedSegments = 1))
    }

    @Test
    fun reusableSegmentsStopsAtExpectedTotal() {
        val dir = temporaryFolder.newFolder()
        writeSegment(dir, index = 0)
        writeSegment(dir, index = 1)
        writeSegment(dir, index = 2)

        assertEquals(2, reusableGeneratedAudiobookSegments(dir, expectedSegments = 2))
    }

    @Test
    fun deleteGeneratedAudiobookFilesRemovesAudioDirectory() {
        val dir = temporaryFolder.newFolder()
        writeSegment(dir, index = 0)
        val audio = audio(filePath = dir.absolutePath)

        assertTrue(deleteGeneratedAudiobookFiles(audio))
        assertFalse(dir.exists())
    }

    @Test
    fun deleteGeneratedAudiobookFilesIgnoresMissingPath() {
        assertFalse(deleteGeneratedAudiobookFiles(audio(filePath = null)))
        assertFalse(deleteGeneratedAudiobookFiles(audio(filePath = File(temporaryFolder.root, "missing").absolutePath)))
    }

    private fun writeSegment(dir: File, index: Int) {
        File(dir, generatedAudiobookSegmentFileName(index)).writeBytes(ByteArray(64) { 1 })
    }

    private fun audio(filePath: String?): BookAudioEntity =
        BookAudioEntity(
            bookId = 1,
            modelId = "voice",
            modelDisplayName = "Voice",
            speakerId = 0,
            speed = 1.0f,
            status = BookAudioStatus.GENERATED,
            filePath = filePath,
            updatedAt = 1L
        )
}
