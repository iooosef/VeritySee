package com.example.annotator.core.formats.sam

import com.example.annotator.core.codec.CompressedRle
import com.example.annotator.core.codec.Rle
import com.example.annotator.core.formats.ColorPalette
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.formats.ImportResult
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

private val KNOWN_KEYS = setOf("id", "segmentation", "bbox", "area", "category_id", "category_name")
private const val DEFAULT_CLASS_NAME = "object"

object SamImporter {

    private val json = Json { ignoreUnknownKeys = true }

    /** Imports one SA-1B style per-image JSON file (FORMATS 5). */
    fun importFile(text: String): ImportResult {
        val root = json.parseToJsonElement(text).jsonObject
        val imageObj = root.getValue("image").jsonObject
        val width = imageObj.getValue("width").jsonPrimitive.int
        val height = imageObj.getValue("height").jsonPrimitive.int
        val fileName = imageObj.getValue("file_name").jsonPrimitive.content

        val classIds = linkedMapOf<String, Int>() // name -> allocated id
        var nextClassId = 0

        val annotationsJson = root["annotations"]?.jsonArrayOrEmpty().orEmpty()
        val annotations = annotationsJson.map { element ->
            val obj = element.jsonObject
            val rle = parseRle(obj.getValue("segmentation").jsonObject)

            val categoryId = obj["category_id"]?.jsonPrimitive?.int
            val categoryName = obj["category_name"]?.jsonPrimitive?.content
            val className = categoryName ?: DEFAULT_CLASS_NAME
            val classId = categoryId ?: classIds.getOrPut(className) { nextClassId++ }

            val extra = obj.filterKeys { it !in KNOWN_KEYS }

            Annotation(
                id = UUID.randomUUID().toString(),
                classId = classId,
                shape = Shape.Mask(rle),
                source = Source.IMPORTED_SAM,
                extra = extra,
            )
        }

        val usedClassIds = annotations.map { it.classId }.toSortedSet()
        val classes = usedClassIds.map { id ->
            val name = classIds.entries.firstOrNull { it.value == id }?.key ?: DEFAULT_CLASS_NAME
            ClassDef(id, name, ColorPalette.colorFor(id))
        }.ifEmpty { listOf(ClassDef(0, DEFAULT_CLASS_NAME, ColorPalette.colorFor(0))) }

        val image = DatasetImage(fileName, ImageSize(width, height), annotations)
        return ImportResult(listOf(image), classes, emptyList())
    }

    /** Tallies resolved class ids per file without decoding segmentation RLE. */
    fun countClassIds(text: String): Map<Int, Int> {
        val root = json.parseToJsonElement(text).jsonObject
        val classIds = linkedMapOf<String, Int>()
        var nextClassId = 0
        val counts = mutableMapOf<Int, Int>()

        val annotationsJson = root["annotations"]?.jsonArrayOrEmpty().orEmpty()
        for (element in annotationsJson) {
            val obj = element.jsonObject
            val categoryId = obj["category_id"]?.jsonPrimitive?.int
            val categoryName = obj["category_name"]?.jsonPrimitive?.content
            val className = categoryName ?: DEFAULT_CLASS_NAME
            val classId = categoryId ?: classIds.getOrPut(className) { nextClassId++ }
            counts[classId] = (counts[classId] ?: 0) + 1
        }
        return counts
    }

    private fun parseRle(segmentation: JsonObject): Rle {
        val size = segmentation.getValue("size").jsonArrayOrEmpty().map { it.jsonPrimitive.int }
        val counts = CompressedRle.decode(segmentation.getValue("counts").jsonPrimitive.content)
        return Rle(size[0], size[1], counts)
    }
}

private fun JsonElement.jsonArrayOrEmpty() = (this as? kotlinx.serialization.json.JsonArray) ?: kotlinx.serialization.json.JsonArray(emptyList())
