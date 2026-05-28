package com.xreader.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xreader.app.data.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerSettingsDataStore by preferencesDataStore("reader_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val fontScale = floatPreferencesKey("font_scale")
        val lineHeight = floatPreferencesKey("line_height")
        val marginScale = floatPreferencesKey("margin_scale")
        val fontFamily = stringPreferencesKey("font_family")
        val tapZonesEnabled = booleanPreferencesKey("tap_zones_enabled")
        val pageTurnAnimations = booleanPreferencesKey("page_turn_animations")
        val fullScreen = booleanPreferencesKey("full_screen")
        val publisherStyles = booleanPreferencesKey("publisher_styles")
        val textAlign = stringPreferencesKey("text_align")
        val pdfFit = stringPreferencesKey("pdf_fit")
        val idleTimeoutMillis = longPreferencesKey("idle_timeout_millis")
        val librarySort = stringPreferencesKey("library_sort")
        val libraryDensity = stringPreferencesKey("library_density")
    }

    val settings: Flow<ReaderSettings> =
        context.readerSettingsDataStore.data.map { prefs ->
            ReaderSettings(
                theme = prefs[Keys.theme]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                    ?: ReaderTheme.LIGHT,
                fontScale = prefs[Keys.fontScale] ?: 1.18f,
                lineHeight = prefs[Keys.lineHeight] ?: 1.42f,
                marginScale = prefs[Keys.marginScale] ?: 0.52f,
                fontFamily = readerFontFamily(prefs[Keys.fontFamily]) ?: ReaderFontFamily.DEFAULT,
                tapZonesEnabled = prefs[Keys.tapZonesEnabled] ?: true,
                pageTurnAnimations = prefs[Keys.pageTurnAnimations] ?: true,
                fullScreen = prefs[Keys.fullScreen] ?: false,
                publisherStyles = prefs[Keys.publisherStyles] ?: false,
                textAlign = prefs[Keys.textAlign]?.let { runCatching { ReaderTextAlign.valueOf(it) }.getOrNull() }
                    ?: ReaderTextAlign.START,
                pdfFit = prefs[Keys.pdfFit]?.let { runCatching { ReaderPdfFit.valueOf(it) }.getOrNull() }
                    ?: ReaderPdfFit.WIDTH,
                idleTimeoutMillis = prefs[Keys.idleTimeoutMillis] ?: 90_000L
            )
        }

    val librarySettings: Flow<LibrarySettings> =
        context.readerSettingsDataStore.data.map { prefs ->
            LibrarySettings(
                sort = prefs[Keys.librarySort]?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() }
                    ?: LibrarySort.RECENT,
                density = prefs[Keys.libraryDensity]?.let { runCatching { LibraryDensity.valueOf(it) }.getOrNull() }
                    ?: LibraryDensity.COMFORTABLE
            )
        }

    suspend fun setTheme(theme: ReaderTheme) {
        context.readerSettingsDataStore.edit { it[Keys.theme] = theme.name }
    }

    suspend fun setFontScale(value: Float) {
        context.readerSettingsDataStore.edit { it[Keys.fontScale] = value.coerceIn(0.75f, 1.65f) }
    }

    suspend fun setLineHeight(value: Float) {
        context.readerSettingsDataStore.edit { it[Keys.lineHeight] = value.coerceIn(1.1f, 2.0f) }
    }

    suspend fun setMarginScale(value: Float) {
        context.readerSettingsDataStore.edit { it[Keys.marginScale] = value.coerceIn(0.35f, 1.8f) }
    }

    suspend fun setFontFamily(value: ReaderFontFamily) {
        context.readerSettingsDataStore.edit { it[Keys.fontFamily] = value.name }
    }

    suspend fun setTapZonesEnabled(value: Boolean) {
        context.readerSettingsDataStore.edit { it[Keys.tapZonesEnabled] = value }
    }

    suspend fun setPageTurnAnimations(value: Boolean) {
        context.readerSettingsDataStore.edit { it[Keys.pageTurnAnimations] = value }
    }

    suspend fun setFullScreen(value: Boolean) {
        context.readerSettingsDataStore.edit { it[Keys.fullScreen] = value }
    }

    suspend fun setPublisherStyles(value: Boolean) {
        context.readerSettingsDataStore.edit { it[Keys.publisherStyles] = value }
    }

    suspend fun setTextAlign(value: ReaderTextAlign) {
        context.readerSettingsDataStore.edit { it[Keys.textAlign] = value.name }
    }

    suspend fun setPdfFit(value: ReaderPdfFit) {
        context.readerSettingsDataStore.edit { it[Keys.pdfFit] = value.name }
    }

    suspend fun setLibrarySort(value: LibrarySort) {
        context.readerSettingsDataStore.edit { it[Keys.librarySort] = value.name }
    }

    suspend fun setLibraryDensity(value: LibraryDensity) {
        context.readerSettingsDataStore.edit { it[Keys.libraryDensity] = value.name }
    }

    private fun readerFontFamily(value: String?): ReaderFontFamily? =
        when (value) {
            null -> null
            "DYSLEXIC" -> ReaderFontFamily.ACCESSIBLE
            else -> runCatching { ReaderFontFamily.valueOf(value) }.getOrNull()
        }
}
