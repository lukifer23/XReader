package com.xreader.app.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class QnnArtifactManifestValidatorTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun validatesHashToolchainBlockersAndProvenance() {
        val directory = temporaryFolder.newFolder("artifact")
        val model = File(directory, "model.qnn.onnx").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val manifest = File(directory, "model.qnn.manifest.json")
        manifest.writeText(validManifest(model))

        assertTrue(QnnArtifactManifestValidator.isValid(model, manifest))

        model.writeBytes(byteArrayOf(1, 2, 3, 5))
        model.setLastModified(model.lastModified() + 2_000L)
        assertFalse(QnnArtifactManifestValidator.isValid(model, manifest))
    }

    @Test
    fun cacheIsInvalidatedWhenManifestChanges() {
        val directory = temporaryFolder.newFolder("manifest-cache")
        val model = File(directory, "model.qnn.onnx").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val manifest = File(directory, "model.qnn.manifest.json").apply { writeText(validManifest(model)) }
        assertTrue(QnnArtifactManifestValidator.isValid(model, manifest))

        val modified = validManifest(model).replace("\"license\":\"Apache-2.0\"", "\"license\":\"\"")
        manifest.writeText(modified)
        manifest.setLastModified(manifest.lastModified() + 2_000L)
        assertFalse(QnnArtifactManifestValidator.isValid(model, manifest))
    }

    @Test
    fun rejectsDynamicInputsAndUnsortedTokenBuckets() {
        val directory = temporaryFolder.newFolder("invalid")
        val model = File(directory, "model.qnn.onnx").apply { writeBytes(byteArrayOf(6)) }
        val manifest = File(directory, "manifest.json")
        manifest.writeText(
            validManifest(model)
                .replace("\"token_buckets\":[256]", "\"token_buckets\":[512,256]")
                .replace("\"dynamic_inputs\":[]", "\"dynamic_inputs\":[\"tokens\"]")
        )
        assertFalse(QnnArtifactManifestValidator.isValid(model, manifest))
    }

    private fun validManifest(model: File): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(model.readBytes()).joinToString("") { "%02x".format(it) }
        return """{"schema_version":1,"artifact_type":"xreader-kokoro-qnn","output_model":"${model.name}","output_model_sha256":"$hash","output_model_bytes":${model.length()},"strict_qnn_compatible":true,"source_model":{"name":"model.onnx","sha256":"${"a".repeat(64)}","revision":"release"},"toolchain":{"onnx":"1","onnxruntime":"1","qairt":"2"},"token_buckets":[256],"blocker_analysis":{"strict_qnn_compatible":true,"blocking_ops":{},"dynamic_inputs":[],"reason":"compatible"},"provenance":{"source_url":"https://example.invalid/model","license":"Apache-2.0"}}"""
    }
}
