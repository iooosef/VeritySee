package com.example.annotator.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.sqrt

/** Single-finger stroke callbacks for the active tool, in image pixel coordinates. */
interface ToolStrokeHandler {
    fun onTap(imagePoint: Offset) {}
    fun onStart(imagePoint: Offset) {}
    fun onMove(imagePoint: Offset) {}
    fun onEnd() {}
    fun onCancel() {}
}

/**
 * Two fingers always pan and pinch zoom; one finger belongs to the active tool (SPEC section 5).
 * If a second finger touches down mid-stroke, the in-progress tool stroke is cancelled (not
 * committed) and the gesture becomes pan/zoom for its remainder. A single finger that never
 * moves is reported as a tap rather than a start/end stroke.
 */
suspend fun PointerInputScope.detectToolGestures(
    tool: () -> Tool,
    transform: () -> ViewportTransform,
    onTransformChange: (ViewportTransform) -> Unit,
    stroke: ToolStrokeHandler,
) {
    awaitEachGesture {
        var isPanning = false
        var isStroking = false
        var moved = false
        var startImagePoint = Offset.Zero
        var startScreenPoint = Offset.Zero

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            if (pressed.size >= 2) {
                if (isStroking) {
                    stroke.onCancel()
                    isStroking = false
                }
                isPanning = true
                val centroid = pressed.map { it.position }.reduce { a, b -> a + b } / pressed.size.toFloat()
                val prevCentroid = pressed.map { it.position - it.positionChange() }.reduce { a, b -> a + b } / pressed.size.toFloat()
                val pan = centroid - prevCentroid

                val currentSpread = pressed.map { distance(it.position, centroid) }.average().toFloat()
                val prevSpread = pressed.map { distance(it.position - it.positionChange(), prevCentroid) }.average().toFloat()
                val zoom = if (prevSpread > 0.01f) currentSpread / prevSpread else 1f

                onTransformChange(transform().panned(pan).zoomed(zoom, centroid))
                pressed.forEach { it.consume() }
            } else {
                val change = pressed[0]
                when {
                    isPanning -> {
                        // A second finger already lifted off this gesture; keep treating the
                        // remainder as pan rather than suddenly starting a tool stroke.
                        onTransformChange(transform().panned(change.positionChange()))
                        change.consume()
                    }
                    tool() == Tool.PAN -> {
                        onTransformChange(transform().panned(change.positionChange()))
                        change.consume()
                    }
                    else -> {
                        val imagePoint = transform().screenToImage(change.position)
                        if (!isStroking) {
                            isStroking = true
                            startImagePoint = imagePoint
                            startScreenPoint = change.position
                            stroke.onStart(imagePoint)
                        } else {
                            // A touch slop, not any nonzero delta: fast taps (e.g. a quick
                            // double tap to select) always carry a pixel or two of jitter,
                            // which previously got misclassified as a drag and swallowed the
                            // tap/selection entirely.
                            if (distance(change.position, startScreenPoint) > viewConfiguration.touchSlop) moved = true
                            stroke.onMove(imagePoint)
                        }
                        change.consume()
                    }
                }
            }
        }

        if (isStroking) {
            // Brush/Pencil treat a stroke that never cleared touch slop as a valid tiny paint
            // action (a dot, or a short stroke) rather than a tap -- unlike Selection/Box
            // Select/Lasso, where an unmoved touch means "select/hit-test", not "draw".
            val paintsEvenWithoutMovement = tool() == Tool.BRUSH || tool() == Tool.ERASER || tool() == Tool.PENCIL || tool() == Tool.KNIFE
            if (moved || paintsEvenWithoutMovement) {
                stroke.onEnd()
            } else {
                stroke.onCancel()
                stroke.onTap(startImagePoint)
            }
        }
    }
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}
