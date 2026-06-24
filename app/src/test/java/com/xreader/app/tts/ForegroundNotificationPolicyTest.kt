package com.xreader.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundNotificationPolicyTest {
    @Test
    fun serviceStartsForegroundBeforePostingNotificationUpdates() {
        assertEquals(
            ForegroundNotificationOperation.START_FOREGROUND,
            foregroundNotificationOperation(foregroundStarted = false)
        )
        assertEquals(
            ForegroundNotificationOperation.UPDATE_NOTIFICATION,
            foregroundNotificationOperation(foregroundStarted = true)
        )
    }

    @Test
    fun preAndroid13CanPostForegroundNotificationUpdatesWithoutRuntimePermission() {
        assertTrue(
            canPostForegroundNotificationUpdate(
                androidApiLevel = 32,
                postNotificationsGranted = false
            )
        )
    }

    @Test
    fun android13BlocksForegroundNotificationUpdatesWithoutRuntimePermission() {
        assertFalse(
            canPostForegroundNotificationUpdate(
                androidApiLevel = 33,
                postNotificationsGranted = false
            )
        )
    }

    @Test
    fun android13CanPostForegroundNotificationUpdatesWithRuntimePermission() {
        assertTrue(
            canPostForegroundNotificationUpdate(
                androidApiLevel = 33,
                postNotificationsGranted = true
            )
        )
    }
}
