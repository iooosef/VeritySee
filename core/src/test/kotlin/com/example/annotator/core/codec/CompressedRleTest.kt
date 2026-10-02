package com.example.annotator.core.codec

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class CompressedRleTest {

    companion object {
        @JvmStatic
        fun cases() = Fixtures.rleCases()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `counts encode to the expected compressed string`(case: RleCase) {
        val encoded = CompressedRle.encode(case.rleCounts.toIntArray())
        assertEquals(case.cocoCompressedCounts, encoded, case.name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `compressed string decodes to the expected counts`(case: RleCase) {
        val decoded = CompressedRle.decode(case.cocoCompressedCounts)
        assertArrayEquals(case.rleCounts.toIntArray(), decoded, case.name)
    }
}
