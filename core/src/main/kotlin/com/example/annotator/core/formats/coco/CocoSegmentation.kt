package com.example.annotator.core.formats.coco

import com.example.annotator.core.codec.CompressedRle
import com.example.annotator.core.codec.Rle
import com.example.annotator.core.formats.MaskPolygons
import com.example.annotator.core.geometry.Point
import com.example.annotator.core.geometry.Polygon
import com.example.annotator.core.model.Shape
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Parses and writes the COCO `segmentation` field (FORMATS 4.1): polygons or RLE (compressed or not). */
object CocoSegmentation {

    /** Null means "missing or empty": caller should fall back to a box annotation from `bbox`. */
    fun parse(segmentation: JsonElement?, width: Int, height: Int): Shape.Mask? {
        when (segmentation) {
            null -> return null
            is JsonObject -> {
                val size = segmentation["size"]!!.jsonArray.map { it.jsonPrimitive.int }
                val h = size[0]
                val w = size[1]
                val countsElement = segmentation["counts"]!!
                val counts = if (countsElement is JsonPrimitive && countsElement.isString) {
                    CompressedRle.decode(countsElement.content)
                } else {
                    countsElement.jsonArray.map { it.jsonPrimitive.int }.toIntArray()
                }
                return Shape.Mask(Rle(h, w, counts))
            }
            is JsonArray -> {
                if (segmentation.isEmpty()) return null
                val polygons = segmentation.map { polyElement ->
                    val flat = polyElement.jsonArray.map { it.jsonPrimitive.double }
                    flat.chunked(2).map { (x, y) -> Point(x, y) }
                }
                val rle = MaskPolygons.polygonsToMask(polygons, width, height)
                return Shape.Mask(rle)
            }
            else -> return null
        }
    }

    fun writePolygons(polygons: List<Polygon>): JsonElement {
        return JsonArray(
            polygons.map { polygon ->
                JsonArray(polygon.flatMap { listOf(JsonPrimitive(it.x), JsonPrimitive(it.y)) })
            },
        )
    }

    fun writeCompressedRle(rle: Rle): JsonElement {
        return JsonObject(
            mapOf(
                "size" to JsonArray(listOf(JsonPrimitive(rle.height), JsonPrimitive(rle.width))),
                "counts" to JsonPrimitive(CompressedRle.encode(rle.counts)),
            ),
        )
    }
}
