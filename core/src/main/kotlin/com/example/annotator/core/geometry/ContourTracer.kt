package com.example.annotator.core.geometry

import com.example.annotator.core.codec.BooleanGrid

/** Outer boundary of one 4-connected foreground component, in pixel-corner coordinates. */
data class Contour(val points: Polygon, val hasHole: Boolean)

/**
 * Traces the outer boundary of every 4-connected foreground component in a grid
 * (FORMATS.md section 2.2): crack following on the pixel-corner grid. Holes are
 * detected (an interior loop exists) but not traced -- only reported via [Contour.hasHole].
 */
object ContourTracer {

    fun trace(grid: BooleanGrid): List<Contour> {
        val labels = labelComponents(grid)
        val componentCount = labels.second
        val labelGrid = labels.first

        val result = mutableListOf<Contour>()
        for (label in 1..componentCount) {
            val pixels = mutableListOf<Pair<Int, Int>>()
            for (col in 0 until grid.width) {
                for (row in 0 until grid.height) {
                    if (labelGrid[col, row] == label) pixels.add(col to row)
                }
            }
            val edges = buildEdges(pixels.toSet(), grid.height)
            val loops = chainLoops(edges)
            val signedLoops = loops.map { it to signedArea(it) }
            val outer = signedLoops.maxByOrNull { kotlin.math.abs(it.second) }!!.first
            result.add(Contour(outer, hasHole = loops.size > 1))
        }
        return result
    }

    /** Row-major label grid (0 = background), plus the number of distinct components. */
    private fun labelComponents(grid: BooleanGrid): Pair<LabelGrid, Int> {
        val labels = LabelGrid(grid.width, grid.height)
        var next = 1
        val stack = ArrayDeque<Pair<Int, Int>>()
        for (col in 0 until grid.width) {
            for (row in 0 until grid.height) {
                if (grid[col, row] && labels[col, row] == 0) {
                    labels[col, row] = next
                    stack.addLast(col to row)
                    while (stack.isNotEmpty()) {
                        val (c, r) = stack.removeLast()
                        val neighbors = listOf(c - 1 to r, c + 1 to r, c to r - 1, c to r + 1)
                        for ((nc, nr) in neighbors) {
                            if (nc in 0 until grid.width && nr in 0 until grid.height &&
                                grid[nc, nr] && labels[nc, nr] == 0
                            ) {
                                labels[nc, nr] = next
                                stack.addLast(nc to nr)
                            }
                        }
                    }
                    next++
                }
            }
        }
        return labels to (next - 1)
    }

    private class LabelGrid(val width: Int, val height: Int) {
        private val values = IntArray(width * height)
        operator fun get(col: Int, row: Int) = values[row * width + col]
        operator fun set(col: Int, row: Int, value: Int) {
            values[row * width + col] = value
        }
    }

    /**
     * Directed boundary edges for a set of foreground pixels: one edge per side that
     * borders a pixel outside the set (background or out of bounds). Direction is fixed
     * per side so that chained loops come out with a consistent winding: the outer
     * boundary has negative signed area, any hole boundary has positive signed area.
     */
    private fun buildEdges(pixels: Set<Pair<Int, Int>>, height: Int): List<Pair<Point, Point>> {
        val edges = mutableListOf<Pair<Point, Point>>()
        for ((c, r) in pixels) {
            val tl = Point(c.toDouble(), r.toDouble())
            val tr = Point((c + 1).toDouble(), r.toDouble())
            val bl = Point(c.toDouble(), (r + 1).toDouble())
            val br = Point((c + 1).toDouble(), (r + 1).toDouble())

            if ((c to r - 1) !in pixels) edges.add(tr to tl) // top
            if ((c to r + 1) !in pixels) edges.add(bl to br) // bottom
            if ((c - 1 to r) !in pixels) edges.add(tl to bl) // left
            if ((c + 1 to r) !in pixels) edges.add(br to tr) // right
        }
        return edges
    }

    /**
     * Chains directed edges into closed loops: within one 4-connected component every
     * corner has exactly one unvisited outgoing edge until the loop closes, so following
     * `edge.second -> next edge starting there` always terminates back at the start.
     */
    private fun chainLoops(edges: List<Pair<Point, Point>>): List<Polygon> {
        val outgoing = HashMap<Point, MutableList<Pair<Point, Point>>>()
        for (edge in edges) outgoing.getOrPut(edge.first) { mutableListOf() }.add(edge)
        val visited = HashSet<Pair<Point, Point>>()

        val loops = mutableListOf<Polygon>()
        for (startEdge in edges) {
            if (startEdge in visited) continue
            val loop = mutableListOf<Point>()
            var current = startEdge
            while (current !in visited) {
                visited.add(current)
                loop.add(current.first)
                current = outgoing[current.second].orEmpty().firstOrNull { it !in visited } ?: break
            }
            loops.add(loop)
        }
        return loops
    }

    private fun signedArea(points: Polygon): Double {
        var sum = 0.0
        for (i in points.indices) {
            val p = points[i]
            val q = points[(i + 1) % points.size]
            sum += p.x * q.y - q.x * p.y
        }
        return sum / 2.0
    }
}
