package com.example.annotator.core.formats.yolo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoloLineCodecTest {

    @Test
    fun `parses a detect line`() {
        val warnings = mutableListOf<String>()
        val line = YoloLineCodec.parse("0 0.5 0.5 0.2 0.3", 1, warnings)
        assertTrue(line is YoloLine.Detect)
        line as YoloLine.Detect
        assertEquals(0, line.classId)
        assertEquals(0.5, line.cx, 1e-9)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `parses a segment line with 3 points`() {
        val warnings = mutableListOf<String>()
        val line = YoloLineCodec.parse("1 0.1 0.1 0.5 0.1 0.3 0.5", 1, warnings)
        assertTrue(line is YoloLine.Segment)
        line as YoloLine.Segment
        assertEquals(3, line.points.size)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `clamps out of range values with a warning`() {
        val warnings = mutableListOf<String>()
        val line = YoloLineCodec.parse("0 1.5 -0.2 0.2 0.3", 1, warnings)
        line as YoloLine.Detect
        assertEquals(1.0, line.cx, 1e-9)
        assertEquals(0.0, line.cy, 1e-9)
        assertEquals(1, warnings.size)
    }

    @Test
    fun `malformed line with wrong value count is skipped`() {
        val warnings = mutableListOf<String>()
        val line = YoloLineCodec.parse("0 0.1 0.2 0.3 0.4 0.5", 1, warnings)
        assertNull(line)
        assertEquals(1, warnings.size)
    }

    @Test
    fun `non numeric token is skipped`() {
        val warnings = mutableListOf<String>()
        val line = YoloLineCodec.parse("0 abc 0.2 0.3 0.4", 1, warnings)
        assertNull(line)
        assertEquals(1, warnings.size)
    }

    @Test
    fun `blank line yields no result and no warning`() {
        val warnings = mutableListOf<String>()
        val line = YoloLineCodec.parse("   ", 1, warnings)
        assertNull(line)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `write formats with 6 decimal places`() {
        val written = YoloLineCodec.write(YoloLine.Detect(0, 0.5, 0.25, 0.1, 0.2))
        assertEquals("0 0.500000 0.250000 0.100000 0.200000", written)
    }
}
