package com.example.annotator.core.formats.yolo

import com.example.annotator.core.codec.RleCodec
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.formats.bbox
import com.example.annotator.core.geometry.GeometryTestUtils
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoloRoundTripTest {

    @Test
    fun `detect import then export then import gives the same boxes`() {
        val textFiles = mapOf(
            "data.yaml" to "names:\n  0: person\n  1: forklift\n",
            "labels/train/foo.txt" to "0 0.5 0.5 0.2 0.4\n",
            "labels/train/bar.txt" to "",
        )
        val imagePaths = listOf("images/train/foo.jpg", "images/train/bar.jpg")
        val sizeOf = { _: String -> ImageSize(100, 100) }

        val imported = YoloImporter.import(imagePaths, textFiles, sizeOf)
        assertEquals(2, imported.classes.size)
        assertEquals(1, imported.images.first { it.path.endsWith("foo.jpg") }.annotations.size)
        assertTrue(imported.images.first { it.path.endsWith("bar.jpg") }.annotations.isEmpty())

        val exported = YoloExporter.export(
            imported.images,
            imported.classes,
            segment = false,
            splitOf = { "train" },
        )
        val reimported = YoloImporter.import(
            imagePaths,
            mapOf("data.yaml" to exported.dataYaml) + exported.labelFiles,
            sizeOf,
        )

        val original = imported.images.first { it.path.endsWith("foo.jpg") }.annotations.single()
        val roundTripped = reimported.images.first { it.path.endsWith("foo.jpg") }.annotations.single()
        val originalBox = original.shape.bbox()
        val roundTrippedBox = roundTripped.shape.bbox()
        for (i in 0..3) {
            assertEquals(originalBox[i], roundTrippedBox[i], 0.01)
        }
    }

    @Test
    fun `segment import then export then import preserves the mask within IoU tolerance`() {
        val size = ImageSize(80, 80)
        val rle = RleCodec.encode(GeometryTestUtils.circle(80, 40.0, 40.0, 30.0))
        val annotation = Annotation(
            id = "a1",
            classId = 0,
            shape = Shape.Mask(rle),
            source = Source.USER,
        )
        val images = listOf(DatasetImage("images/foo.jpg", size, listOf(annotation)))
        val classes = listOf(com.example.annotator.core.model.ClassDef(0, "object", 0xFF00FF00.toInt()))

        val exported = YoloExporter.export(images, classes, segment = true, simplifyTolerance = 1.0)
        val reimported = YoloImporter.import(
            listOf("images/foo.jpg"),
            mapOf("classes.txt" to "object") + exported.labelFiles,
            { size },
        )

        val roundTripped = reimported.images.single().annotations.single().shape as Shape.Mask
        val originalGrid = RleCodec.decode(rle)
        val roundTrippedGrid = RleCodec.decode(roundTripped.rle)
        val iou = GeometryTestUtils.iou(originalGrid, roundTrippedGrid)
        assertTrue(iou >= 0.97, "IoU $iou below 0.97")
    }

    @Test
    fun `unknown class index creates a placeholder with a warning`() {
        val textFiles = mapOf(
            "classes.txt" to "person\n",
            "labels/foo.txt" to "2 0.5 0.5 0.2 0.2\n",
        )
        val imported = YoloImporter.import(listOf("images/foo.jpg"), textFiles) { ImageSize(10, 10) }
        assertEquals(listOf("person", "class_1", "class_2"), imported.classes.map { it.name })
        assertTrue(imported.warnings.any { it.contains("placeholder") })
    }
}
