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

        assertTrue("Sherpa JNI runtime must stay packaged.", "libsherpa-onnx-jni.so" in packagedLibraries)
        assertTrue("ONNX Runtime must stay packaged.", "libonnxruntime.so" in packagedLibraries)
        assertFalse("C API library is unused when the JNI runtime is packaged.", "libsherpa-onnx-c-api.so" in packagedLibraries)
        assertFalse("C++ API library is unused when the JNI runtime is packaged.", "libsherpa-onnx-cxx-api.so" in packagedLibraries)
    }
}
