package com.example.annotator.core.formats

import com.example.annotator.core.formats.coco.CocoMaskOption

enum class ExportFormat { YOLO_DETECT, YOLO_SEGMENT, COCO, SAM }

enum class ExportScope { ALL, REVIEWED_ONLY }

/** Train/val fractions; test is whatever remains (SPEC section 4.6). */
data class SplitConfig(val trainFraction: Double, val valFraction: Double, val seed: Long)

data class ExportOptions(
    val format: ExportFormat,
    val scope: ExportScope = ExportScope.ALL,
    val split: SplitConfig? = null,
    val cocoMaskOption: CocoMaskOption = CocoMaskOption.POLYGONS,
    val yoloSimplifyTolerance: Double = 1.0,
)
