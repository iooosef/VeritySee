package com.example.annotator.core.formats.sam

import com.example.annotator.core.codec.CompressedRle
import com.example.annotator.core.codec.Rle
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.formats.MaskPolygons
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SamExportResult(val json: String)

object SamExporter {

    private val json = Json { prettyPrint = true }

    /**
     * [includeClassFields] (default on) adds `category_id`/`category_name` (FORMATS 5).
     * An annotation whose [Annotation.source] is not [Source.IMPORTED_SAM] is treated as
     * edited: `predicted_iou`/`stability_score` are dropped, `crop_box` reset to the full
     * image, and `bbox`/`area` recomputed -- the rest of `extra` still passes through.
     */
    fun exportFile(
        image: DatasetImage,
        imageId: Int,
        classes: List<ClassDef>,
        includeClassFields: Boolean = true,
    ): SamExportResult {
        val classById = classes.associateBy { it.id }
        var nextAnnotationId = 1

        val annotationObjs = image.annotations.map { annotation ->
            toAnnotationJson(annotation, nextAnnotationId++, classById[annotation.classId], includeClassFields, image.size.width, image.size.height)
        }

        val root = buildJsonObject {
            put(
                "image",
                buildJsonObject {
                    put("image_id", imageId)
                    put("width", image.size.width)
                    put("height", image.size.height)
                    put("file_name", image.path)
                },
            )
            put("annotations", kotlinx.serialization.json.JsonArray(annotationObjs))
        }
        return SamExportResult(json.encodeToString(JsonElement.serializer(), root))
    }

    private fun toAnnotationJson(
        annotation: Annotation,
        id: Int,
        classDef: ClassDef?,
        includeClassFields: Boolean,
        width: Int,
        height: Int,
    ): JsonObject {
        val rle = when (val shape = annotation.shape) {
            is Shape.Mask -> shape.rle
            is Shape.Box -> MaskPolygons.polygonsToMask(
                listOf(MaskPolygons.boxPolygon(shape.x, shape.y, shape.w, shape.h)),
                width,
                height,
            )
        }
        val bbox = rle.bbox()
        val area = rle.area()
        val edited = annotation.source != Source.IMPORTED_SAM

        return buildJsonObject {
            put("id", id)
            put(
                "segmentation",
                buildJsonObject {
                    put("size", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(rle.height), JsonPrimitive(rle.width))))
                    put("counts", CompressedRle.encode(rle.counts))
                },
            )
            put("bbox", kotlinx.serialization.json.JsonArray(bbox.map { JsonPrimitive(it) }))
            put("area", area)
            if (includeClassFields) {
                put("category_id", classDef?.id ?: annotation.classId)
                classDef?.name?.let { put("category_name", it) }
            }
            if (!edited) {
                for ((key, value) in annotation.extra) {
                    if (key == "crop_box") continue
                    put(key, value)
                }
                put("crop_box", annotation.extra["crop_box"] ?: fullImageCropBox(width, height))
            } else {
                for ((key, value) in annotation.extra) {
                    if (key in setOf("predicted_iou", "stability_score", "crop_box")) continue
                    put(key, value)
                }
                put("crop_box", fullImageCropBox(width, height))
            }
        }
    }

    private fun fullImageCropBox(width: Int, height: Int): JsonElement =
        kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(0), JsonPrimitive(width), JsonPrimitive(height)))
}
