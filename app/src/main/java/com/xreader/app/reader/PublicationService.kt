@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class, org.readium.r2.shared.Search::class)

package com.xreader.app.reader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Trace
import com.xreader.app.data.BookEntity
import com.xreader.app.readium.ReadiumRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.format.FormatHints
import java.io.File
import kotlin.math.roundToInt

class PublicationService(
    private val context: Context,
    private val readiumRuntime: ReadiumRuntime,
) {
    suspend fun open(book: BookEntity): OpenPublication.Readium = withContext(Dispatchers.IO) {
        traced("XReader:openPublication") {
            val file = File(context.filesDir, book.filePath)
            require(file.exists()) { "Book file is missing: ${book.fileName}" }

            val hints = FormatHints().addFileExtension(file.extension)
            val assetTry = traced("XReader:retrieveAsset") {
                readiumRuntime.assetRetriever.retrieve(file, hints)
            }
            val asset = assetTry.getOrNull()
                ?: error("Could not read ${book.title}: ${assetTry.failureOrNull()}")

            val publicationTry = traced("XReader:openReadiumPublication") {
                readiumRuntime.publicationOpener.open(asset, allowUserInteraction = false)
            }
            val publication = publicationTry.getOrNull()
                ?: run {
                    asset.close()
                    error("Could not open ${book.title}: ${publicationTry.failureOrNull()}")
                }

            try {
                val positions = traced("XReader:loadPositions") {
                    publication.positions().ifEmpty {
                        val lastReadingOrderIndex = (publication.readingOrder.size - 1).coerceAtLeast(0)
                        publication.readingOrder.mapIndexedNotNull { index, link ->
                            publication.locatorFromLink(link)?.copyWithLocations(
                                progression = null,
                                position = index + 1,
                                totalProgression = if (publication.readingOrder.isEmpty()) null else {
                                    if (lastReadingOrderIndex == 0) 1.0 else index.toDouble() / lastReadingOrderIndex.toDouble()
                                }
                            )
                        }
                    }
                }

                OpenPublication.Readium(
                    book = book,
                    format = book.format,
                    file = file,
                    publication = publication,
                    units = positions.toReadingUnits(book),
                    positions = positions,
                    tableOfContents = emptyList()
                )
            } catch (error: Throwable) {
                publication.close()
                throw error
            }
        }
    }

    suspend fun tableOfContents(openPublication: OpenPublication.Readium): List<ReaderNavigationItem> =
        withContext(Dispatchers.IO) {
            traced("XReader:loadToc") {
                openPublication.publication.tableOfContents.toNavigationItems(openPublication.publication)
            }
        }

    suspend fun search(openPublication: OpenPublication.Readium, query: String, limit: Int = 80): List<ReaderSearchResult> =
        withContext(Dispatchers.IO) {
            traced("XReader:readiumSearch") {
                val publication = openPublication.publication
                if (!publication.isSearchable || query.isBlank()) return@traced emptyList()

                val iterator = publication.search(query) ?: return@traced emptyList()
                val results = mutableListOf<ReaderSearchResult>()
                try {
                    while (results.size < limit) {
                        val collection = iterator.next().getOrNull() ?: break
                        val locators = collection.locators
                        if (locators.isEmpty()) break
                        locators.take(limit - results.size).mapTo(results) { locator ->
                            ReaderSearchResult(
                                title = locator.title
                                    ?: collection.metadata.title
                                    ?: locator.href.toString(),
                                snippet = locator.searchSnippet(query),
                                locatorJson = locator.toJSON().toString(),
                                unitIndex = locator.searchUnitIndex(openPublication.positions.size)
                            )
                        }
                    }
                } finally {
                    iterator.close()
                }
                results
            }
        }

    private fun List<Locator>.toReadingUnits(book: BookEntity): List<ReadingUnit> {
        val estimatedWordsPerPosition = if (isEmpty() || book.wordCount <= 0) {
            0
        } else {
            (book.wordCount.toDouble() / size.toDouble()).roundToInt().coerceAtLeast(1)
        }
        return mapIndexed { index, locator ->
            ReadingUnit(
                index = index,
                locator = locator.toJSON().toString(),
                heading = locator.title ?: "Position ${index + 1}",
                body = locator.text.highlight.orEmpty(),
                wordCount = estimatedWordsPerPosition
            )
        }
    }

    private fun List<Link>.toNavigationItems(publication: Publication): List<ReaderNavigationItem> {
        val items = mutableListOf<ReaderNavigationItem>()
        fun visit(links: List<Link>, level: Int) {
            links.forEach { link ->
                val title = link.title?.takeIf { it.isNotBlank() } ?: link.href.toString()
                publication.locatorFromLink(link)?.let { locator ->
                    items += ReaderNavigationItem(
                        title = title,
                        locatorJson = locator.toJSON().toString(),
                        level = level
                    )
                }
                if (link.children.isNotEmpty()) visit(link.children, level + 1)
            }
        }
        visit(this, 0)
        return items
    }

    private fun Locator.searchSnippet(query: String): String {
        val parts = listOfNotNull(
            text.before?.takeIf { it.isNotBlank() },
            text.highlight?.takeIf { it.isNotBlank() },
            text.after?.takeIf { it.isNotBlank() }
        )
        return parts.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { title ?: query }
    }

    private fun Locator.searchUnitIndex(positionCount: Int): Int? {
        if (positionCount <= 0) return null
        val lastIndex = positionCount - 1
        locations.position?.let { position ->
            if (position > 0) return (position - 1).coerceIn(0, lastIndex)
        }
        locations.totalProgression?.let { progression ->
            return (lastIndex * progression.coerceIn(0.0, 1.0)).roundToInt().coerceIn(0, lastIndex)
        }
        return null
    }

    @SuppressLint("UnclosedTrace")
    private inline fun <T> traced(name: String, block: () -> T): T {
        Trace.beginSection(name.take(127))
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }
}
