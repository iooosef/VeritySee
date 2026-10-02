package com.example.annotator.core.geometry

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PolygonRasterizerPerformanceTest {

    // Models a real SAM-exported YOLO segment label file: dozens of small, many-vertex
    // polygons scattered across one reasonably large image. The naive per-pixel x per-polygon
    // rasterizer this guards against made imports like this take effectively forever.
    @Test
    fun `rasterizing many small polygons on a large image is fast`() {
        val width = 1024
        val height = 1024
        val random = Random(42)
        val polygons = (0 until 54).map { polygonIndex ->
            val cx = random.nextDouble(50.0, width - 50.0)
            val cy = random.nextDouble(50.0, height - 50.0)
            val radius = random.nextDouble(10.0, 40.0)
            val vertexCount = 60
            (0 until vertexCount).map { i ->
                val angle = 2 * Math.PI * i / vertexCount
                val jitter = radius * (0.85 + random.nextDouble(0.0, 0.3))
                Point(cx + jitter * cos(angle), cy + jitter * sin(angle))
            }
        }

        val elapsedMs = kotlin.system.measureTimeMillis {
            PolygonRasterizer.rasterize(polygons, width, height)
        }

        assertTrue(elapsedMs < 2000, "rasterizing 54 polygons on a 1024x1024 image took ${elapsedMs}ms, expected well under 2000ms")
    }
}
