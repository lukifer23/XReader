package com.xreader.app.ui

import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.reader.OpenPublication
import com.xreader.app.reader.ReadingUnit
import kotlin.math.roundToInt
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

data class ResolvedReaderPosition(
    val unitIndex: Int,
    val locatorJson: String?,
    val fromInitialOverride: Boolean,
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

internal fun String.toReadiumLocatorOrNull(): Locator? {
    if (isBlank() || !trimStart().startsWith("{")) return null
    return runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()
}

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
