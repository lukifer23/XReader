package com.xreader.app.ui

import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.tts.AudiobookPlaybackUiState
import com.xreader.app.tts.GeneratedAudiobookChapter
import com.xreader.app.tts.playableSegmentCount
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
    ): GeneratedAudiobookUiItem =
        GeneratedAudiobookUiItem(
            book = BookEntity(
                id = audio.bookId,
                title = "Book ${audio.id}",
                author = "Author",
                sortTitle = "book ${audio.id}",
                format = BookFormat.EPUB,
                sourceExtension = "epub",
                fileName = "book-${audio.id}.epub",
                filePath = "/tmp/book-${audio.id}.epub",
                checksum = "checksum-${audio.id}",
                fileSizeBytes = 1,
                wordCount = 100,
                importedAt = 1,
                updatedAt = 1
            ),
            audio = audio,
            chapters = chapters,
            playableSegmentFiles = playableSegmentFiles
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
}
