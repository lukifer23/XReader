package com.xreader.app.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XReaderDatabaseSchemaTest {
    @Test
    fun version11BookAudioSchemaIncludesGenerationSessionBaseline() {
        val schema = schema(version = 11)
        val bookAudio = schema.entity("book_audio")
        val fields = bookAudio.getJSONArray("fields")
        val baseline = (0 until fields.length())
            .map { fields.getJSONObject(it) }
            .firstOrNull { it.getString("columnName") == "generationSessionStartCompletedSegments" }

        assertEquals(11, schema.getInt("version"))
        assertTrue(bookAudio.getString("createSql").contains("generationSessionStartCompletedSegments"))
        assertEquals("INTEGER", baseline?.getString("affinity"))
        assertEquals(true, baseline?.getBoolean("notNull"))
    }

    @Test
    fun version10BookAudioSchemaDoesNotHaveGenerationSessionBaseline() {
        val bookAudio = schema(version = 10).entity("book_audio")

        assertFalse(bookAudio.getString("createSql").contains("generationSessionStartCompletedSegments"))
    }

    @Test
    fun bookAudioUniqueIndexIncludesAudiobookScope() {
        val bookAudio = schema(version = 11).entity("book_audio")
        val indices = bookAudio.getJSONArray("indices")
        val scopedIndex = (0 until indices.length())
            .map { indices.getJSONObject(it) }
            .firstOrNull { it.getString("name") == "index_book_audio_bookId_modelId_speakerId_speed_tone_scope" }
        val columns = requireNotNull(scopedIndex) { "Scoped book_audio index missing from Room schema." }
            .getJSONArray("columnNames")
            .let { array -> (0 until array.length()).map { array.getString(it) } }

        assertEquals(listOf("bookId", "modelId", "speakerId", "speed", "tone", "scope"), columns)
        assertTrue(scopedIndex.getBoolean("unique"))
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
