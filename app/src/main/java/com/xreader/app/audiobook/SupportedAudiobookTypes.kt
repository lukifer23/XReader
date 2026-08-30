package com.xreader.app.audiobook

object SupportedAudiobookTypes {
    val extensions: Set<String> = setOf("mp3", "m4b", "m4a", "aac", "ogg", "opus", "flac", "wav")

    val mimeTypes: Set<String> = setOf(
        "audio/mpeg",
        "audio/mp4",
        "audio/aac",
        "audio/ogg",
        "audio/opus",
        "audio/flac",
        "audio/x-flac",
        "audio/wav",
        "audio/x-wav",
    )

    val pickerMimeTypes: Array<String> = (mimeTypes + "audio/*").toTypedArray()

    fun extensionForMimeType(mimeType: String): String = when (mimeType.normalized()) {
        "audio/mpeg" -> "mp3"
        "audio/mp4" -> "m4a"
        "audio/aac" -> "aac"
        "audio/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/wav", "audio/x-wav" -> "wav"
        else -> ""
    }

    fun isSupported(fileName: String, mimeType: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in extensions || mimeType.normalized() in mimeTypes
    }

    private fun String.normalized(): String = substringBefore(';').trim().lowercase()
}
