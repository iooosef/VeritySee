package com.example.annotator.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ScaffoldTest {

    @Test
    fun `core module loads`() {
        assertEquals(1, CORE_MODULE_VERSION)
    }

    @Test
    fun `fixtures are present as test resources`() {
        val rleCases = File(javaClass.classLoader.getResource("fixtures/rle_cases.json")!!.toURI())
        val roboflow = File(javaClass.classLoader.getResource("fixtures/roboflow_sample.json")!!.toURI())
        assertTrue(rleCases.exists())
        assertTrue(roboflow.exists())
    }
}
