package com.xreader.app.tts

import com.xreader.app.settings.NeuralTtsGender

enum class NeuralTtsModelFamily {
    KOKORO,
}

data class NeuralTtsSpeakerSpec(
    val id: Int,
    val name: String,
    val gender: NeuralTtsGender,
) {
    val label: String get() = name.replace('-', ' ').replace('_', ' ')
}

data class NeuralTtsModelSpec(
    val modelId: String,
    val displayName: String,
    val engine: String,
    val family: NeuralTtsModelFamily = NeuralTtsModelFamily.KOKORO,
    val url: String,
    val archiveBytes: Long,
    val sha256: String,
    val rootDirectory: String,
    val modelFile: String,
    val tokensFile: String,
    val dataDirectory: String,
    val voicesFile: String = "",
    val lexiconFile: String = "",
    val speakerCount: Int,
    val speakers: List<NeuralTtsSpeakerSpec> = emptyList(),
    val sampleRate: Int,
    val gender: NeuralTtsGender,
    val voiceDescription: String,
    val recommended: Boolean = false,
) {
    fun speaker(id: Int): NeuralTtsSpeakerSpec =
        speakers.firstOrNull { it.id == id }
            ?: speakers.firstOrNull()
            ?: NeuralTtsSpeakerSpec(0, displayName, gender)

    fun normalizedSpeakerId(id: Int): Int =
        speaker(id).id
}

object NeuralTtsModelCatalog {
    const val DEFAULT_MODEL_ID = "kokoro-multi-lang-v1_0"

    val models: List<NeuralTtsModelSpec> = listOf(
        NeuralTtsModelSpec(
            modelId = DEFAULT_MODEL_ID,
            displayName = "Kokoro v1.0",
            engine = "Sherpa-ONNX Kokoro",
            family = NeuralTtsModelFamily.KOKORO,
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2",
            archiveBytes = 349_418_188L,
            sha256 = "c133d26353d776da730870dac7da07dbfc9a5e3bc80cc5e8e83ab6e823be7046",
            rootDirectory = "kokoro-multi-lang-v1_0",
            modelFile = "model.onnx",
            tokensFile = "tokens.txt",
            dataDirectory = "espeak-ng-data",
            voicesFile = "voices.bin",
            lexiconFile = "lexicon-us-en.txt",
            speakerCount = 54,
            speakers = listOf(
                NeuralTtsSpeakerSpec(0, "af_heart", NeuralTtsGender.FEMALE),
                NeuralTtsSpeakerSpec(3, "af_bella", NeuralTtsGender.FEMALE),
                NeuralTtsSpeakerSpec(6, "af_nicole", NeuralTtsGender.FEMALE),
                NeuralTtsSpeakerSpec(9, "af_sarah", NeuralTtsGender.FEMALE),
                NeuralTtsSpeakerSpec(14, "am_fenrir", NeuralTtsGender.MALE),
                NeuralTtsSpeakerSpec(16, "am_michael", NeuralTtsGender.MALE),
                NeuralTtsSpeakerSpec(18, "am_puck", NeuralTtsGender.MALE),
                NeuralTtsSpeakerSpec(21, "bf_emma", NeuralTtsGender.FEMALE),
                NeuralTtsSpeakerSpec(22, "bf_isabella", NeuralTtsGender.FEMALE),
                NeuralTtsSpeakerSpec(26, "bm_george", NeuralTtsGender.MALE),
            ),
            sampleRate = 24_000,
            gender = NeuralTtsGender.ANY,
            voiceDescription = "Quality-focused Kokoro release with a curated English narrator set from the 54-voice model",
            recommended = true
        )
    )

    fun requireModel(modelId: String): NeuralTtsModelSpec =
        models.firstOrNull { it.modelId == modelId } ?: error("Unknown neural TTS model: $modelId")
}
