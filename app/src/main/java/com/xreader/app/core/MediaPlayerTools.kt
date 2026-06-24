package com.xreader.app.core

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun speechMediaPlayerForFile(
    file: File,
    usage: Int,
): MediaPlayer {
    val player = MediaPlayer()
    return try {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        player.setDataSource(file.absolutePath)
        player
    } catch (error: Throwable) {
        runCatching { player.release() }
        throw error
    }
}

internal fun MediaPlayer.releaseQuietly() {
    runCatching {
        setOnPreparedListener(null)
        setOnCompletionListener(null)
        setOnErrorListener(null)
        release()
    }
}

internal fun MediaPlayer.releaseQuietlyAsync(scope: CoroutineScope): Job =
    scope.launch(Dispatchers.IO) {
        releaseQuietly()
    }
