package com.example.annotator.core.formats

import com.example.annotator.core.formats.coco.CocoExporter
import com.example.annotator.core.formats.sam.SamExporter
import com.example.annotator.core.formats.yolo.YoloExporter
import com.example.annotator.core.model.ClassDef

data class ExportResult(val files: Map<String, String>, val warnings: List<String>)

/**
 * Orchestrates the format exporters built in M3 against a whole (already scope-filtered)
 * dataset: applies the optional train/val/test split, then writes to `<format>/...` (the
 * caller prefixes with the actual export root, e.g. `.annotator/export/`). Image file copying
 * is out of scope here, same as the individual format exporters (SPEC 4.6 output is text only;
 * images are expected to already be in the dataset folder or copied separately).
 */
object DatasetExporter {

    fun export(images: List<DatasetImage>, classes: List<ClassDef>, options: ExportOptions): ExportResult {
        val splitOf: (String) -> String? = buildSplitLookup(images.map { it.path }, options.split)

        return when (options.format) {
            ExportFormat.YOLO_DETECT -> exportYolo(images, classes, splitOf, segment = false, options)
            ExportFormat.YOLO_SEGMENT -> exportYolo(images, classes, splitOf, segment = true, options)
            ExportFormat.COCO -> exportCoco(images, classes, splitOf, options)
            ExportFormat.SAM -> exportSam(images, classes, splitOf)
        }
    }

    private fun buildSplitLookup(paths: List<String>, split: SplitConfig?): (String) -> String? {
        if (split == null) return { null }
        val result = DatasetSplit.split(paths, split.trainFraction, split.valFraction, split.seed)
        val lookup = buildMap {
            result.train.forEach { put(it, "train") }
            result.val_.forEach { put(it, "val") }
            result.test.forEach { put(it, "test") }
        }
        return { path -> lookup[path] }
    }

    private fun exportYolo(
        images: List<DatasetImage>,
        classes: List<ClassDef>,
        splitOf: (String) -> String?,
        segment: Boolean,
        options: ExportOptions,
    ): ExportResult {
        val result = YoloExporter.export(images, classes, segment, options.yoloSimplifyTolerance, splitOf)
        val files = buildMap {
            put("data.yaml", result.dataYaml)
            result.labelFiles.forEach { (path, text) -> put(path, text) }
        }
        return ExportResult(files, result.warnings)
    }

    private fun exportCoco(
        images: List<DatasetImage>,
        classes: List<ClassDef>,
        splitOf: (String) -> String?,
        options: ExportOptions,
    ): ExportResult {
        val files = mutableMapOf<String, String>()
        val warnings = mutableListOf<String>()

        if (options.split == null) {
            // No split requested: a single instances_default.json (FORMATS 4.2).
            val result = CocoExporter.export(renameForExport(images, null), classes, options.cocoMaskOption, options.yoloSimplifyTolerance)
            files["annotations/instances_default.json"] = result.json
            warnings.addAll(result.warnings)
        } else {
            for ((split, splitImages) in images.groupBy { splitOf(it.path) }) {
                val name = split ?: "default"
                val result = CocoExporter.export(renameForExport(splitImages, split), classes, options.cocoMaskOption, options.yoloSimplifyTolerance)
                files["annotations/instances_$name.json"] = result.json
                warnings.addAll(result.warnings)
            }
        }
        return ExportResult(files, warnings)
    }

    private fun exportSam(images: List<DatasetImage>, classes: List<ClassDef>, splitOf: (String) -> String?): ExportResult {
        val files = mutableMapOf<String, String>()
        for ((index, image) in images.withIndex()) {
            val split = splitOf(image.path)
            val baseName = image.path.substringAfterLast("/").substringBeforeLast(".")
            val path = if (split != null) "$split/$baseName.json" else "$baseName.json"
            val renamed = renameForExport(listOf(image), split).single()
            files[path] = SamExporter.exportFile(renamed, imageId = index + 1, classes = classes).json
        }
        return ExportResult(files, emptyList())
    }

    /** file_name in the export points at where the image would live in the export's own images/ folder. */
    private fun renameForExport(images: List<DatasetImage>, split: String?): List<DatasetImage> = images.map { image ->
        val baseName = image.path.substringAfterLast("/")
        val newPath = if (split != null) "images/$split/$baseName" else "images/$baseName"
        image.copy(path = newPath)
    }
}
