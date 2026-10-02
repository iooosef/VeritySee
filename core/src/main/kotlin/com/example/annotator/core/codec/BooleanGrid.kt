package com.example.annotator.core.codec

/** Row-major boolean grid: `set.size == width * height`, index = row * width + col. */
class BooleanGrid(val width: Int, val height: Int) {
    private val bits = BooleanArray(width * height)

    operator fun get(col: Int, row: Int): Boolean = bits[row * width + col]

    operator fun set(col: Int, row: Int, value: Boolean) {
        bits[row * width + col] = value
    }

    companion object {
        fun of(rows: List<String>): BooleanGrid {
            val height = rows.size
            val width = if (rows.isEmpty()) 0 else rows[0].length
            val grid = BooleanGrid(width, height)
            for (row in 0 until height) {
                for (col in 0 until width) {
                    grid[col, row] = rows[row][col] == '1'
                }
            }
            return grid
        }
    }
}
