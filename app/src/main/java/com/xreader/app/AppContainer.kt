package com.xreader.app

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.xreader.app.analytics.AnalyticsExportService
import com.xreader.app.analytics.AnalyticsRepository
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.XReaderDatabase
import com.xreader.app.dictionary.DictionaryRepository
import com.xreader.app.importer.ImportService
import com.xreader.app.opds.OpdsCatalogService
import com.xreader.app.readium.ReadiumRuntime
import com.xreader.app.reader.PublicationService
import com.xreader.app.repository.AnnotationBackupService
import com.xreader.app.repository.AnnotationRepository
import com.xreader.app.repository.LibraryBackupRepository
import com.xreader.app.repository.LibraryBackupService
import com.xreader.app.repository.LibraryRepository
import com.xreader.app.repository.ReadingRepository
import com.xreader.app.settings.SettingsRepository
import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import com.xreader.app.tts.AudiobookGenerationForegroundService
import com.xreader.app.tts.AudiobookGenerationScope
import com.xreader.app.tts.ReadAloudEngine
import com.xreader.app.tts.NeuralTtsRepository
import com.xreader.app.tts.GeneratedAudiobookPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("LogNotTimber")
class AppContainer(
    context: Context,
    val applicationScope: CoroutineScope,
    private val databaseOverride: XReaderDatabase? = null,
) {
    private val appContext = context.applicationContext
    private val readerServiceWarmupStarted = AtomicBoolean(false)
    private val readerWebViewWarmupStarted = AtomicBoolean(false)

    val database: XReaderDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        databaseOverride ?: XReaderDatabase.get(appContext)
    }
    val readiumRuntime: ReadiumRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ReadiumRuntime(appContext)
    }
    private val importService: ImportService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ImportService(appContext, database)
    }

    val libraryRepository: LibraryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LibraryRepository(database, importService)
    }
    val opdsCatalogService: OpdsCatalogService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OpdsCatalogService(importService, File(appContext.cacheDir, "opds"))
    }
    val readingRepository: ReadingRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ReadingRepository(database.reading())
    }
    val annotationRepository: AnnotationRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AnnotationRepository(database.annotations(), database.books())
    }
    val annotationBackupService: AnnotationBackupService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AnnotationBackupService(appContext, annotationRepository)
    }
    private val libraryBackupRepository: LibraryBackupRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LibraryBackupRepository(
            bookDao = database.books(),
            collectionDao = database.collections(),
            readingDao = database.reading(),
            settingsRepository = settingsRepository
        )
    }
    val libraryBackupService: LibraryBackupService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LibraryBackupService(appContext, libraryBackupRepository)
    }
    val dictionaryRepository: DictionaryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DictionaryRepository(appContext, database.dictionary())
    }
    val publicationService: PublicationService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PublicationService(appContext, readiumRuntime)
    }
    val settingsRepository: SettingsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsRepository(appContext)
    }
    val analyticsRepository: AnalyticsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AnalyticsRepository(database.books(), readingRepository)
    }
    val analyticsExportService: AnalyticsExportService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AnalyticsExportService(appContext, analyticsRepository)
    }
    val readAloudEngine: ReadAloudEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ReadAloudEngine(appContext, applicationScope)
    }
    val neuralTtsRepository: NeuralTtsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NeuralTtsRepository(appContext, database.neuralTts())
    }
    val generatedAudiobookPlayback: GeneratedAudiobookPlaybackController by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GeneratedAudiobookPlaybackController(appContext, neuralTtsRepository, applicationScope)
    }

    init {
        if (databaseOverride == null && isMainApplicationProcess(appContext)) {
            applicationScope.launch(Dispatchers.IO) {
                runCatching {
                    delay(STARTUP_NEURAL_TTS_CATALOG_MAINTENANCE_DELAY_MS)
                    runNeuralTtsStartupMaintenance()
                }
                    .onFailure { Log.w("XReader", "Neural TTS startup maintenance failed", it) }
            }
        }
    }

    private suspend fun runNeuralTtsStartupMaintenance() {
        neuralTtsRepository.ensureCatalogSeeded()
        neuralTtsRepository.repairInterruptedModelInstalls()
        delay(STARTUP_AUDIOBOOK_REPAIR_DELAY_MS)
        neuralTtsRepository.repairStaleGeneratingAudio()
        delay(STARTUP_OBSOLETE_TTS_STORAGE_PRUNE_DELAY_MS)
        neuralTtsRepository.pruneObsoleteCatalogStorage()
    }

    fun startAudiobookGeneration(
        bookId: Long,
        modelId: String,
        speakerId: Int = 0,
        pace: NeuralTtsPace,
        tone: NeuralTtsTone,
        scope: AudiobookGenerationScope = AudiobookGenerationScope.FULL_BOOK,
    ) {
        AudiobookGenerationForegroundService.start(
            context = appContext,
            bookId = bookId,
            modelId = modelId,
            speakerId = speakerId,
            pace = pace,
            tone = tone,
            scope = scope
        )
    }

    fun cancelAudiobookGeneration(
        bookId: Long? = null,
        modelId: String? = null,
        speakerId: Int? = null,
        speed: Float? = null,
        tone: NeuralTtsTone? = null,
        scope: AudiobookGenerationScope? = null,
    ) {
        AudiobookGenerationForegroundService.cancel(
            context = appContext,
            bookId = bookId,
            modelId = modelId,
            speakerId = speakerId,
            speed = speed,
            tone = tone,
            scope = scope
        )
    }

    fun cancelAudiobookGeneration(bookId: Long, audio: BookAudioEntity) {
        AudiobookGenerationForegroundService.cancel(
            context = appContext,
            bookId = bookId,
            modelId = audio.modelId,
            speakerId = audio.speakerId,
            speed = audio.speed,
            toneName = audio.tone,
            scopeKey = audio.scope
        )
    }

    fun warmReaderPath() {
        warmReaderServices()
        warmReaderWebView()
    }

    fun warmReaderServices() {
        if (!readerServiceWarmupStarted.compareAndSet(false, true)) return
        applicationScope.launch(Dispatchers.Default) {
            runCatching { publicationService }
                .onFailure { Log.w("XReader", "Readium warmup failed", it) }
        }
    }

    fun warmReaderWebView() {
        if (!readerWebViewWarmupStarted.compareAndSet(false, true)) return
        applicationScope.launch {
            withContext(Dispatchers.Main.immediate) {
                runCatching {
                    WebView(appContext).destroy()
                }.onFailure { Log.w("XReader", "WebView warmup failed", it) }
            }
        }
    }

    private companion object {
        const val STARTUP_NEURAL_TTS_CATALOG_MAINTENANCE_DELAY_MS = 2_000L
        const val STARTUP_AUDIOBOOK_REPAIR_DELAY_MS = 8_000L
        const val STARTUP_OBSOLETE_TTS_STORAGE_PRUNE_DELAY_MS = 20_000L
    }
}
