package com.xreader.app.ui

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
): ReaderTapAction {
    if (!settings.tapZonesEnabled || width <= 0f) return ReaderTapAction.CHROME
    val boundedX = x.coerceIn(0f, width)
    val sideFraction = settings.tapZonePreset.sideFraction.coerceIn(0.18f, 0.46f)
    val edgeGuard = edgeGuardPx.coerceIn(0f, width / 3f)
    val leftEnd = width * sideFraction
    val rightStart = width * (1f - sideFraction)
    return when {
        edgeGuard < leftEnd && boundedX in edgeGuard..leftEnd -> ReaderTapAction.BACKWARD
        rightStart < width - edgeGuard && boundedX in rightStart..(width - edgeGuard) -> ReaderTapAction.FORWARD
        else -> ReaderTapAction.CHROME
    }
}
