package com.xreader.app.tts

import com.xreader.app.data.NarrationOverrideEntity
import com.xreader.app.data.NarrationDao
import com.xreader.app.data.PronunciationRuleEntity
import java.time.Clock

class NarrationPreferencesRepository(
    private val dao: NarrationDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    data class Preferences(
        val rules: List<NarrationPronunciationRule>,
        val overrides: List<NarrationTextOverride>,
    )

    suspend fun preferences(bookId: Long): Preferences = Preferences(
        rules = dao.enabledPronunciationRules(bookId).map { it.toNarrationRule() },
        overrides = dao.narrationOverrides(bookId).map { NarrationTextOverride(it.sourceKey, it.include, it.replacementText) },
    )

    suspend fun saveRule(
        bookId: Long?,
        phrase: String,
        replacement: String,
        languageTag: String = "en-US",
        enabled: Boolean = true,
    ): Long {
        val cleanPhrase = phrase.trim().take(500)
        val cleanReplacement = replacement.trim().take(500)
        require(cleanPhrase.isNotBlank() && cleanReplacement.isNotBlank()) { "Pronunciation phrase and replacement are required." }
        require(isSupportedNeuralNarrationLanguage(languageTag)) { "Only the English narration profile is currently supported." }
        val existing = dao.pronunciationRule(bookId, normalizeNarrationLanguage(languageTag), cleanPhrase)
        val now = clock.millis()
        return dao.upsertPronunciationRule(
            PronunciationRuleEntity(
                id = existing?.id ?: 0,
                bookId = bookId,
                languageTag = normalizeNarrationLanguage(languageTag),
                phrase = cleanPhrase,
                replacement = cleanReplacement,
                enabled = enabled,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
    }

    suspend fun saveOverride(bookId: Long, sourceKey: String, include: Boolean, replacementText: String?): Long {
        require(sourceKey.matches(Regex("[a-f0-9]{64}"))) { "Narration source key is invalid." }
        val existing = dao.narrationOverride(bookId, sourceKey)
        val now = clock.millis()
        return dao.upsertNarrationOverride(
            NarrationOverrideEntity(
                id = existing?.id ?: 0,
                bookId = bookId,
                sourceKey = sourceKey,
                include = include,
                replacementText = replacementText?.trim()?.take(20_000)?.takeIf { it.isNotBlank() },
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
    }

    private fun PronunciationRuleEntity.toNarrationRule(): NarrationPronunciationRule =
        NarrationPronunciationRule(phrase, replacement, languageTag, enabled)
}
