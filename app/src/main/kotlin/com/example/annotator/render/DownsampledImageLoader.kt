package com.example.annotator.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** Decodes an image downsampled so its longest side is at most [maxDimension] px (SPEC section 6). */
object DownsampledImageLoader {

    fun decode(context: Context, uri: Uri, maxDimension: Int = 4096): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }
        val longestSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
        if (longestSide <= 0) return null

        var sampleSize = 1
        while (longestSide / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    }
}
