package com.example.annotator.core.formats.coco

import com.example.annotator.core.formats.ColorPalette
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.formats.ImportResult
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import kotlinx.serialization.json.Json
import java.util.UUID

object CocoImporter {

    private val json = Json { ignoreUnknownKeys = true }

    /** Imports one COCO JSON file's worth of images and annotations. */
    fun importFile(text: String): ImportResult {
        val dto = json.decodeFromString(CocoFileDto.serializer(), text)
        val warnings = mutableListOf<String>()

        val categories = applyRoboflowQuirk(dto.categories, dto.annotations, warnings)
        val categoryNames = categories.associate { it.id to it.name }.toMutableMap()

        val referencedIds = dto.annotations.map { it.category_id }.toSet()
        val knownIds = categoryNames.keys
        for (missing in (referencedIds - knownIds).sorted()) {
            categoryNames[missing] = "class_$missing"
            warnings.add("unknown class index $missing, created placeholder class_$missing")
        }
        val classes = categoryNames.entries.sortedBy { it.key }
            .map { (id, name) -> ClassDef(id, name, ColorPalette.colorFor(id)) }

        val annotationsByImage = dto.annotations.groupBy { it.image_id }
        val images = dto.images.map { imageDto ->
            val anns = annotationsByImage[imageDto.id].orEmpty().mapNotNull { ann ->
                toAnnotation(ann, imageDto.width, imageDto.height)
            }
            DatasetImage(imageDto.file_name, ImageSize(imageDto.width, imageDto.height), anns)
        }

        return ImportResult(images, classes, warnings)
    }

    /**
     * Tallies category ids directly from the annotations list without decoding segmentation
     * (no RLE decode, no polygon rasterization) -- used for class management counts. The
     * Roboflow quirk only ever removes categories with zero annotations, so it never changes
     * which ids get counted here.
     */
    fun countClassIds(text: String): Map<Int, Int> {
        val dto = json.decodeFromString(CocoFileDto.serializer(), text)
        return dto.annotations.groupingBy { it.category_id }.eachCount()
    }

    /**
     * Roboflow quirk (FORMATS 4.2): skip a category with zero annotations whose name is used
     * as another category's supercategory.
     */
    private fun applyRoboflowQuirk(
        categories: List<CocoCategoryDto>,
        annotations: List<CocoAnnotationDto>,
        warnings: MutableList<String>,
    ): List<CocoCategoryDto> {
        val annotationCounts = annotations.groupingBy { it.category_id }.eachCount()
        val supercategoryNames = categories.mapNotNull { it.supercategory }.toSet()
        return categories.filter { category ->
            val isUnused = (annotationCounts[category.id] ?: 0) == 0
            val isSupercategoryOnly = category.name in supercategoryNames
            if (isUnused && isSupercategoryOnly) {
                warnings.add("skipped supercategory-only category '${category.name}' (Roboflow quirk)")
                false
            } else {
                true
            }
        }
    }

    private fun toAnnotation(dto: CocoAnnotationDto, width: Int, height: Int): Annotation? {
        val mask = CocoSegmentation.parse(dto.segmentation, width, height)
        val shape: Shape = mask ?: run {
            if (dto.bbox.size < 4) return null
            Shape.Box(dto.bbox[0], dto.bbox[1], dto.bbox[2], dto.bbox[3])
        }
        return Annotation(
            id = UUID.randomUUID().toString(),
            classId = dto.category_id,
            shape = shape,
            source = Source.IMPORTED_COCO,
        )
    }
}
