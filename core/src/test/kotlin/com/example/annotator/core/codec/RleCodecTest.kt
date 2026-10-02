package com.example.annotator.core.codec

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class RleCodecTest {

    companion object {
        @JvmStatic
        fun cases() = Fixtures.rleCases()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `grid encodes to expected counts`(case: RleCase) {
        val grid = BooleanGrid.of(case.rows)
        val rle = RleCodec.encode(grid)
        assertArrayEquals(case.rleCounts.toIntArray(), rle.counts, case.name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `counts decode back to the same grid`(case: RleCase) {
        val rle = Rle(case.height, case.width, case.rleCounts.toIntArray())
        val decoded = RleCodec.decode(rle)
        val expected = BooleanGrid.of(case.rows)
        for (col in 0 until case.width) {
            for (row in 0 until case.height) {
                assertEquals(expected[col, row], decoded[col, row], "mismatch at ($col,$row) in ${case.name}")
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `round trip grid to rle to grid is identity`(case: RleCase) {
        val grid = BooleanGrid.of(case.rows)
        val roundTripped = RleCodec.decode(RleCodec.encode(grid))
        for (col in 0 until case.width) {
            for (row in 0 until case.height) {
                assertEquals(grid[col, row], roundTripped[col, row], "mismatch at ($col,$row) in ${case.name}")
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `area matches fixture`(case: RleCase) {
        val rle = Rle(case.height, case.width, case.rleCounts.toIntArray())
        assertEquals(case.area.toLong(), rle.area(), case.name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `bbox matches fixture`(case: RleCase) {
        val rle = Rle(case.height, case.width, case.rleCounts.toIntArray())
        val expected = intArrayOf(
            case.bboxXYWH[0].toInt(),
            case.bboxXYWH[1].toInt(),
            case.bboxXYWH[2].toInt(),
            case.bboxXYWH[3].toInt(),
        )
        assertArrayEquals(expected, rle.bbox(), case.name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `bbox center matches fixture`(case: RleCase) {
        val rle = Rle(case.height, case.width, case.rleCounts.toIntArray())
        val bbox = rle.bbox()
        val centerX = bbox[0] + bbox[2] / 2.0
        val centerY = bbox[1] + bbox[3] / 2.0
        assertEquals(case.bboxCenter[0], centerX, 1e-9, case.name)
        assertEquals(case.bboxCenter[1], centerY, 1e-9, case.name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `contains matches the grid`(case: RleCase) {
        val rle = Rle(case.height, case.width, case.rleCounts.toIntArray())
        val grid = BooleanGrid.of(case.rows)
        for (col in 0 until case.width) {
            for (row in 0 until case.height) {
                assertEquals(grid[col, row], rle.contains(col, row), "mismatch at ($col,$row) in ${case.name}")
            }
        }
    }
}
