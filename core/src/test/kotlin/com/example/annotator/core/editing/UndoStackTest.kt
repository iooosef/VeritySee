package com.example.annotator.core.editing

import com.example.annotator.core.codec.Rle
import com.example.annotator.core.codec.RleCodec
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.geometry.GeometryTestUtils
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UndoStackTest {

    private fun maskAnnotation(id: String, rle: Rle) = Annotation(id, 0, Shape.Mask(rle), Source.USER)

    @Test
    fun `100 consecutive mask edits undo back to the exact original RLE`() {
        val size = ImageSize(40, 40)
        val originalRle = RleCodec.encode(GeometryTestUtils.circle(40, 20.0, 20.0, 10.0))
        val annotation = maskAnnotation("a1", originalRle)
        var image = DatasetImage("foo.jpg", size, listOf(annotation))
        val stack = UndoStack()

        var currentRle = originalRle
        repeat(100) { i ->
            val grid = RleCodec.decode(currentRle)
            // toggle one pixel back and forth so each step is a distinct, reversible edit
            grid[i % 40, (i / 40) % 40] = !grid[i % 40, (i / 40) % 40]
            val newRle = RleCodec.encode(grid)
            val command = ChangeShapeCommand("a1", before = Shape.Mask(currentRle), after = Shape.Mask(newRle))
            image = stack.execute(command, image)
            currentRle = newRle
        }

        repeat(100) {
            image = stack.undo(image)!!
        }

        val finalShape = image.annotations.single().shape as Shape.Mask
        assertEquals(originalRle, finalShape.rle)
        assertFalse(stack.canUndo)
    }

    @Test
    fun `redo after undo restores the same state`() {
        val size = ImageSize(10, 10)
        val before = Shape.Box(0.0, 0.0, 2.0, 2.0)
        val after = Shape.Box(1.0, 1.0, 3.0, 3.0)
        val annotation = Annotation("a1", 0, before, Source.USER)
        var image = DatasetImage("foo.jpg", size, listOf(annotation))
        val stack = UndoStack()

        image = stack.execute(ChangeShapeCommand("a1", before, after), image)
        assertEquals(after, image.annotations.single().shape)

        image = stack.undo(image)!!
        assertEquals(before, image.annotations.single().shape)

        image = stack.redo(image)!!
        assertEquals(after, image.annotations.single().shape)
    }

    @Test
    fun `create then undo removes the annotation, redo restores it at the same position`() {
        var image = DatasetImage("foo.jpg", ImageSize(10, 10), emptyList())
        val stack = UndoStack()
        val annotation = Annotation("a1", 0, Shape.Box(0.0, 0.0, 1.0, 1.0), Source.USER)

        image = stack.execute(CreateAnnotationCommand(annotation), image)
        assertEquals(1, image.annotations.size)

        image = stack.undo(image)!!
        assertTrue(image.annotations.isEmpty())

        image = stack.redo(image)!!
        assertEquals(listOf(annotation), image.annotations)
    }

    @Test
    fun `delete then undo restores the annotation at its original index`() {
        val a = Annotation("a", 0, Shape.Box(0.0, 0.0, 1.0, 1.0), Source.USER)
        val b = Annotation("b", 0, Shape.Box(1.0, 1.0, 1.0, 1.0), Source.USER)
        val c = Annotation("c", 0, Shape.Box(2.0, 2.0, 1.0, 1.0), Source.USER)
        var image = DatasetImage("foo.jpg", ImageSize(10, 10), listOf(a, b, c))
        val stack = UndoStack()

        image = stack.execute(DeleteAnnotationCommand(b, index = 1), image)
        assertEquals(listOf(a, c), image.annotations)

        image = stack.undo(image)!!
        assertEquals(listOf(a, b, c), image.annotations)
    }

    @Test
    fun `executing a new command after undo clears the redo list`() {
        var image = DatasetImage("foo.jpg", ImageSize(10, 10), emptyList())
        val stack = UndoStack()
        val a = Annotation("a", 0, Shape.Box(0.0, 0.0, 1.0, 1.0), Source.USER)
        val b = Annotation("b", 0, Shape.Box(1.0, 1.0, 1.0, 1.0), Source.USER)

        image = stack.execute(CreateAnnotationCommand(a), image)
        image = stack.undo(image)!!
        assertTrue(stack.canRedo)

        image = stack.execute(CreateAnnotationCommand(b), image)
        assertFalse(stack.canRedo)
        assertEquals(listOf(b), image.annotations)
    }

    @Test
    fun `undo stack caps at max size, dropping the oldest command`() {
        var image = DatasetImage("foo.jpg", ImageSize(10, 10), listOf(Annotation("a1", 0, Shape.Box(0.0, 0.0, 1.0, 1.0), Source.USER)))
        val stack = UndoStack(maxSize = 5)

        var shape: Shape = Shape.Box(0.0, 0.0, 1.0, 1.0)
        repeat(10) { i ->
            val newShape = Shape.Box(i.toDouble(), 0.0, 1.0, 1.0)
            image = stack.execute(ChangeShapeCommand("a1", shape, newShape), image)
            shape = newShape
        }

        var undoCount = 0
        while (stack.canUndo) {
            image = stack.undo(image)!!
            undoCount++
        }
        assertEquals(5, undoCount)
    }
}
