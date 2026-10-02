package com.example.annotator.core.formats

import com.example.annotator.core.model.IndexEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IndexDiffTest {

    @Test
    fun `detects added, removed, changed, and unchanged files`() {
        val cached = listOf(
            IndexEntry("a.jpg", 100, 1000),
            IndexEntry("b.jpg", 200, 2000),
            IndexEntry("c.jpg", 300, 3000),
        )
        val current = listOf(
            IndexEntry("a.jpg", 100, 1000), // unchanged
            IndexEntry("b.jpg", 999, 2000), // size changed
            IndexEntry("d.jpg", 400, 4000), // added
            // c.jpg removed
        )
        val diff = IndexDiff.diff(cached, current)
        assertEquals(listOf("d.jpg"), diff.added)
        assertEquals(listOf("c.jpg"), diff.removed)
        assertEquals(listOf("b.jpg"), diff.changed)
        assertEquals(listOf("a.jpg"), diff.unchanged)
    }

    @Test
    fun `empty cache marks everything as added`() {
        val current = listOf(IndexEntry("a.jpg", 1, 1))
        val diff = IndexDiff.diff(emptyList(), current)
        assertEquals(listOf("a.jpg"), diff.added)
        assertEquals(emptyList<String>(), diff.removed)
    }
}
