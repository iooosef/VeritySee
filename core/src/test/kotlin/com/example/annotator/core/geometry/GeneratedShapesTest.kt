package com.example.annotator.core.geometry

import com.example.annotator.core.codec.BooleanGrid
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedShapesTest {

    private fun checkRoundTrip(grid: BooleanGrid, label: String) {
        val contours = ContourTracer.trace(grid)
        val polygons = contours.map { it.points }

        val exact = PolygonRasterizer.rasterize(polygons, grid.width, grid.height)
        assertTrue(GeometryTestUtils.exactlyEqual(grid, exact), "exact round trip failed for $label")

        val simplifiedPolygons = polygons.map { DouglasPeucker.simplifyClosed(it, 1.0) }
        val simplified = PolygonRasterizer.rasterize(simplifiedPolygons, grid.width, grid.height)
        val iou = GeometryTestUtils.iou(grid, simplified)
        assertTrue(iou >= 0.97, "IoU $iou below 0.97 after simplification for $label")
    }

    @Test
    fun `circle round trips exactly and within tolerance after simplification`() {
        val grid = GeometryTestUtils.circle(size = 60, cx = 30.0, cy = 30.0, radius = 25.0)
        checkRoundTrip(grid, "circle")
    }

    @Test
    fun `ring with a small hole reports a hole and stays within IoU tolerance after simplification`() {
        // A thin inner hole (not a wide annulus): filling it on export only removes a small
        // fraction of the shape, consistent with FORMATS.md 2.2 ("holes are not traced, filled").
        // A wide annulus would legitimately fail the 0.97 IoU bar once its hole is filled in --
        // that is expected lossy behavior for genuinely hole-heavy shapes, not a bug.
        val grid = GeometryTestUtils.ring(size = 70, cx = 35.0, cy = 35.0, outerRadius = 30.0, innerRadius = 2.0)
        val contours = ContourTracer.trace(grid)
        assertTrue(contours.any { it.hasHole }, "ring should report a hole")

        val polygons = contours.map { it.points }
        val simplifiedPolygons = polygons.map { DouglasPeucker.simplifyClosed(it, 1.0) }
        val simplified = PolygonRasterizer.rasterize(simplifiedPolygons, grid.width, grid.height)
        val iou = GeometryTestUtils.iou(grid, simplified)
        assertTrue(iou >= 0.97, "IoU $iou below 0.97 after simplification for ring")
    }

    @Test
    fun `L shape round trips exactly and within tolerance after simplification`() {
        val grid = GeometryTestUtils.lShape(size = 40)
        checkRoundTrip(grid, "L shape")
    }
}
