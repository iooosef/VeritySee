package com.example.annotator.core.formats

import com.example.annotator.core.codec.CompressedRle
import com.example.annotator.core.codec.Rle
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
private data class CanonicalImageDto(val path: String, val width: Int, val height: Int)

@Serializable
private data class CanonicalAnnotationDto(
    val id: String,
    val classId: Int,
    val type: String,
    val rle: String? = null,
    val bbox: List<Double>,
    val area: Double,
    val source: String,
    val extra: Map<String, JsonElement> = emptyMap(),
)

@Serializable
private data class CanonicalFileDto(
    val version: Int,
    val image: CanonicalImageDto,
    val annotations: List<CanonicalAnnotationDto>,
)

/** Reads and writes the app's per-image canonical annotation file (FORMATS section 6). */
object Canonical {

    private val json = Json { prettyPrint = true }

    fun write(image: DatasetImage): String {
        val dto = CanonicalFileDto(
            version = 1,
            image = CanonicalImageDto(image.path, image.size.width, image.size.height),
            annotations = image.annotations.map { toDto(it) },
        )
        return json.encodeToString(CanonicalFileDto.serializer(), dto)
    }

    fun read(text: String): DatasetImage {
        val dto = json.decodeFromString(CanonicalFileDto.serializer(), text)
        val size = ImageSize(dto.image.width, dto.image.height)
        val annotations = dto.annotations.map { fromDto(it, size) }
        return DatasetImage(dto.image.path, size, annotations)
    }

    private fun toDto(annotation: Annotation): CanonicalAnnotationDto {
        val shape = annotation.shape
        val bbox = shape.bbox().toList()
        val area = shape.area()
        return when (shape) {
            is Shape.Box -> CanonicalAnnotationDto(
                id = annotation.id,
                classId = annotation.classId,
                type = "box",
                rle = null,
                bbox = bbox,
                area = area,
                source = annotation.source.name,
                extra = annotation.extra,
            )
            is Shape.Mask -> CanonicalAnnotationDto(
                id = annotation.id,
                classId = annotation.classId,
                type = "mask",
                rle = CompressedRle.encode(shape.rle.counts),
                bbox = bbox,
                area = area,
                source = annotation.source.name,
                extra = annotation.extra,
            )
        }
    }

    private fun fromDto(dto: CanonicalAnnotationDto, size: ImageSize): Annotation {
        val shape: Shape = when (dto.type) {
            "mask" -> {
                val counts = CompressedRle.decode(requireNotNull(dto.rle) { "mask annotation ${dto.id} missing rle" })
                Shape.Mask(Rle(height = size.height, width = size.width, counts = counts))
            }
            "box" -> Shape.Box(dto.bbox[0], dto.bbox[1], dto.bbox[2], dto.bbox[3])
            else -> error("unknown annotation type '${dto.type}' for ${dto.id}")
        }
        return Annotation(dto.id, dto.classId, shape, Source.valueOf(dto.source), dto.extra)
    }
}
