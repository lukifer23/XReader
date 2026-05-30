package com.xreader.app.annotations

internal fun normalizeAnnotationTags(value: String): String =
    value.split(',', '\n')
        .map { it.trim().trimStart('#').replace(Regex("\\s+"), " ") }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .joinToString(", ")

internal fun annotationTagsLabel(value: String): String =
    normalizeAnnotationTags(value)
        .takeIf { it.isNotBlank() }
        ?.let { "Tags: $it" }
        .orEmpty()
