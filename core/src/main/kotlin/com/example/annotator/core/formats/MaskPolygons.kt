package com.example.annotator.core.formats

import com.example.annotator.core.codec.BooleanGrid
import com.example.annotator.core.codec.Rle
import com.example.annotator.core.codec.RleCodec
import com.example.annotator.core.geometry.ContourTracer
import com.example.annotator.core.geometry.DouglasPeucker
import com.example.annotator.core.geometry.Point
import com.example.annotator.core.geometry.Polygon
import com.example.annotator.core.geometry.PolygonRasterizer

/** [polygons] is one entry per connected component, in absolute image pixels. */
data class MaskToPolygonsResult(val polygons: List<Polygon>, val hadHoles: Boolean)

/** Shared mask/polygon conversion for YOLO segment and COCO polygon export/import (FORMATS 2.2). */
object MaskPolygons {

    fun maskToPolygons(rle: Rle, simplifyTolerance: Double): MaskToPolygonsResult {
        val grid = RleCodec.decode(rle)
        val contours = ContourTracer.trace(grid)
        val polygons = contours
            .map { DouglasPeucker.simplifyClosed(it.points, simplifyTolerance) }
            .filter { it.size >= 3 && polygonArea(it) >= 1.0 }
        return MaskToPolygonsResult(polygons, hadHoles = contours.any { it.hasHole })
    }

    fun polygonsToMask(polygons: List<Polygon>, width: Int, height: Int): Rle {
        val grid: BooleanGrid = PolygonRasterizer.rasterize(polygons, width, height)
        return RleCodec.encode(grid)
    }

    fun polygonArea(points: Polygon): Double {
        var sum = 0.0
        for (i in points.indices) {
            val p = points[i]
            val q = points[(i + 1) % points.size]
            sum += p.x * q.y - q.x * p.y
        }
        return kotlin.math.abs(sum) / 2.0
    }

    fun boxPolygon(x: Double, y: Double, w: Double, h: Double): Polygon = listOf(
        Point(x, y),
        Point(x + w, y),
        Point(x + w, y + h),
        Point(x, y + h),
    )
}
