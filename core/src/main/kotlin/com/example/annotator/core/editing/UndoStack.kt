package com.example.annotator.core.editing

import com.example.annotator.core.formats.DatasetImage

/** Per-image undo/redo command stack, capped at [maxSize] steps (SPEC section 5: max 100). */
class UndoStack(private val maxSize: Int = 100) {

    private val undoList = ArrayDeque<Command>()
    private val redoList = ArrayDeque<Command>()

    val canUndo: Boolean get() = undoList.isNotEmpty()
    val canRedo: Boolean get() = redoList.isNotEmpty()

    /** Applies [command]'s redo to [image], records it, and clears the redo list. */
    fun execute(command: Command, image: DatasetImage): DatasetImage {
        val updated = command.redo(image)
        undoList.addLast(command)
        if (undoList.size > maxSize) undoList.removeFirst()
        redoList.clear()
        return updated
    }

    fun undo(image: DatasetImage): DatasetImage? {
        val command = undoList.removeLastOrNull() ?: return null
        redoList.addLast(command)
        return command.undo(image)
    }

    fun redo(image: DatasetImage): DatasetImage? {
        val command = redoList.removeLastOrNull() ?: return null
        undoList.addLast(command)
        return command.redo(image)
    }
}
