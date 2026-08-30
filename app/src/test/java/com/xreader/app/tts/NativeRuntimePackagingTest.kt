package com.xreader.app.tts

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRuntimePackagingTest {
    @Test
    fun sherpaJniPackagingOmitsUnusedNativeApiLibraries() {
        val nativeRoot = File("src/main/jniLibs")
        assertTrue("Missing native runtime directory: ${nativeRoot.absolutePath}", nativeRoot.isDirectory)

        val packagedLibraries = nativeRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "so" }
            .map { it.name }
            .toSet()

        assertFalse(
            "Native runtime libraries must not include duplicate Finder-style filenames.",
            packagedLibraries.any { it.contains(' ') }
        )
        assertTrue("Sherpa JNI runtime must stay packaged.", "libsherpa-onnx-jni.so" in packagedLibraries)
        assertTrue("ONNX Runtime must stay packaged.", "libonnxruntime.so" in packagedLibraries)
        assertTrue("QNN HTP runtime must include NetRun extensions.", "libQnnHtpNetRunExtensions.so" in packagedLibraries)
        assertTrue("QNN HTP transport requires libcdsprpc.so to be packaged.", "libcdsprpc.so" in packagedLibraries)
        assertFalse("QNN GPU backend library must not be packaged.", "libQnnGpu.so" in packagedLibraries)
        assertFalse("QNN GPU NetRun extension library must not be packaged.", "libQnnGpuNetRunExtensions.so" in packagedLibraries)
        assertFalse("Device OpenCL loader must not be packaged for audiobook generation.", "libOpenCL.so" in packagedLibraries)
        assertFalse("Device Adreno OpenCL library must not be packaged for audiobook generation.", "libOpenCL_adreno.so" in packagedLibraries)
        val qnnProviderModes = TtsAccelerationRuntime.qnnProviderModes(packagedLibraries)
        assertTrue(
            "QNN HTP runtime should remain selectable when its packaged libraries are present.",
            qnnProviderModes.any { it.backend == QnnBackend.HTP }
        )
        assertFalse("C API library is unused when the JNI runtime is packaged.", "libsherpa-onnx-c-api.so" in packagedLibraries)
        assertFalse("C++ API library is unused when the JNI runtime is packaged.", "libsherpa-onnx-cxx-api.so" in packagedLibraries)
    }

    @Test
    fun androidManifestDeclaresQnnDspRpcNativeLibrary() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("Missing Android manifest: ${manifest.absolutePath}", manifest.isFile)

        val text = manifest.readText()
        assertTrue(
            "QNN HTP transport requires libcdsprpc.so to be declared as a native library.",
            text.contains("""<uses-native-library""") &&
                text.contains("""android:name="libcdsprpc.so"""") &&
                text.contains("""android:required="false"""")
        )
    }

    @Test
    fun onnxRuntimeBinaryContainsQnnProviderImplementation() {
        val onnxRuntime = File("src/main/jniLibs/arm64-v8a/libonnxruntime.so")
        assertTrue("Missing packaged ONNX Runtime binary: ${onnxRuntime.absolutePath}", onnxRuntime.isFile)
        assertTrue(
            "Packaged ONNX Runtime must contain QNNExecutionProvider metadata.",
            onnxRuntime.containsAscii("QNNExecutionProvider")
        )
        assertTrue(
            "Packaged ONNX Runtime must contain QNN HTP backend metadata.",
            onnxRuntime.containsAscii("QnnHtp")
        )
        assertTrue(
            "Packaged ONNX Runtime must support hard no-CPU-fallback enforcement.",
            onnxRuntime.containsAscii("session.disable_cpu_ep_fallback")
        )
    }

    @Test
    fun sherpaJniBinaryContainsStrictHardwareTtsPath() {
        val sherpaJni = File("src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so")
        assertTrue("Missing packaged Sherpa JNI binary: ${sherpaJni.absolutePath}", sherpaJni.isFile)
        assertTrue(
            "Packaged Sherpa JNI must include the strict Kokoro fixed-bucket adapter.",
            sherpaJni.containsAscii("strict_token_bucket")
        )
        assertTrue(
            "Packaged Sherpa JNI must enforce no CPU EP fallback in hardware sessions.",
            sherpaJni.containsAscii("session.disable_cpu_ep_fallback")
        )
        assertTrue(
            "Packaged Sherpa JNI must expose QNNExecutionProvider initialization.",
            sherpaJni.containsAscii("QNNExecutionProvider")
        )
        assertTrue(
            "Packaged Sherpa JNI must explicitly reject NNAPI in strict QNN builds.",
            sherpaJni.containsAscii("NNAPI provider is disabled in XReader strict QNN builds.")
        )
        assertFalse(
            "Packaged Sherpa JNI must not expose the NNAPI provider entrypoint.",
            sherpaJni.containsAscii("OrtSessionOptionsAppendExecutionProvider_Nnapi")
        )
        assertFalse(
            "Unsupported provider strings must fail closed instead of falling back to CPU.",
            sherpaJni.containsAscii("Unsupported string: %s. Fallback to cpu")
        )
        assertFalse(
            "WebGPU initialization failure must fail closed instead of falling back to CPU.",
            sherpaJni.containsAscii("Failed to enable WebGpuExecutionProvider: %s. Available providers: %s. Fallback to cpu")
        )
        assertFalse(
            "Packaged Sherpa JNI must not expose WebGPU provider initialization.",
            sherpaJni.containsAscii("WebGpuExecutionProvider")
        )
        assertFalse(
            "Packaged Sherpa JNI must not expose WebGPU provider selection.",
            sherpaJni.containsAscii("Use WebGpuExecutionProvider")
        )
        assertFalse(
            "Packaged Sherpa JNI must not contain provider CPU fallback branches.",
            sherpaJni.containsAscii("Fallback to cpu")
        )
        assertFalse(
            "Packaged Sherpa JNI must not contain provider CUDA fallback branches.",
            sherpaJni.containsAscii("Fallback to cuda")
        )
    }

    private fun File.containsAscii(needle: String): Boolean {
        val target = needle.toByteArray(Charsets.US_ASCII)
        if (target.isEmpty()) return true
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var matched = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) return false
                for (index in 0 until read) {
                    val byte = buffer[index]
                    matched = if (byte == target[matched]) {
                        matched + 1
                    } else if (byte == target[0]) {
                        1
                    } else {
                        0
                    }
                    if (matched == target.size) return true
                }
            }
        }
    }
}
