package com.xreader.app.ui

import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import com.xreader.app.data.BookAudioWithBook
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.NeuralTtsModelStatus
import com.xreader.app.tts.AudiobookGenerationScope
import com.xreader.app.tts.AudiobookGenerationHardwareReadiness
import com.xreader.app.tts.AudiobookPlaybackUiState
import com.xreader.app.tts.EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE
import com.xreader.app.tts.GeneratedAudiobookChapter
import com.xreader.app.tts.audiobookGenerationProgressLabel
import com.xreader.app.tts.playableSegmentCount
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    fun audiobookPerformanceLabelShowsBackendAndRealtimeFactor() {
        val audio = BookAudioEntity(
            bookId = 7,
            modelId = "voice",
            modelDisplayName = "Voice",
            speakerId = 0,
            speed = 1.0f,
            status = BookAudioStatus.GENERATING,
            generationProvider = "webgpu",
            generationAudioMillis = 20_000L,
            generationComputeMillis = 50_000L,
            updatedAt = 1L
        )

        assertEquals("WebGPU • 2.5x audio time", audio.audiobookPerformanceLabel())
        assertEquals("13x audio time", generationAudioTimeFactorLabel(audioMillis = 10_000L, computeMillis = 135_000L))
        assertNull(generationAudioTimeFactorLabel(audioMillis = 0L, computeMillis = 135_000L))
        assertEquals(
            "NPU",
            audio.copy(
                generationProvider = "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-provider.config",
                generationAudioMillis = 0L,
                generationComputeMillis = 0L
            ).audiobookPerformanceLabel()
        )
    }

    @Test
    fun scanStorageLabelExplainsLocalAudioAndFormatsLargeAudioAsGb() {
        assertEquals("~512 MB local audio", audiobookStorageLabel(512L * 1_048_576L))
        assertEquals("~2.3 GB local audio", audiobookStorageLabel(2_355L * 1_048_576L))
    }

    @Test
    fun scanSummaryIncludesDetectedChapterCount() {
        val scan = AudiobookScanUiState(
            wordCount = 30_000,
            segmentCount = 90,
            chapterCount = 12,
            chapterTitles = listOf("Chapter 1", "Chapter 2"),
            sourceSectionCount = 120
        )

        assertEquals("120 source sections prepared • 12 chapters detected", audiobookScanSummary(scan))
    }

    @Test
    fun scanSummaryUsesSingularChapterLabel() {
        val scan = AudiobookScanUiState(
            wordCount = 8_000,
            segmentCount = 18,
            chapterCount = 1,
            chapterTitles = listOf("Part One"),
            sourceSectionCount = 24
        )

        assertEquals("24 source sections prepared • 1 chapter detected", audiobookScanSummary(scan))
    }

    @Test
    fun scanSummarySkipsChapterTextWhenNoneWereDetected() {
        val scan = AudiobookScanUiState(
            wordCount = 8_000,
            segmentCount = 18,
            chapterCount = 0,
            sourceSectionCount = 24
        )

        assertEquals("24 source sections prepared", audiobookScanSummary(scan))
    }

    @Test
    fun scopeActionLabelsShowGenerationSizeAfterScan() {
        val scan = AudiobookScanUiState(
            wordCount = 30_000,
            segmentCount = 90,
            firstChapterSegmentCount = 8,
            estimatedAudioMillis = 200 * 60_000L,
            sourceSectionCount = 120
        )

        assertEquals("Sample • 12 seg • ~27m audio", audiobookScopeActionLabel(com.xreader.app.tts.AudiobookGenerationScope.SAMPLE, scan))
        assertEquals("Chapter • 8 seg • ~18m audio", audiobookScopeActionLabel(com.xreader.app.tts.AudiobookGenerationScope.FIRST_CHAPTER, scan))
        assertEquals("Full book • 90 seg • ~3h 20m audio", audiobookScopeActionLabel(com.xreader.app.tts.AudiobookGenerationScope.FULL_BOOK, scan))
        assertEquals("Sample", audiobookScopeActionTitle(com.xreader.app.tts.AudiobookGenerationScope.SAMPLE))
        assertEquals("12 seg • ~27m audio", audiobookScopeActionMetaLabel(com.xreader.app.tts.AudiobookGenerationScope.SAMPLE, scan))
    }

    @Test
    fun scopeActionLabelsFallbackToChapterCapWithoutChapterMetadata() {
        val scan = AudiobookScanUiState(
            wordCount = 30_000,
            segmentCount = 90,
            estimatedAudioMillis = 200 * 60_000L,
            sourceSectionCount = 120
        )

        assertEquals("Chapter • 60 seg • ~2h 14m audio", audiobookScopeActionLabel(com.xreader.app.tts.AudiobookGenerationScope.FIRST_CHAPTER, scan))
    }

    @Test
    fun scopeActionLabelsStaySimpleBeforeScan() {
        assertEquals("Sample", audiobookScopeActionLabel(com.xreader.app.tts.AudiobookGenerationScope.SAMPLE, null))
        assertEquals("Chapter", audiobookScopeActionLabel(com.xreader.app.tts.AudiobookGenerationScope.FIRST_CHAPTER, AudiobookScanUiState()))
        assertEquals("Full book", audiobookScopeActionLabel(com.xreader.app.tts.AudiobookGenerationScope.FULL_BOOK, null))
        assertNull(audiobookScopeActionMetaLabel(com.xreader.app.tts.AudiobookGenerationScope.SAMPLE, null))
    }

    @Test
    fun generationBlockedReasonExplainsDisabledGenerationButtons() {
        assertNull(
            audiobookGenerationBlockedReason(
                status = NeuralTtsModelStatus.INSTALLED,
                generatingSelectedAudio = false,
                modelName = "Kokoro v1.0"
            )
        )
        assertEquals(
            "Download Kokoro v1.0 before generating audiobook audio.",
            audiobookGenerationBlockedReason(
                status = NeuralTtsModelStatus.NOT_DOWNLOADED,
                generatingSelectedAudio = false,
                modelName = "Kokoro v1.0"
            )
        )
        assertEquals(
            "This voice is already generating audio. Stop it before starting another scope.",
            audiobookGenerationBlockedReason(
                status = NeuralTtsModelStatus.INSTALLED,
                generatingSelectedAudio = true,
                modelName = "Kokoro v1.0"
            )
        )
    }

    @Test
    fun selectedAudiobookStatusItemPrefersActiveGenerationAcrossScopes() {
        val fullBook = bookAudioItem(
            audio(1).copy(
                modelId = "kokoro",
                speakerId = 3,
                speed = 1.0f,
                tone = "NATURAL",
                scope = AudiobookGenerationScope.FULL_BOOK.key,
                status = BookAudioStatus.GENERATED
            )
        )
        val activeSample = bookAudioItem(
            audio(2).copy(
                modelId = "kokoro",
                speakerId = 3,
                speed = 1.0f,
                tone = "NATURAL",
                scope = AudiobookGenerationScope.SAMPLE.key,
                status = BookAudioStatus.GENERATING
            )
        )

        assertEquals(
            activeSample,
            selectedAudiobookStatusItem(
                items = listOf(fullBook, activeSample),
                modelId = "kokoro",
                speakerId = 3,
                speed = 1.0f,
                tone = "NATURAL"
            )
        )
    }

    @Test
    fun audiobookProfileMatcherUsesStableVoicePaceAndToneFields() {
        val audio = audio(1).copy(
            modelId = "kokoro",
            speakerId = 3,
            speed = 1.0005f,
            tone = "NATURAL"
        )

        assertTrue(audio.matchesAudiobookProfile(modelId = "kokoro", speakerId = 3, speed = 1.0f, tone = "NATURAL"))
        assertEquals(false, audio.matchesAudiobookProfile(modelId = "other", speakerId = 3, speed = 1.0f, tone = "NATURAL"))
        assertEquals(false, audio.matchesAudiobookProfile(modelId = "kokoro", speakerId = 4, speed = 1.0f, tone = "NATURAL"))
        assertEquals(false, audio.matchesAudiobookProfile(modelId = "kokoro", speakerId = 3, speed = 1.1f, tone = "NATURAL"))
        assertEquals(false, audio.matchesAudiobookProfile(modelId = "kokoro", speakerId = 3, speed = 1.0f, tone = "DRAMATIC"))
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
    fun visibleGeneratedAudioItemsKeepSelectedProfileVisibleWhenCollapsed() {
        val items = (1L..6L).map { id -> bookAudioItem(audio(id)) }

        assertEquals(listOf(1L, 2L, 3L, 6L), visibleGeneratedAudiobookItems(items, selectedAudioId = 6L, expanded = false).map { it.audio.id })
    }

    @Test
    fun generatedAudiobookItemsIdentityKeyTracksOnlyListIdentity() {
        val items = listOf(
            bookAudioItem(audio(1).copy(updatedAt = 10L)),
            bookAudioItem(audio(2).copy(updatedAt = 20L))
        )

        assertEquals(
            generatedAudiobookItemsIdentityKey(items),
            generatedAudiobookItemsIdentityKey(
                listOf(
                    bookAudioItem(audio(1).copy(updatedAt = 100L, completedSegments = 4)),
                    bookAudioItem(audio(2).copy(updatedAt = 200L, completedSegments = 8))
                )
            )
        )
        assertTrue(
            generatedAudiobookItemsIdentityKey(items) !=
                generatedAudiobookItemsIdentityKey(items.reversed())
        )
        assertTrue(
            generatedAudiobookItemsIdentityKey(items) !=
                generatedAudiobookItemsIdentityKey(items + bookAudioItem(audio(3)))
        )
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

    @Test
    fun playbackLabelsExposePreparingState() {
        val preparing = AudiobookPlaybackUiState(segmentIndex = 1, segmentCount = 4, preparing = true)

        assertEquals("Preparing", audiobookPlaybackActionLabel(active = true, playback = preparing))
        assertEquals("preparing 2 / 4", audiobookPlaybackStateLabel(preparing))
        assertEquals("Play", audiobookPlaybackActionLabel(active = false, playback = preparing))
    }

    @Test
    fun playbackProgressLabelSkipsBlankOptionalParts() {
        assertEquals("Ready", playbackProgressLabel(AudiobookPlaybackUiState()))
        assertEquals(
            "playing 2 / 4",
            playbackProgressLabel(AudiobookPlaybackUiState(segmentIndex = 1, segmentCount = 4, playing = true))
        )
        assertEquals(
            "playing 2 / 4 • Chapter 3 • 0:12 / 1:05",
            playbackProgressLabel(
                AudiobookPlaybackUiState(
                    segmentIndex = 1,
                    segmentCount = 4,
                    chapterTitle = " Chapter 3 ",
                    segmentPositionMs = 12_300,
                    segmentDurationMs = 65_000,
                    playing = true
                )
            )
        )
    }

    @Test
    fun generationProgressLabelShowsActiveSegment() {
        assertEquals(
            "working on 4/10",
            audio(1).copy(
                status = BookAudioStatus.GENERATING,
                segmentCount = 10,
                completedSegments = 3
            ).audiobookGenerationProgressLabel()
        )
        assertEquals(
            "10/10 segments",
            audio(2).copy(
                status = BookAudioStatus.GENERATING,
                segmentCount = 10,
                completedSegments = 12
            ).audiobookGenerationProgressLabel()
        )
        assertNull(
            audio(3).copy(
                status = BookAudioStatus.GENERATED,
                segmentCount = 10,
                completedSegments = 10
            ).audiobookGenerationProgressLabel()
        )
    }

    @Test
    fun audiobookStatusDetailUsesActiveGenerationProgress() {
        val audio = audio(1).copy(
            status = BookAudioStatus.GENERATING,
            segmentCount = 10,
            completedSegments = 3
        )

        assertEquals(
            "Generating • working on 4/10",
            audio.audiobookStatusDetail(
                activePlayback = false,
                playback = EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE,
                playableSegmentFiles = 3
            )
        )
    }

    @Test
    fun audiobookStatusDetailKeepsActivePlaybackChapterOutOfCompactStatusLine() {
        val audio = playableAudio(1)
        val playback = AudiobookPlaybackUiState(
            audioId = audio.id,
            segmentIndex = 1,
            segmentCount = 4,
            playing = true,
            chapterIndex = 0,
            chapterCount = 2,
            chapterTitle = "Chapter 1"
        )

        assertEquals(
            "Ready • 4 segments • playing 2 / 4",
            audio.audiobookStatusDetail(
                activePlayback = true,
                playback = playback,
                playableSegmentFiles = 4
            )
        )
        assertEquals("Chapter 1 • 1 / 2", playback.chapterLabel())
    }

    @Test
    fun generatedAudioActionLabelsDistinguishPartialAudio() {
        val partialGenerating = audio(1).copy(
            status = BookAudioStatus.GENERATING,
            segmentCount = 12,
            completedSegments = 3
        )
        val partialStopped = audio(2).copy(
            status = BookAudioStatus.CANCELED,
            segmentCount = 12,
            completedSegments = 3
        )
        val generated = audio(3).copy(
            status = BookAudioStatus.GENERATED,
            segmentCount = 12,
            completedSegments = 12
        )

        assertEquals("Play partial", audiobookPlaybackActionLabel(active = false, playback = AudiobookPlaybackUiState(), audio = partialGenerating))
        assertEquals("Save partial", audiobookExportActionLabel(partialGenerating))
        assertEquals("Saved partial generated audiobook audio.", audiobookExportSuccessMessage(partialGenerating))
        assertEquals(
            "Play",
            audiobookPlaybackActionLabel(
                active = false,
                playback = AudiobookPlaybackUiState(),
                audio = partialGenerating,
                playableSegmentFiles = 0
            )
        )
        assertEquals("Save", audiobookExportActionLabel(partialGenerating, playableSegmentFiles = 0))
        assertEquals("Saved generated audiobook audio.", audiobookExportSuccessMessage(partialGenerating, playableSegmentFiles = 0))
        assertEquals(
            "Play partial",
            audiobookPlaybackActionLabel(
                active = false,
                playback = AudiobookPlaybackUiState(),
                audio = partialGenerating,
                playableSegmentFiles = 1
            )
        )
        assertEquals("Save partial", audiobookExportActionLabel(partialGenerating, playableSegmentFiles = 1))
        assertEquals("Play partial", audiobookPlaybackActionLabel(active = false, playback = AudiobookPlaybackUiState(), audio = partialStopped))
        assertEquals("Save partial", audiobookExportActionLabel(partialStopped))
        assertEquals("Play", audiobookPlaybackActionLabel(active = false, playback = AudiobookPlaybackUiState(), audio = generated))
        assertEquals("Save", audiobookExportActionLabel(generated))
        assertEquals("Saved generated audiobook audio.", audiobookExportSuccessMessage(generated))
    }

    @Test
    fun playbackActionLabelUsesResumeForSavedAudiobookPosition() {
        val resumedAudio = audio(1).copy(
            segmentCount = 6,
            playbackSegmentIndex = 2,
            playbackPositionMs = 0
        )
        val firstSegmentResume = audio(2).copy(
            segmentCount = 6,
            playbackSegmentIndex = 0,
            playbackPositionMs = 12_000
        )
        val finishedAudio = audio(3).copy(
            segmentCount = 6,
            playbackSegmentIndex = 6,
            playbackPositionMs = 0
        )

        assertTrue(resumedAudio.hasAudiobookResumePosition())
        assertTrue(firstSegmentResume.hasAudiobookResumePosition())
        assertEquals(false, finishedAudio.hasAudiobookResumePosition())
        assertEquals(
            "Resume",
            audiobookPlaybackActionLabel(active = false, playback = AudiobookPlaybackUiState(), audio = resumedAudio)
        )
        assertEquals(
            "Resume",
            audiobookPlaybackActionLabel(
                active = true,
                playback = AudiobookPlaybackUiState(audioId = resumedAudio.id, playing = false, segmentCount = 6),
                audio = resumedAudio
            )
        )
        assertEquals(
            "Play",
            audiobookPlaybackActionLabel(active = false, playback = AudiobookPlaybackUiState(), audio = finishedAudio)
        )
        assertEquals("Resume 3 / 6", resumedAudio.audiobookResumeLabel())
        assertEquals("resume 3 / 6", resumedAudio.audiobookResumeLabel(prefix = "resume"))
        assertNull(finishedAudio.audiobookResumeLabel())
    }

    @Test
    fun resumeLabelsClampToVerifiedPlayableAudio() {
        val staleDeepResume = audio(1).copy(
            status = BookAudioStatus.CANCELED,
            segmentCount = 10,
            completedSegments = 10,
            playbackSegmentIndex = 7,
            playbackPositionMs = 12_000
        )
        val playableResume = staleDeepResume.copy(playbackSegmentIndex = 1)
        val firstSegmentResume = staleDeepResume.copy(playbackSegmentIndex = 0, playbackPositionMs = 8_000)

        assertTrue(staleDeepResume.hasAudiobookResumePosition())
        assertEquals("Resume 8 / 10", staleDeepResume.audiobookResumeLabel())
        assertEquals(false, staleDeepResume.hasAudiobookResumePosition(playableSegmentFiles = 2))
        assertNull(staleDeepResume.audiobookResumeLabel(playableSegmentFiles = 2))
        assertEquals("Resume 2 / 2", playableResume.audiobookResumeLabel(playableSegmentFiles = 2))
        assertEquals("Resume 1 / 2", firstSegmentResume.audiobookResumeLabel(playableSegmentFiles = 2))
    }

    @Test
    fun playbackIconLabelsUseSharedGeneratedAudioActions() {
        val partial = audio(1).copy(
            status = BookAudioStatus.CANCELED,
            segmentCount = 6,
            completedSegments = 2
        )
        val resumed = audio(2).copy(
            segmentCount = 6,
            playbackSegmentIndex = 2
        )
        val playing = AudiobookPlaybackUiState(audioId = partial.id, playing = true, segmentCount = 6)
        val preparing = AudiobookPlaybackUiState(audioId = partial.id, preparing = true, segmentCount = 6)

        assertEquals("Play partial generated audio", audiobookPlaybackIconLabel(active = false, playback = AudiobookPlaybackUiState(), audio = partial))
        assertEquals(
            "Play generated audio",
            audiobookPlaybackIconLabel(
                active = false,
                playback = AudiobookPlaybackUiState(),
                audio = partial,
                playableSegmentFiles = 0
            )
        )
        assertEquals(
            "Play partial generated audio",
            audiobookPlaybackIconLabel(
                active = false,
                playback = AudiobookPlaybackUiState(),
                audio = partial,
                playableSegmentFiles = 1
            )
        )
        assertEquals("Resume generated audio", audiobookPlaybackIconLabel(active = false, playback = AudiobookPlaybackUiState(), audio = resumed))
        assertEquals("Pause generated audio", audiobookPlaybackIconLabel(active = true, playback = playing, audio = partial))
        assertEquals("Preparing generated audio", audiobookPlaybackIconLabel(active = true, playback = preparing, audio = partial))
    }

    @Test
    fun generatedAudiobookActionStateCombinesLabelsAndAvailability() {
        val partial = audio(1).copy(
            status = BookAudioStatus.CANCELED,
            segmentCount = 6,
            completedSegments = 2
        )
        val preparing = AudiobookPlaybackUiState(audioId = partial.id, preparing = true, segmentCount = 2)

        val inactive = generatedAudiobookActionState(
            active = false,
            playback = AudiobookPlaybackUiState(),
            audio = partial,
            playableSegmentFiles = 2
        )
        assertEquals("Play partial", inactive.playLabel)
        assertEquals("Play partial generated audio", inactive.playIconLabel)
        assertEquals("Save partial", inactive.exportLabel)
        assertTrue(inactive.canPlay)
        assertTrue(inactive.canExport)

        val activePreparing = generatedAudiobookActionState(
            active = true,
            playback = preparing,
            audio = partial,
            playableSegmentFiles = 2
        )
        assertEquals("Preparing", activePreparing.playLabel)
        assertEquals(false, activePreparing.canPlay)
        assertTrue(activePreparing.canExport)
    }

    @Test
    fun generatedAudioActionEnablementRequiresPlayableAudioAndSafeState() {
        val emptyGenerated = audio(1).copy(
            status = BookAudioStatus.GENERATED,
            segmentCount = 0,
            completedSegments = 0
        )
        val playable = audio(2).copy(
            status = BookAudioStatus.GENERATED,
            segmentCount = 4,
            completedSegments = 4
        )
        val preparing = AudiobookPlaybackUiState(audioId = playable.id, preparing = true, segmentCount = 4)

        assertEquals(false, canPlayGeneratedAudiobookAction(active = false, playback = AudiobookPlaybackUiState(), audio = emptyGenerated))
        assertEquals(false, canExportGeneratedAudiobookAction(emptyGenerated))
        assertTrue(canPlayGeneratedAudiobookAction(active = false, playback = AudiobookPlaybackUiState(), audio = playable))
        assertTrue(canExportGeneratedAudiobookAction(playable))
        assertEquals(false, canPlayGeneratedAudiobookAction(active = true, playback = preparing, audio = playable))
        assertEquals(
            false,
            canPlayGeneratedAudiobookAction(
                active = false,
                playback = AudiobookPlaybackUiState(),
                playableSegmentFiles = 0
            )
        )
        assertEquals(false, canExportGeneratedAudiobookAction(playableSegmentFiles = 0))
        assertTrue(
            canPlayGeneratedAudiobookAction(
                active = false,
                playback = AudiobookPlaybackUiState(),
                playableSegmentFiles = 2
            )
        )
        assertTrue(canExportGeneratedAudiobookAction(playableSegmentFiles = 2))
    }

    @Test
    fun audiobooksScreenProtectsActiveGenerationFromDelete() {
        val generating = audio(1).copy(status = BookAudioStatus.GENERATING)
        val generated = audio(2).copy(status = BookAudioStatus.GENERATED)
        val failed = audio(3).copy(status = BookAudioStatus.FAILED)

        assertTrue(generating.canCancelGenerationFromAudiobooksScreen())
        assertEquals(false, generating.canDeleteFromAudiobooksScreen())
        assertEquals(false, generated.canCancelGenerationFromAudiobooksScreen())
        assertTrue(generated.canDeleteFromAudiobooksScreen())
        assertTrue(failed.canDeleteFromAudiobooksScreen())
    }

    @Test
    fun audiobooksScreenSortsActivePlaybackBeforeGenerationAndRecentAudio() {
        val oldPlaying = item(playableAudio(1).copy(status = BookAudioStatus.GENERATED, updatedAt = 10L))
        val generating = item(audio(2).copy(status = BookAudioStatus.GENERATING, updatedAt = 40L))
        val recentGenerated = item(playableAudio(3).copy(status = BookAudioStatus.GENERATED, updatedAt = 80L))
        val failed = item(audio(4).copy(status = BookAudioStatus.FAILED, updatedAt = 100L))

        val sorted = listOf(failed, recentGenerated, generating, oldPlaying)
            .sortedForAudiobooksScreen(AudiobookPlaybackUiState(audioId = oldPlaying.audio.id))

        assertEquals(listOf(1L, 2L, 3L, 4L), sorted.map { it.audio.id })
    }

    @Test
    fun audiobooksScreenSortsPlayableRowsBeforeFailedRows() {
        val failedRecent = item(audio(1).copy(status = BookAudioStatus.FAILED, updatedAt = 100L))
        val partialPlayable = item(audio(2).copy(status = BookAudioStatus.CANCELED, completedSegments = 2, segmentCount = 8, updatedAt = 20L))
        val generatedOlder = item(playableAudio(3).copy(status = BookAudioStatus.GENERATED, updatedAt = 10L))

        val sorted = listOf(failedRecent, partialPlayable, generatedOlder)
            .sortedForAudiobooksScreen(AudiobookPlaybackUiState())

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.audio.id })
    }

    @Test
    fun audiobooksScreenSortUsesPrecomputedSortTitle() {
        val sameTimestamp = 100L
        val zeta = item(
            playableAudio(1).copy(status = BookAudioStatus.GENERATED, generatedAt = sameTimestamp, updatedAt = sameTimestamp),
            bookTitle = "Zeta"
        )
        val alpha = item(
            playableAudio(2).copy(status = BookAudioStatus.GENERATED, generatedAt = sameTimestamp, updatedAt = sameTimestamp),
            bookTitle = "The Alpha"
        ).let { it.copy(book = it.book.copy(sortTitle = "alpha")) }

        val sorted = listOf(zeta, alpha)
            .sortedForAudiobooksScreen(AudiobookPlaybackUiState())

        assertEquals(listOf("The Alpha", "Zeta"), sorted.map { it.book.title })
    }

    @Test
    fun audiobooksScreenKeepsGeneratingRowsStableAcrossHeartbeatUpdates() {
        val firstStarted = item(
            audio(1).copy(
                status = BookAudioStatus.GENERATING,
                generationStartedAt = 1_000L,
                updatedAt = 10_000L
            )
        )
        val secondStarted = item(
            audio(2).copy(
                status = BookAudioStatus.GENERATING,
                generationStartedAt = 2_000L,
                updatedAt = 3_000L
            )
        )
        val heartbeat = firstStarted.copy(audio = firstStarted.audio.copy(updatedAt = 50_000L))

        val sorted = listOf(heartbeat, secondStarted)
            .sortedForAudiobooksScreen(AudiobookPlaybackUiState())

        assertEquals(listOf(2L, 1L), sorted.map { it.audio.id })
    }

    @Test
    fun audiobooksScreenSearchMatchesBookAuthorVoiceScopeAndStatus() {
        val ready = item(
            playableAudio(1).copy(
                modelDisplayName = "Kokoro v1.0",
                scopeLabel = "Full book"
            ),
            bookTitle = "The Long Patrol",
            author = "Brad Thor"
        )
        val sample = item(
            playableAudio(2).copy(
                modelDisplayName = "Kokoro v1.0",
                scopeLabel = "Sample"
            ),
            bookTitle = "A Deepness in the Sky",
            author = "Vernor Vinge"
        )
        val generating = item(
            audio(3).copy(
                status = BookAudioStatus.GENERATING,
                modelDisplayName = "Kokoro v1.0",
                scopeLabel = "First chapter"
            ),
            bookTitle = "Blowback",
            author = "Brad Thor"
        )

        val rows = listOf(ready, sample, generating)

        assertEquals(listOf(1L, 3L), rows.filteredForAudiobooksScreen("brad").map { it.audio.id })
        assertEquals(listOf(2L), rows.filteredForAudiobooksScreen("deepness sample").map { it.audio.id })
        assertEquals(listOf(3L), rows.filteredForAudiobooksScreen("generating chapter").map { it.audio.id })
        assertEquals(listOf(1L, 2L, 3L), rows.filteredForAudiobooksScreen("kokoro").map { it.audio.id })
    }

    @Test
    fun audiobooksScreenSearchUsesPrecomputedSearchText() {
        val row = item(
            playableAudio(1).copy(modelDisplayName = "Kokoro v1.0"),
            bookTitle = "Visible Book",
            author = "Visible Author"
        ).copy(searchText = "cached phrase only")

        assertEquals(listOf(1L), listOf(row).filteredForAudiobooksScreen("cached phrase").map { it.audio.id })
        assertTrue(listOf(row).filteredForAudiobooksScreen("visible kokoro").isEmpty())
    }

    @Test
    fun audiobooksScreenMaterializesRowsInRoomOrderWithoutIdRemap() {
        val firstAudio = playableAudio(1).copy(bookId = 101, updatedAt = 30L)
        val hiddenAudio = audio(2).copy(bookId = 102, status = BookAudioStatus.CANCELED, completedSegments = 0)
        val thirdAudio = playableAudio(3).copy(bookId = 103, updatedAt = 10L)
        val rows = listOf(
            BookAudioWithBook(firstAudio, book(id = 101, title = "First")),
            BookAudioWithBook(hiddenAudio, book(id = 102, title = "Hidden")),
            BookAudioWithBook(thirdAudio, book(id = 103, title = "Third"))
        )
        val audioItems = listOf(
            bookAudioItem(firstAudio, playableSegmentFiles = 4),
            bookAudioItem(hiddenAudio, playableSegmentFiles = 0),
            bookAudioItem(thirdAudio, playableSegmentFiles = 4)
        )

        val uiItems = rows.toGeneratedAudiobookUiItems(audioItems)

        assertEquals(listOf("First", "Third"), uiItems.map { it.book.title })
        assertEquals(listOf(1L, 3L), uiItems.map { it.audio.id })
    }

    @Test
    fun audiobooksScreenMaterializerFallsBackToIdsForMismatchedRowAndAudioLists() {
        val firstAudio = playableAudio(1).copy(bookId = 101)
        val secondAudio = playableAudio(2).copy(bookId = 102)
        val rows = listOf(
            BookAudioWithBook(firstAudio, book(id = 101, title = "First")),
            BookAudioWithBook(secondAudio, book(id = 102, title = "Second"))
        )

        val uiItems = rows.toGeneratedAudiobookUiItems(listOf(bookAudioItem(secondAudio)))

        assertEquals(listOf("Second"), uiItems.map { it.book.title })
        assertEquals(listOf(2L), uiItems.map { it.audio.id })
    }

    @Test
    fun audiobookUiCacheMaterializesBookRowsWithoutIntermediateAudioList() {
        val cache = BookAudiobookAudioUiItemCache()
        val firstAudio = playableAudio(1).copy(bookId = 101, completedSegments = 2, segmentCount = 4)
        val secondAudio = playableAudio(2).copy(bookId = 102, completedSegments = 3, segmentCount = 6)
        val firstRows = listOf(
            BookAudioWithBook(firstAudio, book(id = 101, title = "First")),
            BookAudioWithBook(secondAudio, book(id = 102, title = "Second"))
        )

        val firstItems = cache.toUiItemsForRows(firstRows)
        val secondItems = cache.toUiItemsForRows(listOf(BookAudioWithBook(secondAudio, book(id = 102, title = "Second"))))

        assertEquals(listOf(1L, 2L), firstItems.map { it.audio.id })
        assertEquals(listOf(2L), secondItems.map { it.audio.id })
        assertEquals(6, secondItems.single().playableSegmentFiles)
    }

    @Test
    fun audiobookUiCacheReusesGeneratingFallbackChaptersAcrossHeartbeats() {
        val cache = BookAudiobookAudioUiItemCache()
        val generating = audio(1).copy(
            status = BookAudioStatus.GENERATING,
            segmentCount = 10,
            completedSegments = 3,
            updatedAt = 1L
        )

        val first = cache.toUiItems(listOf(generating)).single()
        val heartbeat = cache.toUiItems(listOf(generating.copy(updatedAt = 10_000L))).single()
        val progressed = cache.toUiItems(listOf(generating.copy(completedSegments = 4, updatedAt = 20_000L))).single()

        assertSame(first.chapters, heartbeat.chapters)
        assertEquals(3, heartbeat.playableSegmentFiles)
        assertEquals(4, progressed.playableSegmentFiles)
        assertEquals(4, progressed.chapters.single().segmentCount)
    }

    @Test
    fun audiobookDisplayProfileLabelCanIncludeOrOmitScope() {
        val sample = audio(1).copy(
            modelDisplayName = "Kokoro v1.0",
            speakerId = 2,
            tone = "WARM",
            speed = 1.15f,
            scopeLabel = "Sample"
        )
        val fullBook = sample.copy(scopeLabel = "Full book")

        assertEquals("Sample Kokoro v1.0 Speaker 3 Warm 1.15x", sample.audiobookDisplayProfileLabel(includeScope = true))
        assertEquals("Kokoro v1.0 Speaker 3 Warm 1.15x", sample.audiobookDisplayProfileLabel(includeScope = false))
        assertEquals("Kokoro v1.0 Speaker 3 Warm 1.15x", fullBook.audiobookDisplayProfileLabel(includeScope = true))
    }

    @Test
    fun neuralTtsStatusTextIsSharedBySettingsAndBookDialog() {
        assertEquals(
            "Not installed • 333 MB",
            neuralTtsStatusText(
                status = NeuralTtsModelStatus.NOT_DOWNLOADED,
                downloaded = 0,
                total = 333L * 1_048_576L,
                archiveBytes = 333L * 1_048_576L,
                error = null
            )
        )
        assertEquals(
            "Downloading 12 MB of 333 MB",
            neuralTtsStatusText(
                status = NeuralTtsModelStatus.DOWNLOADING,
                downloaded = 12L * 1_048_576L,
                total = 333L * 1_048_576L,
                archiveBytes = 333L * 1_048_576L,
                error = null
            )
        )
        assertEquals(
            "boom",
            neuralTtsStatusText(
                status = NeuralTtsModelStatus.FAILED,
                downloaded = 0,
                total = 333L * 1_048_576L,
                archiveBytes = 333L * 1_048_576L,
                error = "boom"
            )
        )
    }

    @Test
    fun continueReadingPrimaryActionsExposeBookMaintenanceAndAudio() {
        val actions = continueReadingPrimaryActions()

        assertEquals(
            listOf("Read", "Audio", "Repair", "More"),
            actions.map { it.label }
        )
        assertEquals(
            listOf(
                ContinueReadingPrimaryActionKind.READ,
                ContinueReadingPrimaryActionKind.AUDIO,
                ContinueReadingPrimaryActionKind.REPAIR,
                ContinueReadingPrimaryActionKind.MORE
            ),
            actions.map { it.kind }
        )
    }

    @Test
    fun playbackChapterLabelShowsCurrentChapterAndPosition() {
        val playback = AudiobookPlaybackUiState(
            segmentIndex = 4,
            segmentCount = 20,
            chapterIndex = 1,
            chapterCount = 5,
            chapterTitle = "Chapter 2"
        )

        assertEquals("Chapter 2 • 2 / 5", playback.chapterLabel())
        assertTrue(playback.canSkipPreviousChapter())
        assertTrue(playback.canSkipNextChapter())
    }

    @Test
    fun playbackChapterJumpRulesAvoidDeadControls() {
        val first = AudiobookPlaybackUiState(segmentIndex = 0, segmentCount = 4, chapterIndex = 0, chapterCount = 2, chapterTitle = "Chapter 1")
        val last = AudiobookPlaybackUiState(segmentIndex = 3, segmentCount = 4, chapterIndex = 1, chapterCount = 2, chapterTitle = "Chapter 2")
        val missingMetadata = AudiobookPlaybackUiState(segmentIndex = 1, segmentCount = 4)

        assertEquals(false, first.canSkipPreviousChapter())
        assertTrue(first.canSkipNextChapter())
        assertTrue(last.canSkipPreviousChapter())
        assertEquals(false, last.canSkipNextChapter())
        assertNull(missingMetadata.chapterLabel())
        assertEquals(false, missingMetadata.canSkipPreviousChapter())
        assertEquals(false, missingMetadata.canSkipNextChapter())
    }

    @Test
    fun playbackChapterJumpRulesUsePreparedChapterBoundariesWhenAvailable() {
        val chapters = listOf(
            GeneratedAudiobookChapter(index = 0, title = "Chapter 1", firstSegmentIndex = 3, segmentCount = 2),
            GeneratedAudiobookChapter(index = 1, title = "Chapter 2", firstSegmentIndex = 5, segmentCount = 3)
        )
        val beforeFirstBoundary = AudiobookPlaybackUiState(segmentIndex = 1, segmentCount = 8, chapterCount = 2)
        val insideFirstChapter = AudiobookPlaybackUiState(segmentIndex = 4, segmentCount = 8, chapterCount = 2)
        val insideLastChapter = AudiobookPlaybackUiState(segmentIndex = 6, segmentCount = 8, chapterCount = 2)

        assertEquals(false, beforeFirstBoundary.canSkipPreviousChapter(chapters))
        assertTrue(beforeFirstBoundary.canSkipNextChapter(chapters))
        assertTrue(insideFirstChapter.canSkipPreviousChapter(chapters))
        assertTrue(insideFirstChapter.canSkipNextChapter(chapters))
        assertTrue(insideLastChapter.canSkipPreviousChapter(chapters))
        assertEquals(false, insideLastChapter.canSkipNextChapter(chapters))
    }

    @Test
    fun generatedAudiobookChapterRangeLabelsUseOneBasedSegments() {
        assertEquals(
            "Segment 1",
            GeneratedAudiobookChapter(index = 0, title = "Intro", firstSegmentIndex = 0, segmentCount = 1).chapterRangeLabel()
        )
        assertEquals(
            "Segments 3-6",
            GeneratedAudiobookChapter(index = 1, title = "Chapter", firstSegmentIndex = 2, segmentCount = 4).chapterRangeLabel()
        )
    }

    @Test
    fun generatedAudiobookChapterCountLabelUsesSingularAndPluralText() {
        assertEquals("1 chapter", generatedAudiobookChapterCountLabel(1))
        assertEquals("2 chapters", generatedAudiobookChapterCountLabel(2))
    }

    @Test
    fun generatedAudiobookUiItemCarriesPreparedChaptersAndPlayableFileCount() {
        val chapters = listOf(
            GeneratedAudiobookChapter(index = 0, title = "Chapter 1", firstSegmentIndex = 0, segmentCount = 2)
        )

        val item = item(playableAudio(1), chapters, playableSegmentFiles = 2)

        assertEquals(chapters, item.chapters)
        assertEquals(2, item.playableSegmentFiles)
    }

    @Test
    fun generatedAudiobookUiItemUsesChapterSidecarWithoutVerifyingEverySegmentFile() {
        val dir = kotlin.io.path.createTempDirectory("xreader-audio-ui").toFile()
        try {
            File(dir, "chapters.tsv").writeText(
                """
                index	firstSegment	segmentCount	title
                0	0	2	Chapter 1
                1	2	3	Chapter 2
                """.trimIndent()
            )
            val audio = playableAudio(1).copy(
                filePath = dir.absolutePath,
                segmentCount = 5,
                completedSegments = 5
            )

            val item = audio.toBookAudiobookAudioUiItem()

            assertEquals(listOf("Chapter 1", "Chapter 2"), item.chapters.map { it.title })
            assertEquals(5, item.playableSegmentFiles)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun generatedAudiobookUiItemSkipsChapterSidecarWhileGenerating() {
        val dir = kotlin.io.path.createTempDirectory("xreader-audio-generating-ui").toFile()
        try {
            File(dir, "chapters.tsv").writeText(
                """
                index	firstSegment	segmentCount	title
                0	0	2	Chapter 1
                1	2	3	Chapter 2
                """.trimIndent()
            )
            val audio = playableAudio(1).copy(
                status = BookAudioStatus.GENERATING,
                filePath = dir.absolutePath,
                scope = AudiobookGenerationScope.FULL_BOOK.key,
                scopeLabel = AudiobookGenerationScope.FULL_BOOK.label,
                segmentCount = 5,
                completedSegments = 3
            )

            val item = audio.toBookAudiobookAudioUiItem()

            assertEquals(listOf("Full book"), item.chapters.map { it.title })
            assertEquals(3, item.playableSegmentFiles)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun audiobookUiItemCacheReusesUnchangedChapterMetadata() {
        val dir = kotlin.io.path.createTempDirectory("xreader-audio-ui-cache").toFile()
        try {
            File(dir, "chapters.tsv").writeText(
                """
                index	firstSegment	segmentCount	title
                0	0	3	Original
                """.trimIndent()
            )
            val audio = playableAudio(1).copy(
                filePath = dir.absolutePath,
                segmentCount = 3,
                completedSegments = 3,
                updatedAt = 1_000L
            )
            val cache = BookAudiobookAudioUiItemCache()

            assertEquals(listOf("Original"), cache.toUiItems(listOf(audio)).single().chapters.map { it.title })

            File(dir, "chapters.tsv").writeText(
                """
                index	firstSegment	segmentCount	title
                0	0	3	Changed
                """.trimIndent()
            )

            assertEquals(listOf("Original"), cache.toUiItems(listOf(audio)).single().chapters.map { it.title })
            assertEquals(
                listOf("Changed"),
                cache.toUiItems(listOf(audio.copy(updatedAt = 2_000L))).single().chapters.map { it.title }
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun audiobookUiItemCacheKeepsHeartbeatUpdatesWithoutReparsingSidecars() {
        val dir = kotlin.io.path.createTempDirectory("xreader-audio-ui-cache-heartbeat").toFile()
        try {
            File(dir, "chapters.tsv").writeText(
                """
                index	firstSegment	segmentCount	title
                0	0	3	Original
                """.trimIndent()
            )
            val cache = BookAudiobookAudioUiItemCache()
            val audio = playableAudio(1).copy(
                status = BookAudioStatus.GENERATING,
                filePath = dir.absolutePath,
                scope = AudiobookGenerationScope.FULL_BOOK.key,
                scopeLabel = AudiobookGenerationScope.FULL_BOOK.label,
                segmentCount = 3,
                completedSegments = 2,
                generationAudioMillis = 10_000L,
                generationComputeMillis = 20_000L,
                updatedAt = 1_000L
            )

            assertEquals(listOf("Full book"), cache.toUiItems(listOf(audio)).single().chapters.map { it.title })

            File(dir, "chapters.tsv").writeText(
                """
                index	firstSegment	segmentCount	title
                0	0	3	Changed
                """.trimIndent()
            )
            val heartbeat = audio.copy(
                generationAudioMillis = 15_000L,
                generationComputeMillis = 30_000L,
                updatedAt = 2_000L
            )
            val item = cache.toUiItems(listOf(heartbeat)).single()

            assertEquals(listOf("Full book"), item.chapters.map { it.title })
            assertEquals(15_000L, item.audio.generationAudioMillis)
            assertEquals(30_000L, item.audio.generationComputeMillis)
            assertEquals(2_000L, item.audio.updatedAt)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun audiobookUiItemCacheKeepsGeneratingProgressLiveWithoutReparsingSidecars() {
        val dir = kotlin.io.path.createTempDirectory("xreader-audio-ui-cache-progress").toFile()
        try {
            File(dir, "chapters.tsv").writeText(
                """
                index	firstSegment	segmentCount	title
                0	0	5	Original
                """.trimIndent()
            )
            val cache = BookAudiobookAudioUiItemCache()
            val audio = playableAudio(1).copy(
                status = BookAudioStatus.GENERATING,
                filePath = dir.absolutePath,
                scope = AudiobookGenerationScope.FULL_BOOK.key,
                scopeLabel = AudiobookGenerationScope.FULL_BOOK.label,
                segmentCount = 5,
                completedSegments = 1,
                updatedAt = 1_000L
            )

            assertEquals(1, cache.toUiItems(listOf(audio)).single().playableSegmentFiles)

            File(dir, "chapters.tsv").writeText(
                """
                index	firstSegment	segmentCount	title
                0	0	5	Changed
                """.trimIndent()
            )
            val progress = audio.copy(
                completedSegments = 3,
                generationAudioMillis = 30_000L,
                generationComputeMillis = 12_000L,
                updatedAt = 2_000L
            )
            val item = cache.toUiItems(listOf(progress)).single()

            assertEquals(3, item.playableSegmentFiles)
            assertEquals(listOf("Full book"), item.chapters.map { it.title })
            assertEquals(listOf(3), item.chapters.map { it.segmentCount })
            assertEquals(3, item.audio.completedSegments)
            assertEquals(30_000L, item.audio.generationAudioMillis)
            assertEquals(12_000L, item.audio.generationComputeMillis)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun audiobookUiItemCachePrunesRowsOutsideActiveSet() {
        val dir = kotlin.io.path.createTempDirectory("xreader-audio-ui-cache-prune").toFile()
        try {
            File(dir, "chapters.tsv").writeText(
                """
                index	firstSegment	segmentCount	title
                0	0	2	Original
                """.trimIndent()
            )
            val first = playableAudio(1).copy(
                filePath = dir.absolutePath,
                segmentCount = 2,
                completedSegments = 2,
                updatedAt = 1_000L
            )
            val second = playableAudio(2).copy(updatedAt = 1_000L)
            val cache = BookAudiobookAudioUiItemCache()

            assertEquals(listOf("Original"), cache.toUiItems(listOf(first, second)).first().chapters.map { it.title })

            File(dir, "chapters.tsv").writeText(
                """
                index	firstSegment	segmentCount	title
                0	0	2	Rebuilt
                """.trimIndent()
            )

            cache.toUiItems(listOf(second))

            assertEquals(listOf("Rebuilt"), cache.toUiItems(listOf(first, second)).first().chapters.map { it.title })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun globalAudiobookVisibilityKeepsGeneratedActiveAndPlayablePartialRows() {
        assertTrue(
            bookAudioItem(audio(1).copy(status = BookAudioStatus.GENERATED), playableSegmentFiles = 0)
                .shouldShowInGlobalAudiobooksScreen()
        )
        assertTrue(
            bookAudioItem(audio(2).copy(status = BookAudioStatus.GENERATING), playableSegmentFiles = 0)
                .shouldShowInGlobalAudiobooksScreen()
        )
        assertTrue(
            bookAudioItem(audio(3).copy(status = BookAudioStatus.CANCELED), playableSegmentFiles = 2)
                .shouldShowInGlobalAudiobooksScreen()
        )
    }

    @Test
    fun globalAudiobookVisibilityHidesStoppedRowsWithoutPlayableSegments() {
        assertEquals(
            false,
            bookAudioItem(audio(4).copy(status = BookAudioStatus.CANCELED), playableSegmentFiles = 0)
                .shouldShowInGlobalAudiobooksScreen()
        )
        assertEquals(
            false,
            bookAudioItem(audio(5).copy(status = BookAudioStatus.FAILED), playableSegmentFiles = 0)
                .shouldShowInGlobalAudiobooksScreen()
        )
    }

    @Test
    fun libraryPlaybackChromeDropsPerSecondPositionChurn() {
        val playback = AudiobookPlaybackUiState(
            audioId = 10,
            bookId = 7,
            playing = true,
            segmentIndex = 3,
            segmentCount = 9,
            chapterIndex = 1,
            chapterCount = 4,
            chapterTitle = "Chapter 2",
            segmentPositionMs = 42_000,
            segmentDurationMs = 120_000
        )

        val chrome = playback.forLibraryChrome()

        assertEquals(playback.audioId, chrome.audioId)
        assertEquals(playback.bookId, chrome.bookId)
        assertEquals(playback.playing, chrome.playing)
        assertEquals(playback.segmentIndex, chrome.segmentIndex)
        assertEquals(playback.chapterTitle, chrome.chapterTitle)
        assertEquals(0, chrome.segmentPositionMs)
        assertEquals(0, chrome.segmentDurationMs)
    }

    @Test
    fun audiobookRowsOnlyReceiveLivePlaybackForActiveAudio() {
        val playback = AudiobookPlaybackUiState(
            audioId = 10,
            bookId = 7,
            playing = true,
            segmentIndex = 3,
            segmentCount = 9,
            segmentPositionMs = 42_000,
            segmentDurationMs = 120_000
        )

        assertEquals(playback, playback.forAudiobooksScreenRow(audioId = 10))
        assertEquals(AudiobookPlaybackUiState(), playback.forAudiobooksScreenRow(audioId = 11))
        assertSame(EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE, playback.forAudiobooksScreenRow(audioId = 11))
        assertEquals(
            playback.forAudiobooksScreenRow(audioId = 11),
            playback.copy(segmentPositionMs = 65_000).forAudiobooksScreenRow(audioId = 11)
        )
        assertSame(
            playback.forAudiobooksScreenRow(audioId = 11),
            playback.copy(segmentPositionMs = 65_000).forAudiobooksScreenRow(audioId = 11)
        )
    }

    @Test
    fun audiobooksScreenPlaybackCoalescesPositionTicks() {
        val playback = AudiobookPlaybackUiState(
            audioId = 10,
            bookId = 7,
            playing = true,
            segmentIndex = 3,
            segmentCount = 9,
            segmentPositionMs = 42_999,
            segmentDurationMs = 120_000
        )

        val screen = playback.forAudiobooksScreen()

        assertEquals(40_000, screen.segmentPositionMs)
        assertEquals(120_000, screen.segmentDurationMs)
        assertEquals(screen, playback.copy(segmentPositionMs = 44_999).forAudiobooksScreen())
        assertTrue(screen != playback.copy(segmentPositionMs = 45_000).forAudiobooksScreen())
        assertSame(EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE, AudiobookPlaybackUiState().forAudiobooksScreen())
    }

    @Test
    fun readerGeneratedReadAloudChromeIgnoresPositionOnlyPlaybackTicks() {
        val playback = AudiobookPlaybackUiState(
            audioId = 10,
            bookId = 7,
            bookTitle = "Takedown",
            profileLabel = "Kokoro Natural",
            playing = true,
            segmentIndex = 3,
            segmentCount = 9,
            segmentPositionMs = 42_000,
            segmentDurationMs = 120_000
        )

        assertEquals(
            playback.toReaderGeneratedReadAloudState(bookId = 7),
            playback.copy(segmentPositionMs = 65_000, segmentDurationMs = 120_000).toReaderGeneratedReadAloudState(bookId = 7)
        )
        assertTrue(
            playback.toReaderGeneratedReadAloudState(bookId = 7) !=
                playback.copy(segmentIndex = 4).toReaderGeneratedReadAloudState(bookId = 7)
        )
    }

    @Test
    fun readerGeneratedReadAloudChromeHidesUnrelatedAudiobookPlayback() {
        val playback = AudiobookPlaybackUiState(
            audioId = 10,
            bookId = 9,
            bookTitle = "Other Book",
            playing = true,
            segmentIndex = 1,
            segmentCount = 4
        )

        assertEquals(
            com.xreader.app.tts.ReadAloudState(),
            playback.toReaderGeneratedReadAloudState(bookId = 7)
        )
    }

    @Test
    fun audiobookUiInvalidationKeyIgnoresPlaybackPositionOnlyChanges() {
        val base = playableAudio(4).copy(
            playbackSegmentIndex = 1,
            playbackPositionMs = 2_000
        )

        assertEquals(
            base.audiobookUiInvalidationKey(),
            base.copy(playbackSegmentIndex = 3, playbackPositionMs = 45_000).audiobookUiInvalidationKey()
        )
        assertTrue(
            base.audiobookUiInvalidationKey() !=
                base.copy(completedSegments = 3).audiobookUiInvalidationKey()
        )
        assertEquals(false, sameAudiobookUiInvalidationRows(listOf(base), listOf(base.copy(status = BookAudioStatus.CANCELED))))
    }

    @Test
    fun audiobookUiInvalidationKeyTracksGenerationTimingMetricChanges() {
        val base = playableAudio(4).copy(
            generationAudioMillis = 30_000L,
            generationComputeMillis = 12_000L
        )

        assertTrue(
            base.audiobookUiInvalidationKey() !=
            base.copy(
                generationAudioMillis = 60_000L,
                generationComputeMillis = 24_000L
            ).audiobookUiInvalidationKey()
        )
        assertTrue(
            base.audiobookUiInvalidationKey() !=
                base.copy(completedSegments = 3).audiobookUiInvalidationKey()
        )
    }

    @Test
    fun audiobookUiInvalidationKeyTracksGenerationHeartbeatTimestampChanges() {
        val base = playableAudio(4).copy(updatedAt = 1_000L)

        assertTrue(
            base.audiobookUiInvalidationKey() !=
                base.copy(updatedAt = 2_000L).audiobookUiInvalidationKey()
        )
        assertTrue(
            base.audiobookUiInvalidationKey() !=
                base.copy(generatedAt = 2_000L).audiobookUiInvalidationKey()
        )
    }

    @Test
    fun audiobookUiInvalidationKeyIgnoresGeneratingHeartbeatTimingAndTimestamps() {
        val base = audio(4).copy(
            status = BookAudioStatus.GENERATING,
            segmentCount = 10,
            completedSegments = 3,
            generationAudioMillis = 30_000L,
            generationComputeMillis = 12_000L,
            updatedAt = 1_000L
        )

        assertEquals(
            base.audiobookUiInvalidationKey(),
            base.copy(updatedAt = 2_000L).audiobookUiInvalidationKey()
        )
        assertEquals(
            base.audiobookUiInvalidationKey(),
            base.copy(
                generationAudioMillis = 36_000L,
                generationComputeMillis = 14_000L
            ).audiobookUiInvalidationKey()
        )
        assertTrue(
            base.audiobookUiInvalidationKey() !=
                base.copy(completedSegments = 4).audiobookUiInvalidationKey()
        )
    }

    @Test
    fun audiobookUiInvalidationRowsCompareWithoutMaterializingKeys() {
        val base = playableAudio(4).copy(
            playbackSegmentIndex = 1,
            playbackPositionMs = 2_000,
            updatedAt = 1_000L
        )

        assertTrue(
            sameAudiobookUiInvalidationRows(
                listOf(base),
                listOf(base.copy(playbackSegmentIndex = 3, playbackPositionMs = 45_000))
            )
        )
        assertEquals(
            false,
            sameAudiobookUiInvalidationRows(listOf(base), listOf(base.copy(completedSegments = 3)))
        )
        assertEquals(
            false,
            sameAudiobookUiInvalidationRows(listOf(base), listOf(base.copy(updatedAt = 2_000L)))
        )
    }

    @Test
    fun audiobookUiInvalidationRowsIgnoreGeneratingHeartbeatOnlyUpdates() {
        val base = audio(4).copy(
            status = BookAudioStatus.GENERATING,
            segmentCount = 10,
            completedSegments = 3,
            generationAudioMillis = 30_000L,
            generationComputeMillis = 12_000L,
            updatedAt = 1_000L
        )

        assertTrue(
            sameAudiobookUiInvalidationRows(
                listOf(base),
                listOf(
                    base.copy(
                        generationAudioMillis = 36_000L,
                        generationComputeMillis = 14_000L,
                        updatedAt = 2_000L
                    )
                )
            )
        )
        assertEquals(
            false,
            sameAudiobookUiInvalidationRows(listOf(base), listOf(base.copy(completedSegments = 4)))
        )
    }

    @Test
    fun audiobooksScreenRowInvalidationIgnoresGeneratingHeartbeatOnlyAndOrderChanges() {
        val generating = audio(4).copy(
            status = BookAudioStatus.GENERATING,
            segmentCount = 10,
            completedSegments = 3,
            generationStartedAt = 1_000L,
            generationAudioMillis = 30_000L,
            generationComputeMillis = 12_000L,
            updatedAt = 1_000L
        )
        val ready = playableAudio(5)
        val previous = listOf(
            audioRow(generating, title = "Generating"),
            audioRow(ready, title = "Ready")
        )
        val heartbeat = listOf(
            audioRow(
                generating.copy(
                    generationAudioMillis = 36_000L,
                    generationComputeMillis = 14_000L,
                    updatedAt = 9_000L
                ),
                title = "Generating"
            ),
            audioRow(ready, title = "Ready")
        )
        val progress = listOf(
            audioRow(generating.copy(completedSegments = 4, updatedAt = 10_000L), title = "Generating"),
            audioRow(ready, title = "Ready")
        )

        assertTrue(sameAudiobookScreenRows(previous, heartbeat))
        assertEquals(false, sameAudiobookScreenRows(previous, progress))
    }

    @Test
    fun audiobooksScreenRowInvalidationPreservesDaoOrderWithoutSorting() {
        val first = audioRow(playableAudio(1), title = "First")
        val second = audioRow(playableAudio(2), title = "Second")

        assertEquals(false, sameAudiobookScreenRows(listOf(first, second), listOf(second, first)))
    }

    @Test
    fun audiobooksScreenRowInvalidationIgnoresUnrenderedBookMetadata() {
        val row = audioRow(playableAudio(7), title = "Rendered", author = "Visible Author")
        val metadataOnly = row.copy(
            book = row.book.copy(
                description = "Updated description",
                genre = "New genre",
                year = 2026,
                favorite = true,
                finished = true,
                updatedAt = row.book.updatedAt + 1_000
            )
        )

        assertTrue(sameAudiobookScreenRows(listOf(row), listOf(metadataOnly)))
    }

    @Test
    fun audiobooksScreenRowInvalidationTracksVisibleAndSortableBookFields() {
        val row = audioRow(playableAudio(8), title = "Rendered", author = "Visible Author")

        assertEquals(false, sameAudiobookScreenRows(listOf(row), listOf(row.copy(book = row.book.copy(title = "Changed")))))
        assertEquals(false, sameAudiobookScreenRows(listOf(row), listOf(row.copy(book = row.book.copy(author = "Changed")))))
        assertEquals(false, sameAudiobookScreenRows(listOf(row), listOf(row.copy(book = row.book.copy(sortTitle = "changed")))))
    }

    @Test
    fun audiobookGenerationBlockedReasonIncludesHardwareReadinessAfterInstall() {
        val hardwareReason = "No strict hardware TTS provider is available."

        assertEquals(
            hardwareReason,
            audiobookGenerationBlockedReason(
                status = NeuralTtsModelStatus.INSTALLED,
                generatingSelectedAudio = false,
                modelName = "Kokoro v1.0",
                hardwareReadiness = AudiobookGenerationHardwareReadiness(
                    ready = false,
                    reason = hardwareReason
                )
            )
        )
        assertEquals(
            "Download Kokoro v1.0 before generating audiobook audio.",
            audiobookGenerationBlockedReason(
                status = NeuralTtsModelStatus.NOT_DOWNLOADED,
                generatingSelectedAudio = false,
                modelName = "Kokoro v1.0",
                hardwareReadiness = AudiobookGenerationHardwareReadiness(
                    ready = false,
                    reason = hardwareReason
                )
            )
        )
        assertNull(
            audiobookGenerationBlockedReason(
                status = NeuralTtsModelStatus.INSTALLED,
                generatingSelectedAudio = false,
                modelName = "Kokoro v1.0",
                hardwareReadiness = AudiobookGenerationHardwareReadiness(ready = true)
            )
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

    private fun playableAudio(id: Long): BookAudioEntity =
        audio(id).copy(
            segmentCount = 4,
            completedSegments = 4
        )

    private fun item(
        audio: BookAudioEntity,
        chapters: List<GeneratedAudiobookChapter> = emptyList(),
        playableSegmentFiles: Int = audio.playableSegmentCount(),
        bookTitle: String = "Book ${audio.id}",
        author: String = "Author",
    ): GeneratedAudiobookUiItem =
        GeneratedAudiobookUiItem(
            book = book(id = audio.bookId, title = bookTitle, author = author, suffix = audio.id.toString()),
            audio = audio,
            chapters = chapters,
            playableSegmentFiles = playableSegmentFiles
        )

    private fun book(
        id: Long,
        title: String,
        author: String = "Author",
        suffix: String = id.toString(),
    ): BookEntity =
        BookEntity(
            id = id,
            title = title,
            author = author,
            sortTitle = title.lowercase(),
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "book-$suffix.epub",
            filePath = "/tmp/book-$suffix.epub",
            checksum = "checksum-$suffix",
            fileSizeBytes = 1,
            wordCount = 100,
            importedAt = 1,
            updatedAt = 1
        )

    private fun bookAudioItem(
        audio: BookAudioEntity,
        chapters: List<GeneratedAudiobookChapter> = emptyList(),
        playableSegmentFiles: Int = audio.playableSegmentCount(),
    ): BookAudiobookAudioUiItem =
        BookAudiobookAudioUiItem(
            audio = audio,
            chapters = chapters,
            playableSegmentFiles = playableSegmentFiles
        )

    private fun audioRow(
        audio: BookAudioEntity,
        title: String = "Book ${audio.id}",
        author: String = "Author",
    ): BookAudioWithBook =
        BookAudioWithBook(
            audio = audio,
            book = book(
                id = audio.bookId,
                title = title,
                author = author,
                suffix = "audio-row-${audio.id}"
            )
        )
}
