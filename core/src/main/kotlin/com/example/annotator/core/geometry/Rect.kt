package com.example.annotator.core.geometry

/** Top-left based rectangle in image pixels. */
data class Rect(val x: Double, val y: Double, val w: Double, val h: Double) {
    fun intersects(other: Rect): Boolean {
        return x < other.x + other.w &&
            other.x < x + w &&
            y < other.y + other.h &&
            other.y < y + h
    }

    fun contains(point: Point): Boolean {
        return point.x >= x && point.x <= x + w && point.y >= y && point.y <= y + h
    }

    val centerX: Double get() = x + w / 2.0
    val centerY: Double get() = y + h / 2.0
}
