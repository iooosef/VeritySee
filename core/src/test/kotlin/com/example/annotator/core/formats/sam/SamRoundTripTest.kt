package com.example.annotator.core.formats.sam

import com.example.annotator.core.codec.RleCodec
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.geometry.GeometryTestUtils
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SamRoundTripTest {

    private val handWrittenJson = """
        {
          "image": {"image_id": 42, "width": 10, "height": 10, "file_name": "foo.jpg"},
          "annotations": [{
            "id": 7,
            "segmentation": {"size": [10, 10], "counts": "0:j2"},
            "bbox": [0, 0, 1, 10],
            "area": 10,
            "predicted_iou": 0.97,
            "stability_score": 0.95,
            "crop_box": [0, 0, 10, 10],
            "point_coords": [[0.5, 5.0]]
          }]
        }
    """.trimIndent()

    @Test
    fun `parses a hand written annotation and keeps unknown fields in extra`() {
        val result = SamImporter.importFile(handWrittenJson)
        val annotation = result.images.single().annotations.single()
        assertTrue(annotation.shape is Shape.Mask)
        assertEquals(0.97, (annotation.extra["predicted_iou"] as JsonPrimitive).content.toDouble())
        assertTrue(annotation.extra.containsKey("point_coords"))
        assertEquals("object", result.classes.single().name)
    }

    @Test
    fun `mask export then import round trips exactly`() {
        val size = ImageSize(80, 80)
        val rle = RleCodec.encode(GeometryTestUtils.circle(80, 40.0, 40.0, 30.0))
        val annotation = Annotation("a1", 0, Shape.Mask(rle), Source.IMPORTED_SAM)
        val image = DatasetImage("foo.jpg", size, listOf(annotation))
        val classes = listOf(ClassDef(0, "object", 0xFF112233.toInt()))

        val exported = SamExporter.exportFile(image, imageId = 1, classes = classes)
        val reimported = SamImporter.importFile(exported.json)

        val roundTripped = reimported.images.single().annotations.single().shape as Shape.Mask
        assertEquals(rle, roundTripped.rle)
    }

    @Test
    fun `box shape exports as a filled rectangular mask`() {
        val size = ImageSize(20, 20)
        val annotation = Annotation("a1", 0, Shape.Box(2.0, 2.0, 5.0, 5.0), Source.USER)
        val image = DatasetImage("foo.jpg", size, listOf(annotation))
        val classes = listOf(ClassDef(0, "object", 0xFF112233.toInt()))

        val exported = SamExporter.exportFile(image, imageId = 1, classes = classes)
        val reimported = SamImporter.importFile(exported.json)
        val mask = reimported.images.single().annotations.single().shape as Shape.Mask
        assertEquals(25.0, mask.rle.area().toDouble(), 0.001)
    }

    @Test
    fun `editing a SAM imported annotation drops predicted iou and stability score on export`() {
        val size = ImageSize(10, 10)
        val imported = SamImporter.importFile(handWrittenJson)
        val original = imported.images.single().annotations.single()
        // Simulate an edit: same shape, but now authored by the user (no longer trustworthy SAM metadata).
        val edited = original.copy(source = Source.USER)
        val image = DatasetImage("foo.jpg", size, listOf(edited))
        val classes = listOf(ClassDef(0, "object", 0xFF112233.toInt()))

        val exported = SamExporter.exportFile(image, imageId = 1, classes = classes)
        assertFalse(exported.json.contains("predicted_iou"))
        assertFalse(exported.json.contains("stability_score"))
        assertTrue(exported.json.contains("crop_box"))
    }

    @Test
    fun `unedited SAM annotation keeps predicted iou on export`() {
        val size = ImageSize(10, 10)
        val imported = SamImporter.importFile(handWrittenJson)
        val original = imported.images.single().annotations.single()
        val image = DatasetImage("foo.jpg", size, listOf(original))
        val classes = listOf(ClassDef(0, "object", 0xFF112233.toInt()))

        val exported = SamExporter.exportFile(image, imageId = 1, classes = classes)
        assertTrue(exported.json.contains("predicted_iou"))
    }
}
