package com.xreader.app.tts

import android.app.Notification
import android.app.Notification.Action
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.xreader.app.MainActivity
import com.xreader.app.R
import com.xreader.app.XReaderApplication
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class AudiobookGenerationForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as XReaderApplication).container

    private var foregroundStarted = false
    private var generationJob: Job? = null
    private var progressJob: Job? = null
    private var cancelingGeneration = false
    private var activeBookId: Long? = null
    private var activeTitle: String = "XReader audiobook"
    private var activeModelId: String = NeuralTtsModelCatalog.DEFAULT_MODEL_ID
    private var activeSpeakerId: Int = 0
    private var activePace: NeuralTtsPace = NeuralTtsPace.STANDARD
    private var activeTone: NeuralTtsTone = NeuralTtsTone.NATURAL
    private var activeScope: AudiobookGenerationScope = AudiobookGenerationScope.FULL_BOOK

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundIfNeeded(buildNotification(title = activeTitle, audio = null, preparing = true))
        when (intent?.action) {
            ACTION_START -> startGeneration(intent)
            ACTION_CANCEL -> cancelGeneration()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        generationJob?.cancel()
        progressJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startGeneration(intent: Intent) {
        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L).takeIf { it > 0L } ?: run {
            stopForegroundAndSelf(removeNotification = true)
            return
        }
        when (audiobookGenerationStartGate(canceling = cancelingGeneration, jobActive = generationJob?.isActive == true)) {
            AudiobookGenerationStartGate.START -> Unit
            AudiobookGenerationStartGate.CANCELING -> {
                updateNotification(buildNotification(title = activeTitle, audio = null, preparing = true, canceling = true))
                return
            }
            AudiobookGenerationStartGate.ALREADY_RUNNING -> {
                updateNotification(buildNotification(title = activeTitle, audio = null, preparing = true, alreadyRunning = true))
                return
            }
        }
        activeBookId = bookId
        activeModelId = intent.getStringExtra(EXTRA_MODEL_ID)?.takeIf { it.isNotBlank() }
            ?: NeuralTtsModelCatalog.DEFAULT_MODEL_ID
        activeSpeakerId = intent.getIntExtra(EXTRA_SPEAKER_ID, 0).coerceAtLeast(0)
        activePace = intent.getStringExtra(EXTRA_PACE)?.let { runCatching { NeuralTtsPace.valueOf(it) }.getOrNull() }
            ?: NeuralTtsPace.STANDARD
        activeTone = intent.getStringExtra(EXTRA_TONE)?.let { runCatching { NeuralTtsTone.valueOf(it) }.getOrNull() }
            ?: NeuralTtsTone.NATURAL
        activeScope = AudiobookGenerationScope.fromKey(intent.getStringExtra(EXTRA_SCOPE))
        updateNotification(buildNotification(title = activeTitle, audio = null, preparing = true))
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            container.neuralTtsRepository.observeBookAudio(bookId)
                .map { rows -> rows.firstOrNull { it.matchesActiveProfile() } }
                .distinctUntilChanged { previous, next ->
                    previous.generationNotificationKey() == next.generationNotificationKey()
                }
                .collectLatest { audio ->
                    updateNotification(buildNotification(title = activeTitle, audio = audio, preparing = audio == null))
                }
        }
        generationJob = serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val book = requireNotNull(container.libraryRepository.getBook(bookId)) {
                    "This book is no longer in the library."
                }
                activeTitle = book.title
                withContext(Dispatchers.Main.immediate) {
                    updateNotification(buildNotification(title = activeTitle, audio = null, preparing = true))
                }
                container.neuralTtsRepository.markBookAudioPreparing(
                    bookId = book.id,
                    modelId = activeModelId,
                    speakerId = activeSpeakerId,
                    pace = activePace,
                    tone = activeTone,
                    scope = activeScope
                )
                val indexedRows = container.libraryRepository.indexedRowsForBook(bookId)
                val chunks = withContext(Dispatchers.Default) {
                    ReadAloudPlanner.chunksFromRows(indexedRows)
                }
                container.neuralTtsRepository.generateBookAudio(
                    bookId = book.id,
                    bookTitle = book.title,
                    chunks = chunks,
                    modelId = activeModelId,
                    speakerId = activeSpeakerId,
                    pace = activePace,
                    tone = activeTone,
                    scope = activeScope
                )
            }.onFailure { error ->
                if (error !is CancellationException) {
                    Log.e("XReader", "Foreground audiobook generation failed for $bookId", error)
                }
            }
            withContext(Dispatchers.Main.immediate) {
                generationJob = null
                cancelingGeneration = false
                stopForegroundAndSelf(removeNotification = false)
            }
        }
    }

    private fun cancelGeneration() {
        val job = generationJob
        if (cancelingGeneration) {
            updateNotification(buildNotification(title = activeTitle, audio = null, preparing = true, canceling = true))
            return
        }
        if (job == null) {
            progressJob?.cancel()
            progressJob = null
            stopForegroundAndSelf(removeNotification = true)
            return
        }
        cancelingGeneration = true
        updateNotification(buildNotification(title = activeTitle, audio = null, preparing = true, canceling = true))
        serviceScope.launch {
            job.cancelAndJoin()
            generationJob = null
            cancelingGeneration = false
            progressJob?.cancel()
            progressJob = null
            stopForegroundAndSelf(removeNotification = true)
        }
    }

    private fun BookAudioEntity.matchesActiveProfile(): Boolean =
        modelId == activeModelId &&
            speakerId == activeSpeakerId &&
            kotlin.math.abs(speed - activePace.speed) < 0.001f &&
            tone == activeTone.name &&
            scope == activeScope.key

    private fun BookAudioEntity?.generationNotificationKey(): GenerationNotificationKey? =
        this?.let { audio ->
            GenerationNotificationKey(
                id = audio.id,
                status = audio.status,
                completedSegments = audio.completedSegments,
                segmentCount = audio.segmentCount,
                error = audio.error,
                generationStartedAt = audio.generationStartedAt,
                generationSessionStartCompletedSegments = audio.generationSessionStartCompletedSegments,
                updatedMinuteBucket = audio.updatedAt / NOTIFICATION_UPDATE_BUCKET_MS
            )
        }

    private fun startForegroundIfNeeded(notification: Notification) {
        if (foregroundStarted) return
        startForegroundCompat(notification)
        foregroundStarted = true
    }

    private fun updateNotification(notification: Notification) {
        if (foregroundStarted) {
            startForegroundCompat(notification)
        } else {
            startForegroundIfNeeded(notification)
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundAndSelf(removeNotification: Boolean) {
        if (foregroundStarted) {
            stopForeground(if (removeNotification) Service.STOP_FOREGROUND_REMOVE else Service.STOP_FOREGROUND_DETACH)
            foregroundStarted = false
        }
        stopSelf()
    }

    private fun buildNotification(
        title: String,
        audio: BookAudioEntity?,
        preparing: Boolean,
        alreadyRunning: Boolean = false,
        canceling: Boolean = false,
    ): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title.takeIf { it.isNotBlank() } ?: "XReader audiobook")
            .setContentText(audiobookGenerationStatusText(audio, preparing, alreadyRunning, canceling))
            .setSubText(audiobookGenerationProgressText(audio))
            .setContentIntent(openAppIntent())
            .setDeleteIntent(serviceIntent(ACTION_CANCEL, REQUEST_CANCEL))
            .setOngoing(audio?.status == BookAudioStatus.GENERATING || preparing)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(notificationAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_CANCEL, REQUEST_CANCEL))
            .build()

    private fun notificationAction(icon: Int, title: String, action: String, requestCode: Int): Action =
        Action.Builder(Icon.createWithResource(this, icon), title, serviceIntent(action, requestCode)).build()

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, AudiobookGenerationForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Audiobook generation", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Neural audiobook generation"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "audiobook_generation"
        private const val NOTIFICATION_ID = 42_501
        private const val ACTION_START = "com.xreader.app.tts.AUDIOBOOK_GENERATION_START"
        private const val ACTION_CANCEL = "com.xreader.app.tts.AUDIOBOOK_GENERATION_CANCEL"
        private const val EXTRA_BOOK_ID = "book_id"
        private const val EXTRA_MODEL_ID = "model_id"
        private const val EXTRA_SPEAKER_ID = "speaker_id"
        private const val EXTRA_PACE = "pace"
        private const val EXTRA_TONE = "tone"
        private const val EXTRA_SCOPE = "scope"
        private const val REQUEST_OPEN_APP = 42_510
        private const val REQUEST_CANCEL = 42_511

        fun start(
            context: Context,
            bookId: Long,
            modelId: String,
            speakerId: Int = 0,
            pace: NeuralTtsPace,
            tone: NeuralTtsTone,
            scope: AudiobookGenerationScope = AudiobookGenerationScope.FULL_BOOK,
        ) {
            val intent = Intent(context.applicationContext, AudiobookGenerationForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_BOOK_ID, bookId)
                .putExtra(EXTRA_MODEL_ID, modelId)
                .putExtra(EXTRA_SPEAKER_ID, speakerId.coerceAtLeast(0))
                .putExtra(EXTRA_PACE, pace.name)
                .putExtra(EXTRA_TONE, tone.name)
                .putExtra(EXTRA_SCOPE, scope.key)
            androidx.core.content.ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context.applicationContext, AudiobookGenerationForegroundService::class.java)
                .setAction(ACTION_CANCEL)
            androidx.core.content.ContextCompat.startForegroundService(context.applicationContext, intent)
        }
    }
}

