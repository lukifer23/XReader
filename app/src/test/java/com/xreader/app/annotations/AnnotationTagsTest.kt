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
}
