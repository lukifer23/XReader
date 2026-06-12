package com.xreader.app.tts

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.xreader.app.settings.NeuralTtsTone
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TtsAccelerationInstrumentedTest {
    @Test
    fun stagedQnnRuntimeInitializesKokoroAndGeneratesSamples() {
        assumeTrue(
            "Pass -e xreader.tts.accelerationSmoke true to run the installed-model provider smoke test.",
            InstrumentationRegistry.getArguments().getString("xreader.tts.accelerationSmoke") == "true"
        )

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val spec = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)
        val modelDir = File(context.filesDir, "neural-tts/models/${spec.modelId}/${spec.rootDirectory}")
        assumeTrue("Kokoro model must be installed on this device.", File(modelDir, spec.modelFile).isFile)

        val requestedProvider = InstrumentationRegistry.getArguments().getString("xreader.tts.provider")
        val providers = TtsAccelerationRuntime.providerOrder(context)
        val provider = requestedProvider ?: providers.first()
        assertTrue(provider in providers || requestedProvider != null)

        val tts = if (requestedProvider == null) {
            createWithFallback(spec, modelDir, providers)
        } else {
            OfflineTts(config = ttsConfig(spec, modelDir, provider))
        }
        try {
            val generated = tts.generateWithConfig(
                text = "XReader hardware acceleration smoke test.",
                config = GenerationConfig(
                    sid = spec.normalizedSpeakerId(0),
                    speed = 1.0f,
                    silenceScale = NeuralTtsTone.NATURAL.silenceScale,
                )
            )
            assertTrue(generated.sampleRate > 0)
            assertTrue(generated.samples.isNotEmpty())
        } finally {
            tts.release()
        }
    }

    private fun ttsConfig(spec: NeuralTtsModelSpec, modelDir: File, provider: String) =
        getOfflineTtsConfig(
            modelDir = modelDir.absolutePath,
            modelName = spec.modelFile,
            acousticModelName = "",
            vocoder = "",
            voices = spec.voicesFile,
            lexicon = spec.lexiconFile,
            dataDir = File(modelDir, spec.dataDirectory).absolutePath,
            dictDir = "",
            ruleFsts = "",
            ruleFars = "",
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
            provider = provider,
            isKitten = false
        ).apply {
            silenceScale = NeuralTtsTone.NATURAL.silenceScale
        }

    private fun createWithFallback(
        spec: NeuralTtsModelSpec,
        modelDir: File,
        providers: List<String>,
    ): OfflineTts {
        var lastError: Throwable? = null
        providers.forEach { provider ->
            runCatching {
                return OfflineTts(config = ttsConfig(spec, modelDir, provider))
            }.onFailure { error ->
                lastError = error
            }
        }
        throw AssertionError("No TTS provider initialized.", lastError)
    }
}
