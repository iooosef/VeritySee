package com.example.annotator.core.geometry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RectTest {

    @Test
    fun `overlapping rects intersect`() {
        val a = Rect(0.0, 0.0, 10.0, 10.0)
        val b = Rect(5.0, 5.0, 10.0, 10.0)
        assertTrue(a.intersects(b))
        assertTrue(b.intersects(a))
    }

    @Test
    fun `disjoint rects do not intersect`() {
        val a = Rect(0.0, 0.0, 10.0, 10.0)
        val b = Rect(20.0, 20.0, 5.0, 5.0)
        assertFalse(a.intersects(b))
    }

    @Test
    fun `touching edges do not count as intersecting`() {
        val a = Rect(0.0, 0.0, 10.0, 10.0)
        val b = Rect(10.0, 0.0, 10.0, 10.0)
        assertFalse(a.intersects(b))
    }

    @Test
    fun `center is computed from top left and size`() {
        val rect = Rect(2.0, 4.0, 10.0, 6.0)
        assertEquals(7.0, rect.centerX)
        assertEquals(7.0, rect.centerY)
    }
}
