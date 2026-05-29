package com.xreader.app.tts

import com.xreader.app.data.SearchIndexEntity
import com.xreader.app.reader.ReadingUnit

data class ReadAloudChunk(
    val unitIndex: Int,
    val locator: String,
    val heading: String,
    val text: String,
    val wordCount: Int,
)

object ReadAloudPlanner {
    fun chunksFromRows(rows: List<SearchIndexEntity>): List<ReadAloudChunk> =
        rows
            .sortedBy { it.unitIndex }
            .mapNotNull { row ->
                val text = cleanSpeechText(row.body)
                if (text.isBlank()) {
                    null
                } else {
                    ReadAloudChunk(
                        unitIndex = row.unitIndex,
                        locator = row.locator,
                        heading = row.heading.ifBlank { "Position ${row.unitIndex + 1}" },
                        text = text,
                        wordCount = wordCount(text)
                    )
                }
            }

    fun chunksFromUnits(units: List<ReadingUnit>): List<ReadAloudChunk> =
        units
            .sortedBy { it.index }
            .mapNotNull { unit ->
                val text = cleanSpeechText(unit.body)
                if (text.isBlank()) {
                    null
                } else {
                    ReadAloudChunk(
                        unitIndex = unit.index,
                        locator = unit.locator,
                        heading = unit.heading.ifBlank { "Position ${unit.index + 1}" },
                        text = text,
                        wordCount = unit.wordCount.takeIf { it > 0 } ?: wordCount(text)
                    )
                }
            }

    fun startIndex(
        chunks: List<ReadAloudChunk>,
        currentUnit: Int,
        currentLocator: String? = null,
    ): Int {
        if (chunks.isEmpty()) return 0
        locatorStartIndex(chunks, currentLocator)?.let { return it }
        val exact = chunks.indexOfFirst { it.unitIndex == currentUnit }
        if (exact >= 0) return exact
        val previous = chunks.indexOfLast { it.unitIndex < currentUnit }
        return if (previous >= 0) previous else 0
    }

    fun splitForSpeech(text: String, maxLength: Int = DEFAULT_MAX_SPEECH_LENGTH): List<String> {
        val clean = cleanSpeechText(text)
        if (clean.isBlank()) return emptyList()
        if (clean.length <= maxLength) return listOf(clean)

        val result = mutableListOf<String>()
        val sentences = clean.split(Regex("(?<=[.!?])\\s+"))
        val current = StringBuilder()
        sentences.forEach { sentence ->
            if (sentence.length > maxLength) {
                flush(current, result)
                result += sentence.chunkByWords(maxLength)
            } else if (current.isEmpty()) {
                current.append(sentence)
            } else if (current.length + 1 + sentence.length <= maxLength) {
                current.append(' ').append(sentence)
            } else {
                flush(current, result)
                current.append(sentence)
            }
        }
        flush(current, result)
        return result.filter { it.isNotBlank() }
    }

    fun cleanSpeechText(text: String): String =
        text
            .replace(Regex("\\s+"), " ")
            .replace(" .", ".")
            .replace(" ,", ",")
            .trim()

    private fun flush(builder: StringBuilder, result: MutableList<String>) {
        if (builder.isNotBlank()) {
            result += builder.toString()
            builder.clear()
        }
    }

    private fun String.chunkByWords(maxLength: Int): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        split(Regex("\\s+")).forEach { word ->
            if (word.length > maxLength) {
                flush(current, chunks)
                word.chunked(maxLength).forEach(chunks::add)
            } else if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= maxLength) {
                current.append(' ').append(word)
            } else {
                flush(current, chunks)
                current.append(word)
            }
        }
        flush(current, chunks)
        return chunks
    }

    private fun CharSequence.isNotBlank(): Boolean =
        any { !it.isWhitespace() }

    private fun wordCount(text: String): Int =
        text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }

    private fun locatorStartIndex(
        chunks: List<ReadAloudChunk>,
        currentLocator: String?,
    ): Int? {
        val locator = currentLocator?.trim()?.takeIf { it.isNotBlank() } ?: return null
        chunks.indexOfFirst { it.locator == locator }.takeIf { it >= 0 }?.let { return it }

        val target = locator.readAloudLocatorSignal() ?: return null
        val signals = chunks.map { it.locator.readAloudLocatorSignal() }
        target.position?.let { position ->
            signals.indexOfFirst { it?.position == position }
                .takeIf { it >= 0 }
                ?.let { return it }
            signals.indexOfLast { (it?.position ?: Int.MIN_VALUE) <= position }
                .takeIf { it >= 0 }
                ?.let { return it }
        }

        target.totalProgression?.let { progression ->
            signals.indexOfLast { signal ->
                signal?.totalProgression?.let { chunkProgression -> chunkProgression <= progression } == true
            }.takeIf { it >= 0 }?.let { return it }
        }

        target.progression?.let { progression ->
            signals.indexOfLast { signal ->
                val sameHref = target.href == null || signal?.href == target.href
                sameHref && signal?.progression?.let { chunkProgression -> chunkProgression <= progression } == true
            }.takeIf { it >= 0 }?.let { return it }
        }

        target.href?.let { href ->
            signals.indexOfFirst { signal ->
                signal?.href == href && (target.fragments.isEmpty() || signal.fragments == target.fragments)
            }.takeIf { it >= 0 }?.let { return it }
        }
        return null
    }

    private fun String.readAloudLocatorSignal(): LocatorSignal? {
        if (!trimStart().startsWith("{")) return null
        return runCatching {
            LocatorSignal(
                href = HREF_REGEX.find(this)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() },
                fragments = FRAGMENTS_REGEX.find(this)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let { fragmentBody ->
                        STRING_VALUE_REGEX.findAll(fragmentBody)
                            .mapNotNull { it.groupValues.getOrNull(1) }
                            .toList()
                    }
                    .orEmpty(),
                position = POSITION_REGEX.find(this)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                totalProgression = TOTAL_PROGRESSION_REGEX.find(this)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                progression = PROGRESSION_REGEX.find(this)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            )
        }.getOrNull()
    }

    private data class LocatorSignal(
        val href: String?,
        val fragments: List<String>,
        val position: Int?,
        val totalProgression: Double?,
        val progression: Double?,
    )

    private const val DEFAULT_MAX_SPEECH_LENGTH = 3_600
    private val HREF_REGEX = Regex("\"href\"\\s*:\\s*\"([^\"]+)\"")
    private val FRAGMENTS_REGEX = Regex("\"fragments\"\\s*:\\s*\\[(.*?)]")
    private val STRING_VALUE_REGEX = Regex("\"([^\"]+)\"")
    private val POSITION_REGEX = Regex("\"position\"\\s*:\\s*(\\d+)")
    private val TOTAL_PROGRESSION_REGEX = Regex("\"totalProgression\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    private val PROGRESSION_REGEX = Regex("(?<!total)\"progression\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
}
