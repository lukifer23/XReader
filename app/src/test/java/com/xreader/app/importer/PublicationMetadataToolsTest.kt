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
}
