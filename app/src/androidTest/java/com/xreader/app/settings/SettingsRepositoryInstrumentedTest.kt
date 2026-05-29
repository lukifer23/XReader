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
import org.junit.Assert.assertNull
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
        repository.setReadAloudRate(3.0f)
        val clampedSettings = repository.settings.first()
        assertEquals(1.65f, clampedSettings.fontScale, 0.001f)
        assertEquals(1.1f, clampedSettings.lineHeight, 0.001f)
        assertEquals(1.8f, clampedSettings.marginScale, 0.001f)
        assertEquals(1.4f, clampedSettings.readAloudRate, 0.001f)

        repository.setSpacingPreset(ReaderSpacingPreset.ACCESSIBLE)
        repository.setFontFamily(ReaderFontFamily.ACCESSIBLE)
        repository.setTapZonesEnabled(false)
        repository.setTapZonePreset(ReaderTapZonePreset.COMPACT)
        repository.setPageTurnAnimations(false)
        repository.setReadAloudRate(0.2f)
        repository.setReadAloudVoiceName("local-en-us-voice")
        repository.setReadAloudSleepTimer(ReadAloudSleepTimer.THIRTY_MINUTES)
        repository.setHighlightColor(ReaderHighlightColor.GREEN.hex)
        repository.setFullScreen(true)
        repository.setPublisherStyles(true)
        repository.setTextAlign(ReaderTextAlign.JUSTIFY)
        repository.setPdfFit(ReaderPdfFit.CONTAIN)
        repository.setLibrarySort(LibrarySort.SERIES)
        repository.setLibraryDensity(LibraryDensity.COMPACT)

        val readerSettings = repository.settings.first()
        val librarySettings = repository.librarySettings.first()

        assertEquals(ReaderTheme.OLED, readerSettings.theme)
        assertEquals(ReaderSpacingPreset.ACCESSIBLE.fontScale, readerSettings.fontScale, 0.001f)
        assertEquals(ReaderSpacingPreset.ACCESSIBLE.lineHeight, readerSettings.lineHeight, 0.001f)
        assertEquals(ReaderSpacingPreset.ACCESSIBLE.marginScale, readerSettings.marginScale, 0.001f)
        assertEquals(ReaderFontFamily.ACCESSIBLE, readerSettings.fontFamily)
        assertFalse(readerSettings.tapZonesEnabled)
        assertEquals(ReaderTapZonePreset.COMPACT, readerSettings.tapZonePreset)
        assertFalse(readerSettings.pageTurnAnimations)
        assertEquals(0.7f, readerSettings.readAloudRate, 0.001f)
        assertEquals("local-en-us-voice", readerSettings.readAloudVoiceName)
        assertEquals(ReadAloudSleepTimer.THIRTY_MINUTES, readerSettings.readAloudSleepTimer)
        assertEquals(ReaderHighlightColor.GREEN.hex, readerSettings.highlightColor)
        assertTrue(readerSettings.fullScreen)
        assertTrue(readerSettings.publisherStyles)
        assertEquals(ReaderTextAlign.JUSTIFY, readerSettings.textAlign)
        assertEquals(ReaderPdfFit.CONTAIN, readerSettings.pdfFit)
        assertEquals(LibrarySort.SERIES, librarySettings.sort)
        assertEquals(LibraryDensity.COMPACT, librarySettings.density)

        repository.setReadAloudVoiceName(null)
        assertNull(repository.settings.first().readAloudVoiceName)
        repository.setHighlightColor("#not-a-palette-color")
        assertEquals(ReaderHighlightColor.YELLOW.hex, repository.settings.first().highlightColor)
    }

    @Test
    fun persistsAndClearsBookAppearanceOverrides() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = {
                root.mkdirs()
                File(root, "book_appearance.preferences_pb")
            }
        )
        val repository = SettingsRepository(context, dataStore)
        val seed = ReaderSettings(
            fontScale = 1.3f,
            lineHeight = 1.55f,
            marginScale = 0.9f,
            fontFamily = ReaderFontFamily.HUMANIST,
            publisherStyles = true,
            textAlign = ReaderTextAlign.JUSTIFY,
            pdfFit = ReaderPdfFit.CONTAIN
        )

        repository.setBookAppearanceEnabled(bookId = 42L, enabled = true, seed = seed)
        repository.setBookFontScale(bookId = 42L, value = 4.0f)
        repository.setBookLineHeight(bookId = 42L, value = 0.6f)
        repository.setBookMarginScale(bookId = 42L, value = 3.0f)
        repository.setBookSpacingPreset(bookId = 42L, value = ReaderSpacingPreset.COMPACT)
        repository.setBookFontFamily(bookId = 42L, value = ReaderFontFamily.ACCESSIBLE)
        repository.setBookPublisherStyles(bookId = 42L, value = false)
        repository.setBookTextAlign(bookId = 42L, value = ReaderTextAlign.START)
        repository.setBookPdfFit(bookId = 42L, value = ReaderPdfFit.WIDTH)

        val appearance = requireNotNull(repository.bookAppearance(42L).first())
        assertEquals(ReaderSpacingPreset.COMPACT.fontScale, appearance.fontScale, 0.001f)
        assertEquals(ReaderSpacingPreset.COMPACT.lineHeight, appearance.lineHeight, 0.001f)
        assertEquals(ReaderSpacingPreset.COMPACT.marginScale, appearance.marginScale, 0.001f)
        assertEquals(ReaderFontFamily.ACCESSIBLE, appearance.fontFamily)
        assertFalse(appearance.publisherStyles)
        assertEquals(ReaderTextAlign.START, appearance.textAlign)
        assertEquals(ReaderPdfFit.WIDTH, appearance.pdfFit)
        assertNull(repository.bookAppearance(7L).first())

        repository.setBookAppearanceEnabled(bookId = 42L, enabled = false, seed = seed)

        assertNull(repository.bookAppearance(42L).first())
    }
}
