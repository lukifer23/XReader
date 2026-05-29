package com.xreader.app.ui

import com.xreader.app.data.BookmarkEntity
import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.reader.OpenPublication
import com.xreader.app.reader.ReadingUnit
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

data class ResolvedReaderPosition(
    val unitIndex: Int,
    val locatorJson: String?,
    val fromInitialOverride: Boolean,
)

data class ResolvedVisibleReaderPosition(
    val unitIndex: Int,
    val locatorJson: String,
    val readiumLocator: Locator?,
)

fun OpenPublication.positionIndexFor(locator: Locator): Int {
    locator.locations.position?.let { position ->
        if (position > 0) return position - 1
    }
    val href = locator.href.toString()
    val exact = positions.indexOfFirst { it.href.toString() == href && it.locations.fragments == locator.locations.fragments }
    if (exact >= 0) return exact
    locator.locations.totalProgression?.let { progression ->
        if (positions.isNotEmpty()) return ((positions.size - 1).coerceAtLeast(0) * progression).toInt()
    }
    val byHref = positions.indexOfFirst { it.href.toString() == href }
    if (byHref >= 0) return byHref
    locator.locations.progression?.let { progression ->
        if (positions.isNotEmpty()) return ((positions.size - 1).coerceAtLeast(0) * progression).toInt()
    }
    val unit = units.indexOfFirst { it.locator == locator.toJSON().toString() }
    return unit.takeIf { it >= 0 } ?: 0
}

fun locatorToUnit(locator: String, units: List<ReadingUnit>): Int =
    locatorToUnitOrNull(locator, units) ?: 0

internal const val SEARCH_UNIT_LOCATOR_PREFIX = "xreader-search:"

internal fun String.searchUnitIndexOrNull(): Int? =
    takeIf { it.startsWith(SEARCH_UNIT_LOCATOR_PREFIX) }
        ?.removePrefix(SEARCH_UNIT_LOCATOR_PREFIX)
        ?.toIntOrNull()

internal fun resolveInitialReaderPosition(
    initialLocatorOverride: String?,
    saved: ReadingStateEntity?,
    positions: List<Locator>,
    units: List<ReadingUnit>,
    maxIndexedUnit: Int,
): ResolvedReaderPosition {
    val requestedLocator = initialLocatorOverride ?: saved?.locator
    val requestedReadiumLocator = requestedLocator?.toReadiumLocatorOrNull()
    val requestedSearchUnit = requestedLocator?.searchUnitIndexOrNull()
    val positionCount = positions.size.takeIf { it > 0 } ?: units.size.coerceAtLeast(1)
    val unitCount = units.size.coerceAtLeast(1)
    val initialUnit = requestedReadiumLocator
        ?.let { positionIndexFor(locator = it, positions = positions, units = units) }
        ?: requestedSearchUnit?.let { searchUnit ->
            if (positionCount <= 1) {
                0
            } else {
                ((positionCount - 1) * (searchUnit.coerceAtLeast(0).toDouble() / maxIndexedUnit.coerceAtLeast(1).toDouble()))
                    .roundToInt()
            }
        }
        ?: requestedLocator?.let { locatorToUnitOrNull(it, units) }
        ?: saved?.currentUnit
        ?: 0
    val boundedUnit = initialUnit.coerceIn(0, unitCount - 1)
    val locatorJson = requestedReadiumLocator?.toJSON()?.toString()
        ?: positions.getOrNull(boundedUnit)?.toJSON()?.toString()
        ?: units.getOrNull(boundedUnit)?.locator
        ?: requestedLocator
    return ResolvedReaderPosition(
        unitIndex = boundedUnit,
        locatorJson = locatorJson,
        fromInitialOverride = initialLocatorOverride != null
    )
}

internal fun resolveVisibleReaderPosition(
    visibleUnit: Int?,
    visibleLocatorJson: String?,
    fallbackUnit: Int,
    positions: List<Locator>,
    units: List<ReadingUnit>,
): ResolvedVisibleReaderPosition? {
    if (units.isEmpty()) return null
    val requestedLocator = visibleLocatorJson.cleanLocator()
    val readiumLocator = requestedLocator?.toReadiumLocatorOrNull()
    val unitIndex = (
        readiumLocator?.let { positionIndexFor(locator = it, positions = positions, units = units) }
            ?: visibleUnit
            ?: fallbackUnit
        ).coerceIn(0, units.lastIndex)
    val locatorJson = readiumLocator?.toJSON()?.toString()
        ?: requestedLocator
        ?: positions.getOrNull(unitIndex)?.toJSON()?.toString()
        ?: units.getOrNull(unitIndex)?.locator
        ?: return null
    return ResolvedVisibleReaderPosition(
        unitIndex = unitIndex,
        locatorJson = locatorJson,
        readiumLocator = readiumLocator
    )
}

