package com.example.annotator.core.formats.yolo

/**
 * Minimal `data.yaml` support (FORMATS 3.2): only the `names:` key, as either a mapping
 * (`0: person`) or a list (`- person`), plus plain `classes.txt` (one name per line).
 */
object YoloClasses {

    /** Returns class names ordered by id, starting at 0. */
    fun parseDataYaml(text: String): List<String> {
        val lines = text.lines()
        val namesIndex = lines.indexOfFirst { it.trimStart().startsWith("names:") && !it.trim().startsWith("#") }
        require(namesIndex >= 0) { "data.yaml has no 'names:' key" }

        val inlineValue = lines[namesIndex].substringAfter("names:").trim()
        if (inlineValue.isNotEmpty() && inlineValue != "{}" && inlineValue != "[]") {
            return parseInlineNames(inlineValue)
        }

        val entries = sortedMapOf<Int, String>()
        var listIndex = 0
        var i = namesIndex + 1
        while (i < lines.size) {
            val raw = lines[i]
            if (raw.isBlank()) { i++; continue }
            val indent = raw.takeWhile { it == ' ' }.length
            if (indent == 0) break
            val content = raw.trim()

            val mapMatch = Regex("^(\\d+):\\s*(.+)$").matchEntire(content)
            val listMatch = Regex("^-\\s*(.+)$").matchEntire(content)
            when {
                mapMatch != null -> entries[mapMatch.groupValues[1].toInt()] = mapMatch.groupValues[2].trim()
                listMatch != null -> {
                    entries[listIndex] = listMatch.groupValues[1].trim()
                    listIndex++
                }
                else -> break
            }
            i++
        }
        return entries.entries.sortedBy { it.key }.map { it.value }
    }

    private fun parseInlineNames(value: String): List<String> {
        val inner = value.trim().removePrefix("[").removeSuffix("]").removePrefix("{").removeSuffix("}")
        return inner.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun parseClassesTxt(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun writeDataYaml(names: List<String>, trainPath: String, valPath: String, testPath: String?): String {
        val sb = StringBuilder()
        sb.appendLine("path: .")
        sb.appendLine("train: $trainPath")
        sb.appendLine("val: $valPath")
        if (testPath != null) sb.appendLine("test: $testPath")
        sb.appendLine("names:")
        for ((id, name) in names.withIndex()) {
            sb.appendLine("  $id: $name")
        }
        return sb.toString()
    }
}
