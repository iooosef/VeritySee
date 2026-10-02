package com.example.annotator.editor

import androidx.compose.ui.geometry.Offset
import com.example.annotator.core.model.Shape
import kotlin.math.min

private enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, MOVE }

/** Bounding box tool: drag to create a box. Minimum size 4x4 image px or it is discarded (SPEC 5). */
class BoundingBoxToolHandler(private val onCreate: (Shape.Box) -> Unit, private val onPreview: (Shape.Box?) -> Unit) : ToolStrokeHandler {
    private var start: Offset = Offset.Zero
    private var last: Shape.Box? = null

    override fun onStart(imagePoint: Offset) {
        start = imagePoint
        last = boxFrom(start, imagePoint)
        onPreview(last)
    }

    override fun onMove(imagePoint: Offset) {
        last = boxFrom(start, imagePoint)
        onPreview(last)
    }

    override fun onEnd() {
        val box = last
        onPreview(null)
        last = null
        if (box != null && box.w >= 4.0 && box.h >= 4.0) onCreate(box)
    }

    override fun onCancel() {
        last = null
        onPreview(null)
    }

    private fun boxFrom(a: Offset, b: Offset): Shape.Box {
        val x = min(a.x, b.x).toDouble()
        val y = min(a.y, b.y).toDouble()
        return Shape.Box(x, y, kotlin.math.abs(b.x - a.x).toDouble(), kotlin.math.abs(b.y - a.y).toDouble())
    }
}

/**
 * Selection tool drag handling for a selected box: drag a corner handle to resize, drag inside
 * to move (SPEC 5). Tap-to-select itself is handled separately via [ToolStrokeHandler.onTap].
 */
class SelectionDragHandler(
    private val selectedBox: () -> Shape.Box?,
    private val handleRadiusImagePx: () -> Float,
    private val onPreview: (Shape.Box?) -> Unit,
    private val onCommit: (Shape.Box) -> Unit,
) : ToolStrokeHandler {
    private var handle: Handle? = null
    private var original: Shape.Box? = null
    private var dragStart: Offset = Offset.Zero
    private var last: Shape.Box? = null

    override fun onStart(imagePoint: Offset) {
        val box = selectedBox() ?: return
        val r = handleRadiusImagePx()
        handle = when {
            near(imagePoint, Offset(box.x.toFloat(), box.y.toFloat()), r) -> Handle.TOP_LEFT
            near(imagePoint, Offset((box.x + box.w).toFloat(), box.y.toFloat()), r) -> Handle.TOP_RIGHT
            near(imagePoint, Offset((box.x + box.w).toFloat(), (box.y + box.h).toFloat()), r) -> Handle.BOTTOM_RIGHT
            near(imagePoint, Offset(box.x.toFloat(), (box.y + box.h).toFloat()), r) -> Handle.BOTTOM_LEFT
            imagePoint.x in box.x.toFloat()..(box.x + box.w).toFloat() && imagePoint.y in box.y.toFloat()..(box.y + box.h).toFloat() -> Handle.MOVE
            else -> null
        }
        original = box
        dragStart = imagePoint
        last = null
    }

    override fun onMove(imagePoint: Offset) {
        val h = handle ?: return
        val box = original ?: return
        val dx = (imagePoint.x - dragStart.x).toDouble()
        val dy = (imagePoint.y - dragStart.y).toDouble()
        val updated = when (h) {
            Handle.MOVE -> box.copy(x = box.x + dx, y = box.y + dy)
            Handle.TOP_LEFT -> normalize(box.x + dx, box.y + dy, box.x + box.w, box.y + box.h)
            Handle.TOP_RIGHT -> normalize(box.x, box.y + dy, box.x + box.w + dx, box.y + box.h)
            Handle.BOTTOM_RIGHT -> normalize(box.x, box.y, box.x + box.w + dx, box.y + box.h + dy)
            Handle.BOTTOM_LEFT -> normalize(box.x + dx, box.y, box.x + box.w, box.y + box.h + dy)
        }
        last = updated
        onPreview(updated)
    }

    override fun onEnd() {
        val finalBox = last
        handle = null
        original = null
        last = null
        onPreview(null)
        if (finalBox != null) onCommit(finalBox)
    }

    override fun onCancel() {
        handle = null
        original = null
        last = null
        onPreview(null)
    }

    private fun normalize(x1: Double, y1: Double, x2: Double, y2: Double): Shape.Box =
        Shape.Box(min(x1, x2), min(y1, y2), kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1))

    private fun near(a: Offset, b: Offset, radius: Float): Boolean {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy <= radius * radius
    }
}
