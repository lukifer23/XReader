package com.xreader.app.tts

import android.content.pm.ServiceInfo
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import com.xreader.app.settings.NeuralTtsTone
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookGenerationForegroundServiceTest {
    @Test
    fun startGateRejectsStartsWhileCanceling() {
        assertEquals(
            AudiobookGenerationStartGate.CANCELING,
            audiobookGenerationStartGate(canceling = true, jobActive = false)
        )
        assertEquals(
            AudiobookGenerationStartGate.CANCELING,
            audiobookGenerationStartGate(canceling = true, jobActive = true)
        )
        assertEquals(
            AudiobookGenerationStartGate.ALREADY_RUNNING,
            audiobookGenerationStartGate(canceling = false, jobActive = true)
        )
        assertEquals(
            AudiobookGenerationStartGate.START,
            audiobookGenerationStartGate(canceling = false, jobActive = false)
        )
    }

    @Test
    fun rejectedStartNotificationPolicyPreservesRunningProgress() {
        assertEquals(
            true,
            shouldReplaceAudiobookGenerationNotificationForRejectedStart(AudiobookGenerationStartGate.CANCELING)
        )
        assertEquals(
            false,
            shouldReplaceAudiobookGenerationNotificationForRejectedStart(AudiobookGenerationStartGate.ALREADY_RUNNING)
        )
        assertEquals(
            false,
            shouldReplaceAudiobookGenerationNotificationForRejectedStart(AudiobookGenerationStartGate.START)
        )
    }

    @Test
    fun serviceDestroyCancelsOnlyKnownActiveGenerationRows() {
        assertEquals(
            true,
            shouldCancelActiveAudiobookGenerationOnDestroy(jobActive = true, activeBookId = 42L)
        )
        assertEquals(
            false,
            shouldCancelActiveAudiobookGenerationOnDestroy(jobActive = false, activeBookId = 42L)
        )
        assertEquals(
            false,
            shouldCancelActiveAudiobookGenerationOnDestroy(jobActive = true, activeBookId = null)
        )
    }

    @Test
    fun setupFailureMessageUsesFirstUsefulLine() {
        assertEquals(
            "Could not index book text.",
            audiobookGenerationSetupFailureMessage(IllegalStateException("Could not index book text.\nDetails"))
        )
        assertEquals(
            "Audiobook setup failed.",
            audiobookGenerationSetupFailureMessage(IllegalStateException(""))
        )
    }

    @Test
    fun setupFailureMarkerOnlyOwnsPreGenerationSetupWindow() {
        assertTrue(
            shouldMarkAudiobookSetupFailure(
                error = IllegalStateException("Planning failed"),
                preparingRowCreated = true,
                repositoryGenerationStarted = false
            )
        )
        assertFalse(
            shouldMarkAudiobookSetupFailure(
                error = IllegalStateException("Book missing"),
                preparingRowCreated = false,
                repositoryGenerationStarted = false
            )
        )
        assertFalse(
            shouldMarkAudiobookSetupFailure(
                error = IllegalStateException("Provider failed"),
                preparingRowCreated = true,
                repositoryGenerationStarted = true
            )
        )
        assertFalse(
            shouldMarkAudiobookSetupFailure(
                error = CancellationException("Stopped"),
                preparingRowCreated = true,
                repositoryGenerationStarted = false
            )
        )
    }

    @Test
    fun foregroundServiceTypeUsesMediaProcessingOnAndroid15AndNewer() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            audiobookGenerationForegroundServiceType(34)
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            audiobookGenerationForegroundServiceType(35)
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            audiobookGenerationForegroundServiceType(36)
        )
    }

    @Test
    fun statusTextHandlesPreparingRunningAndCompletion() {
        assertEquals(
            "Preparing book text",
            audiobookGenerationStatusText(audio = null, preparing = true)
        )
        assertEquals(
            "Generating neural audiobook",
            audiobookGenerationStatusText(audio = audio(BookAudioStatus.GENERATING), preparing = false)
        )
        assertEquals(
            "Audiobook ready",
            audiobookGenerationStatusText(audio = audio(BookAudioStatus.GENERATED), preparing = false)
        )
        assertEquals(
            "Another audiobook is already generating",
            audiobookGenerationStatusText(audio = null, preparing = true, alreadyRunning = true)
        )
        assertEquals(
            "Stopping audiobook generation",
            audiobookGenerationStatusText(audio = null, preparing = true, canceling = true)
        )
    }

    @Test
    fun statusTextUsesFailureErrorWhenPresent() {
        assertEquals(
            "Model missing",
            audiobookGenerationStatusText(audio = audio(BookAudioStatus.FAILED, error = "Model missing"), preparing = false)
        )
        assertEquals(
            "Generation failed",
            audiobookGenerationStatusText(audio = audio(BookAudioStatus.FAILED), preparing = false)
        )
    }

    @Test
    fun progressTextFormatsBoundedSegments() {
        assertNull(audiobookGenerationProgressText(null))
        assertNull(audiobookGenerationProgressText(audio(BookAudioStatus.GENERATING, segmentCount = 0, completedSegments = 0)))
        assertEquals(
            "working on 4/10",
            audiobookGenerationProgressText(audio(BookAudioStatus.GENERATING, segmentCount = 10, completedSegments = 3))
        )
        assertEquals(
            "10/10 segments",
            audiobookGenerationProgressText(audio(BookAudioStatus.GENERATING, segmentCount = 10, completedSegments = 13))
        )
        assertEquals(
            "working on 5/10 • under 1m left",
            audiobookGenerationProgressText(
                audio(
                    status = BookAudioStatus.GENERATING,
                    segmentCount = 10,
                    completedSegments = 4,
                    generationStartedAt = 1_000L,
                    generationSessionStartCompletedSegments = 0
                ),
                nowMillis = 21_000L
            )
        )
    }

    @Test
    fun generationNotificationKeyIgnoresHeartbeatWhenVisibleTextIsStable() {
        val base = audio(
            status = BookAudioStatus.GENERATING,
            segmentCount = 10,
            completedSegments = 3,
            generationStartedAt = 1_000L,
            generationSessionStartCompletedSegments = 3,
            updatedAt = 11_000L
        )

        assertEquals(
            generationNotificationKey(base),
            generationNotificationKey(base.copy(updatedAt = 25_000L))
        )
    }

    @Test
    fun generationNotificationKeyIgnoresEtaOnlyAging() {
        val base = audio(
            status = BookAudioStatus.GENERATING,
            segmentCount = 100,
            completedSegments = 10,
            generationStartedAt = 1_000L,
            generationSessionStartCompletedSegments = 0,
            updatedAt = 61_000L
        )

        assertEquals(
            generationNotificationKey(base),
            generationNotificationKey(base.copy(updatedAt = 601_000L))
        )
    }

    @Test
    fun generationNotificationKeyTracksActualSegmentProgress() {
        val base = audio(
            status = BookAudioStatus.GENERATING,
            segmentCount = 100,
            completedSegments = 10,
            generationStartedAt = 1_000L,
            generationSessionStartCompletedSegments = 0,
            updatedAt = 61_000L
        )

        assertTrue(
            generationNotificationKey(base) !=
                generationNotificationKey(base.copy(completedSegments = 11, updatedAt = 90_000L))
        )
    }

    @Test
    fun etaUsesOnlySegmentsGeneratedInCurrentSession() {
        val audio = audio(
            status = BookAudioStatus.GENERATING,
            segmentCount = 10,
            completedSegments = 6,
            generationStartedAt = 1_000L,
            generationSessionStartCompletedSegments = 4
        )

        assertEquals("under 1m left", audio.generationEtaLabel(nowMillis = 11_000L))
    }

    @Test
    fun etaWaitsForNewProgressAfterResume() {
        val audio = audio(
            status = BookAudioStatus.GENERATING,
            segmentCount = 10,
            completedSegments = 4,
            generationStartedAt = 1_000L,
            generationSessionStartCompletedSegments = 4
        )

        assertNull(audio.generationEtaLabel(nowMillis = 20_000L))
    }

    @Test
    fun targetedCancelRequestParsesCompleteGenerationProfile() {
        val request = audiobookGenerationCancelRequest(
            bookId = 42L,
            modelId = "kokoro-v1",
            speakerId = 3,
            speed = 1.1f,
            toneName = NeuralTtsTone.WARM.name,
            scopeKey = AudiobookGenerationScope.FIRST_CHAPTER.key
        )

        assertEquals(
            AudiobookGenerationCancelRequest(
                bookId = 42L,
                modelId = "kokoro-v1",
                speakerId = 3,
                speed = 1.1f,
                tone = NeuralTtsTone.WARM,
                scope = AudiobookGenerationScope.FIRST_CHAPTER
            ),
            request
        )
    }

    @Test
    fun targetedCancelRequestRejectsPartialGenerationProfile() {
        assertNull(
            audiobookGenerationCancelRequest(
                bookId = 42L,
                modelId = "kokoro-v1",
                speakerId = null,
                speed = 1.0f,
                toneName = NeuralTtsTone.NATURAL.name,
                scopeKey = AudiobookGenerationScope.FULL_BOOK.key
            )
        )
    }

    @Test
    fun targetedCancelRequestRejectsMalformedGenerationProfile() {
        assertNull(
            audiobookGenerationCancelRequest(
                bookId = 42L,
                modelId = "kokoro-v1",
                speakerId = 3,
                speed = -1f,
                toneName = NeuralTtsTone.NATURAL.name,
                scopeKey = AudiobookGenerationScope.FULL_BOOK.key
            )
        )
        assertNull(
            audiobookGenerationCancelRequest(
                bookId = 42L,
                modelId = "kokoro-v1",
                speakerId = 3,
                speed = 1.0f,
                toneName = NeuralTtsTone.NATURAL.name,
                scopeKey = "BOOKISH"
            )
        )
    }

    @Test
    fun cancelPolicyIgnoresStaleAndMalformedTargetedRequestsOnly() {
        assertFalse(
            shouldIgnoreAudiobookGenerationCancel(
                hasCancelProfileExtras = false,
                requestMatchesActive = null
            )
        )
        assertFalse(
            shouldIgnoreAudiobookGenerationCancel(
                hasCancelProfileExtras = true,
                requestMatchesActive = true
            )
        )
        assertTrue(
            shouldIgnoreAudiobookGenerationCancel(
                hasCancelProfileExtras = true,
                requestMatchesActive = false
            )
        )
        assertTrue(
            shouldIgnoreAudiobookGenerationCancel(
                hasCancelProfileExtras = true,
                requestMatchesActive = null
            )
        )
    }

    private fun audio(
        status: BookAudioStatus,
        segmentCount: Int = 10,
        completedSegments: Int = 0,
        generationStartedAt: Long? = null,
        generationSessionStartCompletedSegments: Int = 0,
        error: String? = null,
        updatedAt: Long = 1L,
    ): BookAudioEntity =
        BookAudioEntity(
            bookId = 1,
            modelId = "voice",
            modelDisplayName = "Voice",
            speakerId = 0,
            speed = 1.0f,
            status = status,
            segmentCount = segmentCount,
            completedSegments = completedSegments,
            generationStartedAt = generationStartedAt,
            generationSessionStartCompletedSegments = generationSessionStartCompletedSegments,
            updatedAt = updatedAt,
            error = error
        )
}
