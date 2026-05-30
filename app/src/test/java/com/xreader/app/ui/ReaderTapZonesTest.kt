package com.xreader.app.ui

import com.xreader.app.settings.ReaderSettings
import com.xreader.app.settings.ReaderPageDirection
import com.xreader.app.settings.ReaderTapZonePreset
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapZonesTest {
    @Test
    fun disabledTapZonesAlwaysToggleChrome() {
        val settings = ReaderSettings(tapZonesEnabled = false)

        assertEquals(ReaderTapAction.CHROME, resolveReaderTapAction(120f, 1000f, settings, edgeGuardPx = 44f))
        assertEquals(ReaderTapAction.CHROME, resolveReaderTapAction(880f, 1000f, settings, edgeGuardPx = 44f))
    }

    @Test
    fun balancedPresetKeepsEdgeGuardAndCenterChrome() {
        val settings = ReaderSettings(tapZonePreset = ReaderTapZonePreset.BALANCED)

        assertEquals(ReaderTapAction.CHROME, resolveReaderTapAction(20f, 1000f, settings, edgeGuardPx = 44f))
        assertEquals(ReaderTapAction.BACKWARD, resolveReaderTapAction(120f, 1000f, settings, edgeGuardPx = 44f))
        assertEquals(ReaderTapAction.CHROME, resolveReaderTapAction(500f, 1000f, settings, edgeGuardPx = 44f))
        assertEquals(ReaderTapAction.FORWARD, resolveReaderTapAction(880f, 1000f, settings, edgeGuardPx = 44f))
        assertEquals(ReaderTapAction.CHROME, resolveReaderTapAction(980f, 1000f, settings, edgeGuardPx = 44f))
    }

    @Test
    fun compactPresetLeavesMoreCenterSpaceThanWidePreset() {
        val compact = ReaderSettings(tapZonePreset = ReaderTapZonePreset.COMPACT)
        val wide = ReaderSettings(tapZonePreset = ReaderTapZonePreset.WIDE)

        assertEquals(ReaderTapAction.CHROME, resolveReaderTapAction(320f, 1000f, compact, edgeGuardPx = 64f))
        assertEquals(ReaderTapAction.BACKWARD, resolveReaderTapAction(320f, 1000f, wide, edgeGuardPx = 24f))
        assertEquals(ReaderTapAction.CHROME, resolveReaderTapAction(680f, 1000f, compact, edgeGuardPx = 64f))
        assertEquals(ReaderTapAction.FORWARD, resolveReaderTapAction(680f, 1000f, wide, edgeGuardPx = 24f))
    }

    @Test
    fun explicitRightToLeftDirectionSwapsSideTapActions() {
        val settings = ReaderSettings(pageDirection = ReaderPageDirection.RIGHT_TO_LEFT)

        assertEquals(ReaderTapAction.FORWARD, resolveReaderTapAction(120f, 1000f, settings, edgeGuardPx = 44f))
        assertEquals(ReaderTapAction.BACKWARD, resolveReaderTapAction(880f, 1000f, settings, edgeGuardPx = 44f))
    }

    @Test
    fun narrowViewsDoNotLetEdgeGuardsTakeOverTheReader() {
        val settings = ReaderSettings(tapZonePreset = ReaderTapZonePreset.COMPACT)

        assertEquals(ReaderTapAction.CHROME, resolveReaderTapAction(20f, 120f, settings, edgeGuardPx = 64f))
        assertEquals(ReaderTapAction.CHROME, resolveReaderTapAction(60f, 120f, settings, edgeGuardPx = 64f))
        assertEquals(ReaderTapAction.CHROME, resolveReaderTapAction(100f, 120f, settings, edgeGuardPx = 64f))
    }
}
