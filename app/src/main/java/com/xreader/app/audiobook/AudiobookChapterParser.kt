package com.xreader.app.audiobook

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ParsedAudiobookChapter(val title: String, val startMs: Long, val endMs: Long)

object AudiobookChapterParser {
    fun parse(file: File, durationMs: Long): List<ParsedAudiobookChapter> = when (file.extension.lowercase()) {
        "mp3" -> parseId3Chapters(file)
        "m4a", "m4b" -> parseMp4Chapters(file)
        "ogg", "opus", "flac" -> parseCommentChapters(file)
        else -> emptyList()
    }.normalize(durationMs)

    private fun parseId3Chapters(file: File): List<ParsedAudiobookChapter> {
        val bytes = file.inputStream().buffered().use { input ->
            val header = ByteArray(10)
            if (input.read(header) != header.size || !header.copyOfRange(0, 3).contentEquals("ID3".toByteArray())) return emptyList()
            val size = synchsafe(header, 6).coerceAtMost(MAX_TAG_BYTES)
            ByteArray(size).also { payload -> if (input.read(payload) != size) return emptyList() }
        }
        val version = file.inputStream().use { input -> input.skip(3); input.read() }
        val chapters = mutableListOf<ParsedAudiobookChapter>()
        var offset = 0
        while (offset + 10 <= bytes.size) {
            val frameId = bytes.copyOfRange(offset, offset + 4).toString(Charsets.ISO_8859_1)
            val frameSize = if (version >= 4) synchsafe(bytes, offset + 4) else int32(bytes, offset + 4)
            if (frameSize <= 0 || offset + 10 + frameSize > bytes.size) break
            if (frameId == "CHAP") parseChapterFrame(bytes.copyOfRange(offset + 10, offset + 10 + frameSize))?.let(chapters::add)
            offset += 10 + frameSize
        }
        return chapters.sortedBy { it.startMs }
    }

    private fun parseChapterFrame(frame: ByteArray): ParsedAudiobookChapter? {
        val idEnd = frame.indexOf(0)
        if (idEnd < 0 || idEnd + 17 > frame.size) return null
        val start = uint32(frame, idEnd + 1)
        val end = uint32(frame, idEnd + 5)
        var title = frame.copyOfRange(0, idEnd).toString(Charsets.UTF_8).ifBlank { "Chapter" }
        var offset = idEnd + 17
        while (offset + 10 <= frame.size) {
            val id = frame.copyOfRange(offset, offset + 4).toString(Charsets.ISO_8859_1)
            val size = int32(frame, offset + 4)
            if (size <= 0 || offset + 10 + size > frame.size) break
            if (id == "TIT2") title = decodeId3Text(frame.copyOfRange(offset + 10, offset + 10 + size)).ifBlank { title }
            offset += 10 + size
        }
        return ParsedAudiobookChapter(title.sanitizedTitle(), start, end)
    }

