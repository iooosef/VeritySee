package com.example.annotator.core.formats.yolo

import com.example.annotator.core.formats.ColorPalette
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.formats.ImportResult
import com.example.annotator.core.formats.MaskPolygons
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import java.util.UUID

object YoloImporter {

    fun import(
        imagePaths: List<String>,
        textFiles: Map<String, String>,
        imageSizeOf: (String) -> ImageSize,
    ): ImportResult {
        val warnings = mutableListOf<String>()
        val names = findClassNames(textFiles).toMutableList()

        val parsedPerImage = imagePaths.associateWith { imagePath ->
            val labelPath = YoloLayout.labelPathFor(imagePath)
            val text = labelPath?.let { textFiles[it] }
            if (text == null) {
                emptyList()
            } else {
                text.lines().filterIndexed { _, l -> l.isNotBlank() }
                    .mapIndexedNotNull { idx, line -> YoloLineCodec.parse(line, idx + 1, warnings) }
            }
        }

        val maxClassId = parsedPerImage.values.flatten().maxOfOrNull { it.classId } ?: -1
        while (names.size <= maxClassId) {
            val id = names.size
            names.add("class_$id")
            warnings.add("unknown class index $id, created placeholder class_$id")
        }
        val classes = names.mapIndexed { id, name -> ClassDef(id, name, ColorPalette.colorFor(id)) }

        val images = imagePaths.map { imagePath ->
            val size = imageSizeOf(imagePath)
            val lines = parsedPerImage[imagePath].orEmpty()
            val annotations = lines.map { line -> toAnnotation(line, size) }
            DatasetImage(imagePath, size, annotations)
        }

        return ImportResult(images, classes, warnings)
    }

    /**
     * Tallies class ids referenced across label files without building shapes (no image size,
     * no polygon rasterization) -- used for class management counts and discovering the full
     * set of used class ids when there's no data.yaml/classes.txt to read names from.
     */
    fun countClassIds(labelTexts: Collection<String>): Map<Int, Int> {
        val counts = mutableMapOf<Int, Int>()
        for (text in labelTexts) {
            for (line in text.lines()) {
                if (line.isBlank()) continue
                val classId = line.trim().substringBefore(' ').toIntOrNull() ?: continue
                counts[classId] = (counts[classId] ?: 0) + 1
            }
        }
        return counts
    }

    private fun findClassNames(textFiles: Map<String, String>): List<String> {
        val yamlPath = textFiles.keys.firstOrNull { it.endsWith("data.yaml") || it.endsWith("data.yml") }
        if (yamlPath != null) return YoloClasses.parseDataYaml(textFiles.getValue(yamlPath))
        val classesPath = textFiles.keys.firstOrNull { it.endsWith("classes.txt") }
        if (classesPath != null) return YoloClasses.parseClassesTxt(textFiles.getValue(classesPath))
        return emptyList()
    }

    private fun toAnnotation(line: YoloLine, size: ImageSize): Annotation {
        val shape: Shape = when (line) {
            is YoloLine.Detect -> {
                val w = line.w * size.width
                val h = line.h * size.height
                val x = line.cx * size.width - w / 2
                val y = line.cy * size.height - h / 2
                Shape.Box(x, y, w, h)
            }
            is YoloLine.Segment -> {
                val absolute = line.points.map {
                    com.example.annotator.core.geometry.Point(it.x * size.width, it.y * size.height)
                }
                val rle = MaskPolygons.polygonsToMask(listOf(absolute), size.width, size.height)
                Shape.Mask(rle)
            }
        }
        return Annotation(
            id = UUID.randomUUID().toString(),
            classId = line.classId,
            shape = shape,
            source = Source.IMPORTED_YOLO,
        )
    }
}
