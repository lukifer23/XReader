package com.xreader.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderNavigationPaneTest {
    @Test
    fun defaultsToContentsWhenTocIsAvailableOrLoading() {
        assertEquals(
            ReaderNavigationPane.CONTENTS,
            defaultReaderNavigationPane(
                hasContents = true,
                hasBookmarks = true,
                hasAnnotations = true
            )
        )
    }

    @Test
    fun fallsBackToBookmarksThenNotesWhenTocIsEmpty() {
        assertEquals(
            ReaderNavigationPane.BOOKMARKS,
            defaultReaderNavigationPane(
                hasContents = false,
                hasBookmarks = true,
                hasAnnotations = true
            )
        )
        assertEquals(
            ReaderNavigationPane.NOTES,
            defaultReaderNavigationPane(
                hasContents = false,
                hasBookmarks = false,
                hasAnnotations = true
            )
        )
    }

    @Test
    fun emptyNavigationStillShowsContentsPane() {
        assertEquals(
            ReaderNavigationPane.CONTENTS,
            defaultReaderNavigationPane(
                hasContents = false,
                hasBookmarks = false,
                hasAnnotations = false
            )
        )
    }
}
