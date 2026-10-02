package com.example.annotator.core.geometry

/**
 * Joins multiple component polygons into a single polygon for YOLO segment export
 * (FORMATS.md section 2.2): connects each component to the next at their closest pair
 * of points with a zero-width bridge, same idea as Ultralytics `merge_multi_segment`.
 * Zero-area bridges do not change the even-odd rasterized shape.
 */
object PolygonJoiner {

    fun joinForYolo(components: List<Polygon>): Polygon {
        if (components.isEmpty()) return emptyList()
        if (components.size == 1) return components[0]

        val remaining = components.toMutableList()
        val merged = mutableListOf<Point>()
        val first = remaining.removeAt(0)
        merged.addAll(first)

        while (remaining.isNotEmpty()) {
            val from = merged.last()
            var bestSegment = -1
            var bestPointIndex = -1
            var bestDist = Double.MAX_VALUE
            for ((segIndex, seg) in remaining.withIndex()) {
                for ((pointIndex, p) in seg.withIndex()) {
                    val d = from.distanceTo(p)
                    if (d < bestDist) {
                        bestDist = d
                        bestSegment = segIndex
                        bestPointIndex = pointIndex
                    }
                }
            }
            val seg = remaining.removeAt(bestSegment)
            val rotated = seg.subList(bestPointIndex, seg.size) + seg.subList(0, bestPointIndex)

            merged.add(rotated.first())
            merged.addAll(rotated)
            merged.add(rotated.first())
            merged.add(from)
        }
        return merged
    }
}
