package com.xreader.app

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.xreader.app.analytics.AnalyticsRepository
import com.xreader.app.data.XReaderDatabase
import com.xreader.app.dictionary.DictionaryRepository
import com.xreader.app.importer.ImportService
import com.xreader.app.readium.ReadiumRuntime
import com.xreader.app.reader.PublicationService
import com.xreader.app.repository.AnnotationBackupService
import com.xreader.app.repository.AnnotationRepository
import com.xreader.app.repository.LibraryRepository
import com.xreader.app.repository.ReadingRepository
import com.xreader.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("LogNotTimber")
class AppContainer(
    context: Context,
    val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val readerServiceWarmupStarted = AtomicBoolean(false)
    private val readerWebViewWarmupStarted = AtomicBoolean(false)

    val database: XReaderDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        XReaderDatabase.get(appContext)
    }
    val readiumRuntime: ReadiumRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ReadiumRuntime(appContext)
    }
    private val importService: ImportService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ImportService(appContext, database)
    }

    val libraryRepository: LibraryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LibraryRepository(database.books(), database.search(), importService)
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
}
