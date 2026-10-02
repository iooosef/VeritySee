package com.example.annotator.core.formats

import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClassOpsTest {

    private fun box(classId: Int) = Annotation("id-$classId-${System.nanoTime()}", classId, Shape.Box(0.0, 0.0, 1.0, 1.0), Source.USER)

    @Test
    fun `remapClass reassigns only the matching class id`() {
        val annotations = listOf(box(1), box(2), box(1))
        val remapped = ClassOps.remapClass(annotations, fromId = 1, toId = 3)
        assertEquals(listOf(3, 2, 3), remapped.map { it.classId })
    }

    @Test
    fun `countByClass tallies annotations across all images`() {
        val images = listOf(
            DatasetImage("a.jpg", ImageSize(10, 10), listOf(box(1), box(2))),
            DatasetImage("b.jpg", ImageSize(10, 10), listOf(box(1))),
        )
        val counts = ClassOps.countByClass(images)
        assertEquals(2, counts[1])
        assertEquals(1, counts[2])
    }
}
