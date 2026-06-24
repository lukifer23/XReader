package com.xreader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessIdentityTest {
    @Test
    fun mainProcessMatchesPackageName() {
        assertTrue(
            isMainApplicationProcess(
                packageName = "com.xreader.app",
                processName = "com.xreader.app"
            )
        )
    }

    @Test
    fun isolatedAudiobookGenerationProcessIsNotMainProcess() {
        assertFalse(
            isMainApplicationProcess(
                packageName = "com.xreader.app",
                processName = "com.xreader.app:audiobook_generation"
            )
        )
    }

    @Test
    fun missingProcessNameDefaultsToMainForOldDevices() {
        assertTrue(
            isMainApplicationProcess(
                packageName = "com.xreader.app",
                processName = null
            )
        )
    }
}
