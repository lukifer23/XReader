package com.xreader.app.tts

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal object QnnArtifactManifestValidator {
    private data class CacheKey(
        val modelPath: String,
        val modelBytes: Long,
        val modelModifiedAt: Long,
        val manifestPath: String,
        val manifestBytes: Long,
        val manifestModifiedAt: Long,
    )
    private val validCache = ConcurrentHashMap<CacheKey, Boolean>()

    fun isValid(model: File, manifest: File): Boolean {
        if (!model.isFile || !manifest.isFile) return false
        val key = CacheKey(
            model.absolutePath, model.length(), model.lastModified(),
            manifest.absolutePath, manifest.length(), manifest.lastModified(),
        )
        validCache[key]?.let { return it }
        val valid = runCatching {
            val root = JSONObject(manifest.readText())
            require(root.optInt("schema_version") == 1)
            require(root.optString("artifact_type") == "xreader-kokoro-qnn")
            require(root.optString("output_model") == model.name)
            require(root.optBoolean("strict_qnn_compatible"))
            require(root.optLong("output_model_bytes") == model.length())
            require(root.optString("output_model_sha256").matches(Regex("[a-f0-9]{64}")))
            require(root.optString("output_model_sha256").equals(sha256(model), ignoreCase = true))
            val source = requireNotNull(root.optJSONObject("source_model"))
            require(source.optString("name").isNotBlank())
            require(source.optString("sha256").matches(Regex("[a-f0-9]{64}")))
            require(source.optString("revision").isNotBlank())
            val toolchain = requireNotNull(root.optJSONObject("toolchain"))
            require(toolchain.optString("onnx").isNotBlank())
            require(toolchain.optString("onnxruntime").isNotBlank())
            require(toolchain.optString("qairt").isNotBlank())
            val buckets = requireNotNull(root.optJSONArray("token_buckets"))
            require(buckets.length() > 0)
            val tokenBuckets = (0 until buckets.length()).map(buckets::getInt)
            require(tokenBuckets.all { it > 0 } && tokenBuckets == tokenBuckets.distinct().sorted())
            val blockers = requireNotNull(root.optJSONObject("blocker_analysis"))
            require(blockers.optBoolean("strict_qnn_compatible"))
            require(requireNotNull(blockers.optJSONObject("blocking_ops")).length() == 0)
            require(requireNotNull(blockers.optJSONArray("dynamic_inputs")).length() == 0)
            require(blockers.optString("reason").isNotBlank())
            val provenance = requireNotNull(root.optJSONObject("provenance"))
            require(provenance.optString("source_url").startsWith("https://"))
            require(provenance.optString("license").isNotBlank())
            true
        }.getOrDefault(false)
        if (valid) {
            validCache.keys.removeAll { it.modelPath == model.absolutePath && it != key }
            validCache[key] = true
        }
        return valid
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
