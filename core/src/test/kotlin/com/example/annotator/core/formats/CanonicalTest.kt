package com.example.annotator.core.formats

import com.example.annotator.core.codec.RleCodec
import com.example.annotator.core.geometry.GeometryTestUtils
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CanonicalTest {

    @Test
    fun `write then read round trips a box annotation exactly`() {
        val image = DatasetImage(
            "train/images/foo.jpg",
            ImageSize(100, 100),
            listOf(Annotation("a1", 3, Shape.Box(10.0, 20.0, 30.0, 40.0), Source.USER)),
        )
        val text = Canonical.write(image)
        val roundTripped = Canonical.read(text)

        assertEquals(image.path, roundTripped.path)
        assertEquals(image.size, roundTripped.size)
        assertEquals(image.annotations, roundTripped.annotations)
    }

    @Test
    fun `write then read round trips a mask annotation exactly`() {
        val rle = RleCodec.encode(GeometryTestUtils.circle(40, 20.0, 20.0, 15.0))
        val image = DatasetImage(
            "foo.jpg",
            ImageSize(40, 40),
            listOf(Annotation("a1", 0, Shape.Mask(rle), Source.IMPORTED_SAM)),
        )
        val text = Canonical.write(image)
        val roundTripped = Canonical.read(text)

        assertEquals(image.annotations, roundTripped.annotations)
    }

    @Test
    fun `bbox and area in the file are recomputed, not trusted, on read`() {
        // Hand-edit a written file so its bbox field is wrong; the mask itself is still correct,
        // so a correct reader must recompute bbox from the mask rather than trusting the field.
        val rle = RleCodec.encode(GeometryTestUtils.circle(40, 20.0, 20.0, 15.0))
        val image = DatasetImage("foo.jpg", ImageSize(40, 40), listOf(Annotation("a1", 0, Shape.Mask(rle), Source.USER)))
        val text = Canonical.write(image)
        val corrupted = text.replace(Regex("\"bbox\":\\s*\\[[^]]*]"), "\"bbox\": [0, 0, 1, 1]")

        val roundTripped = Canonical.read(corrupted)
        val mask = roundTripped.annotations.single().shape as Shape.Mask
        assertEquals(rle, mask.rle)
    }
}
