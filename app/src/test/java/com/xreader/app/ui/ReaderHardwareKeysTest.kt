package com.xreader.app.ui

import android.view.KeyEvent
import com.xreader.app.settings.ReaderPageDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHardwareKeysTest {
    @Test
    fun resolvesKeyboardAndDpadPageKeys() {
        assertEquals(ReaderHardwareKeyAction.BACKWARD, resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(ReaderHardwareKeyAction.BACKWARD, resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(ReaderHardwareKeyAction.FORWARD, resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(ReaderHardwareKeyAction.FORWARD, resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(ReaderHardwareKeyAction.FORWARD, resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_SPACE))
        assertEquals(ReaderHardwareKeyAction.CHROME, resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(ReaderHardwareKeyAction.CHROME, resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun explicitRightToLeftDirectionSwapsHorizontalKeysOnly() {
        assertEquals(
            ReaderHardwareKeyAction.FORWARD,
            resolveReaderHardwareKeyAction(
                KeyEvent.KEYCODE_DPAD_LEFT,
                pageDirection = ReaderPageDirection.RIGHT_TO_LEFT
            )
        )
        assertEquals(
            ReaderHardwareKeyAction.BACKWARD,
            resolveReaderHardwareKeyAction(
                KeyEvent.KEYCODE_DPAD_RIGHT,
                pageDirection = ReaderPageDirection.RIGHT_TO_LEFT
            )
        )
        assertEquals(
            ReaderHardwareKeyAction.BACKWARD,
            resolveReaderHardwareKeyAction(
                KeyEvent.KEYCODE_PAGE_UP,
                pageDirection = ReaderPageDirection.RIGHT_TO_LEFT
            )
        )
        assertEquals(
            ReaderHardwareKeyAction.FORWARD,
            resolveReaderHardwareKeyAction(
                KeyEvent.KEYCODE_PAGE_DOWN,
                pageDirection = ReaderPageDirection.RIGHT_TO_LEFT
            )
        )
    }

    @Test
    fun doesNotHijackSystemVolumeKeys() {
        assertNull(resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertNull(resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertNull(resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
    }

    @Test
    fun volumeKeysCanBeOptedIntoForPageTurns() {
        assertEquals(
            ReaderHardwareKeyAction.BACKWARD,
            resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_VOLUME_UP, volumeKeysTurnPages = true)
        )
        assertEquals(
            ReaderHardwareKeyAction.FORWARD,
            resolveReaderHardwareKeyAction(KeyEvent.KEYCODE_VOLUME_DOWN, volumeKeysTurnPages = true)
        )
    }

    @Test
    fun volumeKeyPageTurnsConsumePressBeforeTurningOnRelease() {
        assertEquals(
            ReaderHardwareKeyHandling.CONSUME,
            readerHardwareKeyHandling(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                volumeKeysTurnPages = true
            )
        )
        assertEquals(
            ReaderHardwareKeyHandling.FORWARD,
            readerHardwareKeyHandling(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0,
                volumeKeysTurnPages = true
            )
        )
        assertEquals(
            ReaderHardwareKeyHandling.IGNORE,
            readerHardwareKeyHandling(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                volumeKeysTurnPages = false
            )
        )
    }

    @Test
    fun repeatedVolumeKeyReleaseDoesNotTurnMultiplePages() {
        assertEquals(
            ReaderHardwareKeyHandling.IGNORE,
            readerHardwareKeyHandling(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_UP,
                repeatCount = 2,
                volumeKeysTurnPages = true
            )
        )
    }

    @Test
    fun handlesOnlyCompletedNonRepeatedKeyPresses() {
        assertTrue(shouldHandleReaderHardwareKey(KeyEvent.ACTION_UP, repeatCount = 0))
        assertFalse(shouldHandleReaderHardwareKey(KeyEvent.ACTION_DOWN, repeatCount = 0))
        assertFalse(shouldHandleReaderHardwareKey(KeyEvent.ACTION_UP, repeatCount = 2))
    }
}
