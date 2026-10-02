package com.example.annotator.core.geometry

data class Point(val x: Double, val y: Double) {
    fun distanceTo(other: Point): Double {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

/** A closed polygon ring: implicitly closes from the last point back to the first. */
typealias Polygon = List<Point>
