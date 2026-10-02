package com.example.annotator.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.annotator.core.model.ImageSize

/** Reads an image's pixel dimensions from its header only (SPEC section 7: no full decode). */
object ImageBounds {

    fun read(context: Context, uri: Uri): ImageSize? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return if (options.outWidth > 0 && options.outHeight > 0) {
            ImageSize(options.outWidth, options.outHeight)
        } else {
            null
        }
    }
}
