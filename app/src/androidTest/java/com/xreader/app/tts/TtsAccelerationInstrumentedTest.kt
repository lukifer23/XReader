package com.xreader.app.tts

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.xreader.app.settings.NeuralTtsTone
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TtsAccelerationInstrumentedTest {
    private val tag = "TtsAccelerationBenchmark"

    @Test
    fun stagedQnnRuntimeInitializesKokoroAndGeneratesSamples() {
        assumeTrue(
            "Pass -e xreader.tts.accelerationSmoke true to run the installed-model provider smoke test.",
            InstrumentationRegistry.getArguments().getString("xreader.tts.accelerationSmoke") == "true"
        )

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getArguments().getString("xreader.qnn.signed_pd")?.let { raw ->
            TtsAccelerationRuntime.overrideQnnSignedProcessDomainForCurrentProcess(raw.toBooleanStrictOrNull())
        }
        TtsAccelerationRuntime.overrideQnnHtpDeviceOptionsForCurrentProcess(
            includeSignedProcessDomainOption = InstrumentationRegistry.getArguments()
                .getString("xreader.qnn.include_signed_pd_option")
                ?.toBooleanStrictOrNull(),
            socModel = InstrumentationRegistry.getArguments()
                .getString("xreader.qnn.soc_model")
                ?.toIntOrNull(),
            htpArch = InstrumentationRegistry.getArguments()
                .getString("xreader.qnn.htp_arch")
                ?.toIntOrNull(),
        )
        val spec = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)
        val modelDir = File(context.filesDir, "neural-tts/models/${spec.modelId}/${spec.rootDirectory}")
        assumeTrue("Kokoro model must be installed on this device.", File(modelDir, spec.modelFile).isFile)

        val requestedProvider = InstrumentationRegistry.getArguments().getString("xreader.tts.provider")
        val allProviders = TtsAccelerationRuntime.providerOrder(context = context)
        val manifest = File(modelDir, spec.hardwareModelManifestFile)
        Log.i(
            tag,
            "modelDir=${modelDir.absolutePath} manifestExists=${manifest.isFile} " +
                "allProviders=${allProviders.joinToString { TtsAccelerationRuntime.providerDisplayKey(it) }}"
        )
        val providers = allProviders.filter { provider ->
            val hasArtifact = TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider(provider) &&
                neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, provider)
            Log.i(tag, "provider=${TtsAccelerationRuntime.providerDisplayKey(provider)} hasRequiredArtifact=$hasArtifact")
            hasArtifact
        }
        assertTrue(
            "No strict hardware TTS provider is available with the required Kokoro model artifact. " +
                "Package QNN/OpenCL and install a strict-compatible QNN model artifact.",
            requestedProvider != null || providers.isNotEmpty()
        )
        val provider = requestedProvider?.toProviderString(context) ?: providers.first()
        assertTrue(
            "Requested provider is not a strict hardware audiobook generation provider.",
            TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider(provider)
        )
        assertTrue(
            "Requested provider is missing its required Kokoro model artifact.",
            neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, provider)
        )

        val tts = OfflineTts(config = ttsConfig(spec, modelDir, provider))
        try {
            val started = System.nanoTime()
            val generated = tts.generateWithConfig(
                text = "XReader hardware acceleration smoke test.",
                config = GenerationConfig(
                    sid = spec.normalizedSpeakerId(0),
                    speed = 1.0f,
                    silenceScale = NeuralTtsTone.NATURAL.silenceScale,
                )
            )
            val elapsedMillis = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
            assertTrue(generated.sampleRate > 0)
            assertTrue(generated.samples.isNotEmpty())
            val audioMillis = generated.audioDurationMillis().coerceAtLeast(1L)
            val realtimeFactor = generationRealtimeFactor(audioMillis, elapsedMillis)
                ?: throw AssertionError("Hardware smoke test produced invalid timing metrics.")
            Log.i(
                tag,
                "smokeProvider=$provider elapsedMs=$elapsedMillis audioMs=$audioMillis " +
                    "realtimeFactor=$realtimeFactor samples=${generated.samples.size} sampleRate=${generated.sampleRate}"
            )
            assertTrue(
                "Hardware smoke provider ${provider.providerLabel()} is too slow for audiobook generation: " +
                    "realtimeFactor=${"%.2f".format(Locale.US, realtimeFactor)}.",
                isUsableAudiobookHardwareRealtimeFactor(realtimeFactor)
            )
        } finally {
            tts.release()
        }
    }

    @Test
    fun installedKokoroProviderBenchmark() {
        assumeTrue(
            "Pass -e xreader.tts.benchmark true to run the installed-model provider benchmark.",
            InstrumentationRegistry.getArguments().getString("xreader.tts.benchmark") == "true"
        )

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val spec = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)
        val modelDir = File(context.filesDir, "neural-tts/models/${spec.modelId}/${spec.rootDirectory}")
        assumeTrue("Kokoro model must be installed on this device.", File(modelDir, spec.modelFile).isFile)

        val requestedProviders = InstrumentationRegistry.getArguments()
            .getString("xreader.tts.providers")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: TtsAccelerationRuntime.providerOrder(context = context)
        assertTrue(
            "No strict hardware TTS providers are available for benchmark. Pass -e xreader.tts.providers for explicit comparison providers.",
            requestedProviders.isNotEmpty()
        )
        val providers = requestedProviders
            .map { it.toProviderString(context) }
            .filter {
                TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider(it) &&
                    neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, it)
            }
        assertTrue(
            "No requested hardware providers have the required Kokoro model artifacts.",
            providers.isNotEmpty()
        )

        val text = "XReader measures local audiobook generation speed with a realistic sentence that includes punctuation, pauses, and normal narration cadence."
        val results = mutableListOf<Pair<String, Float>>()
        providers.forEach { provider ->
            val runtime = runCatching { OfflineTts(config = ttsConfig(spec, modelDir, provider)) }
                .onFailure { error -> Log.w(tag, "provider=$provider init failed", error) }
                .getOrNull() ?: return@forEach
            runtime.useRuntime {
                runCatching {
                    runtime.generateWithConfig(
                        text = "Warm up the local narrator.",
                        config = generationConfig(spec)
                    )
                    val started = System.nanoTime()
                    val generated = runtime.generateWithConfig(
                        text = text,
                        config = generationConfig(spec)
                    )
                    val elapsedMillis = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
                    val audioMillis = generated.audioDurationMillis().coerceAtLeast(1L)
                    val realtimeFactor = elapsedMillis.toFloat() / audioMillis.toFloat()
                    results += provider to realtimeFactor
                    Log.i(
                        tag,
                        "provider=$provider elapsedMs=$elapsedMillis audioMs=$audioMillis realtimeFactor=$realtimeFactor samples=${generated.samples.size} sampleRate=${generated.sampleRate}"
                    )
                }.onFailure { error ->
                    Log.w(tag, "provider=$provider generation failed", error)
                }
            }
        }

        assertTrue("At least one provider must initialize and generate audio.", results.isNotEmpty())
        val fastest = results.minBy { it.second }
        Log.i(tag, "fastestProvider=${fastest.first} realtimeFactor=${fastest.second} all=$results")
        assertTrue(
            "Fastest hardware provider ${fastest.first.providerLabel()} is still too slow for audiobook generation: " +
                "realtimeFactor=${"%.2f".format(Locale.US, fastest.second)}.",
            isUsableAudiobookHardwareRealtimeFactor(fastest.second)
        )
    }

    private fun ttsConfig(spec: NeuralTtsModelSpec, modelDir: File, provider: String) =
        getOfflineTtsConfig(
            modelDir = modelDir.absolutePath,
            modelName = neuralTtsModelFileForProvider(
                spec = spec,
                modelDir = modelDir,
                provider = provider
            ),
            acousticModelName = "",
            vocoder = "",
            voices = spec.voicesFile,
            lexicon = spec.lexiconFile,
            dataDir = File(modelDir, spec.dataDirectory).absolutePath,
            dictDir = "",
            ruleFsts = "",
            ruleFars = "",
            numThreads = neuralTtsHostThreadCount(
                provider = provider,
                workload = NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION
            ),
            provider = provider,
            isKitten = false
        ).apply {
            require(neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, provider)) {
                "Provider ${provider.providerLabel()} is missing its required Kokoro model artifact."
            }
            silenceScale = NeuralTtsTone.NATURAL.silenceScale
        }

    private fun generationConfig(spec: NeuralTtsModelSpec) =
        GenerationConfig(
            sid = spec.normalizedSpeakerId(0),
            speed = 1.0f,
            silenceScale = NeuralTtsTone.NATURAL.silenceScale,
        )

    private inline fun OfflineTts.useRuntime(block: () -> Unit) {
        try {
            block()
        } finally {
            release()
        }
    }

    private fun String.toProviderString(context: android.content.Context): String {
        val requested = trim().lowercase()
        if (requested !in setOf("qnn", "qnn-gpu", "qnn-htp")) return this
        val qnnProviders = TtsAccelerationRuntime.qnnProviderStrings(context)
        if (requested == "qnn") {
            return qnnProviders.firstOrNull()
                ?: throw AssertionError("QNN provider is unavailable on this device.")
        }
        return qnnProviders.firstOrNull { TtsAccelerationRuntime.providerDisplayKey(it) == requested }
            ?: throw AssertionError("Requested $requested provider is unavailable on this device.")
    }

    private fun String.providerLabel(): String =
        TtsAccelerationRuntime.providerDisplayKey(this)
}

private fun generationRealtimeFactor(audioMillis: Long, computeMillis: Long): Float? {
    if (audioMillis <= 0L || computeMillis <= 0L) return null
    return computeMillis.toFloat() / audioMillis.toFloat()
}

private fun isUsableAudiobookHardwareRealtimeFactor(realtimeFactor: Float): Boolean =
    realtimeFactor <= MAX_AUDIOBOOK_HARDWARE_AUDIO_TIME_FACTOR
