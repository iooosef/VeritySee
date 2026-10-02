package com.example.annotator.core.formats.yolo

import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.formats.MaskPolygons
import com.example.annotator.core.formats.bbox
import com.example.annotator.core.geometry.Point
import com.example.annotator.core.geometry.Polygon
import com.example.annotator.core.geometry.PolygonJoiner
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.Shape

data class YoloExportResult(
    val labelFiles: Map<String, String>,
    val dataYaml: String,
    val warnings: List<String>,
)

object YoloExporter {

    /**
     * [splitOf] assigns each image path to "train", "val", or "test" (or null for a flat,
     * unsplit layout). Export always writes layout B (images/{split}/, labels/{split}/)
     * when a split is given, per FORMATS 3.3.
     */
    fun export(
        images: List<DatasetImage>,
        classes: List<ClassDef>,
        segment: Boolean,
        simplifyTolerance: Double = 1.0,
        splitOf: (String) -> String? = { null },
    ): YoloExportResult {
        val warnings = mutableListOf<String>()
        val sortedClasses = classes.sortedBy { it.id }
        val exportIndex = sortedClasses.withIndex().associate { (i, c) -> c.id to i }

        val labelFiles = mutableMapOf<String, String>()
        for (image in images) {
            val lines = image.annotations.mapNotNull { annotation ->
                val newClassId = exportIndex[annotation.classId] ?: return@mapNotNull null
                val line = toYoloLine(annotation.shape, newClassId, image.size.width, image.size.height, segment, simplifyTolerance, warnings)
                if (line is YoloLine.Segment && line.points.size < 3) {
                    warnings.add("dropped empty annotation (${annotation.id}) after polygon conversion")
                    null
                } else {
                    line
                }
            }
            val text = lines.joinToString("\n") { YoloLineCodec.write(it) }.let { if (it.isEmpty()) "" else it + "\n" }
            val split = splitOf(image.path)
            val baseName = image.path.substringAfterLast("/").substringBeforeLast(".")
            val labelPath = if (split != null) "labels/$split/$baseName.txt" else "labels/$baseName.txt"
            labelFiles[labelPath] = text
        }

        val names = sortedClasses.map { it.name }
        val hasTest = images.any { splitOf(it.path) == "test" }
        val dataYaml = YoloClasses.writeDataYaml(
            names,
            trainPath = "images/train",
            valPath = "images/val",
            testPath = if (hasTest) "images/test" else null,
        )

        return YoloExportResult(labelFiles, dataYaml, warnings)
    }

    private fun toYoloLine(
        shape: Shape,
        classId: Int,
        width: Int,
        height: Int,
        segment: Boolean,
        simplifyTolerance: Double,
        warnings: MutableList<String>,
    ): YoloLine {
        if (!segment) {
            val bbox = shape.bbox()
            val cx = (bbox[0] + bbox[2] / 2) / width
            val cy = (bbox[1] + bbox[3] / 2) / height
            val w = bbox[2] / width
            val h = bbox[3] / height
            return YoloLine.Detect(classId, cx, cy, w, h)
        }

        val polygon: Polygon = when (shape) {
            is Shape.Box -> MaskPolygons.boxPolygon(shape.x, shape.y, shape.w, shape.h)
            is Shape.Mask -> {
                val result = MaskPolygons.maskToPolygons(shape.rle, simplifyTolerance)
                if (result.hadHoles) warnings.add("mask had holes that were filled")
                when {
                    result.polygons.isEmpty() -> emptyList()
                    result.polygons.size == 1 -> result.polygons[0]
                    else -> {
                        warnings.add("joined ${result.polygons.size} components into one polygon")
                        PolygonJoiner.joinForYolo(result.polygons)
                    }
                }
            }
        }
        val normalized = polygon.map { Point(it.x / width, it.y / height) }
        return YoloLine.Segment(classId, normalized)
    }
}
