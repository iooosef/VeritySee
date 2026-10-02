package com.example.annotator.core.formats.coco

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CocoImageDto(val id: Int, val file_name: String, val width: Int, val height: Int)

@Serializable
data class CocoCategoryDto(val id: Int, val name: String, val supercategory: String? = null)

@Serializable
data class CocoRleDto(val size: List<Int>, val counts: JsonElement)

@Serializable
data class CocoAnnotationDto(
    val id: Int,
    val image_id: Int,
    val category_id: Int,
    val bbox: List<Double>,
    val area: Double,
    val iscrowd: Int = 0,
    val segmentation: JsonElement? = null,
)

@Serializable
data class CocoFileDto(
    val images: List<CocoImageDto>,
    val categories: List<CocoCategoryDto>,
    val annotations: List<CocoAnnotationDto>,
)
