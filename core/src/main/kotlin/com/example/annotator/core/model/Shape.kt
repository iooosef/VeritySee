package com.example.annotator.core.model

import com.example.annotator.core.codec.Rle

/** All coordinates are in original image pixels, top-left origin. */
sealed interface Shape {
    /** Top-left based box. */
    data class Box(val x: Double, val y: Double, val w: Double, val h: Double) : Shape

    /** Full image RLE mask. The only editable region type in the MVP. */
    data class Mask(val rle: Rle) : Shape
}
