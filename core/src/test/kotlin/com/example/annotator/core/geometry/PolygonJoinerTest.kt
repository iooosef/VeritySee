package com.example.annotator.core.geometry

import com.example.annotator.core.codec.BooleanGrid
import com.example.annotator.core.codec.Fixtures
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PolygonJoinerTest {

    @Test
    fun `joining two blobs into one polygon reproduces the union mask`() {
        val case = Fixtures.rleCases().first { it.name == "two_blobs" }
        val grid = BooleanGrid.of(case.rows)
        val contours = ContourTracer.trace(grid)
        assertTrue(contours.size >= 2, "fixture should have multiple components")

        val joined = PolygonJoiner.joinForYolo(contours.map { it.points })
        val rasterized = PolygonRasterizer.rasterize(listOf(joined), case.width, case.height)

        val iou = GeometryTestUtils.iou(grid, rasterized)
        assertTrue(iou >= 0.97, "IoU $iou below 0.97 after joining components")
    }

    @Test
    fun `a single component is returned unchanged`() {
        val square = listOf(Point(0.0, 0.0), Point(2.0, 0.0), Point(2.0, 2.0), Point(0.0, 2.0))
        val joined = PolygonJoiner.joinForYolo(listOf(square))
        assertTrue(joined == square)
    }
}
