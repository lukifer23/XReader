package com.xreader.app.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XReaderDatabaseSchemaTest {
    @Test
    fun version12BookAudioSchemaIncludesGenerationTelemetry() {
        val schema = schema(version = 12)
        val bookAudio = schema.entity("book_audio")
        val fields = bookAudio.getJSONArray("fields")
        val columns = (0 until fields.length())
            .map { fields.getJSONObject(it) }
            .associateBy { it.getString("columnName") }

        assertEquals(12, schema.getInt("version"))
        assertTrue(bookAudio.getString("createSql").contains("generationSessionStartCompletedSegments"))
        assertTrue(bookAudio.getString("createSql").contains("generationProvider"))
        assertEquals("INTEGER", columns["generationSessionStartCompletedSegments"]?.getString("affinity"))
        assertEquals(true, columns["generationSessionStartCompletedSegments"]?.getBoolean("notNull"))
        assertEquals("TEXT", columns["generationProvider"]?.getString("affinity"))
        assertFalse(columns["generationProvider"]?.optBoolean("notNull", false) ?: true)
        assertEquals("INTEGER", columns["generationAudioMillis"]?.getString("affinity"))
        assertEquals(true, columns["generationAudioMillis"]?.getBoolean("notNull"))
        assertEquals("INTEGER", columns["generationComputeMillis"]?.getString("affinity"))
        assertEquals(true, columns["generationComputeMillis"]?.getBoolean("notNull"))
    }

    @Test
    fun version10BookAudioSchemaDoesNotHaveGenerationSessionBaseline() {
        val bookAudio = schema(version = 10).entity("book_audio")

        assertFalse(bookAudio.getString("createSql").contains("generationSessionStartCompletedSegments"))
    }

    @Test
    fun bookAudioUniqueIndexIncludesAudiobookScope() {
        val bookAudio = schema(version = 13).entity("book_audio")
        val indices = bookAudio.getJSONArray("indices")
        val scopedIndex = (0 until indices.length())
            .map { indices.getJSONObject(it) }
            .firstOrNull { it.getString("name") == "index_book_audio_bookId_modelId_speakerId_speed_tone_scope" }
        val columns = requireNotNull(scopedIndex) { "Scoped book_audio index missing from Room schema." }
            .getJSONArray("columnNames")
            .let { array -> (0 until array.length()).map { array.getString(it) } }

        assertEquals(listOf("bookId", "modelId", "speakerId", "speed", "tone", "scope"), columns)
        assertTrue(scopedIndex.getBoolean("unique"))
        assertEquals(
            "Generated-audiobook profile lookup depends on this left-prefix index.",
            listOf("bookId", "modelId", "speakerId", "speed", "tone"),
            columns.take(5)
        )
    }

    @Test
    fun version13BookAudioSchemaIndexesHotAudiobookQueries() {
        val bookAudio = schema(version = 13).entity("book_audio")
        val indexNames = bookAudio.getJSONArray("indices")
            .let { array -> (0 until array.length()).map { array.getJSONObject(it).getString("name") }.toSet() }

        assertEquals(13, schema(version = 13).getInt("version"))
        assertTrue("Missing global audiobook ordering index.", "index_book_audio_updatedAt" in indexNames)
        assertTrue("Missing generation status index.", "index_book_audio_status" in indexNames)
        assertTrue("Missing model cleanup index.", "index_book_audio_modelId" in indexNames)
    }

    @Test
    fun version14BookAudioSchemaIndexesPlayablePartialRows() {
        val bookAudio = schema(version = 14).entity("book_audio")
        val indices = bookAudio.getJSONArray("indices")
        val index = (0 until indices.length())
            .map { indices.getJSONObject(it) }
            .firstOrNull { it.getString("name") == "index_book_audio_completedSegments" }
        val columns = requireNotNull(index) { "Playable partial audiobook index missing from Room schema." }
            .getJSONArray("columnNames")
            .let { array -> (0 until array.length()).map { array.getString(it) } }

        assertEquals(14, schema(version = 14).getInt("version"))
        assertEquals(listOf("completedSegments"), columns)
        assertFalse(index.getBoolean("unique"))
    }

    @Test
    fun version15SearchSchemaUsesFtsWithoutFullBodyBtreeIndex() {
        val search = schema(version = 15).entity("search_index")
        val indexNames = search.getJSONArray("indices")
            .let { array -> (0 until array.length()).map { array.getJSONObject(it).getString("name") }.toSet() }

        assertEquals(15, schema(version = 15).getInt("version"))
        assertTrue("Book lookup index must remain.", "index_search_index_bookId" in indexNames)
        assertFalse("Normalized full-text must not use a redundant B-tree index.", "index_search_index_normalizedBody" in indexNames)
        schema(version = 15).entity("search_index_fts")
    }

    private fun schema(version: Int): JSONObject {
        val file = File("schemas/com.xreader.app.data.XReaderDatabase/$version.json")
        assertTrue("Missing exported Room schema ${file.path}", file.isFile)
        return JSONObject(file.readText())
            .getJSONObject("database")
    }

    private fun JSONObject.entity(tableName: String): JSONObject {
        val entities = getJSONArray("entities")
        return (0 until entities.length())
            .map { entities.getJSONObject(it) }
            .firstOrNull { it.getString("tableName") == tableName }
            ?: error("Missing entity $tableName")
    }
}
