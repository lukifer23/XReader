package com.xreader.app.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun releaseNeuralPreviewPlayback(player: MediaPlayer?): MediaPlayer? {
    player?.let { runCatching { it.release() } }
    return null
}

internal suspend fun startNeuralPreviewPlayback(
    file: File,
    previousPlayer: MediaPlayer?,
    onCleared: (MediaPlayer) -> Unit,
): MediaPlayer {
    releaseNeuralPreviewPlayback(previousPlayer)
    val player = withContext(Dispatchers.IO) {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(file.absolutePath)
        }
    }
    return try {
        withContext(Dispatchers.Main.immediate) {
            player.apply {
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    it.release()
                    onCleared(it)
                }
                setOnErrorListener { failedPlayer, _, _ ->
                    failedPlayer.release()
                    onCleared(failedPlayer)
                    true
                }
                prepareAsync()
            }
        }
    } catch (error: Throwable) {
        runCatching { player.release() }
        throw error
    }
}
