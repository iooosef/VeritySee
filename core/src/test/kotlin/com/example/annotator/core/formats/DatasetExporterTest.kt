package com.example.annotator.core.formats

import com.example.annotator.core.formats.coco.CocoImporter
import com.example.annotator.core.formats.yolo.YoloImporter
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatasetExporterTest {

    private val classes = listOf(ClassDef(0, "person", 0xFF112233.toInt()))

    private fun image(path: String) = DatasetImage(
        path,
        ImageSize(100, 100),
        listOf(Annotation("a-$path", 0, Shape.Box(10.0, 10.0, 20.0, 20.0), Source.USER)),
    )

    @Test
    fun `YOLO detect export without a split writes a flat data yaml and label files`() {
        val images = listOf(image("images/a.jpg"), image("images/b.jpg"))
        val result = DatasetExporter.export(images, classes, ExportOptions(format = ExportFormat.YOLO_DETECT))

        assertTrue(result.files.containsKey("data.yaml"))
        assertTrue(result.files.containsKey("labels/a.txt"))
        assertTrue(result.files.containsKey("labels/b.txt"))

        val reimported = YoloImporter.import(
            listOf("images/a.jpg"),
            mapOf("data.yaml" to result.files.getValue("data.yaml")) + result.files.filterKeys { it.startsWith("labels/") },
        ) { ImageSize(100, 100) }
        assertEquals(1, reimported.images.single().annotations.size)
    }

    @Test
    fun `YOLO export with a split places labels under train val test`() {
        val images = (1..10).map { image("images/img$it.jpg") }
        val options = ExportOptions(format = ExportFormat.YOLO_DETECT, split = SplitConfig(0.7, 0.2, seed = 42))
        val result = DatasetExporter.export(images, classes, options)

        val trainFiles = result.files.keys.filter { it.startsWith("labels/train/") }
        val valFiles = result.files.keys.filter { it.startsWith("labels/val/") }
        val testFiles = result.files.keys.filter { it.startsWith("labels/test/") }
        assertEquals(10, trainFiles.size + valFiles.size + testFiles.size)
        assertEquals(7, trainFiles.size)
        assertEquals(2, valFiles.size)
        assertEquals(1, testFiles.size)
    }

    @Test
    fun `COCO export without a split writes a single instances default json`() {
        val images = listOf(image("images/a.jpg"))
        val result = DatasetExporter.export(images, classes, ExportOptions(format = ExportFormat.COCO))

        assertEquals(setOf("annotations/instances_default.json"), result.files.keys)
        val reimported = CocoImporter.importFile(result.files.getValue("annotations/instances_default.json"))
        assertEquals(1, reimported.images.single().annotations.size)
    }

    @Test
    fun `COCO export with a split writes one instances file per split`() {
        val images = (1..10).map { image("images/img$it.jpg") }
        val options = ExportOptions(format = ExportFormat.COCO, split = SplitConfig(0.7, 0.2, seed = 1))
        val result = DatasetExporter.export(images, classes, options)

        assertEquals(setOf("annotations/instances_train.json", "annotations/instances_val.json", "annotations/instances_test.json"), result.files.keys)
    }

    @Test
    fun `SAM export writes one json per image`() {
        val images = listOf(image("images/a.jpg"), image("images/b.jpg"))
        val result = DatasetExporter.export(images, classes, ExportOptions(format = ExportFormat.SAM))

        assertEquals(setOf("a.json", "b.json"), result.files.keys)
    }
}
