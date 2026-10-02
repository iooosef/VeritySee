package com.example.annotator.core.formats

import com.example.annotator.core.model.Annotation

/** Dataset-wide class operations used by class management (merge, usage counts). */
object ClassOps {

    fun remapClass(annotations: List<Annotation>, fromId: Int, toId: Int): List<Annotation> =
        annotations.map { if (it.classId == fromId) it.copy(classId = toId) else it }

    fun countByClass(images: List<DatasetImage>): Map<Int, Int> =
        images.flatMap { it.annotations }.groupingBy { it.classId }.eachCount()
}
