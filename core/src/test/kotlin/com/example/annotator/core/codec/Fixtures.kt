package com.example.annotator.core.codec

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RleCase(
    val name: String,
    val note: String,
    val height: Int,
    val width: Int,
    val rows: List<String>,
    val rleCounts: List<Int>,
    val cocoCompressedCounts: String,
    val roboflowMask: String,
    val area: Int,
    val bboxXYWH: List<Double>,
    val bboxCenter: List<Double>,
)

@Serializable
data class RleCasesFile(val cases: List<RleCase>)

@Serializable
data class RoboflowInput(
    val id: String,
    val type: String,
    val label: String,
    val x: String,
    val y: String,
    val width: String,
    val height: String,
    val area: String,
    val mask: String,
)

@Serializable
data class RoboflowExpected(
    val imageWidth: Int,
    val imageHeight: Int,
    val cocoCompressedCounts: String,
    val runCount: Int,
    val runSum: Long,
    val firstRuns: List<Int>,
    val area: Int,
    val bboxXYWH: List<Double>,
)

@Serializable
data class RoboflowSampleFile(
    val note: String,
    val input: RoboflowInput,
    val expected: RoboflowExpected,
)

object Fixtures {
    private val json = Json { ignoreUnknownKeys = true }

    fun rleCases(): List<RleCase> {
        val text = requireResource("fixtures/rle_cases.json")
        return json.decodeFromString<RleCasesFile>(text).cases
    }

    fun roboflowSample(): RoboflowSampleFile {
        val text = requireResource("fixtures/roboflow_sample.json")
        return json.decodeFromString(text)
    }

    private fun requireResource(path: String): String {
        val stream = Fixtures::class.java.classLoader.getResourceAsStream(path)
            ?: error("missing test resource $path")
        return stream.bufferedReader().readText()
    }
}
