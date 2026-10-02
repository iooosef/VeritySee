package com.example.annotator.core.codec

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class RoboflowMaskTest {

    companion object {
        @JvmStatic
        fun cases() = Fixtures.rleCases()
    }

    // Compared at the counts level per FORMATS.md 1.2: robust to zlib implementation
    // differences while still proving the codec round-trips correctly.
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `roboflow mask decodes to the expected counts`(case: RleCase) {
        val decoded = RoboflowMask.decode(case.roboflowMask)
        assertArrayEquals(case.rleCounts.toIntArray(), decoded, case.name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `counts encode and decode back through roboflow mask`(case: RleCase) {
        val encoded = RoboflowMask.encode(case.rleCounts.toIntArray())
        val roundTripped = RoboflowMask.decode(encoded)
        assertArrayEquals(case.rleCounts.toIntArray(), roundTripped, case.name)
    }

    @Test
    fun `real roboflow sample decodes to expected counts and derived values`() {
        val sample = Fixtures.roboflowSample()
        val counts = RoboflowMask.decode(sample.input.mask)

        assertEquals(sample.expected.runCount, counts.size)
        assertEquals(sample.expected.runSum, counts.sumOf { it.toLong() })
        assertArrayEquals(sample.expected.firstRuns.toIntArray(), counts.copyOfRange(0, sample.expected.firstRuns.size))

        val rle = Rle(sample.expected.imageHeight, sample.expected.imageWidth, counts)
        assertEquals(sample.expected.area.toLong(), rle.area())

        val bbox = rle.bbox()
        val expectedBbox = intArrayOf(
            sample.expected.bboxXYWH[0].toInt(),
            sample.expected.bboxXYWH[1].toInt(),
            sample.expected.bboxXYWH[2].toInt(),
            sample.expected.bboxXYWH[3].toInt(),
        )
        assertArrayEquals(expectedBbox, bbox)

        assertEquals(sample.expected.cocoCompressedCounts, CompressedRle.encode(counts))
    }
}
