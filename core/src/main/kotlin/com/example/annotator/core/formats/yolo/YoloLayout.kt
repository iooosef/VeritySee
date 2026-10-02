package com.example.annotator.core.formats.yolo

/**
 * The label path for an image path: replace the last `images` path segment with `labels`
 * and swap the extension to `.txt` (FORMATS 3.3). Covers layouts A, B, and C uniformly.
 */
object YoloLayout {

    fun labelPathFor(imagePath: String): String? {
        val segments = imagePath.split("/")
        val lastImagesIndex = segments.indexOfLast { it == "images" }
        if (lastImagesIndex < 0) return null
        val labelSegments = segments.toMutableList()
        labelSegments[lastImagesIndex] = "labels"
        val fileName = labelSegments.last()
        val baseName = fileName.substringBeforeLast(".")
        labelSegments[labelSegments.lastIndex] = "$baseName.txt"
        return labelSegments.joinToString("/")
    }
}
