package com.example.annotator.core.formats.coco

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CocoClassCountTest {

    @Test
    fun `counts category ids directly without decoding segmentation`() {
        val jsonText = """
            {
              "images": [{"id": 1, "file_name": "foo.jpg", "width": 10, "height": 10}],
              "categories": [{"id": 1, "name": "person"}, {"id": 2, "name": "car"}],
              "annotations": [
                {"id": 1, "image_id": 1, "category_id": 1, "bbox": [1,1,2,2], "area": 4, "iscrowd": 0, "segmentation": []},
                {"id": 2, "image_id": 1, "category_id": 1, "bbox": [1,1,2,2], "area": 4, "iscrowd": 0, "segmentation": []},
                {"id": 3, "image_id": 1, "category_id": 2, "bbox": [1,1,2,2], "area": 4, "iscrowd": 0, "segmentation": []}
              ]
            }
        """.trimIndent()
        val counts = CocoImporter.countClassIds(jsonText)
        assertEquals(2, counts[1])
        assertEquals(1, counts[2])
    }
}
