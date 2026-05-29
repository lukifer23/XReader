package com.xreader.app.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadAloudEngineInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: ReadAloudEngine? = null

    @After
    fun cleanUp() = runBlocking {
        engine?.shutdown()
        scope.cancel()
    }

    @Test
    fun refreshVoicesUsesDeviceTtsWithoutCrashing() = runBlocking {
        val testEngine = ReadAloudEngine(context, scope)
        engine = testEngine

        val voices = testEngine.refreshVoices()

        assertEquals(voices, testEngine.voices.first())
        assertEquals(voices.size, voices.distinctBy { it.name }.size)
        assertTrue(voices.all { it.name.isNotBlank() && it.label.isNotBlank() })
    }
}
