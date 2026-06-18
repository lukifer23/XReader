package com.xreader.app.tts

import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("segment-00012.wav.tmp", generatedAudiobookTempSegmentFileName(11))
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
    fun reusableSegmentsIgnoreInterruptedTempSegmentFiles() {
        val dir = temporaryFolder.newFolder()
        writeSegment(dir, index = 0)
        File(dir, generatedAudiobookTempSegmentFileName(1)).writeBytes(ByteArray(4096) { 1 })

        assertEquals(1, reusableGeneratedAudiobookSegments(dir, expectedSegments = 3))
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

    @Test
    fun prepareAudiobookGenerationTargetClearsOnlyFreshGeneration() {
        val freshDir = temporaryFolder.newFolder()
        writeSegment(freshDir, index = 0)
        File(freshDir, generatedAudiobookTempSegmentFileName(1)).writeBytes(ByteArray(4096) { 1 })
        File(freshDir, "chapters.tsv").writeText("stale")

        prepareAudiobookGenerationTarget(target = freshDir, canResumeExistingAudio = false)

        assertTrue(freshDir.isDirectory)
        assertFalse(File(freshDir, generatedAudiobookSegmentFileName(0)).exists())
        assertFalse(File(freshDir, generatedAudiobookTempSegmentFileName(1)).exists())
        assertFalse(File(freshDir, "chapters.tsv").exists())

        val resumeDir = temporaryFolder.newFolder()
        writeSegment(resumeDir, index = 0)
        File(resumeDir, generatedAudiobookTempSegmentFileName(1)).writeBytes(ByteArray(4096) { 1 })
        File(resumeDir, "chapters.tsv").writeText("current")

        prepareAudiobookGenerationTarget(target = resumeDir, canResumeExistingAudio = true)

        assertTrue(File(resumeDir, generatedAudiobookSegmentFileName(0)).isFile)
        assertFalse(File(resumeDir, generatedAudiobookTempSegmentFileName(1)).exists())
        assertTrue(File(resumeDir, "chapters.tsv").isFile)
    }

    @Test
    fun generatedAudiobookExportManifestPrefersFinalButKeepsPartialManifest() {
        val finalDir = temporaryFolder.newFolder()
        val finalManifest = File(finalDir, "manifest.txt").apply { writeText("status=generated") }
        File(finalDir, "manifest.in-progress.txt").writeText("status=generating")

        assertEquals(finalManifest, finalDir.generatedAudiobookExportManifestFile())

        val partialDir = temporaryFolder.newFolder()
        val partialManifest = File(partialDir, "manifest.in-progress.txt").apply { writeText("status=canceled") }

        assertEquals(partialManifest, partialDir.generatedAudiobookExportManifestFile())
    }

    @Test
    fun recoveryManifestRewriteUpdatesStatusAndPreservesMetadata() {
        val rewritten = rewriteAudiobookManifestText(
            text = """
                title=Example Book
                model=Kokoro v1.0
                status=generating
                segments=12
                completed=7
                updatedAt=100
                error=old failure
            """.trimIndent(),
            status = BookAudioStatus.CANCELED,
            completedSegments = 3,
            updatedAt = 250,
            error = null
        )

        assertEquals(
            """
            title=Example Book
            model=Kokoro v1.0
            status=canceled
            segments=12
            completed=3
            updatedAt=250

            """.trimIndent(),
            rewritten
        )
    }

    @Test
    fun recoveryManifestRewriteAddsMissingFieldsAndFailureReason() {
        val rewritten = rewriteAudiobookManifestText(
            text = "title=Example Book\n",
            status = BookAudioStatus.FAILED,
            completedSegments = -4,
            updatedAt = 300,
            error = "Generated audio files are missing.\nTry again."
        )

        assertEquals(
            """
            title=Example Book
            status=failed
            completed=0
            updatedAt=300
            error=Generated audio files are missing.

            """.trimIndent(),
            rewritten
        )
    }

    @Test
    fun recoveryManifestRewriteUpdatesInProgressManifestFile() {
        val dir = temporaryFolder.newFolder()
        val manifest = File(dir, "manifest.in-progress.txt").apply {
            writeText(
                """
                title=Example Book
                status=generating
                completed=2
                updatedAt=100
                """.trimIndent()
            )
        }

        assertTrue(
            rewriteAudiobookRecoveryManifest(
                target = dir,
                status = BookAudioStatus.CANCELED,
                completedSegments = 1,
                updatedAt = 500,
                error = null
            )
        )
        assertEquals(
            """
            title=Example Book
            status=canceled
            completed=1
            updatedAt=500

            """.trimIndent(),
            manifest.readText()
        )
    }

    @Test
    fun generatedAudiobookDeletePolicyProtectsActiveGeneration() {
        assertFalse(audio(filePath = null).copy(status = BookAudioStatus.GENERATING).canDeleteGeneratedAudiobook())
        assertTrue(audio(filePath = null).copy(status = BookAudioStatus.GENERATED).canDeleteGeneratedAudiobook())
        assertTrue(audio(filePath = null).copy(status = BookAudioStatus.CANCELED).canDeleteGeneratedAudiobook())
        assertTrue(audio(filePath = null).copy(status = BookAudioStatus.FAILED).canDeleteGeneratedAudiobook())
    }

    @Test
    fun playableSegmentCountUsesCompletedSegmentsForPartials() {
        assertEquals(
            3,
            audio(filePath = null).copy(
                status = BookAudioStatus.CANCELED,
                segmentCount = 10,
                completedSegments = 3
            ).playableSegmentCount()
        )
        assertEquals(
            10,
            audio(filePath = null).copy(
                status = BookAudioStatus.GENERATED,
                segmentCount = 10,
                completedSegments = 3
            ).playableSegmentCount()
        )
    }

    @Test
    fun persistedPlaybackPositionKeepsFinishedStateOutOfResumePath() {
        assertEquals(
            GeneratedAudiobookPersistedPlaybackPosition(segmentIndex = 2, positionMs = 0),
            generatedAudiobookPersistedPlaybackPosition(
                requestedSegmentIndex = 2,
                positionMs = 9_000,
                segmentCount = 2
            )
        )
        assertEquals(
            GeneratedAudiobookPersistedPlaybackPosition(segmentIndex = 2, positionMs = 0),
            generatedAudiobookPersistedPlaybackPosition(
                requestedSegmentIndex = 5,
                positionMs = 9_000,
                segmentCount = 2
            )
        )
        assertEquals(
            GeneratedAudiobookPersistedPlaybackPosition(segmentIndex = 0, positionMs = 0),
            generatedAudiobookPersistedPlaybackPosition(
                requestedSegmentIndex = -1,
                positionMs = -20,
                segmentCount = 2
            )
        )
        assertEquals(
            GeneratedAudiobookPersistedPlaybackPosition(segmentIndex = 1, positionMs = 4_200),
            generatedAudiobookPersistedPlaybackPosition(
                requestedSegmentIndex = 1,
                positionMs = 4_200,
                segmentCount = 2
            )
        )
    }

    @Test
    fun persistedPlaybackPositionClearsWhenNoVerifiedSegmentsExist() {
        assertEquals(
            GeneratedAudiobookPersistedPlaybackPosition(segmentIndex = 0, positionMs = 0),
            generatedAudiobookPersistedPlaybackPosition(
                requestedSegmentIndex = 4,
                positionMs = 12_000,
                segmentCount = 0
            )
        )
    }

    @Test
    fun playbackStartIndexRestartsFinishedAudiobookFromBeginning() {
        assertEquals(0, generatedAudiobookStartSegmentIndex(requestedSegmentIndex = 4, segmentCount = 4))
        assertEquals(0, generatedAudiobookStartSegmentIndex(requestedSegmentIndex = 9, segmentCount = 4))
        assertEquals(0, generatedAudiobookStartSegmentIndex(requestedSegmentIndex = -1, segmentCount = 4))
        assertEquals(2, generatedAudiobookStartSegmentIndex(requestedSegmentIndex = 2, segmentCount = 4))
        assertEquals(0, generatedAudiobookStartSegmentIndex(requestedSegmentIndex = 0, segmentCount = 0))
    }

    @Test
    fun positionPersistCoalescingKeepsLatestPositionForSameSegment() {
        val pending = PendingGeneratedAudiobookPositionPersist(
            audioId = 7,
            segmentIndex = 2,
            positionMs = 5_000,
            playableSegmentCount = 10
        )
        val newer = PendingGeneratedAudiobookPositionPersist(
            audioId = 7,
            segmentIndex = 2,
            positionMs = 8_000,
            playableSegmentCount = 10
        )
        val older = PendingGeneratedAudiobookPositionPersist(
            audioId = 7,
            segmentIndex = 2,
            positionMs = 4_000,
            playableSegmentCount = 10
        )

        assertEquals(newer, pending.coalescedWith(newer))
        assertEquals(pending, pending.coalescedWith(older))
    }

    @Test
    fun positionPersistCoalescingKeepsForwardProgressAndCurrentAudio() {
        val pending = PendingGeneratedAudiobookPositionPersist(
            audioId = 7,
            segmentIndex = 2,
            positionMs = 5_000,
            playableSegmentCount = 10
        )
        val nextSegment = PendingGeneratedAudiobookPositionPersist(
            audioId = 7,
            segmentIndex = 3,
            positionMs = 0,
            playableSegmentCount = 10
        )
        val differentAudio = PendingGeneratedAudiobookPositionPersist(
            audioId = 8,
            segmentIndex = 0,
            positionMs = 1_000,
            playableSegmentCount = 4
        )

        assertEquals(nextSegment, pending.coalescedWith(nextSegment))
        assertEquals(differentAudio, pending.coalescedWith(differentAudio))
        assertEquals(pending, (null as PendingGeneratedAudiobookPositionPersist?).coalescedWith(pending))
    }

    @Test
    fun positionPersistQueueStartsOneDrainAndDeliversLatestPendingPosition() {
        val queue = GeneratedAudiobookPositionPersistQueue()
        val first = PendingGeneratedAudiobookPositionPersist(
            audioId = 7,
            segmentIndex = 2,
            positionMs = 5_000,
            playableSegmentCount = 10
        )
        val second = first.copy(positionMs = 8_000)

        assertTrue(queue.offer(first, coalesce = true))
        assertEquals(false, queue.offer(second, coalesce = true))
        assertEquals(second, queue.poll())
        assertNull(queue.poll())
        assertTrue(queue.offer(first, coalesce = true))
    }

    @Test
    fun positionPersistQueueCanRecoverAfterCanceledDrain() {
        val queue = GeneratedAudiobookPositionPersistQueue()
        val first = PendingGeneratedAudiobookPositionPersist(
            audioId = 7,
            segmentIndex = 2,
            positionMs = 5_000,
            playableSegmentCount = 10
        )
        val second = first.copy(segmentIndex = 3, positionMs = 0)

        assertTrue(queue.offer(first, coalesce = true))
        queue.finishCanceledDrain()

        assertTrue(queue.offer(second, coalesce = true))
        assertEquals(second, queue.poll())
    }

    @Test
    fun playbackStateEmissionSkipsIdenticalStateOnly() {
        val current = AudiobookPlaybackUiState(
            audioId = 7,
            bookId = 9,
            bookTitle = "Book",
            playing = true,
            segmentIndex = 1,
            segmentCount = 4,
            segmentPositionMs = 12_000,
            segmentDurationMs = 60_000
        )

        assertFalse(shouldEmitAudiobookPlaybackState(current, current.copy()))
        assertTrue(shouldEmitAudiobookPlaybackState(current, current.copy(segmentPositionMs = 13_000)))
        assertTrue(shouldEmitAudiobookPlaybackState(current, current.copy(playing = false)))
    }

    @Test
    fun playbackPositionPersistenceSkipsIdenticalPositionOnly() {
        val audio = audio(filePath = null).copy(
            playbackSegmentIndex = 2,
            playbackPositionMs = 8_500
        )
        val current = GeneratedAudiobookPersistedPlaybackPosition(
            segmentIndex = 2,
            positionMs = 8_500
        )

        assertFalse(shouldPersistGeneratedAudiobookPlaybackPosition(audio, current))
        assertTrue(shouldPersistGeneratedAudiobookPlaybackPosition(audio, current.copy(positionMs = 9_000)))
        assertTrue(shouldPersistGeneratedAudiobookPlaybackPosition(audio, current.copy(segmentIndex = 3, positionMs = 0)))
    }

    @Test
    fun bookAudioPlaybackBoundingUsesVerifiedGeneratedSegments() {
        val stale = audio(filePath = null).copy(
            segmentCount = 10,
            completedSegments = 10,
            playbackSegmentIndex = 8,
            playbackPositionMs = 12_000
        )

        assertEquals(
            stale.copy(playbackSegmentIndex = 3, playbackPositionMs = 0),
            stale.withPlaybackBoundedToGeneratedAudio(playableSegments = 3)
        )
        assertEquals(
            stale.copy(playbackSegmentIndex = 0, playbackPositionMs = 0),
            stale.withPlaybackBoundedToGeneratedAudio(playableSegments = 0)
        )
        assertEquals(
            stale.copy(playbackSegmentIndex = 8, playbackPositionMs = 12_000),
            stale.withPlaybackBoundedToGeneratedAudio(playableSegments = 10)
        )
    }

    @Test
    fun playableSegmentFilesReturnsOnlyVerifiedContiguousSegments() {
        val dir = temporaryFolder.newFolder()
        writeSegment(dir, index = 0)
        writeSegment(dir, index = 2)
        writeSegment(dir, index = 3)

        val files = audio(filePath = dir.absolutePath).copy(
            status = BookAudioStatus.CANCELED,
            segmentCount = 4,
            completedSegments = 4
        ).playableSegmentFiles()

        assertEquals(listOf(generatedAudiobookSegmentFileName(0)), files.map { it.name })
    }

    @Test
    fun verifiedPlayableSegmentCountUsesOnlyContiguousFiles() {
        val dir = temporaryFolder.newFolder()
        writeSegment(dir, index = 0)
        writeSegment(dir, index = 2)

        assertEquals(
            1,
            audio(filePath = dir.absolutePath).copy(
                status = BookAudioStatus.CANCELED,
                segmentCount = 4,
                completedSegments = 4
            ).verifiedPlayableSegmentCount()
        )
        assertEquals(
            0,
            audio(filePath = File(temporaryFolder.root, "missing").absolutePath).copy(
                status = BookAudioStatus.CANCELED,
                segmentCount = 4,
                completedSegments = 4
            ).verifiedPlayableSegmentCount()
        )
    }

    @Test
    fun verifiedGeneratedSegmentsRecoverWhenDatabaseCompletedCountIsStale() {
        val dir = temporaryFolder.newFolder()
        writeSegment(dir, index = 0)
        writeSegment(dir, index = 1)
        writeSegment(dir, index = 2)

        val staleAudio = audio(filePath = dir.absolutePath).copy(
            status = BookAudioStatus.GENERATING,
            segmentCount = 5,
            completedSegments = 0
        )

        assertTrue(staleAudio.playableSegmentFiles().isEmpty())
        assertEquals(3, staleAudio.verifiedGeneratedSegmentFiles().size)
        assertEquals(3, staleAudio.withVerifiedGeneratedProgress().completedSegments)
        assertEquals(3, staleAudio.withVerifiedGeneratedProgress().playableSegmentFiles().size)
    }

    @Test
    fun generatedAudiobookFileSnapshotKeepsActiveGenerationBoundedToCompletedProgress() {
        val dir = temporaryFolder.newFolder()
        writeSegment(dir, index = 0)
        writeSegment(dir, index = 1)

        val snapshot = audio(filePath = dir.absolutePath).copy(
            status = BookAudioStatus.GENERATING,
            scopeLabel = "First chapter",
            segmentCount = 4,
            completedSegments = 0
        ).generatedAudiobookFileSnapshot()

        assertEquals(0, snapshot.audio.completedSegments)
        assertEquals(0, snapshot.playableSegmentCount)
        assertTrue(snapshot.playableSegmentFiles.isEmpty())
        assertTrue(snapshot.chapters.isEmpty())
    }

    @Test
    fun generatedAudiobookFileSnapshotUsesVerifiedFilesAndRecoveredProgressWhenStopped() {
        val dir = temporaryFolder.newFolder()
        writeSegment(dir, index = 0)
        writeSegment(dir, index = 1)

        val snapshot = audio(filePath = dir.absolutePath).copy(
            status = BookAudioStatus.CANCELED,
            scopeLabel = "First chapter",
            segmentCount = 4,
            completedSegments = 0
        ).generatedAudiobookFileSnapshot()

        assertEquals(2, snapshot.audio.completedSegments)
        assertEquals(2, snapshot.playableSegmentCount)
        assertEquals(listOf("First chapter"), snapshot.chapters.map { it.title })
        assertEquals(2, snapshot.chapters.single().segmentCount)
    }

    @Test
    fun playableSegmentFilesRejectsHeaderOnlyAndMissingDirectories() {
        val dir = temporaryFolder.newFolder()
        File(dir, generatedAudiobookSegmentFileName(0)).writeBytes(ByteArray(44))

        assertTrue(
            audio(filePath = dir.absolutePath).copy(
                status = BookAudioStatus.CANCELED,
                segmentCount = 1,
                completedSegments = 1
            ).playableSegmentFiles().isEmpty()
        )
        assertTrue(audio(filePath = null).playableSegmentFiles().isEmpty())
    }

    @Test
    fun completePlayableAudiobookRequiresEveryGeneratedSegment() {
        val completeDir = temporaryFolder.newFolder()
        writeSegment(completeDir, index = 0)
        writeSegment(completeDir, index = 1)

        assertTrue(
            audio(filePath = completeDir.absolutePath).copy(
                status = BookAudioStatus.GENERATED,
                segmentCount = 2,
                completedSegments = 2
            ).hasCompletePlayableAudiobook()
        )

        val sparseDir = temporaryFolder.newFolder()
        writeSegment(sparseDir, index = 0)
        writeSegment(sparseDir, index = 2)

        assertFalse(
            audio(filePath = sparseDir.absolutePath).copy(
                status = BookAudioStatus.GENERATED,
                segmentCount = 3,
                completedSegments = 3
            ).hasCompletePlayableAudiobook()
        )
    }

    @Test
    fun bestPlayableAudiobookPrefersFullBookThenChapterThenSample() {
        val sampleDir = temporaryFolder.newFolder()
        val chapterDir = temporaryFolder.newFolder()
        val fullDir = temporaryFolder.newFolder()
        writeSegment(sampleDir, index = 0)
        writeSegment(chapterDir, index = 0)
        writeSegment(chapterDir, index = 1)
        writeSegment(fullDir, index = 0)

        val sample = audio(filePath = sampleDir.absolutePath).copy(
            id = 1,
            scope = AudiobookGenerationScope.SAMPLE.key,
            scopeLabel = AudiobookGenerationScope.SAMPLE.label,
            segmentCount = 1,
            completedSegments = 1,
            updatedAt = 30L
        )
        val chapter = audio(filePath = chapterDir.absolutePath).copy(
            id = 2,
            scope = AudiobookGenerationScope.FIRST_CHAPTER.key,
            scopeLabel = AudiobookGenerationScope.FIRST_CHAPTER.label,
            segmentCount = 2,
            completedSegments = 2,
            updatedAt = 20L
        )
        val full = audio(filePath = fullDir.absolutePath).copy(
            id = 3,
            scope = AudiobookGenerationScope.FULL_BOOK.key,
            scopeLabel = AudiobookGenerationScope.FULL_BOOK.label,
            segmentCount = 1,
            completedSegments = 1,
            updatedAt = 10L
        )

        assertEquals(
            full.id,
            listOf(sample, chapter, full).bestPlayableAudiobookForProfile(
                modelId = "voice",
                speakerId = 0,
                speed = 1.0f,
                tone = "NATURAL"
            )?.id
        )
        assertEquals(
            chapter.id,
            listOf(sample, chapter).bestPlayableAudiobookForProfile(
                modelId = "voice",
                speakerId = 0,
                speed = 1.0f,
                tone = "NATURAL"
            )?.id
        )
    }

    @Test
    fun bestPlayableAudiobookRejectsWrongProfileAndMissingFiles() {
        val playableDir = temporaryFolder.newFolder()
        writeSegment(playableDir, index = 0)
        val playable = audio(filePath = playableDir.absolutePath).copy(
            id = 1,
            scope = AudiobookGenerationScope.SAMPLE.key,
            segmentCount = 1,
            completedSegments = 1
        )
        val missing = audio(filePath = File(temporaryFolder.root, "missing").absolutePath).copy(
            id = 2,
            scope = AudiobookGenerationScope.FULL_BOOK.key,
            segmentCount = 10,
            completedSegments = 10
        )

        assertEquals(
            playable.id,
            listOf(missing, playable).bestPlayableAudiobookForProfile(
                modelId = "voice",
                speakerId = 0,
                speed = 1.0f,
                tone = "NATURAL"
            )?.id
        )
        assertEquals(
            null,
            listOf(playable).bestPlayableAudiobookForProfile(
                modelId = "other",
                speakerId = 0,
                speed = 1.0f,
                tone = "NATURAL"
            )
        )
    }

    @Test
    fun segmentMetadataEscapesTsvControlCharacters() {
        assertEquals(
            "Chapter\\\\One\\tLine\\nNext\\rDone",
            "Chapter\\One\tLine\nNext\rDone".tsvEscaped()
        )
    }

    @Test
    fun generatedAudiobookChapterSidecarTextUsesEscapedChapterMetadata() {
        val chapters = listOf(
            GeneratedAudiobookChapter(index = 0, title = "Intro\tOne", firstSegmentIndex = 0, segmentCount = 2),
            GeneratedAudiobookChapter(index = 1, title = "Chapter\\Two", firstSegmentIndex = 2, segmentCount = 3)
        )

        assertEquals(
            """
            index	firstSegment	segmentCount	title
            0	0	2	Intro\tOne
            1	2	3	Chapter\\Two

            """.trimIndent(),
            chapters.toGeneratedAudiobookChaptersTsv()
        )
    }

    @Test
    fun generatedAudiobookFallbackSegmentsSidecarTextKeepsPlayableSegmentOrder() {
        assertEquals(
            """
            index	chapterIndex	pauseAfterMs	text
            0	0	240	
            1	0	240	
            2	1	240	

            """.trimIndent(),
            generatedAudiobookFallbackSegmentsTsv(segmentCount = 3, chapterIndexes = listOf(0, 0, 1))
        )
    }

    @Test
    fun generatedAudiobookExportSegmentsSidecarKeepsOnlyPlayableRows() {
        val dir = temporaryFolder.newFolder()
        val sidecar = File(dir, "segments.tsv").apply {
            writeText(
                """
                index	chapterIndex	pauseAfterMs	text
                0	0	240	one
                1	0	360	two
                2	1	480	three
                3	1	600	four
                """.trimIndent()
            )
        }

        assertEquals(
            """
            index	chapterIndex	pauseAfterMs	text
            0	0	240	one
            1	0	360	two

            """.trimIndent(),
            sidecar.generatedAudiobookExportSegmentsTsv(segmentCount = 2, chapterIndexes = listOf(0, 0))
        )
    }

    @Test
    fun generatedAudiobookExportSegmentsSidecarFillsMissingPlayableRows() {
        val dir = temporaryFolder.newFolder()
        val sidecar = File(dir, "segments.tsv").apply {
            writeText(
                """
                index	chapterIndex	pauseAfterMs	text
                0	0	240	one
                3	1	600	four
                """.trimIndent()
            )
        }

        assertEquals(
            """
            index	chapterIndex	pauseAfterMs	text
            0	0	240	one
            1	0	240	
            2	1	240	

            """.trimIndent(),
            sidecar.generatedAudiobookExportSegmentsTsv(segmentCount = 3, chapterIndexes = listOf(0, 0, 1))
        )
    }

    @Test
    fun generatedAudiobookSegmentMetadataReadsChapterPauseAndExportRowsTogether() {
        val dir = temporaryFolder.newFolder()
        val sidecar = File(dir, "segments.tsv").apply {
            writeText(
                """
                index	chapterIndex	pauseAfterMs	text
                0	0	180	one
                1	99	220	two
                2	1	520	three
                4	1	900	out of range
                """.trimIndent()
            )
        }
        val chapters = listOf(
            GeneratedAudiobookChapter(index = 0, title = "Chapter 1", firstSegmentIndex = 0, segmentCount = 2),
            GeneratedAudiobookChapter(index = 1, title = "Chapter 2", firstSegmentIndex = 2, segmentCount = 1)
        )

        val metadata = sidecar.generatedAudiobookSegmentMetadata(segmentCount = 3, chapters = chapters)

        assertEquals(listOf(0, 0, 1), metadata.chapterIndexes)
        assertEquals(listOf(180L, 220L, 520L), metadata.pauseAfterMillis)
        assertEquals(
            """
            index	chapterIndex	pauseAfterMs	text
            0	0	180	one
            1	99	220	two
            2	1	520	three

            """.trimIndent(),
            metadata.exportTsv
        )
    }

    @Test
    fun generatedAudiobookChaptersReadSidecarAndNavigateBoundaries() {
        val dir = temporaryFolder.newFolder()
        repeat(5) { index -> writeSegment(dir, index) }
        File(dir, "chapters.tsv").writeText(
            """
            index	firstSegment	segmentCount	title
            0	0	2	Chapter 1
            1	2	3	Chapter\tTwo
            """.trimIndent()
        )
        val chapters = audio(filePath = dir.absolutePath).copy(
            segmentCount = 5,
            completedSegments = 5
        ).generatedAudiobookChapters()

        assertEquals(listOf("Chapter 1", "Chapter\tTwo"), chapters.map { it.title })
        assertEquals("Chapter 1", chapters.chapterForSegment(1)?.title)
        assertEquals("Chapter\tTwo", chapters.chapterForSegment(3)?.title)
        assertEquals(2, chapters.nextChapterStart(1))
        assertEquals(2, chapters.previousChapterStart(4))
        assertEquals(0, chapters.previousChapterStart(2))
    }

    @Test
    fun generatedAudiobookChaptersFallbackToScopeWhenSidecarMissing() {
        val dir = temporaryFolder.newFolder()
        repeat(3) { index -> writeSegment(dir, index) }

        val chapters = audio(filePath = dir.absolutePath).copy(
            scope = AudiobookGenerationScope.FIRST_CHAPTER.key,
            scopeLabel = AudiobookGenerationScope.FIRST_CHAPTER.label,
            segmentCount = 5,
            completedSegments = 3
        ).generatedAudiobookChapters()

        assertEquals(listOf("First chapter"), chapters.map { it.title })
        assertEquals(listOf(0), chapters.map { it.firstSegmentIndex })
        assertEquals(listOf(3), chapters.map { it.segmentCount })
    }

    @Test
    fun generatedAudiobookChaptersFallbackWhenSidecarInvalidButAudioPlayable() {
        val dir = temporaryFolder.newFolder()
        repeat(2) { index -> writeSegment(dir, index) }
        File(dir, "chapters.tsv").writeText(
            """
            index	firstSegment	segmentCount	title
            broken
            9	99	1	Out of range
            """.trimIndent()
        )

        val chapters = audio(filePath = dir.absolutePath).copy(
            scope = AudiobookGenerationScope.SAMPLE.key,
            scopeLabel = AudiobookGenerationScope.SAMPLE.label,
            segmentCount = 2,
            completedSegments = 2
        ).generatedAudiobookChapters()

        assertEquals(listOf("Sample"), chapters.map { it.title })
        assertEquals(listOf(2), chapters.map { it.segmentCount })
    }

    @Test
    fun generatedAudiobookChaptersDoNotFallbackWithoutPlayableAudio() {
        val dir = temporaryFolder.newFolder()

        val chapters = audio(filePath = dir.absolutePath).copy(
            segmentCount = 3,
            completedSegments = 3
        ).generatedAudiobookChapters()

        assertTrue(chapters.isEmpty())
    }

    @Test
    fun generatedAudiobookChaptersSanitizeCorruptOverlappingSidecarRows() {
        val dir = temporaryFolder.newFolder()
        repeat(4) { index -> writeSegment(dir, index) }
        File(dir, "chapters.tsv").writeText(
            """
            index	firstSegment	segmentCount	title
            7	0	3	Opening
            8	2	3	Overlap
            9	2	1	Duplicate
            10	6	2	Out of range
            """.trimIndent()
        )

        val chapters = audio(filePath = dir.absolutePath).copy(
            segmentCount = 4,
            completedSegments = 4
        ).generatedAudiobookChapters()

        assertEquals(listOf("Opening", "Overlap"), chapters.map { it.title })
        assertEquals(listOf(0, 1), chapters.map { it.index })
        assertEquals(listOf(0, 3), chapters.map { it.firstSegmentIndex })
        assertEquals(listOf(3, 1), chapters.map { it.segmentCount })
    }

    @Test
    fun segmentChapterIndexesIgnoreInvalidSidecarChapterIds() {
        val dir = temporaryFolder.newFolder()
        repeat(4) { index -> writeSegment(dir, index) }
        File(dir, "chapters.tsv").writeText(
            """
            index	firstSegment	segmentCount	title
            0	0	2	Chapter 1
            1	2	2	Chapter 2
            """.trimIndent()
        )
        File(dir, "segments.tsv").writeText(
            """
            index	chapterIndex	pauseAfterMs	text
            0	0	240	one
            1	99	240	two
            2	1	240	three
            3	-5	240	four
            """.trimIndent()
        )
        val audio = audio(filePath = dir.absolutePath).copy(
            segmentCount = 4,
            completedSegments = 4
        )
        val chapters = audio.generatedAudiobookChapters()

        assertEquals(listOf(0, 0, 1, 1), audio.generatedAudiobookSegmentChapterIndexes(4, chapters))
    }

    @Test
    fun generationProgressWritesEveryNewCompletedSegmentForShortJobs() {
        assertTrue(
            shouldWriteGenerationProgress(
                completedSegments = 1,
                totalSegments = 20,
                lastProgressWrittenSegments = 0
            )
        )
        assertTrue(
            shouldWriteGenerationProgress(
                completedSegments = 2,
                totalSegments = 20,
                lastProgressWrittenSegments = 1
            )
        )
        assertTrue(
            shouldWriteGenerationProgress(
                completedSegments = 3,
                totalSegments = 20,
                lastProgressWrittenSegments = 2
            )
        )
        assertTrue(
            shouldWriteGenerationProgress(
                completedSegments = 20,
                totalSegments = 20,
                lastProgressWrittenSegments = 19
            )
        )
    }

    @Test
    fun generationProgressCoalescesLongJobsWithoutLosingFirstOrFinalUpdate() {
        assertEquals(4, generationProgressWriteSegmentStep(120))
        assertEquals(12, generationProgressWriteSegmentStep(1_200))
        assertTrue(
            shouldWriteGenerationProgress(
                completedSegments = 1,
                totalSegments = 400,
                lastProgressWrittenSegments = 0
            )
        )
        assertFalse(
            shouldWriteGenerationProgress(
                completedSegments = 2,
                totalSegments = 400,
                lastProgressWrittenSegments = 1
            )
        )
        assertFalse(
            shouldWriteGenerationProgress(
                completedSegments = 4,
                totalSegments = 400,
                lastProgressWrittenSegments = 1
            )
        )
        assertTrue(
            shouldWriteGenerationProgress(
                completedSegments = 5,
                totalSegments = 400,
                lastProgressWrittenSegments = 1
            )
        )
        assertTrue(
            shouldWriteGenerationProgress(
                completedSegments = 400,
                totalSegments = 400,
                lastProgressWrittenSegments = 397
            )
        )
    }

    @Test
    fun generationProgressSkipsDuplicateAndInvalidUpdates() {
        assertFalse(
            shouldWriteGenerationProgress(
                completedSegments = 0,
                totalSegments = 20,
                lastProgressWrittenSegments = 0
            )
        )
        assertFalse(
            shouldWriteGenerationProgress(
                completedSegments = 8,
                totalSegments = 20,
                lastProgressWrittenSegments = 8
            )
        )
        assertFalse(
            shouldWriteGenerationProgress(
                completedSegments = 21,
                totalSegments = 20,
                lastProgressWrittenSegments = 20
            )
        )
    }

    @Test
    fun generationManifestCheckpointsStayFrequentForShortJobs() {
        assertEquals(4, generationManifestCheckpointSegmentStep(12))
        assertTrue(shouldWriteGenerationCheckpoint(completedSegments = 1, totalSegments = 12))
        assertFalse(shouldWriteGenerationCheckpoint(completedSegments = 2, totalSegments = 12))
        assertTrue(shouldWriteGenerationCheckpoint(completedSegments = 4, totalSegments = 12))
        assertTrue(shouldWriteGenerationCheckpoint(completedSegments = 8, totalSegments = 12))
        assertTrue(shouldWriteGenerationCheckpoint(completedSegments = 12, totalSegments = 12))
    }

    @Test
    fun generationManifestCheckpointsCoalesceLongJobsWithoutLosingFirstOrFinalWrite() {
        assertEquals(12, generationManifestCheckpointSegmentStep(1_200))
        assertTrue(shouldWriteGenerationCheckpoint(completedSegments = 1, totalSegments = 1_200))
        assertFalse(shouldWriteGenerationCheckpoint(completedSegments = 4, totalSegments = 1_200))
        assertTrue(shouldWriteGenerationCheckpoint(completedSegments = 12, totalSegments = 1_200))
        assertFalse(shouldWriteGenerationCheckpoint(completedSegments = 1_196, totalSegments = 1_200))
        assertTrue(shouldWriteGenerationCheckpoint(completedSegments = 1_200, totalSegments = 1_200))
    }

    @Test
    fun generatedAudiobookKnownFilesSizeCountsSegmentsAndSidecarsOnly() {
        val dir = temporaryFolder.newFolder()
        writeSegment(dir, index = 0)
        writeSegment(dir, index = 1)
        writeSegment(dir, index = 2)
        File(dir, "manifest.txt").writeText("final")
        File(dir, "manifest.in-progress.txt").writeText("progress")
        File(dir, "chapters.tsv").writeText("chapters")
        File(dir, "segments.tsv").writeText("segments")
        File(dir, "unrelated.tmp").writeBytes(ByteArray(100))
        temporaryFolder.newFolder("nested").also { nested ->
            File(nested, "ignored.bin").writeBytes(ByteArray(100))
        }

        val expected = 64L + 64L + "final".length + "progress".length + "chapters".length + "segments".length

        assertEquals(expected, dir.generatedAudiobookKnownFilesSizeBytes(completedSegments = 2))
        assertEquals(expected, audio(filePath = dir.absolutePath).generatedAudiobookKnownFilesSizeBytes(completedSegments = 2))
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
