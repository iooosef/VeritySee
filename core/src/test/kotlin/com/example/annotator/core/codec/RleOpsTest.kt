package com.example.annotator.core.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RleOpsTest {

    // 4x4 grid, two overlapping 2x2 squares:
    // a covers cols 0-1, rows 0-1. b covers cols 1-2, rows 1-2.
    private fun squareA() = BooleanGrid.of(
        listOf(
            "1100",
            "1100",
            "0000",
            "0000",
        ),
    )

    private fun squareB() = BooleanGrid.of(
        listOf(
            "0000",
            "0110",
            "0110",
            "0000",
        ),
    )

    @Test
    fun `union combines both shapes`() {
        val a = RleCodec.encode(squareA())
        val b = RleCodec.encode(squareB())
        val union = RleOps.union(a, b)
        val grid = RleCodec.decode(union)

        val expected = BooleanGrid.of(
            listOf(
                "1100",
                "1110",
                "0110",
                "0000",
            ),
        )
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                assertEquals(expected[col, row], grid[col, row], "mismatch at ($col,$row)")
            }
        }
    }

    @Test
    fun `subtract removes overlap only`() {
        val a = RleCodec.encode(squareA())
        val b = RleCodec.encode(squareB())
        val result = RleOps.subtract(a, b)
        val grid = RleCodec.decode(result)

        val expected = BooleanGrid.of(
            listOf(
                "1100",
                "1000",
                "0000",
                "0000",
            ),
        )
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                assertEquals(expected[col, row], grid[col, row], "mismatch at ($col,$row)")
            }
        }
    }

    @Test
    fun `isEmpty is true only for an all zero mask`() {
        val empty = RleCodec.encode(BooleanGrid(4, 4))
        val nonEmpty = RleCodec.encode(squareA())
        assertTrue(RleOps.isEmpty(empty))
        assertFalse(RleOps.isEmpty(nonEmpty))
    }

    @Test
    fun `subtracting everything yields empty`() {
        val a = RleCodec.encode(squareA())
        val result = RleOps.subtract(a, a)
        assertTrue(RleOps.isEmpty(result))
    }
}
