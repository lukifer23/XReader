package com.xreader.app.tts

import android.content.pm.ServiceInfo
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun audio(
        status: BookAudioStatus,
        segmentCount: Int = 10,
        completedSegments: Int = 0,
        generationStartedAt: Long? = null,
        generationSessionStartCompletedSegments: Int = 0,
        error: String? = null,
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
            updatedAt = 1L,
            error = error
        )
}
