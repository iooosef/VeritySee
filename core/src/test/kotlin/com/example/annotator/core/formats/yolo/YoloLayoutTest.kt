package com.example.annotator.core.formats.yolo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YoloLayoutTest {

    @Test
    fun `layout A flat`() {
        assertEquals("labels/foo.txt", YoloLayout.labelPathFor("images/foo.jpg"))
    }

    @Test
    fun `layout B split`() {
        assertEquals("labels/train/foo.txt", YoloLayout.labelPathFor("images/train/foo.jpg"))
    }

    @Test
    fun `layout C roboflow`() {
        assertEquals("train/labels/foo.txt", YoloLayout.labelPathFor("train/images/foo.jpg"))
    }

    @Test
    fun `no images segment returns null`() {
        assertEquals(null, YoloLayout.labelPathFor("foo.jpg"))
    }
}
