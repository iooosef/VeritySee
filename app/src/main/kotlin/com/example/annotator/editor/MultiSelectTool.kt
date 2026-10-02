package com.example.annotator.editor

import androidx.compose.ui.geometry.Offset
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.formats.bbox
import com.example.annotator.core.geometry.Point
import com.example.annotator.core.geometry.Rect
import com.example.annotator.core.geometry.pointInPolygon
import com.example.annotator.core.model.Shape
import kotlin.math.min

/** Box selection: drag a rectangle, selects every annotation whose bbox intersects it (SPEC 5). */
class BoxSelectToolHandler(
    private val dataset: () -> DatasetImage?,
    private val hiddenClassIds: () -> Set<Int>,
    private val hiddenAnnotationIds: () -> Set<String>,
    private val onPreview: (Shape.Box?) -> Unit,
    private val onSelect: (Set<String>) -> Unit,
) : ToolStrokeHandler {
    private var start: Offset = Offset.Zero
    private var last: Offset? = null

    override fun onStart(imagePoint: Offset) {
        start = imagePoint
        last = imagePoint
        onPreview(rectFrom(start, imagePoint))
    }

    override fun onMove(imagePoint: Offset) {
        last = imagePoint
        onPreview(rectFrom(start, imagePoint))
    }

    override fun onEnd() {
        val end = last ?: start
        onPreview(null)
        val box = rectFrom(start, end)
        val selectionRect = Rect(box.x, box.y, box.w, box.h)
        val ds = dataset() ?: return
        val ids = ds.annotations
            .filterNot { it.classId in hiddenClassIds() || it.id in hiddenAnnotationIds() }
            .filter { ann ->
                val b = ann.shape.bbox()
                Rect(b[0], b[1], b[2], b[3]).intersects(selectionRect)
            }
            .map { it.id }
            .toSet()
        onSelect(ids)
    }

    override fun onCancel() {
        last = null
        onPreview(null)
    }

    private fun rectFrom(a: Offset, b: Offset): Shape.Box {
        val x = min(a.x, b.x).toDouble()
        val y = min(a.y, b.y).toDouble()
        return Shape.Box(x, y, kotlin.math.abs(b.x - a.x).toDouble(), kotlin.math.abs(b.y - a.y).toDouble())
    }
}

/** Lasso: freeform loop, selects every annotation whose bbox center falls inside it (SPEC 5). */
class LassoToolHandler(
    private val dataset: () -> DatasetImage?,
    private val hiddenClassIds: () -> Set<Int>,
    private val hiddenAnnotationIds: () -> Set<String>,
    private val onPreviewPath: (List<Offset>?) -> Unit,
    private val onSelect: (Set<String>) -> Unit,
) : ToolStrokeHandler {
    private val points = mutableListOf<Offset>()

    override fun onStart(imagePoint: Offset) {
        points.clear()
        points.add(imagePoint)
        onPreviewPath(points.toList())
    }

    override fun onMove(imagePoint: Offset) {
        points.add(imagePoint)
        onPreviewPath(points.toList())
    }

    override fun onEnd() {
        onPreviewPath(null)
        if (points.size < 3) {
            points.clear()
            return
        }
        val polygon = points.map { Point(it.x.toDouble(), it.y.toDouble()) }
        points.clear()
        val ds = dataset() ?: return
        val ids = ds.annotations
            .filterNot { it.classId in hiddenClassIds() || it.id in hiddenAnnotationIds() }
            .filter { ann ->
                val b = ann.shape.bbox()
                val center = Point(b[0] + b[2] / 2, b[1] + b[3] / 2)
                pointInPolygon(center, polygon)
            }
            .map { it.id }
            .toSet()
        onSelect(ids)
    }

    override fun onCancel() {
        points.clear()
        onPreviewPath(null)
    }
}
