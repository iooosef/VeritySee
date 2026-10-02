package com.example.annotator.render

import android.graphics.Bitmap
import com.example.annotator.core.codec.Rle
import com.example.annotator.core.codec.RleCodec
import java.nio.ByteBuffer

/**
 * Rasterizes an [Rle] mask into an `ALPHA_8` bitmap at original resolution (SPEC section 6):
 * drawn with a color filter so it tints with the annotation's class color.
 */
object MaskOverlay {

    fun toAlpha8Bitmap(rle: Rle): Bitmap {
        val grid = RleCodec.decode(rle)
        val pixels = ByteArray(rle.width * rle.height)
        for (y in 0 until rle.height) {
            val rowStart = y * rle.width
            for (x in 0 until rle.width) {
                if (grid[x, y]) pixels[rowStart + x] = 0xFF.toByte()
            }
        }
        val bitmap = Bitmap.createBitmap(rle.width, rle.height, Bitmap.Config.ALPHA_8)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        return bitmap
    }

    /** The inverse of [toAlpha8Bitmap]: re-encodes an edited `ALPHA_8` bitmap back to [Rle]. */
    fun fromAlpha8Bitmap(bitmap: Bitmap): Rle {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = ByteArray(width * height)
        bitmap.copyPixelsToBuffer(ByteBuffer.wrap(pixels))
        val grid = com.example.annotator.core.codec.BooleanGrid(width, height)
        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                if (pixels[rowStart + x].toInt() and 0xFF > 127) grid[x, y] = true
            }
        }
        return RleCodec.encode(grid)
    }
}
