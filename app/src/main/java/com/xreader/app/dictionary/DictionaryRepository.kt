package com.xreader.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.xreader.app.data.DictionaryDao
import com.xreader.app.data.DictionaryEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DictionaryRepository(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val dao: DictionaryDao,
) {
    @Volatile private var database: SQLiteDatabase? = null

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        openDatabase()
    }

    suspend fun lookup(rawWord: String): List<DictionaryEntryEntity> = withContext(Dispatchers.IO) {
        val candidates = DictionaryLemmatizer.candidates(rawWord)
        if (candidates.isEmpty()) return@withContext emptyList()
        candidates.firstNotNullOfOrNull { candidate ->
            query(candidate).takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    private fun query(lemma: String): List<DictionaryEntryEntity> {
        val db = openDatabase()
        val entries = mutableListOf<DictionaryEntryEntity>()
        db.rawQuery(
            """
            SELECT id, lemma, part_of_speech, definition, synonyms
            FROM entries
            WHERE lemma = ?
            ORDER BY part_of_speech ASC, id ASC
            LIMIT 24
            """.trimIndent(),
            arrayOf(lemma)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries += DictionaryEntryEntity(
                    id = cursor.getLong(0),
                    lemma = cursor.getString(1),
                    partOfSpeech = cursor.getString(2),
                    definition = cursor.getString(3),
                    synonyms = cursor.getString(4)
                )
            }
        }
        return entries
    }

    private fun openDatabase(): SQLiteDatabase {
        database?.let { return it }
        return synchronized(this) {
            database ?: run {
                val targetDir = File(context.noBackupFilesDir, "dictionary").apply { mkdirs() }
                val target = File(targetDir, "wordnet.db")
                val opened = synchronized(assetCopyLock) {
                    ensureAsset(target)
                    runCatching { openReadOnly(target) }.getOrElse { error ->
                        if (error is SQLiteException) {
                            target.delete()
                            ensureAsset(target)
                            openReadOnly(target)
                        } else {
                            throw error
                        }
                    }
                }
                opened.also { database = it }
            }
        }
    }

    private fun ensureAsset(target: File) {
        if (target.exists() && target.length() > 0L) return
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.delete()
        context.assets.open("dictionary/wordnet.db").use { input ->
            temp.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Could not install dictionary database asset." }
    }

    private fun openReadOnly(target: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            target.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )

    companion object {
        private val assetCopyLock = Any()
    }
}
