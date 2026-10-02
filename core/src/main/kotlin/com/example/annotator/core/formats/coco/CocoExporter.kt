package com.example.annotator.core.formats.coco

import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.formats.MaskPolygons
import com.example.annotator.core.formats.area
import com.example.annotator.core.formats.bbox
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.Shape
import kotlinx.serialization.json.Json

enum class CocoMaskOption { POLYGONS, COMPRESSED_RLE }

data class CocoExportResult(val json: String, val warnings: List<String>)

object CocoExporter {

    private val json = Json { prettyPrint = true }

    fun export(
        images: List<DatasetImage>,
        classes: List<ClassDef>,
        maskOption: CocoMaskOption = CocoMaskOption.POLYGONS,
        simplifyTolerance: Double = 1.0,
    ): CocoExportResult {
        val warnings = mutableListOf<String>()
        val sortedClasses = classes.sortedBy { it.id }
        val exportCategoryId = sortedClasses.withIndex().associate { (i, c) -> c.id to (i + 1) }

        val categoryDtos = sortedClasses.map { c -> CocoCategoryDto(exportCategoryId.getValue(c.id), c.name) }

        val imageDtos = mutableListOf<CocoImageDto>()
        val annotationDtos = mutableListOf<CocoAnnotationDto>()
        var nextAnnotationId = 1

        for ((imageIndex, image) in images.withIndex()) {
            val imageId = imageIndex + 1
            imageDtos.add(CocoImageDto(imageId, image.path, image.size.width, image.size.height))

            for (annotation in image.annotations) {
                val categoryId = exportCategoryId[annotation.classId] ?: continue
                val bbox = annotation.shape.bbox().toList()
                val area = annotation.shape.area()
                val segmentation = when (val shape = annotation.shape) {
                    is Shape.Box -> null
                    is Shape.Mask -> when (maskOption) {
                        CocoMaskOption.COMPRESSED_RLE -> CocoSegmentation.writeCompressedRle(shape.rle)
                        CocoMaskOption.POLYGONS -> {
                            val result = MaskPolygons.maskToPolygons(shape.rle, simplifyTolerance)
                            if (result.hadHoles) warnings.add("mask had holes that were filled")
                            if (result.polygons.isEmpty()) null else CocoSegmentation.writePolygons(result.polygons)
                        }
                    }
                }

                annotationDtos.add(
                    CocoAnnotationDto(
                        id = nextAnnotationId++,
                        image_id = imageId,
                        category_id = categoryId,
                        bbox = bbox,
                        area = area,
                        iscrowd = 0,
                        segmentation = segmentation,
                    ),
                )
            }
        }

        val dto = CocoFileDto(imageDtos, categoryDtos, annotationDtos)
        return CocoExportResult(json.encodeToString(CocoFileDto.serializer(), dto), warnings)
    }
}