private data class GenerationNotificationKey(
    val id: Long,
    val status: BookAudioStatus,
    val completedSegments: Int,
    val segmentCount: Int,
    val error: String?,
    val generationStartedAt: Long?,
    val generationSessionStartCompletedSegments: Int,
    val updatedMinuteBucket: Long,
)

internal enum class AudiobookGenerationStartGate {
    START,
    CANCELING,
    ALREADY_RUNNING,
}

internal fun audiobookGenerationStartGate(canceling: Boolean, jobActive: Boolean): AudiobookGenerationStartGate =
    when {
        canceling -> AudiobookGenerationStartGate.CANCELING
        jobActive -> AudiobookGenerationStartGate.ALREADY_RUNNING
        else -> AudiobookGenerationStartGate.START
    }

internal fun audiobookGenerationStatusText(
    audio: BookAudioEntity?,
    preparing: Boolean,
    alreadyRunning: Boolean = false,
    canceling: Boolean = false,
): String =
    when {
        canceling -> "Stopping audiobook generation"
        alreadyRunning -> "Another audiobook is already generating"
        audio?.status == BookAudioStatus.GENERATING -> "Generating neural audiobook"
        audio?.status == BookAudioStatus.GENERATED -> "Audiobook ready"
        audio?.status == BookAudioStatus.CANCELED -> "Generation stopped"
        audio?.status == BookAudioStatus.FAILED -> audio.error ?: "Generation failed"
        preparing -> "Preparing book text"
        else -> "Audiobook generation"
    }

