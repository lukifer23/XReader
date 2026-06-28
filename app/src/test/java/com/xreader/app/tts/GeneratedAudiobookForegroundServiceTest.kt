package com.xreader.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneratedAudiobookForegroundServiceTest {
    @Test
    fun playbackNotificationKeyIgnoresPositionOnlyTicks() {
        val base = AudiobookPlaybackUiState(
            audioId = 7,
            bookTitle = "Book",
            profileLabel = "Kokoro",
            playing = true,
            segmentIndex = 2,
            segmentCount = 10,
            segmentPositionMs = 5_000,
            segmentDurationMs = 60_000
        )

        assertEquals(
            base.toPlaybackNotificationKey(),
            base.copy(segmentPositionMs = 10_000, segmentDurationMs = 61_000).toPlaybackNotificationKey()
        )
        assertNotEquals(
            base.toPlaybackNotificationKey(),
            base.copy(segmentIndex = 3).toPlaybackNotificationKey()
        )
    }

    @Test
    fun playbackNotificationStateComparisonIgnoresPositionOnlyTicks() {
        val base = AudiobookPlaybackUiState(
            audioId = 7,
            bookTitle = "Book",
            profileLabel = "Kokoro",
            playing = true,
            segmentIndex = 2,
            segmentCount = 10,
            segmentPositionMs = 5_000,
            segmentDurationMs = 60_000
        )

        assertEquals(true, samePlaybackNotificationState(base, base.copy(segmentPositionMs = 10_000, segmentDurationMs = 61_000)))
        assertEquals(false, samePlaybackNotificationState(base, base.copy(segmentIndex = 3)))
        assertEquals(false, samePlaybackNotificationState(base, base.copy(playing = false)))
    }

    @Test
    fun mediaSessionMetadataKeyIgnoresPositionOnlyTicks() {
        val base = AudiobookPlaybackUiState(
            audioId = 7,
            bookTitle = "Book",
            profileLabel = "Kokoro",
            playing = true,
            segmentIndex = 2,
            segmentCount = 10,
            segmentPositionMs = 5_000,
            segmentDurationMs = 60_000
        )

        assertEquals(
            base.generatedAudiobookMetadataKey(),
            base.copy(segmentPositionMs = 10_000).generatedAudiobookMetadataKey()
        )
        assertNotEquals(
            base.generatedAudiobookMetadataKey(),
            base.copy(segmentDurationMs = 61_000).generatedAudiobookMetadataKey()
        )
    }

    @Test
    fun mediaSessionMetadataMatcherAvoidsPositionOnlyTickKeys() {
        val base = AudiobookPlaybackUiState(
            audioId = 7,
            bookTitle = "Book",
            profileLabel = "Kokoro",
            playing = true,
            segmentIndex = 2,
            segmentCount = 10,
            segmentPositionMs = 5_000,
            segmentDurationMs = 60_000
        )
        val key = base.generatedAudiobookMetadataKey()

        assertEquals(true, base.copy(segmentPositionMs = 10_000).matchesGeneratedAudiobookMetadataKey(key))
        assertEquals(false, base.copy(segmentDurationMs = 61_000).matchesGeneratedAudiobookMetadataKey(key))
        assertEquals(false, base.matchesGeneratedAudiobookMetadataKey(null))
    }

    @Test
    fun mediaSessionPlaybackStateKeyIgnoresPositionOnlyTicks() {
        val base = AudiobookPlaybackUiState(
            audioId = 7,
            bookTitle = "Book",
            profileLabel = "Kokoro",
            playing = true,
            segmentIndex = 2,
            segmentCount = 10,
            segmentPositionMs = 5_000,
            segmentDurationMs = 60_000
        )

        assertEquals(
            base.generatedAudiobookPlaybackStateKey(),
            base.copy(segmentPositionMs = 10_000).generatedAudiobookPlaybackStateKey()
        )
        assertNotEquals(
            base.generatedAudiobookPlaybackStateKey(),
            base.copy(playing = false).generatedAudiobookPlaybackStateKey()
        )
        assertNotEquals(
            base.generatedAudiobookPlaybackStateKey(),
            base.copy(segmentIndex = 3).generatedAudiobookPlaybackStateKey()
        )
    }

    @Test
    fun mediaSessionPlaybackMatcherAvoidsPositionOnlyTickKeys() {
        val base = AudiobookPlaybackUiState(
            audioId = 7,
            bookTitle = "Book",
            profileLabel = "Kokoro",
            playing = true,
            segmentIndex = 2,
            segmentCount = 10,
            segmentPositionMs = 5_000,
            segmentDurationMs = 60_000
        )
        val key = base.generatedAudiobookPlaybackStateKey()

        assertEquals(true, base.copy(segmentPositionMs = 10_000).matchesGeneratedAudiobookPlaybackStateKey(key))
        assertEquals(false, base.copy(playing = false).matchesGeneratedAudiobookPlaybackStateKey(key))
        assertEquals(false, base.copy(segmentIndex = 3).matchesGeneratedAudiobookPlaybackStateKey(key))
        assertEquals(false, base.matchesGeneratedAudiobookPlaybackStateKey(null))
    }

    @Test
    fun playbackNotificationTextUsesVisiblePlaybackState() {
        assertEquals(
            "Kokoro",
            notificationStatusText(AudiobookPlaybackUiState(audioId = 1, profileLabel = "Kokoro", playing = true))
        )
        assertEquals(
            "Paused",
            notificationStatusText(AudiobookPlaybackUiState(audioId = 1, playing = false, preparing = false))
        )
        assertNull(notificationProgressText(AudiobookPlaybackUiState(segmentCount = 0)))
        assertEquals(
            "3/10",
            notificationProgressText(AudiobookPlaybackUiState(segmentIndex = 2, segmentCount = 10))
        )
    }
}
