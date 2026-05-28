package com.xreader.app.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xreader.app.data.ReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val root = File(context.cacheDir, "settings-repository-test-${System.nanoTime()}")
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun cleanUp() {
        dataStoreScope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun persistsReaderAndLibraryPreferencesWithBounds() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = {
                root.mkdirs()
                File(root, "reader_settings.preferences_pb")
            }
        )
        val repository = SettingsRepository(context, dataStore)

        repository.setTheme(ReaderTheme.OLED)
        repository.setFontScale(3.0f)
        repository.setLineHeight(0.7f)
        repository.setMarginScale(2.4f)
        repository.setFontFamily(ReaderFontFamily.ACCESSIBLE)
        repository.setTapZonesEnabled(false)
        repository.setPageTurnAnimations(false)
        repository.setFullScreen(true)
        repository.setPublisherStyles(true)
        repository.setTextAlign(ReaderTextAlign.JUSTIFY)
        repository.setPdfFit(ReaderPdfFit.CONTAIN)
        repository.setLibrarySort(LibrarySort.SERIES)
        repository.setLibraryDensity(LibraryDensity.COMPACT)

        val readerSettings = repository.settings.first()
        val librarySettings = repository.librarySettings.first()

        assertEquals(ReaderTheme.OLED, readerSettings.theme)
        assertEquals(1.65f, readerSettings.fontScale, 0.001f)
        assertEquals(1.1f, readerSettings.lineHeight, 0.001f)
        assertEquals(1.8f, readerSettings.marginScale, 0.001f)
        assertEquals(ReaderFontFamily.ACCESSIBLE, readerSettings.fontFamily)
        assertFalse(readerSettings.tapZonesEnabled)
        assertFalse(readerSettings.pageTurnAnimations)
        assertTrue(readerSettings.fullScreen)
        assertTrue(readerSettings.publisherStyles)
        assertEquals(ReaderTextAlign.JUSTIFY, readerSettings.textAlign)
        assertEquals(ReaderPdfFit.CONTAIN, readerSettings.pdfFit)
        assertEquals(LibrarySort.SERIES, librarySettings.sort)
        assertEquals(LibraryDensity.COMPACT, librarySettings.density)
    }
}
