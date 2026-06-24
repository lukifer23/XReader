package com.xreader.app.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.xreader.app.core.releaseQuietlyAsync
import com.xreader.app.core.speechMediaPlayerForFile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun releaseNeuralPreviewPlaybackAsync(
    player: MediaPlayer?,
    scope: CoroutineScope,
): MediaPlayer? {
    player?.let { current ->
        current.releaseQuietlyAsync(scope)
    }
    return null
}

internal suspend fun startNeuralPreviewPlayback(
    file: File,
    releaseScope: CoroutineScope,
    onCleared: (MediaPlayer) -> Unit,
): MediaPlayer {
    val player = withContext(Dispatchers.IO) {
        speechMediaPlayerForFile(
            file = file,
            usage = AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
        )
    }
    return try {
        withContext(Dispatchers.Main.immediate) {
            player.apply {
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    onCleared(it)
                    releaseNeuralPreviewPlaybackAsync(it, releaseScope)
                }
                setOnErrorListener { failedPlayer, _, _ ->
                    onCleared(failedPlayer)
                    releaseNeuralPreviewPlaybackAsync(failedPlayer, releaseScope)
                    true
                }
                prepareAsync()
            }
        }
    } catch (error: Throwable) {
        player.releaseQuietlyAsync(releaseScope)
        throw error
    }
}