internal fun resolveReadAloudStartPosition(
    visibleUnit: Int?,
    visibleLocatorJson: String?,
    storedLocatorJson: String?,
    fallbackUnit: Int,
    positions: List<Locator>,
    units: List<ReadingUnit>,
): ResolvedVisibleReaderPosition? {
    val visibleLocator = visibleLocatorJson.cleanLocator()
    val effectiveVisibleUnit = if (visibleLocator == null && visibleUnit != null && visibleUnit <= 0 && fallbackUnit > 0) {
        fallbackUnit
    } else {
        visibleUnit
    }
    val locatorForResolution = visibleLocator ?: storedLocatorJson.cleanLocator().takeIf { effectiveVisibleUnit == null }
    return resolveVisibleReaderPosition(
        visibleUnit = effectiveVisibleUnit,
        visibleLocatorJson = locatorForResolution,
        fallbackUnit = fallbackUnit,
        positions = positions,
        units = units
    )
}

internal fun String.toReadiumLocatorOrNull(): Locator? {
    if (isBlank() || !trimStart().startsWith("{")) return null
    return runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()
}

internal fun List<BookmarkEntity>.bookmarkAtReaderLocation(
    visibleLocatorJson: String?,
    fallbackUnitLocator: String?,
): BookmarkEntity? {
    val visible = visibleLocatorJson.cleanLocator()
    val fallback = fallbackUnitLocator.cleanLocator()
    val visibleKey = visible?.toBookmarkLocatorKey()
    val fallbackKey = fallback?.toBookmarkLocatorKey()
    return firstOrNull { bookmark -> visible != null && bookmark.locator.trim() == visible }
        ?: firstOrNull { bookmark ->
            val bookmarkKey = bookmark.locator.toBookmarkLocatorKey()
            bookmarkKey != null && visibleKey != null && bookmarkKey.sameLocationAs(visibleKey)
        }
        ?: firstOrNull { bookmark -> fallback != null && bookmark.locator.trim() == fallback }
        ?: firstOrNull { bookmark ->
            val bookmarkKey = bookmark.locator.toBookmarkLocatorKey()
            bookmarkKey != null && fallbackKey != null && bookmarkKey.sameLocationAs(fallbackKey)
        }
}

internal fun pushReaderReturnLocator(
    history: MutableList<String>,
    visibleLocatorJson: String?,
    fallbackUnitLocator: String?,
    targetLocatorJson: String,
    maxEntries: Int = MAX_READER_RETURN_HISTORY,
) {
    val source = visibleLocatorJson.cleanLocator() ?: fallbackUnitLocator.cleanLocator() ?: return
    val target = targetLocatorJson.cleanLocator() ?: return
    if (source == target) return
    if (history.lastOrNull() == source) return
    history += source
    while (history.size > maxEntries.coerceAtLeast(1)) {
        history.removeAt(0)
    }
}

internal fun popReaderReturnLocator(history: MutableList<String>): String? =
    if (history.isEmpty()) null else history.removeAt(history.lastIndex)

private fun positionIndexFor(
    locator: Locator,
    positions: List<Locator>,
    units: List<ReadingUnit>,
): Int {
    locator.locations.position?.let { position ->
        if (position > 0) return position - 1
    }
    val href = locator.href.toString()
    val exact = positions.indexOfFirst { it.href.toString() == href && it.locations.fragments == locator.locations.fragments }
    if (exact >= 0) return exact
    locator.locations.totalProgression?.let { progression ->
        if (positions.isNotEmpty()) return ((positions.size - 1).coerceAtLeast(0) * progression).toInt()
    }
    val byHref = positions.indexOfFirst { it.href.toString() == href }
    if (byHref >= 0) return byHref
    locator.locations.progression?.let { progression ->
        if (positions.isNotEmpty()) return ((positions.size - 1).coerceAtLeast(0) * progression).toInt()
    }
    val unit = units.indexOfFirst { it.locator == locator.toJSON().toString() }
    return unit.takeIf { it >= 0 } ?: 0
}