    private fun parseMp4Chapters(file: File): List<ParsedAudiobookChapter> {
        val bytes = file.inputStream().buffered().use { input ->
            val limit = minOf(file.length(), MAX_SCAN_BYTES.toLong()).toInt()
            ByteArray(limit).also { input.read(it) }
        }
        val marker = "chpl".toByteArray(Charsets.US_ASCII)
        val markerIndex = bytes.indexOfSequence(marker)
        if (markerIndex < 4 || markerIndex + 13 >= bytes.size) return emptyList()
        val atomStart = markerIndex - 4
        val atomSize = int32(bytes, atomStart)
        val atomEnd = (atomStart + atomSize).coerceAtMost(bytes.size)
        if (atomSize < 14 || atomEnd <= markerIndex + 13) return emptyList()
        var offset = markerIndex + 12
        val count = bytes[offset].toInt() and 0xff
        offset += 1
        val chapters = mutableListOf<Pair<Long, String>>()
        repeat(count.coerceAtMost(MAX_CHAPTERS)) {
            if (offset + 9 > atomEnd) return@repeat
            val start100ns = ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN).long
            offset += 8
            val length = bytes[offset].toInt() and 0xff
            offset += 1
            if (offset + length > atomEnd) return@repeat
            val title = bytes.copyOfRange(offset, offset + length).toString(Charsets.UTF_8).sanitizedTitle()
            offset += length
            chapters += (start100ns / 10_000L).coerceAtLeast(0L) to title
        }
        return chapters.mapIndexed { index, (start, title) ->
            ParsedAudiobookChapter(title.ifBlank { "Chapter ${index + 1}" }, start, chapters.getOrNull(index + 1)?.first ?: 0L)
        }
    }

    private fun parseCommentChapters(file: File): List<ParsedAudiobookChapter> {
        val bytes = file.inputStream().buffered().use { input ->
            val limit = minOf(file.length(), MAX_SCAN_BYTES.toLong()).toInt()
            ByteArray(limit).also { input.read(it) }
        }
        val text = bytes.toString(Charsets.ISO_8859_1)
        val starts = COMMENT_CHAPTER_REGEX.findAll(text).associate { match ->
            match.groupValues[1].toInt() to parseTimestamp(match.groupValues[2])
        }
        val names = COMMENT_NAME_REGEX.findAll(text).associate { match ->
            match.groupValues[1].toInt() to match.groupValues[2].take(256).sanitizedTitle()
        }
        return starts.entries.sortedBy { it.key }.mapIndexed { index, entry ->
            val next = starts.entries.sortedBy { it.key }.getOrNull(index + 1)?.value ?: 0L
            ParsedAudiobookChapter(names[entry.key].orEmpty().ifBlank { "Chapter ${index + 1}" }, entry.value, next)
        }
    }

    private fun List<ParsedAudiobookChapter>.normalize(durationMs: Long): List<ParsedAudiobookChapter> =
        sortedBy { it.startMs }.mapIndexedNotNull { index, chapter ->
            val end = chapter.endMs.takeIf { it > chapter.startMs }
                ?: getOrNull(index + 1)?.startMs
                ?: durationMs
            chapter.copy(endMs = end.coerceAtLeast(chapter.startMs)).takeIf { it.startMs < durationMs || durationMs <= 0 }
        }

    private fun decodeId3Text(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val charset = when (bytes[0].toInt()) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return bytes.copyOfRange(1, bytes.size).toString(charset).trim('\u0000', ' ')
    }

    private fun parseTimestamp(value: String): Long {
        val parts = value.split(':')
        if (parts.size != 3) return 0L
        return (((parts[0].toLongOrNull() ?: 0L) * 60L + (parts[1].toLongOrNull() ?: 0L)) * 60_000L +
            ((parts[2].toDoubleOrNull() ?: 0.0) * 1_000.0).toLong()).coerceAtLeast(0L)
    }

    private fun synchsafe(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7f) shl 21) or ((bytes[offset + 1].toInt() and 0x7f) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7f) shl 7) or (bytes[offset + 3].toInt() and 0x7f)

    private fun int32(bytes: ByteArray, offset: Int): Int = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
    private fun uint32(bytes: ByteArray, offset: Int): Long = int32(bytes, offset).toLong() and 0xffffffffL
    private fun ByteArray.indexOf(value: Int): Int = indices.firstOrNull { this[it].toInt() == value } ?: -1
    private fun ByteArray.indexOfSequence(value: ByteArray): Int = indices.firstOrNull { start ->
        start + value.size <= size && value.indices.all { this[start + it] == value[it] }
    } ?: -1
    private fun String.sanitizedTitle(): String = replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), " ").replace(Regex("\\s+"), " ").trim().take(256)

    private const val MAX_TAG_BYTES = 16 * 1024 * 1024
    private const val MAX_SCAN_BYTES = 32 * 1024 * 1024
    private const val MAX_CHAPTERS = 10_000
    private val COMMENT_CHAPTER_REGEX = Regex("CHAPTER(\\d{1,4})=([0-9:.]+)", RegexOption.IGNORE_CASE)
    private val COMMENT_NAME_REGEX = Regex("CHAPTER(\\d{1,4})NAME=([^\\u0000\\r\\n]+)", RegexOption.IGNORE_CASE)
}
