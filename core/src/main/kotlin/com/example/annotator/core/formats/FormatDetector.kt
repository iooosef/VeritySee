package com.example.annotator.core.formats

enum class DetectedFormat { YOLO, COCO, SAM, NONE }

/** Pure function over a folder listing: detects the annotation format present, if any. */
object FormatDetector {

    fun detect(paths: List<String>, readFile: (String) -> String?): DetectedFormat {
        if (paths.any { it.endsWith("data.yaml") || it.endsWith("data.yml") } ||
            (paths.any { it.contains("/labels/") || it.startsWith("labels/") } &&
                paths.any { it.endsWith(".txt") && (it.contains("/labels/") || it.startsWith("labels/")) })
        ) {
            return DetectedFormat.YOLO
        }

        val cocoJson = paths.firstOrNull {
            val dir = it.substringBeforeLast("/", "")
            (dir == "annotations" || dir.endsWith("/annotations")) && it.substringAfterLast("/").startsWith("instances_")
        } ?: paths.firstOrNull { it.substringAfterLast("/") == "_annotations.coco.json" }
            ?: paths.firstOrNull { path ->
            path.endsWith(".json") && !path.contains("/") &&
                readFile(path)?.let { it.contains("\"images\"") && it.contains("\"annotations\"") } == true
        }
        if (cocoJson != null) return DetectedFormat.COCO

        val samJson = paths.firstOrNull { path ->
            path.endsWith(".json") &&
                readFile(path)?.let { it.contains("\"image_id\"") && it.contains("\"segmentation\"") } == true
        }
        if (samJson != null) return DetectedFormat.SAM

        return DetectedFormat.NONE
    }
}
