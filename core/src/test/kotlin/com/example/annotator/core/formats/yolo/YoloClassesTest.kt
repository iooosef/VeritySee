package com.example.annotator.core.formats.yolo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YoloClassesTest {

    @Test
    fun `parses mapping style names`() {
        val yaml = """
            path: .
            train: images/train
            val: images/val
            names:
              0: person
              1: forklift
        """.trimIndent()
        assertEquals(listOf("person", "forklift"), YoloClasses.parseDataYaml(yaml))
    }

    @Test
    fun `parses list style names`() {
        val yaml = """
            path: .
            names:
              - person
              - forklift
        """.trimIndent()
        assertEquals(listOf("person", "forklift"), YoloClasses.parseDataYaml(yaml))
    }

    @Test
    fun `parses classes txt`() {
        val text = "person\nforklift\n"
        assertEquals(listOf("person", "forklift"), YoloClasses.parseClassesTxt(text))
    }
}
