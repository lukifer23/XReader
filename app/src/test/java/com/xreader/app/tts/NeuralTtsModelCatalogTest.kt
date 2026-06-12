package com.xreader.app.tts

import com.xreader.app.settings.NeuralTtsGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralTtsModelCatalogTest {
    @Test
    fun defaultModelDescribesRealDownloadableSherpaAsset() {
        val model = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)

        assertEquals(1, NeuralTtsModelCatalog.models.size)
        assertEquals("kokoro-multi-lang-v1_0", model.modelId)
        assertEquals("Sherpa-ONNX Kokoro", model.engine)
        assertTrue(model.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"))
        assertEquals(349_418_188L, model.archiveBytes)
        assertEquals("c133d26353d776da730870dac7da07dbfc9a5e3bc80cc5e8e83ab6e823be7046", model.sha256)
        assertEquals("model.onnx", model.modelFile)
        assertEquals("tokens.txt", model.tokensFile)
        assertEquals("espeak-ng-data", model.dataDirectory)
        assertEquals("voices.bin", model.voicesFile)
        assertEquals("lexicon-us-en.txt", model.lexiconFile)
        assertEquals(24_000, model.sampleRate)
        assertEquals(NeuralTtsGender.ANY, model.gender)
        assertEquals(54, model.speakerCount)
        assertEquals("af_heart", model.speaker(0).name)
        assertEquals("bm_george", model.speaker(26).name)
        assertTrue(model.recommended)
    }
}
