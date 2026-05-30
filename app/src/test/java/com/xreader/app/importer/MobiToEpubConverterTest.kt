package com.xreader.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class MobiToEpubConverterTest {
    @Test
    fun convertsLegacyMobiMetadataAndBodyIntoParseableEpub() {
        val dir = Files.createTempDirectory("xreader-mobi-test").toFile()
        val source = File(dir, "Orbital Legacy.mobi")
        val output = File(dir, "Orbital Legacy.epub")
        source.writeBytes(
            legacyMobi(
                title = "Orbital Legacy",
                author = "Mina Patel",
                body = """
                    <html><body>
                      <h1>Arrival</h1>
                      <p>The courier crossed the quiet orbit.</p>
                      <p>Every docking light stayed green.</p>
                    </body></html>
                """.trimIndent()
            )
        )

        MobiToEpubConverter().convert(source, output, "Fallback")

        val parsed = EpubParser().parse(output)
        assertEquals("Orbital Legacy", parsed.metadata.title)
        assertEquals("Mina Patel", parsed.metadata.author)
        assertTrue(parsed.units.any { it.body.contains("courier crossed") })
        assertTrue(parsed.units.sumOf { it.wordCount } >= 10)
    }

    @Test
    fun decompressesPalmDocCompressedRecords() {
        val dir = Files.createTempDirectory("xreader-palmdoc-test").toFile()
        val source = File(dir, "PalmDoc.mobi")
        val output = File(dir, "PalmDoc.epub")
        source.writeBytes(
            legacyMobi(
                title = "PalmDoc",
                author = null,
                body = "PalmDOC compression can still carry readable text.",
                compression = PALMDOC_COMPRESSION
            )
        )

        MobiToEpubConverter().convert(source, output, "PalmDoc")

        val parsed = EpubParser().parse(output)
        assertEquals("PalmDoc", parsed.metadata.title)
        assertTrue(parsed.units.any { it.body.contains("readable text") })
    }

    @Test
    fun rejectsEncryptedMobi() {
        val dir = Files.createTempDirectory("xreader-encrypted-mobi-test").toFile()
        val source = File(dir, "Encrypted.mobi")
        val output = File(dir, "Encrypted.epub")
        source.writeBytes(
            legacyMobi(
                title = "Encrypted",
                author = null,
                body = "Encrypted content",
                encryption = 1
            )
        )

        val error = runCatching {
            MobiToEpubConverter().convert(source, output, "Encrypted")
        }.exceptionOrNull()

        assertEquals("Encrypted MOBI files are not supported.", error?.message)
    }

    private fun legacyMobi(
        title: String,
        author: String?,
        body: String,
        compression: Int = PALMDOC_COMPRESSION_NONE,
        encryption: Int = 0,
    ): ByteArray {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val textRecord = when (compression) {
            PALMDOC_COMPRESSION_NONE -> bodyBytes
            PALMDOC_COMPRESSION -> bodyBytes
            else -> error("Unsupported test compression")
        }
        val record0 = mobiHeaderRecord(
            title = title,
            author = author,
            compression = compression,
            encryption = encryption,
            textLength = bodyBytes.size,
            textRecordCount = 1
        )
        return palmDatabase(listOf(record0, textRecord))
    }

    private fun mobiHeaderRecord(
        title: String,
        author: String?,
        compression: Int,
        encryption: Int,
        textLength: Int,
        textRecordCount: Int,
    ): ByteArray {
        val mobiHeaderLength = 232
        val record = ByteArrayOutputStream()
        val palmDoc = ByteArray(16)
        palmDoc.putU16(0, compression)
        palmDoc.putU32(4, textLength)
        palmDoc.putU16(8, textRecordCount)
        palmDoc.putU16(10, 4096)
        palmDoc.putU16(12, encryption)
        record.write(palmDoc)

        val mobi = ByteArray(mobiHeaderLength)
        "MOBI".toByteArray(Charsets.US_ASCII).copyInto(mobi, 0)
        mobi.putU32(4, mobiHeaderLength)
        mobi.putU32(12, 65001)
        mobi.putU32(128, 0x40)
        record.write(mobi)

        val exth = exth(author)
        val titleBytes = title.toByteArray(Charsets.UTF_8)
        val titleOffset = 16 + mobiHeaderLength + exth.size
        val bytes = record.toByteArray()
        bytes.putU32(16 + 84, titleOffset)
        bytes.putU32(16 + 88, titleBytes.size)
        val output = ByteArrayOutputStream()
        output.write(bytes)
        output.write(exth)
        output.write(titleBytes)
        return output.toByteArray()
    }

    private fun exth(author: String?): ByteArray {
        val authorBytes = author?.toByteArray(Charsets.UTF_8)
        val recordLength = authorBytes?.let { 8 + it.size } ?: 0
        val totalLength = 12 + recordLength
        val output = ByteArray(totalLength)
        "EXTH".toByteArray(Charsets.US_ASCII).copyInto(output, 0)
        output.putU32(4, totalLength)
        output.putU32(8, if (authorBytes == null) 0 else 1)
        if (authorBytes != null) {
            output.putU32(12, 100)
            output.putU32(16, recordLength)
            authorBytes.copyInto(output, 20)
        }
        return output
    }

    private fun palmDatabase(records: List<ByteArray>): ByteArray {
        val header = ByteArray(78)
        "XReader MOBI".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
        "BOOK".toByteArray(Charsets.US_ASCII).copyInto(header, 60)
        "MOBI".toByteArray(Charsets.US_ASCII).copyInto(header, 64)
        header.putU16(76, records.size)
        val recordTable = ByteArray(records.size * 8)
        var offset = header.size + recordTable.size
        records.forEachIndexed { index, record ->
            recordTable.putU32(index * 8, offset)
            offset += record.size
        }
        return ByteArrayOutputStream().use { output ->
            output.write(header)
            output.write(recordTable)
            records.forEach(output::write)
            output.toByteArray()
        }
    }

    private fun ByteArray.putU16(offset: Int, value: Int) {
        this[offset] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 1] = (value and 0xFF).toByte()
    }

    private fun ByteArray.putU32(offset: Int, value: Int) {
        this[offset] = ((value ushr 24) and 0xFF).toByte()
        this[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        this[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 3] = (value and 0xFF).toByte()
    }

    private companion object {
        private const val PALMDOC_COMPRESSION_NONE = 1
        private const val PALMDOC_COMPRESSION = 2
    }
}
