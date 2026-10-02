package com.example.annotator.core.geometry

import com.example.annotator.core.codec.BooleanGrid
import com.example.annotator.core.codec.Fixtures
import com.example.annotator.core.codec.RleCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ContourRoundTripTest {

    companion object {
        @JvmStatic
        fun cases() = Fixtures.rleCases()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `trace then rasterize is an exact round trip for shapes without holes`(case: RleCase) {
        val grid = BooleanGrid.of(case.rows)
        val contours = ContourTracer.trace(grid)
        if (contours.any { it.hasHole }) return // holes are filled by design (FORMATS.md 2.2); see IoU test below

        val polygons = contours.map { it.points }
        val rasterized = PolygonRasterizer.rasterize(polygons, case.width, case.height)

        assertTrue(GeometryTestUtils.exactlyEqual(grid, rasterized), "round trip not exact for ${case.name}")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `trace then rasterize never loses foreground pixels, only fills holes`(case: RleCase) {
        val grid = BooleanGrid.of(case.rows)
        val contours = ContourTracer.trace(grid)
        val polygons = contours.map { it.points }
        val rasterized = PolygonRasterizer.rasterize(polygons, case.width, case.height)

        for (col in 0 until case.width) {
            for (row in 0 until case.height) {
                if (grid[col, row]) {
                    assertTrue(rasterized[col, row], "lost foreground pixel ($col,$row) in ${case.name}")
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `component count matches the fixture shape`(case: RleCase) {
        val grid = BooleanGrid.of(case.rows)
        val contours = ContourTracer.trace(grid)

        when (case.name) {
            "empty" -> assertEquals(0, contours.size)
            "two_blobs" -> assertEquals(2, contours.size)
            else -> assertTrue(contours.isNotEmpty(), case.name)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `hole fixture reports a hole, others do not`(case: RleCase) {
        val grid = BooleanGrid.of(case.rows)
        val contours = ContourTracer.trace(grid)

        when (case.name) {
            "hole" -> assertTrue(contours.any { it.hasHole }, "expected a hole warning for ${case.name}")
            "two_blobs" -> assertTrue(contours.none { it.hasHole }, "unexpected hole warning for ${case.name}")
            "rect", "full" -> assertTrue(contours.none { it.hasHole }, "unexpected hole warning for ${case.name}")
            else -> {}
        }
    }
}
