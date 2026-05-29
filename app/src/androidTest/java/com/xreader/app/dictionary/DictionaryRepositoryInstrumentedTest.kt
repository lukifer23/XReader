package com.xreader.app.dictionary

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xreader.app.data.XReaderDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, XReaderDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun lookupReadsPrebuiltWordNetAsset() = runBlocking {
        val repository = DictionaryRepository(context, db.dictionary())

        val entries = repository.lookup("reader")

        assertTrue(entries.isNotEmpty())
        assertTrue(entries.any { it.lemma == "reader" })
    }

    @Test
    fun lookupHandlesPossessiveSelection() = runBlocking {
        val repository = DictionaryRepository(context, db.dictionary())

        val entries = repository.lookup("reader's")

        assertTrue(entries.isNotEmpty())
        assertTrue(entries.any { it.lemma == "reader" })
    }

    @Test
    fun lookupHandlesIrregularPluralSelection() = runBlocking {
        val repository = DictionaryRepository(context, db.dictionary())

        val entries = repository.lookup("children")

        assertTrue(entries.isNotEmpty())
        assertTrue(entries.any { it.lemma == "child" })
    }

    @Test
    fun lookupHandlesVesPluralSelection() = runBlocking {
        val repository = DictionaryRepository(context, db.dictionary())

        val entries = repository.lookup("wolves")

        assertTrue(entries.isNotEmpty())
        assertTrue(entries.any { it.lemma == "wolf" })
    }
}
