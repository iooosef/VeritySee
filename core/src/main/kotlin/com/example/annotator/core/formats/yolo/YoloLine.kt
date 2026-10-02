package com.example.annotator.core.formats.yolo

import com.example.annotator.core.geometry.Point

/** One parsed line from a YOLO label `.txt` file, values still normalized `[0,1]`. */
sealed interface YoloLine {
    val classId: Int

    data class Detect(override val classId: Int, val cx: Double, val cy: Double, val w: Double, val h: Double) : YoloLine
    data class Segment(override val classId: Int, val points: List<Point>) : YoloLine
}

object YoloLineCodec {

    /** Returns null and appends a warning if the line is malformed (FORMATS 3.1). */
    fun parse(line: String, lineNumber: Int, warnings: MutableList<String>): YoloLine? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val tokens = trimmed.split(Regex("\\s+"))
        val values = tokens.drop(1).mapNotNull { it.toDoubleOrNull() }
        if (values.size != tokens.size - 1) {
            warnings.add("line $lineNumber: malformed, skipped")
            return null
        }
        val classId = tokens[0].toIntOrNull()
        if (classId == null) {
            warnings.add("line $lineNumber: malformed, skipped")
            return null
        }

        return when {
            values.size == 4 -> {
                val (cx, cy, w, h) = clampAll(values, lineNumber, warnings)
                YoloLine.Detect(classId, cx, cy, w, h)
            }
            values.size >= 6 && values.size % 2 == 0 -> {
                val clamped = clampAll(values, lineNumber, warnings)
                val points = clamped.chunked(2).map { (x, y) -> Point(x, y) }
                YoloLine.Segment(classId, points)
            }
            else -> {
                warnings.add("line $lineNumber: malformed, skipped")
                null
            }
        }
    }

    fun write(line: YoloLine): String {
        return when (line) {
            is YoloLine.Detect -> {
                val v = listOf(line.cx, line.cy, line.w, line.h).joinToString(" ") { fmt(it) }
                "${line.classId} $v"
            }
            is YoloLine.Segment -> {
                val v = line.points.joinToString(" ") { "${fmt(it.x)} ${fmt(it.y)}" }
                "${line.classId} $v"
            }
        }
    }

    private fun fmt(v: Double): String = String.format(java.util.Locale.ROOT, "%.6f", v)

    private fun clampAll(values: List<Double>, lineNumber: Int, warnings: MutableList<String>): List<Double> {
        var clampedAny = false
        val result = values.map {
            val c = it.coerceIn(0.0, 1.0)
            if (c != it) clampedAny = true
            c
        }
        if (clampedAny) warnings.add("line $lineNumber: value(s) outside [0,1], clamped")
        return result
    }
}
