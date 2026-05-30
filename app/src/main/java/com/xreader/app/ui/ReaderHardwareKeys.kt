package com.xreader.app.ui

import android.view.KeyEvent
import com.xreader.app.settings.ReaderPageDirection

internal enum class ReaderHardwareKeyAction {
    BACKWARD,
    FORWARD,
    CHROME,
}

internal enum class ReaderHardwareKeyHandling {
    IGNORE,
    CONSUME,
    BACKWARD,
    FORWARD,
    CHROME,
}

internal fun resolveReaderHardwareKeyAction(
    keyCode: Int,
    volumeKeysTurnPages: Boolean = false,
    pageDirection: ReaderPageDirection = ReaderPageDirection.AUTO,
): ReaderHardwareKeyAction? =
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> horizontalPageAction(left = true, pageDirection)

        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_MOVE_HOME,
        -> ReaderHardwareKeyAction.BACKWARD

        KeyEvent.KEYCODE_DPAD_RIGHT -> horizontalPageAction(left = false, pageDirection)

        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_MOVE_END,
        -> ReaderHardwareKeyAction.FORWARD

        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> ReaderHardwareKeyAction.CHROME

        KeyEvent.KEYCODE_VOLUME_UP,
        -> if (volumeKeysTurnPages) ReaderHardwareKeyAction.BACKWARD else null

        KeyEvent.KEYCODE_VOLUME_DOWN,
        -> if (volumeKeysTurnPages) ReaderHardwareKeyAction.FORWARD else null

        else -> null
    }

internal fun shouldHandleReaderHardwareKey(action: Int, repeatCount: Int): Boolean =
    action == KeyEvent.ACTION_UP && repeatCount == 0

internal fun readerHardwareKeyHandling(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    volumeKeysTurnPages: Boolean,
    pageDirection: ReaderPageDirection = ReaderPageDirection.AUTO,
): ReaderHardwareKeyHandling {
    val keyAction = resolveReaderHardwareKeyAction(keyCode, volumeKeysTurnPages, pageDirection)
        ?: return ReaderHardwareKeyHandling.IGNORE
    val volumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
    return when {
        shouldHandleReaderHardwareKey(action, repeatCount) -> keyAction.toHandling()
        volumeKeysTurnPages && volumeKey && action == KeyEvent.ACTION_DOWN -> ReaderHardwareKeyHandling.CONSUME
        else -> ReaderHardwareKeyHandling.IGNORE
    }
}

private fun ReaderHardwareKeyAction.toHandling(): ReaderHardwareKeyHandling =
    when (this) {
        ReaderHardwareKeyAction.BACKWARD -> ReaderHardwareKeyHandling.BACKWARD
        ReaderHardwareKeyAction.FORWARD -> ReaderHardwareKeyHandling.FORWARD
        ReaderHardwareKeyAction.CHROME -> ReaderHardwareKeyHandling.CHROME
    }

private fun horizontalPageAction(
    left: Boolean,
    pageDirection: ReaderPageDirection,
): ReaderHardwareKeyAction =
    when {
        pageDirection == ReaderPageDirection.RIGHT_TO_LEFT && left -> ReaderHardwareKeyAction.FORWARD
        pageDirection == ReaderPageDirection.RIGHT_TO_LEFT -> ReaderHardwareKeyAction.BACKWARD
        left -> ReaderHardwareKeyAction.BACKWARD
        else -> ReaderHardwareKeyAction.FORWARD
    }