internal fun audiobookGenerationProgressText(audio: BookAudioEntity?): String? {
    val segmentCount = audio?.segmentCount ?: return null
    if (segmentCount <= 0) return null
    val completed = audio.completedSegments.coerceIn(0, segmentCount)
    val eta = audio.generationEtaLabel()
    return listOfNotNull("$completed/$segmentCount segments", eta).joinToString(" • ")
}

private const val NOTIFICATION_UPDATE_BUCKET_MS = 60_000L

internal fun BookAudioEntity.generationEtaLabel(nowMillis: Long = System.currentTimeMillis()): String? {
    if (status != BookAudioStatus.GENERATING) return null
    val total = segmentCount.takeIf { it > 0 } ?: return null
    val completed = completedSegments.coerceIn(0, total)
    if (completed <= 0 || completed >= total) return null
    val sessionStartCompleted = generationSessionStartCompletedSegments.coerceIn(0, completed)
    val sessionCompleted = completed - sessionStartCompleted
    if (sessionCompleted <= 0) return null
    val startedAt = generationStartedAt ?: return null
    val elapsed = (nowMillis - startedAt).coerceAtLeast(0L)
    if (elapsed < 5_000L) return null
    val millisPerSegment = elapsed.toDouble() / sessionCompleted.toDouble()
    val remainingMillis = ((total - completed) * millisPerSegment).toLong().coerceAtLeast(0L)
    return "${formatGenerationDuration(remainingMillis)} left"
}

private fun formatGenerationDuration(millis: Long): String {
    val minutes = (millis / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return when {
        hours > 0L -> "${hours}h ${remainingMinutes}m"
        minutes > 0L -> "${minutes}m"
        else -> "under 1m"
    }
}
