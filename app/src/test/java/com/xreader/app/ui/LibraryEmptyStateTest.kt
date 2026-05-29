package com.xreader.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryEmptyStateTest {
    @Test
    fun firstRunEmptyStateInvitesImport() {
        val copy = LibraryUiState(totalBookCount = 0).emptyStateCopy()

        assertEquals("Build your library", copy.title)
        assertEquals("Import books", copy.primaryAction)
        assertTrue(copy.importsBooks)
    }

    @Test
    fun queryMissDoesNotLookLikeEmptyLibrary() {
        val copy = LibraryUiState(
            query = "missing title",
            matchedBookCount = 0,
            totalBookCount = 3
        ).emptyStateCopy()

        assertEquals("No matching books", copy.title)
        assertEquals("Show all", copy.primaryAction)
        assertFalse(copy.importsBooks)
    }

    @Test
    fun emptyFavoriteFilterOffersReturnToLibrary() {
        val copy = LibraryUiState(
            group = LibraryGroup.FAVORITES,
            matchedBookCount = 3,
            totalBookCount = 3
        ).emptyStateCopy()

        assertEquals("No favorites yet", copy.title)
        assertEquals("Show all", copy.primaryAction)
        assertFalse(copy.importsBooks)
    }

    @Test
    fun queryMatchesHiddenByFilterDoesNotClaimNoSearchMatches() {
        val copy = LibraryUiState(
            query = "darrow",
            group = LibraryGroup.FAVORITES,
            matchedBookCount = 2,
            totalBookCount = 3
        ).emptyStateCopy()

        assertEquals("No matches in Favorites", copy.title)
        assertEquals("Show all", copy.primaryAction)
        assertFalse(copy.importsBooks)
    }

    @Test
    fun emptyCollectionsViewOffersReturnToLibrary() {
        val copy = LibraryUiState(
            group = LibraryGroup.COLLECTIONS,
            matchedBookCount = 3,
            totalBookCount = 3
        ).emptyStateCopy()

        assertEquals("No collections yet", copy.title)
        assertEquals("Show all", copy.primaryAction)
        assertFalse(copy.importsBooks)
    }
}