private fun locatorToUnitOrNull(locator: String, units: List<ReadingUnit>): Int? =
    units.indexOfFirst { it.locator == locator }.takeIf { it >= 0 }
        ?: locator.substringAfter(':', "").substringBefore(':').toIntOrNull()

private fun String?.cleanLocator(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

private fun String.toBookmarkLocatorKey(): BookmarkLocatorKey? {
    val jsonKey = jsonBookmarkLocatorKey() ?: rawBookmarkLocatorKey()
    return toReadiumLocatorOrNull()?.let { locator ->
        BookmarkLocatorKey(
            href = locator.href.toString().takeIf { it.isNotBlank() } ?: jsonKey?.href ?: return@let null,
            fragments = locator.locations.fragments.ifEmpty { jsonKey?.fragments.orEmpty() },
            position = locator.locations.position ?: jsonKey?.position,
            totalProgression = locator.locations.totalProgression?.quantized() ?: jsonKey?.totalProgression,
            progression = locator.locations.progression?.quantized() ?: jsonKey?.progression
        )
    } ?: jsonKey
}

private fun BookmarkLocatorKey.sameLocationAs(other: BookmarkLocatorKey): Boolean {
    if (position != null && other.position != null) {
        return position == other.position
    }
    if (totalProgression != null && other.totalProgression != null) {
        return totalProgression == other.totalProgression
    }
    if (href != other.href) return false
    if (fragments.isNotEmpty() || other.fragments.isNotEmpty()) {
        return fragments == other.fragments
    }
    if (progression != null && other.progression != null) {
        return progression == other.progression
    }
    return position == null &&
        other.position == null &&
        totalProgression == null &&
        other.totalProgression == null &&
        progression == null &&
        other.progression == null
}

private fun Double.quantized(): Int =
    (coerceIn(0.0, 1.0) * 10_000).roundToInt()

private data class BookmarkLocatorKey(
    val href: String,
    val fragments: List<String>,
    val position: Int?,
    val totalProgression: Int?,
    val progression: Int?,
)

private fun String.jsonBookmarkLocatorKey(): BookmarkLocatorKey? {
    if (!trimStart().startsWith("{")) return null
    return runCatching {
        val root = JSONObject(this)
        val href = root.optString("href").takeIf { it.isNotBlank() } ?: return@runCatching null
        val locations = root.optJSONObject("locations")
        BookmarkLocatorKey(
            href = href,
            fragments = locations?.optJSONArray("fragments")?.toStringList().orEmpty(),
            position = locations?.optIntOrNull("position"),
            totalProgression = locations?.optDoubleOrNull("totalProgression")?.quantized(),
            progression = locations?.optDoubleOrNull("progression")?.quantized()
        )
    }.getOrNull()
}

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }

private fun String.rawBookmarkLocatorKey(): BookmarkLocatorKey? {
    if (!trimStart().startsWith("{")) return null
    val href = HREF_REGEX.find(this)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
    return BookmarkLocatorKey(
        href = href,
        fragments = FRAGMENTS_REGEX.find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { fragmentBody ->
                STRING_VALUE_REGEX.findAll(fragmentBody)
                    .mapNotNull { it.groupValues.getOrNull(1) }
                    .toList()
            }
            .orEmpty(),
        position = POSITION_REGEX.find(this)?.groupValues?.getOrNull(1)?.toIntOrNull(),
        totalProgression = TOTAL_PROGRESSION_REGEX.find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.quantized(),
        progression = PROGRESSION_REGEX.find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.quantized()
    )
}

private val HREF_REGEX = Regex("\"href\"\\s*:\\s*\"([^\"]+)\"")
private val FRAGMENTS_REGEX = Regex("\"fragments\"\\s*:\\s*\\[(.*?)]")
private val STRING_VALUE_REGEX = Regex("\"([^\"]+)\"")
private val POSITION_REGEX = Regex("\"position\"\\s*:\\s*(\\d+)")
private val TOTAL_PROGRESSION_REGEX = Regex("\"totalProgression\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
private val PROGRESSION_REGEX = Regex("(?<!total)\"progression\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
private const val MAX_READER_RETURN_HISTORY = 24
