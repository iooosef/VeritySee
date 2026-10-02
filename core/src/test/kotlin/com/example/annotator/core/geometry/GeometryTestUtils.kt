package com.example.annotator.core.geometry

import com.example.annotator.core.codec.BooleanGrid
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object GeometryTestUtils {

    fun iou(a: BooleanGrid, b: BooleanGrid): Double {
        require(a.width == b.width && a.height == b.height)
        var intersection = 0
        var union = 0
        for (col in 0 until a.width) {
            for (row in 0 until a.height) {
                val av = a[col, row]
                val bv = b[col, row]
                if (av || bv) union++
                if (av && bv) intersection++
            }
        }
        if (union == 0) return 1.0
        return intersection.toDouble() / union
    }

    fun exactlyEqual(a: BooleanGrid, b: BooleanGrid): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        for (col in 0 until a.width) {
            for (row in 0 until a.height) {
                if (a[col, row] != b[col, row]) return false
            }
        }
        return true
    }

    fun circle(size: Int, cx: Double, cy: Double, radius: Double): BooleanGrid {
        val grid = BooleanGrid(size, size)
        for (col in 0 until size) {
            for (row in 0 until size) {
                val dx = col + 0.5 - cx
                val dy = row + 0.5 - cy
                if (hypot(dx, dy) <= radius) grid[col, row] = true
            }
        }
        return grid
    }

    fun ring(size: Int, cx: Double, cy: Double, outerRadius: Double, innerRadius: Double): BooleanGrid {
        val grid = BooleanGrid(size, size)
        for (col in 0 until size) {
            for (row in 0 until size) {
                val dx = col + 0.5 - cx
                val dy = row + 0.5 - cy
                val d = hypot(dx, dy)
                if (d <= outerRadius && d >= innerRadius) grid[col, row] = true
            }
        }
        return grid
    }

    /** An L shape: a tall bar union a wide bar sharing the bottom-left corner. */
    fun lShape(size: Int): BooleanGrid {
        val grid = BooleanGrid(size, size)
        val armWidth = max(2, size / 4)
        for (col in 0 until size) {
            for (row in 0 until size) {
                val inVerticalArm = col < armWidth
                val inHorizontalArm = row >= size - armWidth
                if (inVerticalArm || inHorizontalArm) grid[col, row] = true
            }
        }
        return grid
    }

    fun bbox(grid: BooleanGrid): IntArray {
        var minCol = grid.width
        var maxCol = -1
        var minRow = grid.height
        var maxRow = -1
        for (col in 0 until grid.width) {
            for (row in 0 until grid.height) {
                if (grid[col, row]) {
                    minCol = min(minCol, col)
                    maxCol = max(maxCol, col)
                    minRow = min(minRow, row)
                    maxRow = max(maxRow, row)
                }
            }
        }
        if (maxCol < minCol) return intArrayOf(0, 0, 0, 0)
        return intArrayOf(minCol, minRow, maxCol - minCol + 1, maxRow - minRow + 1)
    }
}
