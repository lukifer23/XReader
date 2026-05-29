package com.xreader.app.ui

import com.xreader.app.tts.ReadAloudVoiceOption
import java.util.Locale

internal data class ReadAloudVoiceGroup(
    val key: String,
    val label: String,
    val voices: List<ReadAloudVoiceOption>,
    val selected: Boolean,
)

internal fun buildReadAloudVoiceGroups(
    voices: List<ReadAloudVoiceOption>,
    selectedVoiceName: String?,
    currentLocale: Locale = Locale.getDefault(),
): List<ReadAloudVoiceGroup> {
    val selectedGroupKey = voices.firstOrNull { it.name == selectedVoiceName }?.voiceGroupKey()
    val preferredLanguage = currentLocale.language
    return voices
        .groupBy { it.voiceGroupKey() }
        .map { (key, groupVoices) ->
            ReadAloudVoiceGroup(
                key = key,
                label = groupVoices.firstOrNull()?.voiceGroupLabel(currentLocale) ?: "Other voices",
                voices = groupVoices.sortedBy { it.label.lowercase(currentLocale) },
                selected = key == selectedGroupKey
            )
        }
        .sortedWith(
            compareByDescending<ReadAloudVoiceGroup> { it.key == selectedGroupKey }
                .thenByDescending { group -> group.voices.any { it.localeLanguage() == preferredLanguage } }
                .thenBy { it.label.lowercase(currentLocale) }
        )
}

internal fun filterReadAloudVoices(
    voices: List<ReadAloudVoiceOption>,
    query: String,
    currentLocale: Locale = Locale.getDefault(),
): List<ReadAloudVoiceOption> {
    val normalizedQuery = query.trim().lowercase(currentLocale)
    if (normalizedQuery.isBlank()) return emptyList()
    return voices
        .filter { voice ->
            voice.label.lowercase(currentLocale).contains(normalizedQuery) ||
                voice.name.lowercase(currentLocale).contains(normalizedQuery) ||
                voice.localeTag.lowercase(currentLocale).contains(normalizedQuery) ||
                voice.voiceGroupLabel(currentLocale).lowercase(currentLocale).contains(normalizedQuery)
        }
        .sortedBy { it.label.lowercase(currentLocale) }
}

internal fun selectedVoiceLabel(
    voices: List<ReadAloudVoiceOption>,
    selectedVoiceName: String?,
): String =
    selectedVoiceName?.let { selected ->
        voices.firstOrNull { it.name == selected }?.label ?: "Selected voice unavailable"
    } ?: "Device default"

private fun ReadAloudVoiceOption.voiceGroupKey(): String =
    localeTag.ifBlank { "other" }

private fun ReadAloudVoiceOption.voiceGroupLabel(displayLocale: Locale): String {
    if (localeTag.isBlank()) return "Other voices"
    val locale = Locale.forLanguageTag(localeTag)
    val displayName = locale.getDisplayName(displayLocale)
    return displayName.takeUnless { it.isBlank() || it.equals("und", ignoreCase = true) } ?: "Other voices"
}

private fun ReadAloudVoiceOption.localeLanguage(): String =
    localeTag.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it).language }.orEmpty()
