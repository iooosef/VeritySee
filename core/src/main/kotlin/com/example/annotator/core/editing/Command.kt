package com.example.annotator.core.editing

import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.Shape

/**
 * Every edit is one of these (CLAUDE.md: "every edit goes through a Command so undo/redo
 * stays exact"). Each command stores before/after state directly rather than computing an
 * inverse -- mask commands keep the RLE before and after, which is small and makes undo exact
 * (SPEC section 5).
 */
sealed interface Command {
    fun redo(image: DatasetImage): DatasetImage
    fun undo(image: DatasetImage): DatasetImage
}

data class CreateAnnotationCommand(val annotation: Annotation, val index: Int = Int.MAX_VALUE) : Command {
    override fun redo(image: DatasetImage): DatasetImage {
        val at = index.coerceIn(0, image.annotations.size)
        val updated = image.annotations.toMutableList().apply { add(at, annotation) }
        return image.copy(annotations = updated)
    }

    override fun undo(image: DatasetImage): DatasetImage =
        image.copy(annotations = image.annotations.filterNot { it.id == annotation.id })
}

data class DeleteAnnotationCommand(val annotation: Annotation, val index: Int) : Command {
    override fun redo(image: DatasetImage): DatasetImage =
        image.copy(annotations = image.annotations.filterNot { it.id == annotation.id })

    override fun undo(image: DatasetImage): DatasetImage {
        val at = index.coerceIn(0, image.annotations.size)
        val updated = image.annotations.toMutableList().apply { add(at, annotation) }
        return image.copy(annotations = updated)
    }
}

data class ChangeShapeCommand(val annotationId: String, val before: Shape, val after: Shape) : Command {
    override fun redo(image: DatasetImage): DatasetImage = replaceShape(image, after)
    override fun undo(image: DatasetImage): DatasetImage = replaceShape(image, before)

    private fun replaceShape(image: DatasetImage, shape: Shape): DatasetImage =
        image.copy(annotations = image.annotations.map { if (it.id == annotationId) it.copy(shape = shape) else it })
}

data class ChangeClassCommand(val annotationId: String, val beforeClassId: Int, val afterClassId: Int) : Command {
    override fun redo(image: DatasetImage): DatasetImage = replaceClass(image, afterClassId)
    override fun undo(image: DatasetImage): DatasetImage = replaceClass(image, beforeClassId)

    private fun replaceClass(image: DatasetImage, classId: Int): DatasetImage =
        image.copy(annotations = image.annotations.map { if (it.id == annotationId) it.copy(classId = classId) else it })
}

/** Groups several commands into one undo/redo step (e.g. bulk change-class or bulk delete). */
data class CompositeCommand(val commands: List<Command>) : Command {
    override fun redo(image: DatasetImage): DatasetImage = commands.fold(image) { acc, c -> c.redo(acc) }
    override fun undo(image: DatasetImage): DatasetImage = commands.foldRight(image) { c, acc -> c.undo(acc) }
}

/**
 * Builds a bulk-delete command for [idsToDelete] within [annotations], as one atomic undo step
 * (SPEC section 8). Each [DeleteAnnotationCommand]'s index is adjusted by how many earlier
 * deletions in this same batch preceded it in the original list -- a naive composite using raw
 * original indices restores items at the wrong position on undo once more than one item in the
 * batch has been removed, since every later deletion's "original" index no longer matches the
 * list state at the point its own undo actually runs.
 */
fun buildBulkDeleteCommand(annotations: List<Annotation>, idsToDelete: Set<String>): CompositeCommand? {
    val toDelete = annotations.withIndex().filter { it.value.id in idsToDelete }
    if (toDelete.isEmpty()) return null
    val commands = toDelete.mapIndexed { position, indexed -> DeleteAnnotationCommand(indexed.value, indexed.index - position) }
    return CompositeCommand(commands)
}
