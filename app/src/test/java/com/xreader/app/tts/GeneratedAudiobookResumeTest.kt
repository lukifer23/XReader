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

    @Test
    fun prepareAudiobookGenerationTargetClearsOnlyFreshGeneration() {
        val freshDir = temporaryFolder.newFolder()
        writeSegment(freshDir, index = 0)
        File(freshDir, "chapters.tsv").writeText("stale")

        prepareAudiobookGenerationTarget(target = freshDir, canResumeExistingAudio = false)

        assertTrue(freshDir.isDirectory)
        assertFalse(File(freshDir, generatedAudiobookSegmentFileName(0)).exists())
        assertFalse(File(freshDir, "chapters.tsv").exists())

        val resumeDir = temporaryFolder.newFolder()
        writeSegment(resumeDir, index = 0)
        File(resumeDir, "chapters.tsv").writeText("current")

        prepareAudiobookGenerationTarget(target = resumeDir, canResumeExistingAudio = true)

        assertTrue(File(resumeDir, generatedAudiobookSegmentFileName(0)).isFile)
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
