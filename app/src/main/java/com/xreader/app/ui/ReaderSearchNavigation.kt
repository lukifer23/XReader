package com.xreader.app.ui

import com.xreader.app.reader.ReaderSearchResult

internal data class ReaderSearchNavigationState(
    val label: String,
    val previousIndex: Int,
    val nextIndex: Int,
)

internal fun readerSearchNavigationState(
    currentUnit: Int,
    results: List<ReaderSearchResult>,
    activeIndex: Int?,
): ReaderSearchNavigationState? {
    if (results.isEmpty()) return null
    val safeActiveIndex = activeIndex?.takeIf { it in results.indices }
    val previous = safeActiveIndex?.let { (it - 1 + results.size) % results.size }
        ?: previousSearchResultIndex(currentUnit, results)
    val next = safeActiveIndex?.let { (it + 1) % results.size }
        ?: nextSearchResultIndex(currentUnit, results)
    val label = safeActiveIndex?.let { "${it + 1} of ${results.size}" }
        ?: "${results.size} ${if (results.size == 1) "match" else "matches"}"
    return ReaderSearchNavigationState(
        label = label,
        previousIndex = previous,
        nextIndex = next
    )
}

private fun previousSearchResultIndex(
    currentUnit: Int,
    results: List<ReaderSearchResult>,
): Int =
    results.indices
        .filter { results[it].sortableUnit(it) < currentUnit }
        .maxByOrNull { results[it].sortableUnit(it) }
        ?: results.lastIndex

private fun nextSearchResultIndex(
    currentUnit: Int,
    results: List<ReaderSearchResult>,
): Int =
    results.indices
        .filter { results[it].sortableUnit(it) >= currentUnit }
        .minByOrNull { results[it].sortableUnit(it) }
        ?: 0

private fun ReaderSearchResult.sortableUnit(fallback: Int): Int =
    unitIndex ?: fallback
