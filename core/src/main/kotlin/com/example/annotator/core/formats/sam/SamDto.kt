package com.example.annotator.core.formats.sam

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SamImageDto(val image_id: Int, val width: Int, val height: Int, val file_name: String)

@Serializable
data class SamSegmentationDto(val size: List<Int>, val counts: String)

@Serializable
data class SamAnnotationDto(
    val id: Int,
    val segmentation: SamSegmentationDto,
    val bbox: List<Double>,
    val area: Double,
    val predicted_iou: Double? = null,
    val stability_score: Double? = null,
    val crop_box: List<Double>? = null,
    val point_coords: JsonElement? = null,
    val category_id: Int? = null,
    val category_name: String? = null,
)

@Serializable
data class SamFileDto(val image: SamImageDto, val annotations: List<SamAnnotationDto>)
