package com.example.annotator.core.formats.yolo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YoloClassCountTest {

    @Test
    fun `counts class ids across multiple label files without building shapes`() {
        val labelA = "0 0.5 0.5 0.2 0.3\n2 0.1 0.1 0.2 0.3 0.1 0.5 0.3 0.5\n"
        val labelB = "2 0.4 0.4 0.1 0.1 0.4 0.3 0.5 0.3\n2 0.1 0.1 0.2 0.2\n"
        val counts = YoloImporter.countClassIds(listOf(labelA, labelB))
        assertEquals(1, counts[0])
        assertEquals(3, counts[2])
    }

    @Test
    fun `blank lines and malformed lines are ignored`() {
        val text = "\n0 0.5 0.5 0.2 0.3\n\nnotanumber 0.1 0.1 0.2 0.2\n"
        val counts = YoloImporter.countClassIds(listOf(text))
        assertEquals(1, counts[0])
        assertEquals(1, counts.size)
    }
}
