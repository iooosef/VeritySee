package com.example.annotator.editor

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Offset
import com.example.annotator.core.codec.Rle
import com.example.annotator.core.codec.RleOps
import com.example.annotator.core.formats.MaskPolygons
import com.example.annotator.core.geometry.Point
import com.example.annotator.core.model.ImageSize
import com.example.annotator.render.MaskOverlay

/**
 * Brush/Eraser tool: paints or clears a circle of [radiusImagePx] onto an editable `ALPHA_8`
 * bitmap at original resolution (SPEC section 6). [erase] is fixed per instance -- Brush and
 * Eraser are separate [Tool] values, each constructing this with its own value, rather than a
 * single tool with a mode switch. With [erase] true and nothing selected, there is nothing to
 * erase and the stroke is a no-op (SPEC section 5).
 */
class BrushToolHandler(
    private val targetAnnotationId: String?,
    private val existingRle: Rle?,
    private val imageSize: ImageSize,
    private val erase: Boolean,
    private val radiusImagePx: () -> Float,
    private val onPreviewPoint: (Offset?) -> Unit,
    private val onCommit: (annotationId: String?, Rle) -> Unit,
) : ToolStrokeHandler {

    private var bitmap: Bitmap? = null
    private var canvas: AndroidCanvas? = null
    private var painted = false
    private var lastPoint: Offset? = null

    private val fillPaint = if (erase) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR); style = Paint.Style.FILL }
    } else {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -0x1 /* opaque white */; style = Paint.Style.FILL }
    }
    private val linePaint = if (erase) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    } else {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -0x1; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    }

    /** The in-progress edit, for live preview rendering while the stroke is active. */
    fun currentBitmap(): Bitmap? = bitmap

    override fun onStart(imagePoint: Offset) {
        if (erase && existingRle == null) return // nothing to erase
        val bmp = existingRle?.let { MaskOverlay.toAlpha8Bitmap(it) }
            ?.copy(Bitmap.Config.ALPHA_8, true)
            ?: Bitmap.createBitmap(imageSize.width, imageSize.height, Bitmap.Config.ALPHA_8)
        bitmap = bmp
        canvas = AndroidCanvas(bmp)
        lastPoint = null
        paintAt(imagePoint)
        onPreviewPoint(imagePoint)
    }

    override fun onMove(imagePoint: Offset) {
        if (bitmap == null) return
        paintAt(imagePoint)
        onPreviewPoint(imagePoint)
    }

    override fun onEnd() {
        onPreviewPoint(null)
        val bmp = bitmap ?: return
        bitmap = null
        canvas = null
        lastPoint = null
        if (!painted) return
        onCommit(targetAnnotationId, MaskOverlay.fromAlpha8Bitmap(bmp))
    }

    override fun onCancel() {
        onPreviewPoint(null)
        bitmap = null
        canvas = null
        lastPoint = null
        painted = false
    }

    /**
     * Stamps a circle at [imagePoint] and, if this isn't the first point of the stroke, also
     * draws a round-capped line from the previous point -- fast finger movement means
     * consecutive touch samples can be far apart on the original-resolution bitmap, and
     * circles alone would leave gaps (a visibly dashed stroke) between them.
     */
    private fun paintAt(imagePoint: Offset) {
        val radius = radiusImagePx()
        lastPoint?.let { prev ->
            linePaint.strokeWidth = radius * 2
            canvas?.drawLine(prev.x, prev.y, imagePoint.x, imagePoint.y, linePaint)
        }
        canvas?.drawCircle(imagePoint.x, imagePoint.y, radius, fillPaint)
        lastPoint = imagePoint
        painted = true
    }
}

/**
 * Pencil/Knife tool: draws an outline; on release the path closes and the interior is filled
 * (SPEC section 5). [erase] is fixed per instance -- Pencil unions the closed shape with the
 * target mask, Knife subtracts it (cutting a piece out), each its own [Tool] value rather than
 * a mode switch on one tool.
 */
class PencilToolHandler(
    private val targetAnnotationId: String?,
    private val existingRle: Rle?,
    private val imageSize: ImageSize,
    private val erase: Boolean,
    private val onPreviewPath: (List<Offset>?) -> Unit,
    private val onCommit: (annotationId: String?, Rle) -> Unit,
) : ToolStrokeHandler {

    private val points = mutableListOf<Offset>()

    override fun onStart(imagePoint: Offset) {
        if (erase && existingRle == null) return
        points.clear()
        points.add(imagePoint)
        onPreviewPath(points.toList()) // a fresh list each call -- Compose state only detects
        // reference/structural changes, and repeatedly handing back the same mutated instance
        // never looked like a change, so the path never actually redrew mid-stroke.
    }

    override fun onMove(imagePoint: Offset) {
        if (points.isEmpty()) return
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
        val strokeRle = MaskPolygons.polygonsToMask(listOf(polygon), imageSize.width, imageSize.height)
        points.clear()

        val result = if (erase) {
            existingRle?.let { RleOps.subtract(it, strokeRle) } ?: return
        } else {
            existingRle?.let { RleOps.union(it, strokeRle) } ?: strokeRle
        }
        onCommit(targetAnnotationId, result)
    }

    override fun onCancel() {
        points.clear()
        onPreviewPath(null)
    }
}
