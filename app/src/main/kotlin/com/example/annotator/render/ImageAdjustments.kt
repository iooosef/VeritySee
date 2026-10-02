package com.example.annotator.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/** Brightness/contrast/gamma, view-only (SPEC section 6): never written to disk or exported. */
data class ImageAdjustments(
    val brightness: Float = 0f, // -1..1
    val contrast: Float = 0f, // -1..1
    val gamma: Float = 1f, // 0.1..5, 1 = no change
) {
    val isIdentity: Boolean get() = brightness == 0f && contrast == 0f && gamma == 1f
}

object ImageAdjustmentRenderer {

    /** Applies brightness/contrast via a ColorMatrix, then gamma via a 256-entry lookup table. */
    fun apply(source: Bitmap, adjustments: ImageAdjustments): Bitmap {
        if (adjustments.isIdentity) return source

        val brightnessOffset = adjustments.brightness * 255f
        val contrastScale = (1f + adjustments.contrast).coerceAtLeast(0f)
        val contrastTranslate = (1f - contrastScale) * 127.5f

        val colorMatrix = ColorMatrix(
            floatArrayOf(
                contrastScale, 0f, 0f, 0f, contrastTranslate + brightnessOffset,
                0f, contrastScale, 0f, 0f, contrastTranslate + brightnessOffset,
                0f, 0f, contrastScale, 0f, contrastTranslate + brightnessOffset,
                0f, 0f, 0f, 1f, 0f,
            ),
        )

        val adjusted = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(adjusted)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap(source, 0f, 0f, paint)

        if (adjustments.gamma == 1f) return adjusted
        return applyGamma(adjusted, adjustments.gamma)
    }

    private fun applyGamma(bitmap: Bitmap, gamma: Float): Bitmap {
        val lut = IntArray(256) { i -> (Math.pow(i / 255.0, 1.0 / gamma) * 255.0).toInt().coerceIn(0, 255) }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = lut[(p ushr 16) and 0xFF]
            val g = lut[(p ushr 8) and 0xFF]
            val b = lut[p and 0xFF]
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }
}
