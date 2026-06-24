package com.xreader.app.tts

import com.xreader.app.data.SearchIndexEntity

internal data class PreparedAudiobookPlan(
    val chunks: List<ReadAloudChunk>,
    val prepared: NeuralTtsPreparedBook,
    val sourceSectionCount: Int,
)

internal fun prepareAudiobookPlan(
    rows: List<SearchIndexEntity>,
    scope: AudiobookGenerationScope = AudiobookGenerationScope.FULL_BOOK,
): PreparedAudiobookPlan {
    val chunks = ReadAloudPlanner.chunksFromRows(rows)
    val scopedChunks = chunks.forAudiobookScope(scope)
    val prepared = NeuralTtsText.prepare(scopedChunks).forScope(scope)
    require(prepared.segments.isNotEmpty()) {
        "This book has no extractable text for audiobook generation."
    }
    return PreparedAudiobookPlan(
        chunks = scopedChunks,
        prepared = prepared,
        sourceSectionCount = rows.size
    )
}
