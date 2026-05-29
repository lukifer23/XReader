package com.xreader.app.ui

import com.xreader.app.tts.ReadAloudVoiceOption
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAloudVoicePickerStateTest {
    @Test
    fun groupsVoicesByLocaleAndPinsSelectedGroup() {
        val groups = buildReadAloudVoiceGroups(
            voices = listOf(
                voice("hu-1", "Hungarian voice", "hu-HU"),
                voice("en-1", "English voice one", "en-US"),
                voice("en-2", "English voice two", "en-US")
            ),
            selectedVoiceName = "en-2",
            currentLocale = Locale.US
        )

        assertEquals("English (United States)", groups.first().label)
        assertEquals(true, groups.first().selected)
        assertEquals(listOf("en-1", "en-2"), groups.first().voices.map { it.name })
    }

    @Test
    fun pinsDeviceLanguageWhenNoVoiceIsSelected() {
        val groups = buildReadAloudVoiceGroups(
            voices = listOf(
                voice("hu-1", "Hungarian voice", "hu-HU"),
                voice("en-1", "English voice", "en-US")
            ),
            selectedVoiceName = null,
            currentLocale = Locale.US
        )

        assertEquals("English (United States)", groups.first().label)
    }

    @Test
    fun filtersVoicesByLabelNameLocaleAndGroup() {
        val voices = listOf(
            voice("local-en-us-a", "Jane - high quality", "en-US"),
            voice("local-es-es-a", "Spanish voice", "es-ES")
        )

        assertEquals(listOf("local-en-us-a"), filterReadAloudVoices(voices, "jane", Locale.US).map { it.name })
        assertEquals(listOf("local-en-us-a"), filterReadAloudVoices(voices, "en-US", Locale.US).map { it.name })
        assertEquals(listOf("local-es-es-a"), filterReadAloudVoices(voices, "spanish", Locale.US).map { it.name })
    }

    @Test
    fun labelsUnavailableSelectedVoiceWithoutClearingIt() {
        val voices = listOf(voice("local-en-us-a", "English voice", "en-US"))

        assertEquals("Device default", selectedVoiceLabel(voices, null))
        assertEquals("English voice", selectedVoiceLabel(voices, "local-en-us-a"))
        assertEquals("Selected voice unavailable", selectedVoiceLabel(voices, "missing"))
    }

    private fun voice(
        name: String,
        label: String,
        localeTag: String,
    ): ReadAloudVoiceOption =
        ReadAloudVoiceOption(
            name = name,
            label = label,
            localeTag = localeTag,
            quality = 400,
            latency = 200
        )
}
