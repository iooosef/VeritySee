package com.example.annotator.core.geometry

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PointInPolygonTest {

    private val square = listOf(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0))

    @Test
    fun `center of the square is inside`() {
        assertTrue(pointInPolygon(Point(5.0, 5.0), square))
    }

    @Test
    fun `point outside the square is outside`() {
        assertFalse(pointInPolygon(Point(15.0, 5.0), square))
    }

    @Test
    fun `point on a concave L shape is classified correctly on both sides of the notch`() {
        val lShape = listOf(
            Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 4.0),
            Point(4.0, 4.0), Point(4.0, 10.0), Point(0.0, 10.0),
        )
        assertTrue(pointInPolygon(Point(2.0, 2.0), lShape))
        assertFalse(pointInPolygon(Point(7.0, 7.0), lShape))
    }
}
