package com.example.annotator.core.codec

/**
 * Full image mask as run-length counts, column major, always starting with a run of zeros
 * (per FORMATS.md section 1). Same definition as pycocotools.
 */
data class Rle(val height: Int, val width: Int, val counts: IntArray) {

    fun area(): Long {
        var area = 0L
        for (i in counts.indices) {
            if (i % 2 == 1) area += counts[i]
        }
        return area
    }

    /** Returns `[x, y, w, h]` top-left XYWH, or `[0,0,0,0]` for an empty mask. */
    fun bbox(): IntArray {
        var minCol = Int.MAX_VALUE
        var maxCol = Int.MIN_VALUE
        var minRow = Int.MAX_VALUE
        var maxRow = Int.MIN_VALUE

        var pos = 0L
        for (i in counts.indices) {
            val run = counts[i]
            if (i % 2 == 1 && run > 0) {
                val start = pos
                val end = pos + run - 1
                val startCol = (start / height).toInt()
                val endCol = (end / height).toInt()
                val startRow = (start % height).toInt()
                val endRow = (end % height).toInt()
                if (startCol < minCol) minCol = startCol
                if (endCol > maxCol) maxCol = endCol
                val runMinRow: Int
                val runMaxRow: Int
                if (startCol == endCol) {
                    runMinRow = startRow
                    runMaxRow = endRow
                } else {
                    // the run crosses a column boundary, so some column within it is covered
                    // top (row 0) to bottom (row height-1).
                    runMinRow = 0
                    runMaxRow = height - 1
                }
                if (runMinRow < minRow) minRow = runMinRow
                if (runMaxRow > maxRow) maxRow = runMaxRow
            }
            pos += run
        }

        if (maxCol < minCol) return intArrayOf(0, 0, 0, 0)
        return intArrayOf(minCol, minRow, maxCol - minCol + 1, maxRow - minRow + 1)
    }

    fun contains(col: Int, row: Int): Boolean {
        val target = col.toLong() * height + row
        var pos = 0L
        for (i in counts.indices) {
            val run = counts[i]
            if (target < pos + run) return i % 2 == 1
            pos += run
        }
        return false
    }

    fun isEmpty(): Boolean = area() == 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rle) return false
        return height == other.height && width == other.width && counts.contentEquals(other.counts)
    }

    override fun hashCode(): Int {
        var result = height
        result = 31 * result + width
        result = 31 * result + counts.contentHashCode()
        return result
    }
}
