package com.xreader.app.ui

import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import com.xreader.app.tts.AudiobookPlaybackUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookUiFormattersTest {
    @Test
    fun durationLabelsRoundUpShortAudio() {
        assertEquals("under 1m audio", audiobookDurationLabel(0))
        assertEquals("~1m audio", audiobookDurationLabel(1))
        assertEquals("~2m audio", audiobookDurationLabel(61_000))
    }

    @Test
    fun durationLabelsHandleHours() {
        assertEquals("~1h audio", audiobookDurationLabel(60 * 60_000L))
        assertEquals("~1h 2m audio", audiobookDurationLabel(61 * 60_000L + 1))
    }

    @Test
    fun estimatedDurationUsesVoicePace() {
        val standard = audiobookEstimatedDurationMillis(wordCount = 15_000, speed = 1.0f)
        val brisk = audiobookEstimatedDurationMillis(wordCount = 15_000, speed = 1.1f)

        assertEquals(100 * 60_000L, standard)
        assertTrue(brisk < standard)
    }

    @Test
    fun generatedAudioDurationLabelUsesWordCount() {
        val audio = BookAudioEntity(
            bookId = 7,
            modelId = "voice",
            modelDisplayName = "Voice",
            speakerId = 0,
            speed = 1.0f,
            status = BookAudioStatus.GENERATED,
            wordCount = 9_000,
            updatedAt = 1L
        )

        assertEquals("~1h audio", audio.estimatedDurationLabel())
    }

    @Test
    fun visibleGeneratedAudioKeepsCollapsedListShort() {
        val audio = (1L..6L).map(::audio)

        assertEquals(listOf(1L, 2L, 3L, 4L), visibleGeneratedAudiobooks(audio, selectedAudioId = null, expanded = false).map { it.id })
        assertEquals((1L..6L).toList(), visibleGeneratedAudiobooks(audio, selectedAudioId = null, expanded = true).map { it.id })
    }

    @Test
    fun visibleGeneratedAudioKeepsSelectedProfileVisibleWhenCollapsed() {
        val audio = (1L..6L).map(::audio)

        assertEquals(listOf(1L, 2L, 3L, 6L), visibleGeneratedAudiobooks(audio, selectedAudioId = 6L, expanded = false).map { it.id })
    }

    @Test
    fun playbackTimeLabelHandlesMissingAndKnownDuration() {
        assertNull(AudiobookPlaybackUiState().segmentTimeLabel())
        assertEquals(
            "0:12 / 1:05",
            AudiobookPlaybackUiState(segmentPositionMs = 12_300, segmentDurationMs = 65_000).segmentTimeLabel()
        )
        assertEquals(
            "1:01:01 / 1:02:00",
            AudiobookPlaybackUiState(segmentPositionMs = 3_661_000, segmentDurationMs = 3_720_000).segmentTimeLabel()
        )
    }

    private fun audio(id: Long): BookAudioEntity =
        BookAudioEntity(
            id = id,
            bookId = 7,
            modelId = "voice-$id",
            modelDisplayName = "Voice $id",
            speakerId = 0,
            speed = 1.0f,
            status = BookAudioStatus.GENERATED,
            updatedAt = id
        )
}
