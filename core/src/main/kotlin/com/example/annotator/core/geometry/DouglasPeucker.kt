package com.example.annotator.core.geometry

import kotlin.math.sqrt

/** Douglas-Peucker polyline/polygon simplification, tolerance in image pixels. */
object DouglasPeucker {

    /** Simplifies an open path, always keeping the first and last point. */
    fun simplifyOpen(points: List<Point>, tolerance: Double): List<Point> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        simplifyRange(points, 0, points.size - 1, tolerance, keep)
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /**
     * Simplifies a closed ring (first point is not repeated at the end). Splits the ring
     * at two well separated anchor points (point 0 and the point farthest from it) so the
     * two open-path passes don't degenerate into a zero-length baseline.
     */
    fun simplifyClosed(points: List<Point>, tolerance: Double): List<Point> {
        if (points.size < 3) return points

        var farIndex = 1
        var farDist = -1.0
        for (i in 1 until points.size) {
            val d = points[0].distanceTo(points[i])
            if (d > farDist) {
                farDist = d
                farIndex = i
            }
        }

        val chainA = points.subList(0, farIndex + 1)
        val chainB = points.subList(farIndex, points.size) + points[0]

        val simplifiedA = simplifyOpen(chainA, tolerance)
        val simplifiedB = simplifyOpen(chainB, tolerance)

        return simplifiedA + simplifiedB.subList(1, simplifiedB.size - 1)
    }

    private fun simplifyRange(points: List<Point>, start: Int, end: Int, tolerance: Double, keep: BooleanArray) {
        if (end <= start + 1) return
        var maxDist = -1.0
        var maxIndex = -1
        val a = points[start]
        val b = points[end]
        for (i in start + 1 until end) {
            val dist = perpendicularDistance(points[i], a, b)
            if (dist > maxDist) {
                maxDist = dist
                maxIndex = i
            }
        }
        if (maxDist > tolerance) {
            keep[maxIndex] = true
            simplifyRange(points, start, maxIndex, tolerance, keep)
            simplifyRange(points, maxIndex, end, tolerance, keep)
        }
    }

    private fun perpendicularDistance(p: Point, a: Point, b: Point): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (dx == 0.0 && dy == 0.0) return p.distanceTo(a)
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)
        val projX = a.x + t * dx
        val projY = a.y + t * dy
        val ex = p.x - projX
        val ey = p.y - projY
        return sqrt(ex * ex + ey * ey)
    }
}
