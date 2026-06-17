package com.xreader.app.importer

import com.xreader.app.core.TextTools
import java.net.URLDecoder

internal fun importSourceTitle(displayName: String, sourceExtension: String): String {
    val fileName = displayName
        .substringAfterLast('/')
        .substringBefore('?')
        .substringBefore('#')
        .ifBlank { displayName }
    val withoutExtension = if (
        sourceExtension.isNotBlank() &&
        fileName.endsWith(".$sourceExtension", ignoreCase = true)
    ) {
        fileName.dropLast(sourceExtension.length + 1)
    } else {
        fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
    }
    return TextTools.cleanTitle(
        withoutExtension
            .urlDecodeFileName()
            .replace('_', ' ')
    )
}

private fun String.urlDecodeFileName(): String =
    runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }
        .getOrDefault(this)
