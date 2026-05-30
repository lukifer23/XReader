package com.xreader.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicationMetadataToolsTest {
    @Test
    fun canonicalGenreUsesKnownGenreLabelsBeforeExistingVariants() {
        assertEquals(
            "Science Fiction",
            PublicationMetadataTools.canonicalGenre(" sci-fi ", listOf("Sci-Fi"))
        )
        assertEquals(
            "Science Fiction",
            PublicationMetadataTools.canonicalGenre("Science fiction", emptyList())
        )
    }

    @Test
    fun canonicalAuthorAndSeriesReuseExistingDisplayForm() {
        assertEquals(
            "Pierce Brown",
            PublicationMetadataTools.canonicalAuthor(
                "pierce brown",
                listOf("pierce brown", "PIERCE BROWN", "Pierce Brown")
            )
        )
        assertEquals(
            "Red Rising",
            PublicationMetadataTools.canonicalSeriesName(
                " red   rising ",
                listOf("red rising", "RED RISING", "Red Rising")
            )
        )
    }

    @Test
    fun blankMetadataStaysBlank() {
        assertNull(PublicationMetadataTools.canonicalAuthor("  ", listOf("Author")))
        assertNull(PublicationMetadataTools.canonicalGenre(null, listOf("Science Fiction")))
        assertNull(PublicationMetadataTools.canonicalSeriesName("", listOf("Series")))
    }

    @Test
    fun seriesGenreConsensusPrefersOneStrongGenreOverWeakSeriesLabels() {
        assertEquals(
            "Science Fiction",
            PublicationMetadataTools.seriesGenreConsensus(listOf("Dystopian", "Adventure", "War"))
        )
        assertEquals(
            "Fantasy",
            PublicationMetadataTools.seriesGenreConsensus(listOf("Fantasy", "Young Adult", null))
        )
    }

    @Test
    fun seriesGenreConsensusRejectsConflictingStrongGenres() {
        assertNull(
            PublicationMetadataTools.seriesGenreConsensus(listOf("Science Fiction", "Fantasy", "Adventure"))
        )
        assertNull(
            PublicationMetadataTools.seriesGenreConsensus(listOf("Adventure", "War"))
        )
    }

    @Test
    fun infersKnownSeriesAndIndexFromCommonTitlePatterns() {
        assertEquals(
            PublicationMetadataTools.SeriesTitleInference("Red Rising", 2.0),
            PublicationMetadataTools.inferSeriesFromTitle(
                "Red Rising #2 - Golden Son",
                listOf("Red Rising")
            )
        )
        assertEquals(
            PublicationMetadataTools.SeriesTitleInference("Red Rising", 2.0),
            PublicationMetadataTools.inferSeriesFromTitle(
                "Golden Son (Red Rising, Book Two)",
                listOf("Red Rising")
            )
        )
        assertEquals(
            PublicationMetadataTools.SeriesTitleInference("Red Rising", 3.0),
            PublicationMetadataTools.inferSeriesFromTitle(
                "Book 3 of Red Rising: Morning Star",
                listOf("Red Rising")
            )
        )
    }

    @Test
    fun infersFirstSeriesBookWhenTitleMatchesSeries() {
        assertEquals(
            PublicationMetadataTools.SeriesTitleInference("The Expanse", 1.0),
            PublicationMetadataTools.inferSeriesFromTitle(
                "The Expanse",
                listOf("The Expanse")
            )
        )
    }

    @Test
    fun titleSeriesInferenceUsesLongestKnownSeriesAndAvoidsUnmatchedTitles() {
        assertEquals(
            PublicationMetadataTools.SeriesTitleInference("Red Rising Saga", 4.0),
            PublicationMetadataTools.inferSeriesFromTitle(
                "Red Rising Saga Book 4 - Iron Gold",
                listOf("Red Rising", "Red Rising Saga")
            )
        )
        assertNull(
            PublicationMetadataTools.inferSeriesFromTitle(
                "Unrelated Orbit",
                listOf("Red Rising")
            )
        )
    }
}
