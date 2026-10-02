package com.example.annotator.core.formats.coco

import com.example.annotator.core.codec.RleCodec
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.geometry.GeometryTestUtils
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CocoRoundTripTest {

    private val handWrittenJson = """
        {
          "images": [{"id": 1, "file_name": "images/foo.jpg", "width": 100, "height": 100}],
          "categories": [{"id": 1, "name": "person", "supercategory": "none"}],
          "annotations": [
            {
              "id": 1, "image_id": 1, "category_id": 1,
              "bbox": [10, 20, 30, 40], "area": 1200, "iscrowd": 0,
              "segmentation": []
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses a hand written box annotation (empty segmentation falls back to bbox)`() {
        val result = CocoImporter.importFile(handWrittenJson)
        assertEquals(1, result.images.size)
        val annotation = result.images.single().annotations.single()
        val box = annotation.shape as Shape.Box
        assertEquals(10.0, box.x)
        assertEquals(20.0, box.y)
        assertEquals(30.0, box.w)
        assertEquals(40.0, box.h)
        assertEquals("person", result.classes.single().name)
    }

    @Test
    fun `box export then import round trips exactly`() {
        val image = DatasetImage(
            "images/foo.jpg",
            ImageSize(100, 100),
            listOf(Annotation("a1", 0, Shape.Box(10.0, 20.0, 30.0, 40.0), Source.USER)),
        )
        val classes = listOf(ClassDef(0, "person", 0xFF112233.toInt()))

        val exported = CocoExporter.export(listOf(image), classes)
        val reimported = CocoImporter.importFile(exported.json)

        val box = reimported.images.single().annotations.single().shape as Shape.Box
        assertEquals(10.0, box.x, 1e-6)
        assertEquals(20.0, box.y, 1e-6)
        assertEquals(30.0, box.w, 1e-6)
        assertEquals(40.0, box.h, 1e-6)
    }

    @Test
    fun `mask export as polygons then import preserves shape within IoU tolerance`() {
        val size = ImageSize(80, 80)
        val rle = RleCodec.encode(GeometryTestUtils.circle(80, 40.0, 40.0, 30.0))
        val image = DatasetImage("images/foo.jpg", size, listOf(Annotation("a1", 0, Shape.Mask(rle), Source.USER)))
        val classes = listOf(ClassDef(0, "object", 0xFF112233.toInt()))

        val exported = CocoExporter.export(listOf(image), classes, maskOption = CocoMaskOption.POLYGONS)
        val reimported = CocoImporter.importFile(exported.json)
        val roundTripped = reimported.images.single().annotations.single().shape as Shape.Mask

        val iou = GeometryTestUtils.iou(RleCodec.decode(rle), RleCodec.decode(roundTripped.rle))
        assertTrue(iou >= 0.97, "IoU $iou below 0.97")
    }

    @Test
    fun `mask export as compressed RLE then import round trips exactly`() {
        val size = ImageSize(80, 80)
        val rle = RleCodec.encode(GeometryTestUtils.circle(80, 40.0, 40.0, 30.0))
        val image = DatasetImage("images/foo.jpg", size, listOf(Annotation("a1", 0, Shape.Mask(rle), Source.USER)))
        val classes = listOf(ClassDef(0, "object", 0xFF112233.toInt()))

        val exported = CocoExporter.export(listOf(image), classes, maskOption = CocoMaskOption.COMPRESSED_RLE)
        val reimported = CocoImporter.importFile(exported.json)
        val roundTripped = reimported.images.single().annotations.single().shape as Shape.Mask

        assertEquals(rle, roundTripped.rle)
    }

    @Test
    fun `roboflow supercategory only category is skipped`() {
        val jsonText = """
            {
              "images": [{"id": 1, "file_name": "foo.jpg", "width": 10, "height": 10}],
              "categories": [
                {"id": 0, "name": "dataset", "supercategory": "none"},
                {"id": 1, "name": "person", "supercategory": "dataset"}
              ],
              "annotations": [
                {"id": 1, "image_id": 1, "category_id": 1, "bbox": [1,1,2,2], "area": 4, "iscrowd": 0, "segmentation": []}
              ]
            }
        """.trimIndent()
        val result = CocoImporter.importFile(jsonText)
        assertEquals(listOf("person"), result.classes.map { it.name })
        assertTrue(result.warnings.any { it.contains("Roboflow") })
    }

    @Test
    fun `unknown category id creates a placeholder class`() {
        val jsonText = """
            {
              "images": [{"id": 1, "file_name": "foo.jpg", "width": 10, "height": 10}],
              "categories": [],
              "annotations": [
                {"id": 1, "image_id": 1, "category_id": 5, "bbox": [1,1,2,2], "area": 4, "iscrowd": 0, "segmentation": []}
              ]
            }
        """.trimIndent()
        val result = CocoImporter.importFile(jsonText)
        assertEquals(listOf("class_5"), result.classes.map { it.name })
    }
}
