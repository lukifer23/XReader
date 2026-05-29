package com.xreader.app.ui

import android.view.KeyEvent

internal enum class ReaderHardwareKeyAction {
    BACKWARD,
    FORWARD,
    CHROME,
}

internal fun resolveReaderHardwareKeyAction(keyCode: Int): ReaderHardwareKeyAction? =
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_MOVE_HOME,
        -> ReaderHardwareKeyAction.BACKWARD

        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_MOVE_END,
        -> ReaderHardwareKeyAction.FORWARD

        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> ReaderHardwareKeyAction.CHROME

        else -> null
    }

internal fun shouldHandleReaderHardwareKey(action: Int, repeatCount: Int): Boolean =
    action == KeyEvent.ACTION_UP && repeatCount == 0
