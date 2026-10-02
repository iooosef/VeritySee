package com.example.annotator.core.codec

/** Converts between row-major [BooleanGrid] and column-major [Rle] counts. */
object RleCodec {

    fun encode(grid: BooleanGrid): Rle {
        val counts = mutableListOf<Int>()
        var current = false // counts always starts with a run of zeros
        var run = 0
        for (col in 0 until grid.width) {
            for (row in 0 until grid.height) {
                val value = grid[col, row]
                if (value == current) {
                    run++
                } else {
                    counts.add(run)
                    current = value
                    run = 1
                }
            }
        }
        counts.add(run)
        return Rle(grid.height, grid.width, counts.toIntArray())
    }

    fun decode(rle: Rle): BooleanGrid {
        val grid = BooleanGrid(rle.width, rle.height)
        var pos = 0
        var value = false
        for (run in rle.counts) {
            if (value) {
                for (i in 0 until run) {
                    val p = pos + i
                    val col = p / rle.height
                    val row = p % rle.height
                    grid[col, row] = true
                }
            }
            pos += run
            value = !value
        }
        return grid
    }
}
