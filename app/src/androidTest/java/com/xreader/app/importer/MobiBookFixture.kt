package com.xreader.app.importer

import java.io.ByteArrayOutputStream

internal fun legacyMobiFixture(
    title: String,
    author: String?,
    body: String,
): ByteArray {
    val bodyBytes = body.toByteArray(Charsets.UTF_8)
    val record0 = mobiHeaderRecord(
        title = title,
        author = author,
        textLength = bodyBytes.size,
        textRecordCount = 1
    )
    return palmDatabase(listOf(record0, bodyBytes))
}

private fun mobiHeaderRecord(
    title: String,
    author: String?,
    textLength: Int,
    textRecordCount: Int,
): ByteArray {
    val mobiHeaderLength = 232
    val record = ByteArrayOutputStream()
    val palmDoc = ByteArray(16)
    palmDoc.putU16(0, 1)
    palmDoc.putU32(4, textLength)
    palmDoc.putU16(8, textRecordCount)
    palmDoc.putU16(10, 4096)
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
    return ByteArrayOutputStream().use { output ->
        output.write(bytes)
        output.write(exth)
        output.write(titleBytes)
        output.toByteArray()
    }
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
