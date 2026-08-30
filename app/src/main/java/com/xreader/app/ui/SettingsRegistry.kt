package com.xreader.app.ui

import java.util.Locale

internal enum class SettingsSectionDefinition(val title: String, val searchTerms: String) {
    APPEARANCE("Appearance", "theme light dark sepia oled"),
    TYPOGRAPHY("Typography", "spacing font size line height margins weight hyphenation alignment publisher"),
    READING("Reading", "pdf layout direction orientation fullscreen tap zones animation awake volume dim read aloud voice sleep neural audiobook pronunciation language"),
    LIBRARY("Library", "sort density books audiobooks list compact comfortable grouping"),
    MAINTENANCE("Maintenance", "repair backup restore export import notes bookmarks progress settings collections integrity"),
}

internal fun SettingsSectionDefinition.matches(query: String): Boolean =
    settingsSectionMatches(query, title, searchTerms)

internal fun settingsSectionMatches(query: String, title: String, terms: String): Boolean {
    val tokens = query.trim().lowercase(Locale.US).split(Regex("\\s+")).filter(String::isNotBlank)
    if (tokens.isEmpty()) return true
    val searchable = "$title $terms".lowercase(Locale.US)
    return tokens.all(searchable::contains)
}

internal fun settingsHasMatches(query: String): Boolean = SettingsSectionDefinition.entries.any { it.matches(query) }
