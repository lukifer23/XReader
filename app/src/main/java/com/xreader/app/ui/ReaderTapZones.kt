package com.xreader.app.ui

import com.xreader.app.settings.ReaderPageDirection
import com.xreader.app.settings.ReaderSettings

internal enum class ReaderTapAction {
    BACKWARD,
    FORWARD,
    CHROME,
}

internal fun resolveReaderTapAction(
    x: Float,
    width: Float,
    settings: ReaderSettings,
    edgeGuardPx: Float,
    pageDirection: ReaderPageDirection = settings.pageDirection,
): ReaderTapAction {
    if (!settings.tapZonesEnabled || width <= 0f) return ReaderTapAction.CHROME
    val boundedX = x.coerceIn(0f, width)
    val sideFraction = settings.tapZonePreset.sideFraction.coerceIn(0.18f, 0.46f)
    val edgeGuard = edgeGuardPx.coerceIn(0f, width / 3f)
    val leftEnd = width * sideFraction
    val rightStart = width * (1f - sideFraction)
    val leftAction = if (pageDirection == ReaderPageDirection.RIGHT_TO_LEFT) {
        ReaderTapAction.FORWARD
    } else {
        ReaderTapAction.BACKWARD
    }
    val rightAction = if (pageDirection == ReaderPageDirection.RIGHT_TO_LEFT) {
        ReaderTapAction.BACKWARD
    } else {
        ReaderTapAction.FORWARD
    }
    return when {
        edgeGuard < leftEnd && boundedX in edgeGuard..leftEnd -> leftAction
        rightStart < width - edgeGuard && boundedX in rightStart..(width - edgeGuard) -> rightAction
        else -> ReaderTapAction.CHROME
    }
}
