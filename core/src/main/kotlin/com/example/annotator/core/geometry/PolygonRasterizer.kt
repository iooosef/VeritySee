package com.example.annotator.core.geometry

import com.example.annotator.core.codec.BooleanGrid
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Rasterizes a set of polygons (image pixel coordinates) into a [BooleanGrid].
 * A pixel is set if its center is inside any one polygon under the even-odd rule
 * (multiple polygons are OR-ed together, per FORMATS.md section 2.1).
 *
 * Uses scanline fill restricted to each polygon's own bounding box rather than testing
 * every pixel in the whole image against every polygon: real datasets can have dozens of
 * small polygon annotations per image, where the naive per-pixel approach costs
 * O(width * height * polygons * verticesPerPolygon) and becomes unusably slow.
 */
object PolygonRasterizer {

    fun rasterize(polygons: List<Polygon>, width: Int, height: Int): BooleanGrid {
        val grid = BooleanGrid(width, height)
        for (polygon in polygons) {
            rasterizeOne(polygon, grid, width, height)
        }
        return grid
    }

    private fun rasterizeOne(polygon: Polygon, grid: BooleanGrid, width: Int, height: Int) {
        if (polygon.size < 3) return

        val minY = polygon.minOf { it.y }
        val maxY = polygon.maxOf { it.y }
        val minRow = max(0, floor(minY).toInt())
        val maxRow = min(height - 1, ceil(maxY).toInt())
        if (minRow > maxRow) return

        val crossings = DoubleArray(polygon.size)
        for (row in minRow..maxRow) {
            val py = row + 0.5
            var count = 0
            var j = polygon.size - 1
            for (i in polygon.indices) {
                val pi = polygon[i]
                val pj = polygon[j]
                if ((pi.y > py) != (pj.y > py)) {
                    crossings[count++] = pi.x + (py - pi.y) / (pj.y - pi.y) * (pj.x - pi.x)
                }
                j = i
            }
            if (count < 2) continue
            java.util.Arrays.sort(crossings, 0, count)

            var k = 0
            while (k + 1 < count) {
                // Same boundary convention as pointInPolygon's strict "<" toggle: a pixel
                // center is inside iff it is strictly between a pair of sorted crossings.
                val colStart = max(0, floor(crossings[k] - 0.5).toInt() + 1)
                val colEnd = min(width - 1, ceil(crossings[k + 1] - 0.5).toInt() - 1)
                for (col in colStart..colEnd) {
                    grid[col, row] = true
                }
                k += 2
            }
        }
    }
}
