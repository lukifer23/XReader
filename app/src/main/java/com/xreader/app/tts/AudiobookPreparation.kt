package com.xreader.app.tts

import com.xreader.app.data.SearchIndexEntity

internal data class PreparedAudiobookPlan(
    val chunks: List<ReadAloudChunk>,
    val prepared: NeuralTtsPreparedBook,
    val sourceSectionCount: Int,
    val report: NarrationPreparationReport,
)

internal fun prepareAudiobookPlan(
    rows: List<SearchIndexEntity>,
    scope: AudiobookGenerationScope = AudiobookGenerationScope.FULL_BOOK,
    languageTag: String? = null,
    pronunciationRules: List<NarrationPronunciationRule> = emptyList(),
    overrides: List<NarrationTextOverride> = emptyList(),
): PreparedAudiobookPlan {
    val chunks = ReadAloudPlanner.chunksFromRows(rows)
    val scopedChunks = chunks.forAudiobookScope(scope)
    val report = prepareNarrationReport(scopedChunks, languageTag, pronunciationRules, overrides)
    val prepared = report.prepared.forScope(scope)
    require(prepared.segments.isNotEmpty()) {
        "This book has no extractable text for audiobook generation."
    }
    return PreparedAudiobookPlan(
        chunks = scopedChunks,
        prepared = prepared,
        sourceSectionCount = rows.size,
        report = report.copy(prepared = prepared),
    )
}
