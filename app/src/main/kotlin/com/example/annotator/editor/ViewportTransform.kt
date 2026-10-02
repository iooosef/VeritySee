package com.example.annotator.editor

import androidx.compose.ui.geometry.Offset
import kotlin.math.min

/**
 * Maps original image pixel coordinates to screen (canvas) coordinates. [scale] is the zoom
 * factor on top of the base "fit to screen" scale; [offset] pans the image on screen.
 */
data class ViewportTransform(
    val baseScale: Float,
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
) {
    val totalScale: Float get() = baseScale * scale

    fun imageToScreen(imagePoint: Offset): Offset = imagePoint * totalScale + offset

    fun screenToImage(screenPoint: Offset): Offset = (screenPoint - offset) / totalScale

    fun zoomPercent(): Int = (totalScale * 100).toInt()

    fun panned(pan: Offset) = copy(offset = offset + pan)

    fun zoomed(zoomFactor: Float, focusScreenPoint: Offset, minScale: Float = 0.1f, maxScale: Float = 20f): ViewportTransform {
        val newScale = (scale * zoomFactor).coerceIn(minScale, maxScale)
        if (newScale == scale) return this
        // Keep the point under the gesture focus stationary on screen while zooming.
        val focusImagePoint = screenToImage(focusScreenPoint)
        val newTotalScale = baseScale * newScale
        val newOffset = focusScreenPoint - focusImagePoint * newTotalScale
        return copy(scale = newScale, offset = newOffset)
    }

    companion object {
        fun fitToScreen(imageWidth: Int, imageHeight: Int, viewportWidth: Float, viewportHeight: Float): ViewportTransform {
            if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
                return ViewportTransform(baseScale = 1f)
            }
            val fit = min(viewportWidth / imageWidth, viewportHeight / imageHeight)
            val offsetX = (viewportWidth - imageWidth * fit) / 2f
            val offsetY = (viewportHeight - imageHeight * fit) / 2f
            return ViewportTransform(baseScale = fit, offset = Offset(offsetX, offsetY))
        }
    }
}
