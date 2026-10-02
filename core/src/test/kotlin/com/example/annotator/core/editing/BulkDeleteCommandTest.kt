package com.example.annotator.core.editing

import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BulkDeleteCommandTest {

    private fun ann(id: String) = Annotation(id, 0, Shape.Box(0.0, 0.0, 1.0, 1.0), Source.USER)

    @Test
    fun `deleting non adjacent items then undoing restores the exact original order`() {
        val a = ann("A")
        val b = ann("B")
        val c = ann("C")
        val d = ann("D")
        val e = ann("E")
        val f = ann("F")
        var image = DatasetImage("foo.jpg", ImageSize(10, 10), listOf(a, b, c, d, e, f))
        val stack = UndoStack()

        // Delete B, D, F -- the exact scenario that breaks a naive composite of raw-index deletes.
        val command = buildBulkDeleteCommand(image.annotations, setOf("B", "D", "F"))!!
        image = stack.execute(command, image)
        assertEquals(listOf("A", "C", "E"), image.annotations.map { it.id })

        image = stack.undo(image)!!
        assertEquals(listOf("A", "B", "C", "D", "E", "F"), image.annotations.map { it.id })

        image = stack.redo(image)!!
        assertEquals(listOf("A", "C", "E"), image.annotations.map { it.id })
    }

    @Test
    fun `deleting all items then undoing restores everything`() {
        val annotations = listOf(ann("A"), ann("B"), ann("C"))
        var image = DatasetImage("foo.jpg", ImageSize(10, 10), annotations)
        val stack = UndoStack()

        val command = buildBulkDeleteCommand(image.annotations, setOf("A", "B", "C"))!!
        image = stack.execute(command, image)
        assertEquals(emptyList<String>(), image.annotations.map { it.id })

        image = stack.undo(image)!!
        assertEquals(listOf("A", "B", "C"), image.annotations.map { it.id })
    }

    @Test
    fun `no matching ids returns null`() {
        val image = DatasetImage("foo.jpg", ImageSize(10, 10), listOf(ann("A")))
        assertNull(buildBulkDeleteCommand(image.annotations, setOf("nonexistent")))
    }
}
