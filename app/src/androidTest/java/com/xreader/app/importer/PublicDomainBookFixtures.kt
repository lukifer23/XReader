package com.xreader.app.importer

import android.content.Context
import java.io.File

class PublicDomainBookFixtures(
    private val instrumentationContext: Context,
    private val root: File,
) {
    fun aliceTxt(): File =
        copyAsset("books/alice_public_domain_excerpt.txt", "Alice Public Domain Excerpt.txt")

    fun aliceEpub(): File {
        val txt = copyAsset("books/alice_public_domain_excerpt.txt", "Alice Public Domain Excerpt Source.txt")
        return File(root, "source/Alice Public Domain Excerpt.epub").also { output ->
            TxtToEpubConverter().convert(txt, output, "Alice Public Domain Excerpt")
        }
    }

    private fun copyAsset(assetPath: String, fileName: String): File {
        val target = File(root, "source/$fileName")
        target.parentFile?.mkdirs()
        instrumentationContext.assets.open(assetPath).use { input ->
            target.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        return target
    }
}
