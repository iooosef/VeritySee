package com.example.annotator.core.formats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatDetectorTest {

    @Test
    fun `detects YOLO layout A`() {
        val paths = listOf("images/foo.jpg", "labels/foo.txt", "data.yaml")
        assertEquals(DetectedFormat.YOLO, FormatDetector.detect(paths) { null })
    }

    @Test
    fun `detects YOLO layout C roboflow`() {
        val paths = listOf("train/images/foo.jpg", "train/labels/foo.txt", "data.yaml")
        assertEquals(DetectedFormat.YOLO, FormatDetector.detect(paths) { null })
    }

    @Test
    fun `detects COCO instances layout`() {
        val paths = listOf("annotations/instances_train.json", "images/train/foo.jpg")
        assertEquals(DetectedFormat.COCO, FormatDetector.detect(paths) { null })
    }

    @Test
    fun `detects COCO roboflow layout`() {
        val paths = listOf("train/_annotations.coco.json", "train/foo.jpg")
        assertEquals(DetectedFormat.COCO, FormatDetector.detect(paths) { null })
    }

    @Test
    fun `detects a single root COCO json by content`() {
        val paths = listOf("annotations.json", "foo.jpg")
        val content = mapOf("annotations.json" to """{"images":[],"annotations":[]}""")
        assertEquals(DetectedFormat.COCO, FormatDetector.detect(paths) { content[it] })
    }

    @Test
    fun `detects SAM by per-image json content`() {
        val paths = listOf("foo.json", "foo.jpg")
        val content = mapOf("foo.json" to """{"image":{"image_id":1},"annotations":[{"segmentation":{}}]}""")
        assertEquals(DetectedFormat.SAM, FormatDetector.detect(paths) { content[it] })
    }

    @Test
    fun `no recognizable format returns NONE`() {
        val paths = listOf("foo.jpg", "bar.jpg")
        assertEquals(DetectedFormat.NONE, FormatDetector.detect(paths) { null })
    }
}
