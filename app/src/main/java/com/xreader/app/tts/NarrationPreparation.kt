package com.xreader.app.tts

import java.security.MessageDigest

data class NarrationPronunciationRule(
    val phrase: String,
    val replacement: String,
    val languageTag: String = "en-US",
    val enabled: Boolean = true,
)

data class NarrationTextOverride(
    val sourceKey: String,
    val include: Boolean,
    val replacementText: String? = null,
)

internal data class NarrationExclusion(
    val sourceKey: String,
    val unitIndex: Int,
    val heading: String,
    val text: String,
    val reason: String,
)

data class NarrationSourceSection(
    val sourceKey: String,
    val unitIndex: Int,
    val heading: String,
    val text: String,
    val included: Boolean,
    val reason: String? = null,
)

internal data class NarrationPreparationReport(
    val prepared: NeuralTtsPreparedBook,
    val exclusions: List<NarrationExclusion>,
    val warnings: List<String>,
    val appliedPronunciationRules: List<NarrationPronunciationRule>,
    val sources: List<NarrationSourceSection>,
    val estimatedDurationMs: Long,
)

internal fun prepareNarrationReport(
    chunks: List<ReadAloudChunk>,
    languageTag: String?,
    pronunciationRules: List<NarrationPronunciationRule> = emptyList(),
    overrides: List<NarrationTextOverride> = emptyList(),
): NarrationPreparationReport {
    val language = normalizeNarrationLanguage(languageTag)
    require(isSupportedNeuralNarrationLanguage(language)) {
        "Neural audiobook generation currently supports English narration only. This book is tagged ${languageTag.orEmpty().ifBlank { "unknown" }}."
    }
    val overrideByKey = overrides.associateBy { it.sourceKey }
    val exclusions = mutableListOf<NarrationExclusion>()
    val eligibleRules = pronunciationRules
        .filter { it.enabled && it.phrase.isNotBlank() && it.replacement.isNotBlank() }
        .filter { normalizeNarrationLanguage(it.languageTag) == language }
        .sortedByDescending { it.phrase.length }
    val preparedChunks = chunks.mapNotNull { chunk ->
        val key = narrationSourceKey(chunk)
        val override = overrideByKey[key]
        if (override?.include == false) {
            exclusions += NarrationExclusion(key, chunk.unitIndex, chunk.heading, chunk.text.take(2_000), "Excluded by the book narration review")
            null
        } else {
            val source = override?.replacementText?.takeIf { it.isNotBlank() } ?: chunk.text
            val replaced = applyExactPronunciationRules(source, eligibleRules)
            chunk.copy(text = replaced, wordCount = readableNarrationWordCount(replaced))
        }
    }
    val structural = NeuralTtsText.prepareDetailed(
        chunks = preparedChunks,
        forceIncludedSourceKeys = overrides.filter { it.include }.mapTo(mutableSetOf()) { it.sourceKey },
    )
    val prepared = structural.prepared
    val allExclusions = exclusions + structural.exclusions
    val exclusionReasons = allExclusions.groupBy { it.sourceKey }.mapValues { (_, values) ->
        values.map { it.reason }.distinct().joinToString("; ")
    }
    val appliedRules = eligibleRules.filter { rule ->
        preparedChunks.any { chunk -> pronunciationRuleMatches(chunk.text, rule) }
    }
    val warnings = buildList {
        if (chunks.isEmpty()) add("No readable source sections were found.")
        if (prepared.segments.isEmpty()) add("Narration preparation removed every source section.")
        if (prepared.segments.any { it.length > 1_400 }) add("At least one unbreakable narration token exceeds the normal segment limit.")
    }
    return NarrationPreparationReport(
        prepared = prepared,
        exclusions = allExclusions,
        warnings = warnings,
        appliedPronunciationRules = appliedRules,
        sources = chunks.map { chunk ->
            val key = narrationSourceKey(chunk)
            NarrationSourceSection(
                sourceKey = key,
                unitIndex = chunk.unitIndex,
                heading = chunk.heading,
                text = chunk.text.take(4_000),
                included = key !in exclusionReasons,
                reason = exclusionReasons[key],
            )
        },
        estimatedDurationMs = ((prepared.wordCount.toDouble() / NARRATION_WORDS_PER_MINUTE) * 60_000.0).toLong(),
    )
}

private fun pronunciationRuleMatches(text: String, rule: NarrationPronunciationRule): Boolean {
    val escaped = Regex.escape(rule.phrase.trim())
    return Regex("(?<![\\p{L}\\p{N}])$escaped(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text)
}

internal fun applyExactPronunciationRules(
    text: String,
    rules: List<NarrationPronunciationRule>,
): String {
    var result = text
    rules.sortedByDescending { it.phrase.length }.forEach { rule ->
        val escaped = Regex.escape(rule.phrase.trim())
        val pattern = Regex("(?<![\\p{L}\\p{N}])$escaped(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        result = pattern.replace(result, rule.replacement.trim())
    }
    return result
}

internal fun narrationSourceKey(chunk: ReadAloudChunk): String {
    val source = "${chunk.unitIndex}\u0000${chunk.locator}\u0000${chunk.heading}\u0000${chunk.text}"
    return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun normalizeNarrationLanguage(value: String?): String {
    val normalized = value.orEmpty().trim().replace('_', '-').lowercase()
    return when {
        normalized.isBlank() -> "en-US"
        normalized == "en" || normalized.startsWith("en-") -> "en-US"
        else -> normalized
    }
}

internal fun isSupportedNeuralNarrationLanguage(languageTag: String): Boolean =
    normalizeNarrationLanguage(languageTag) == "en-US"

private fun readableNarrationWordCount(text: String): Int = Regex("[\\p{L}\\p{N}]+").findAll(text).count()
private const val NARRATION_WORDS_PER_MINUTE = 165.0
