package com.example.annotator.core.formats

import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape

data class DatasetImage(val path: String, val size: ImageSize, val annotations: List<Annotation>)

data class ImportResult(
    val images: List<DatasetImage>,
    val classes: List<ClassDef>,
    val warnings: List<String>,
)

/** `[x, y, w, h]` top-left XYWH in image pixels. */
fun Shape.bbox(): DoubleArray = when (this) {
    is Shape.Box -> doubleArrayOf(x, y, w, h)
    is Shape.Mask -> rle.bbox().map { it.toDouble() }.toDoubleArray()
}

fun Shape.area(): Double = when (this) {
    is Shape.Box -> w * h
    is Shape.Mask -> rle.area().toDouble()
}

fun Shape.isEmpty(): Boolean = when (this) {
    is Shape.Box -> w <= 0 || h <= 0
    is Shape.Mask -> rle.isEmpty()
}
