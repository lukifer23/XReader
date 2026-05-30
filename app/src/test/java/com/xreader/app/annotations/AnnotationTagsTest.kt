package com.xreader.app.annotations

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationTagsTest {
    @Test
    fun normalizesCommaSeparatedTags() {
        assertEquals(
            "Craft, mars, World building",
            normalizeAnnotationTags("  #Craft, mars , Craft\nWorld   building ")
        )
    }

    @Test
    fun labelIsEmptyWhenTagsAreEmpty() {
        assertEquals("", annotationTagsLabel(" , # "))
        assertEquals("Tags: theme, Mars", annotationTagsLabel(" theme, #Mars, theme "))
    }

    @Test
    fun summarizesTagsByFrequency() {
        assertEquals(
            listOf(
                AnnotationTagSummary(label = "Mars", count = 3),
                AnnotationTagSummary(label = "craft", count = 2),
                AnnotationTagSummary(label = "theme", count = 1)
            ),
            summarizeAnnotationTags(
                listOf(
                    "Mars, craft",
                    "#mars, theme",
                    "craft, Mars"
                )
            )
        )
    }

    @Test
    fun tagMatchingIsCaseInsensitive() {
        assertEquals(true, tagMatches("Mars, craft", "mars"))
        assertEquals(false, tagMatches("Mars, craft", "theme"))
        assertEquals(true, tagMatches("Mars, craft", null))
    }
}
