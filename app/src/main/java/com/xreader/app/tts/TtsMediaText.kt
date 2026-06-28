package com.xreader.app.tts

private val TTS_MEDIA_WHITESPACE_REGEX = Regex("\\s+")

internal fun normalizedTtsMediaSubtitle(value: String?): String? =
    value
        ?.replace(TTS_MEDIA_WHITESPACE_REGEX, " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
