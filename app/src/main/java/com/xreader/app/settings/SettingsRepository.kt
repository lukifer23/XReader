package com.xreader.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.Preferences.Key
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xreader.app.data.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerSettingsDataStore by preferencesDataStore("reader_settings")

class SettingsRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.readerSettingsDataStore,
) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val fontScale = floatPreferencesKey("font_scale")
        val lineHeight = floatPreferencesKey("line_height")
        val marginScale = floatPreferencesKey("margin_scale")
        val fontFamily = stringPreferencesKey("font_family")
        val tapZonesEnabled = booleanPreferencesKey("tap_zones_enabled")
        val tapZonePreset = stringPreferencesKey("tap_zone_preset")
        val pageTurnAnimations = booleanPreferencesKey("page_turn_animations")
        val readAloudRate = floatPreferencesKey("read_aloud_rate")
        val readAloudVoiceName = stringPreferencesKey("read_aloud_voice_name")
        val fullScreen = booleanPreferencesKey("full_screen")
        val publisherStyles = booleanPreferencesKey("publisher_styles")
        val textAlign = stringPreferencesKey("text_align")
        val pdfFit = stringPreferencesKey("pdf_fit")
        val idleTimeoutMillis = longPreferencesKey("idle_timeout_millis")
        val librarySort = stringPreferencesKey("library_sort")
        val libraryDensity = stringPreferencesKey("library_density")
    }

    private data class BookAppearanceKeys(
        val enabled: Key<Boolean>,
        val fontScale: Key<Float>,
        val lineHeight: Key<Float>,
        val marginScale: Key<Float>,
        val fontFamily: Key<String>,
        val publisherStyles: Key<Boolean>,
        val textAlign: Key<String>,
        val pdfFit: Key<String>,
    )

    val settings: Flow<ReaderSettings> =
        dataStore.data.map { prefs ->
            ReaderSettings(
                theme = prefs[Keys.theme]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                    ?: ReaderTheme.LIGHT,
                fontScale = prefs[Keys.fontScale] ?: 1.18f,
                lineHeight = prefs[Keys.lineHeight] ?: 1.42f,
                marginScale = prefs[Keys.marginScale] ?: 0.52f,
                fontFamily = readerFontFamily(prefs[Keys.fontFamily]) ?: ReaderFontFamily.DEFAULT,
                tapZonesEnabled = prefs[Keys.tapZonesEnabled] ?: true,
                tapZonePreset = prefs[Keys.tapZonePreset]?.let { runCatching { ReaderTapZonePreset.valueOf(it) }.getOrNull() }
                    ?: ReaderTapZonePreset.BALANCED,
                pageTurnAnimations = prefs[Keys.pageTurnAnimations] ?: true,
                readAloudRate = (prefs[Keys.readAloudRate] ?: 1.0f).coerceIn(0.7f, 1.4f),
                readAloudVoiceName = prefs[Keys.readAloudVoiceName]?.takeIf { it.isNotBlank() },
                fullScreen = prefs[Keys.fullScreen] ?: false,
                publisherStyles = prefs[Keys.publisherStyles] ?: false,
                textAlign = prefs[Keys.textAlign]?.let { runCatching { ReaderTextAlign.valueOf(it) }.getOrNull() }
                    ?: ReaderTextAlign.START,
                pdfFit = prefs[Keys.pdfFit]?.let { runCatching { ReaderPdfFit.valueOf(it) }.getOrNull() }
                    ?: ReaderPdfFit.WIDTH,
                idleTimeoutMillis = prefs[Keys.idleTimeoutMillis] ?: 90_000L
            )
        }

    fun bookAppearance(bookId: Long): Flow<BookReaderAppearance?> {
        val keys = bookAppearanceKeys(bookId)
        return dataStore.data.map { prefs ->
            if (prefs[keys.enabled] != true) {
                null
            } else {
                BookReaderAppearance(
                    fontScale = prefs[keys.fontScale]?.coerceIn(0.75f, 1.65f) ?: 1.18f,
                    lineHeight = prefs[keys.lineHeight]?.coerceIn(1.1f, 2.0f) ?: 1.42f,
                    marginScale = prefs[keys.marginScale]?.coerceIn(0.35f, 1.8f) ?: 0.52f,
                    fontFamily = readerFontFamily(prefs[keys.fontFamily]) ?: ReaderFontFamily.DEFAULT,
                    publisherStyles = prefs[keys.publisherStyles] ?: false,
                    textAlign = prefs[keys.textAlign]?.let { runCatching { ReaderTextAlign.valueOf(it) }.getOrNull() }
                        ?: ReaderTextAlign.START,
                    pdfFit = prefs[keys.pdfFit]?.let { runCatching { ReaderPdfFit.valueOf(it) }.getOrNull() }
                        ?: ReaderPdfFit.WIDTH
                )
            }
        }
    }

    val librarySettings: Flow<LibrarySettings> =
        dataStore.data.map { prefs ->
            LibrarySettings(
                sort = prefs[Keys.librarySort]?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() }
                    ?: LibrarySort.RECENT,
                density = prefs[Keys.libraryDensity]?.let { runCatching { LibraryDensity.valueOf(it) }.getOrNull() }
                    ?: LibraryDensity.COMFORTABLE
            )
        }

    suspend fun setTheme(theme: ReaderTheme) {
        dataStore.edit { it[Keys.theme] = theme.name }
    }

    suspend fun setFontScale(value: Float) {
        dataStore.edit { it[Keys.fontScale] = value.coerceIn(0.75f, 1.65f) }
    }

    suspend fun setLineHeight(value: Float) {
        dataStore.edit { it[Keys.lineHeight] = value.coerceIn(1.1f, 2.0f) }
    }

    suspend fun setMarginScale(value: Float) {
        dataStore.edit { it[Keys.marginScale] = value.coerceIn(0.35f, 1.8f) }
    }

    suspend fun setSpacingPreset(value: ReaderSpacingPreset) {
        dataStore.edit {
            it[Keys.fontScale] = value.fontScale
            it[Keys.lineHeight] = value.lineHeight
            it[Keys.marginScale] = value.marginScale
        }
    }

    suspend fun setFontFamily(value: ReaderFontFamily) {
        dataStore.edit { it[Keys.fontFamily] = value.name }
    }

    suspend fun setTapZonesEnabled(value: Boolean) {
        dataStore.edit { it[Keys.tapZonesEnabled] = value }
    }

    suspend fun setTapZonePreset(value: ReaderTapZonePreset) {
        dataStore.edit { it[Keys.tapZonePreset] = value.name }
    }

    suspend fun setPageTurnAnimations(value: Boolean) {
        dataStore.edit { it[Keys.pageTurnAnimations] = value }
    }

    suspend fun setReadAloudRate(value: Float) {
        dataStore.edit { it[Keys.readAloudRate] = value.coerceIn(0.7f, 1.4f) }
    }

    suspend fun setReadAloudVoiceName(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) {
                prefs.remove(Keys.readAloudVoiceName)
            } else {
                prefs[Keys.readAloudVoiceName] = value
            }
        }
    }

    suspend fun setFullScreen(value: Boolean) {
        dataStore.edit { it[Keys.fullScreen] = value }
    }

    suspend fun setPublisherStyles(value: Boolean) {
        dataStore.edit { it[Keys.publisherStyles] = value }
    }

    suspend fun setTextAlign(value: ReaderTextAlign) {
        dataStore.edit { it[Keys.textAlign] = value.name }
    }

    suspend fun setPdfFit(value: ReaderPdfFit) {
        dataStore.edit { it[Keys.pdfFit] = value.name }
    }

    suspend fun setBookAppearanceEnabled(bookId: Long, enabled: Boolean, seed: ReaderSettings) {
        val keys = bookAppearanceKeys(bookId)
        dataStore.edit { prefs ->
            if (enabled) {
                prefs[keys.enabled] = true
                prefs[keys.fontScale] = seed.fontScale.coerceIn(0.75f, 1.65f)
                prefs[keys.lineHeight] = seed.lineHeight.coerceIn(1.1f, 2.0f)
                prefs[keys.marginScale] = seed.marginScale.coerceIn(0.35f, 1.8f)
                prefs[keys.fontFamily] = seed.fontFamily.name
                prefs[keys.publisherStyles] = seed.publisherStyles
                prefs[keys.textAlign] = seed.textAlign.name
                prefs[keys.pdfFit] = seed.pdfFit.name
            } else {
                prefs.remove(keys.enabled)
                prefs.remove(keys.fontScale)
                prefs.remove(keys.lineHeight)
                prefs.remove(keys.marginScale)
                prefs.remove(keys.fontFamily)
                prefs.remove(keys.publisherStyles)
                prefs.remove(keys.textAlign)
                prefs.remove(keys.pdfFit)
            }
        }
    }

    suspend fun setBookFontScale(bookId: Long, value: Float) {
        dataStore.edit { it[bookAppearanceKeys(bookId).fontScale] = value.coerceIn(0.75f, 1.65f) }
    }

    suspend fun setBookLineHeight(bookId: Long, value: Float) {
        dataStore.edit { it[bookAppearanceKeys(bookId).lineHeight] = value.coerceIn(1.1f, 2.0f) }
    }

    suspend fun setBookMarginScale(bookId: Long, value: Float) {
        dataStore.edit { it[bookAppearanceKeys(bookId).marginScale] = value.coerceIn(0.35f, 1.8f) }
    }

    suspend fun setBookSpacingPreset(bookId: Long, value: ReaderSpacingPreset) {
        val keys = bookAppearanceKeys(bookId)
        dataStore.edit {
            it[keys.fontScale] = value.fontScale
            it[keys.lineHeight] = value.lineHeight
            it[keys.marginScale] = value.marginScale
        }
    }

    suspend fun setBookFontFamily(bookId: Long, value: ReaderFontFamily) {
        dataStore.edit { it[bookAppearanceKeys(bookId).fontFamily] = value.name }
    }

    suspend fun setBookPublisherStyles(bookId: Long, value: Boolean) {
        dataStore.edit { it[bookAppearanceKeys(bookId).publisherStyles] = value }
    }

    suspend fun setBookTextAlign(bookId: Long, value: ReaderTextAlign) {
        dataStore.edit { it[bookAppearanceKeys(bookId).textAlign] = value.name }
    }

    suspend fun setBookPdfFit(bookId: Long, value: ReaderPdfFit) {
        dataStore.edit { it[bookAppearanceKeys(bookId).pdfFit] = value.name }
    }

    suspend fun setLibrarySort(value: LibrarySort) {
        dataStore.edit { it[Keys.librarySort] = value.name }
    }

    suspend fun setLibraryDensity(value: LibraryDensity) {
        dataStore.edit { it[Keys.libraryDensity] = value.name }
    }

    private fun readerFontFamily(value: String?): ReaderFontFamily? =
        when (value) {
            null -> null
            "DYSLEXIC" -> ReaderFontFamily.ACCESSIBLE
            else -> runCatching { ReaderFontFamily.valueOf(value) }.getOrNull()
        }

    private fun bookAppearanceKeys(bookId: Long): BookAppearanceKeys {
        val prefix = "book_${bookId}_appearance"
        return BookAppearanceKeys(
            enabled = booleanPreferencesKey("${prefix}_enabled"),
            fontScale = floatPreferencesKey("${prefix}_font_scale"),
            lineHeight = floatPreferencesKey("${prefix}_line_height"),
            marginScale = floatPreferencesKey("${prefix}_margin_scale"),
            fontFamily = stringPreferencesKey("${prefix}_font_family"),
            publisherStyles = booleanPreferencesKey("${prefix}_publisher_styles"),
            textAlign = stringPreferencesKey("${prefix}_text_align"),
            pdfFit = stringPreferencesKey("${prefix}_pdf_fit")
        )
    }
}
