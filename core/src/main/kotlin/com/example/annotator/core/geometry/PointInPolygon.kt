package com.example.annotator.core.geometry

/**
 * Standard ray-casting even-odd point-in-polygon test. `polygon` is a closed ring
 * (last point implicitly connects back to the first).
 */
fun pointInPolygon(point: Point, polygon: Polygon): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        if ((pi.y > point.y) != (pj.y > point.y)) {
            val intersectX = pi.x + (point.y - pi.y) / (pj.y - pi.y) * (pj.x - pi.x)
            if (point.x < intersectX) inside = !inside
        }
        j = i
    }
    return inside
}
