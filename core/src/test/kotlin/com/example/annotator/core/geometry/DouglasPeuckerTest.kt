package com.example.annotator.core.geometry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DouglasPeuckerTest {

    @Test
    fun `collinear points on an open path are dropped`() {
        val points = listOf(
            Point(0.0, 0.0),
            Point(1.0, 0.0),
            Point(2.0, 0.0),
            Point(3.0, 0.0),
        )
        val simplified = DouglasPeucker.simplifyOpen(points, tolerance = 0.5)
        assertEquals(listOf(Point(0.0, 0.0), Point(3.0, 0.0)), simplified)
    }

    @Test
    fun `a staircase rectangle trace simplifies to its 4 corners`() {
        // Pixel-corner staircase boundary of a solid rectangle, going around clockwise
        // in corner coordinates, with extra colinear points along each edge.
        val points = listOf(
            Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0),
            Point(3.0, 1.0), Point(3.0, 2.0),
            Point(2.0, 2.0), Point(1.0, 2.0), Point(0.0, 2.0),
            Point(0.0, 1.0),
        )
        val simplified = DouglasPeucker.simplifyClosed(points, tolerance = 0.1)
        assertEquals(
            listOf(Point(0.0, 0.0), Point(3.0, 0.0), Point(3.0, 2.0), Point(0.0, 2.0)),
            simplified,
        )
    }

    @Test
    fun `a point within tolerance of the line is removed`() {
        val points = listOf(
            Point(0.0, 0.0),
            Point(5.0, 0.4),
            Point(10.0, 0.0),
        )
        val simplified = DouglasPeucker.simplifyOpen(points, tolerance = 0.5)
        assertEquals(listOf(Point(0.0, 0.0), Point(10.0, 0.0)), simplified)
    }

    @Test
    fun `a point beyond tolerance of the line is kept`() {
        val points = listOf(
            Point(0.0, 0.0),
            Point(5.0, 2.0),
            Point(10.0, 0.0),
        )
        val simplified = DouglasPeucker.simplifyOpen(points, tolerance = 0.5)
        assertEquals(listOf(Point(0.0, 0.0), Point(5.0, 2.0), Point(10.0, 0.0)), simplified)
    }
}
