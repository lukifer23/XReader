package com.xreader.app.annotations

data class AnnotationTagSummary(
    val label: String,
    val count: Int,
)

internal fun normalizeAnnotationTags(value: String): String =
    value.split(',', '\n')
        .map { it.trim().trimStart('#').replace(Regex("\\s+"), " ") }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .joinToString(", ")

internal fun annotationTags(value: String): List<String> =
    normalizeAnnotationTags(value)
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }

internal fun annotationTagsLabel(value: String): String =
    normalizeAnnotationTags(value)
        .takeIf { it.isNotBlank() }
        ?.let { "Tags: $it" }
        .orEmpty()

internal fun summarizeAnnotationTags(values: Iterable<String>): List<AnnotationTagSummary> {
    val counts = linkedMapOf<String, Pair<String, Int>>()
    values.flatMap(::annotationTags).forEach { tag ->
        val key = tag.lowercase()
        val current = counts[key]
        counts[key] = if (current == null) {
            tag to 1
        } else {
            current.first to current.second + 1
        }
    }
    return counts.values
        .map { (label, count) -> AnnotationTagSummary(label = label, count = count) }
        .sortedWith(compareByDescending<AnnotationTagSummary> { it.count }.thenBy { it.label.lowercase() })
}

internal fun tagMatches(value: String, selectedTag: String?): Boolean =
    selectedTag == null || annotationTags(value).any { it.equals(selectedTag, ignoreCase = true) }
