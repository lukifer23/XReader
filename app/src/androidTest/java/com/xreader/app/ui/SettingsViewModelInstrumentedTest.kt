package com.xreader.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xreader.app.AppContainer
import com.xreader.app.data.XReaderDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsViewModelInstrumentedTest {
    private val baseContext: Context = ApplicationProvider.getApplicationContext()
    private val root = File(baseContext.cacheDir, "settings-view-model-test-${System.nanoTime()}")
    private val context = object : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
    }
    private val db = Room.inMemoryDatabaseBuilder(baseContext, XReaderDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun cleanUp() {
        scope.cancel()
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun settingsRepairActionRebuildsSearchIndexAndReportsResult() = runBlocking {
        val container = AppContainer(context, scope, databaseOverride = db)
        val source = File(root, "source/settings_repair.txt").apply {
            parentFile?.mkdirs()
            writeText(
                """
                XReader settings repair

                The settings repair action should rebuild searchable text for stored books.
                """.trimIndent()
            )
        }
        val importResult = container.libraryRepository.import(Uri.fromFile(source))
        assertTrue(db.search().indexedRowCountForBook(importResult.bookId) > 0)
        db.search().deleteFtsForBook(importResult.bookId.toString())
        db.search().deleteForBook(importResult.bookId)
        assertEquals(0, db.search().indexedRowCountForBook(importResult.bookId))

        val viewModel = SettingsViewModel(container)
        viewModel.repairLibrary()

        val repaired = withTimeout(5_000) {
            viewModel.maintenance.first { !it.repairingLibrary && it.message != null }
        }

        assertNotNull(repaired.message)
        assertTrue(repaired.message!!.startsWith("Repaired 1 book"))
        assertTrue(db.search().indexedRowCountForBook(importResult.bookId) > 0)
    }
}
