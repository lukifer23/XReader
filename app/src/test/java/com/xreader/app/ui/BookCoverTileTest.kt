package com.xreader.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BookCoverTileTest {
    @Test
    fun coverTargetMaxPixelsScalesWithRenderedTileSize() {
        assertEquals(
            255,
            coverTargetMaxPixels(widthDp = 48f, heightDp = 68f, density = 3f)
        )
        assertEquals(
            160,
            coverTargetMaxPixels(widthDp = 48f, heightDp = 68f, density = 1f)
        )
    }

    @Test
    fun coverTargetMaxPixelsKeepsLargeCoversCapped() {
        assertEquals(
            420,
            coverTargetMaxPixels(widthDp = 180f, heightDp = 260f, density = 3f)
        )
    }
}
