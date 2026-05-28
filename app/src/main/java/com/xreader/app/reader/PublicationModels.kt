package com.xreader.app.reader

import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.io.File

data class ReadingUnit(
    val index: Int,
    val locator: String,
    val heading: String,
    val body: String,
    val wordCount: Int,
)

data class PublicationMetadata(
    val title: String?,
    val author: String?,
    val language: String?,
    val description: String?,
    val genre: String?,
    val year: Int?,
    val series: String?,
    val seriesIndex: Double?,
)

data class ReaderNavigationItem(
    val title: String,
    val locatorJson: String,
    val level: Int,
)

data class ReaderSearchResult(
    val title: String,
    val snippet: String,
    val locatorJson: String,
)

sealed interface OpenPublication : AutoCloseable {
    val book: BookEntity
    val units: List<ReadingUnit>
    val positions: List<Locator>
    val tableOfContents: List<ReaderNavigationItem>

    data class Readium(
        override val book: BookEntity,
        val format: BookFormat,
        val file: File,
        val publication: Publication,
        override val units: List<ReadingUnit>,
        override val positions: List<Locator>,
        override val tableOfContents: List<ReaderNavigationItem>,
    ) : OpenPublication

    override fun close() {
        if (this is Readium) publication.close()
    }
}
