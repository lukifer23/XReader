package com.xreader.app.ui

import com.xreader.app.reader.OpenPublication
import com.xreader.app.reader.ReadingUnit
import org.readium.r2.shared.publication.Locator

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
    units.indexOfFirst { it.locator == locator }.takeIf { it >= 0 }
        ?: locator.substringAfter(':', "").substringBefore(':').toIntOrNull()
        ?: 0

internal const val SEARCH_UNIT_LOCATOR_PREFIX = "xreader-search:"

internal fun String.searchUnitIndexOrNull(): Int? =
    takeIf { it.startsWith(SEARCH_UNIT_LOCATOR_PREFIX) }
        ?.removePrefix(SEARCH_UNIT_LOCATOR_PREFIX)
        ?.toIntOrNull()
