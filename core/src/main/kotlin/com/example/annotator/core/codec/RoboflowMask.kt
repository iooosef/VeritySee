package com.example.annotator.core.codec

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Roboflow mask string: `base64(zlib_compress(compressedCountsString))`.
 * See FORMATS.md section 1.2.
 */
object RoboflowMask {

    fun encode(counts: IntArray): String {
        val compressed = CompressedRle.encode(counts).toByteArray(Charsets.US_ASCII)
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        deflater.setInput(compressed)
        deflater.finish()
        val out = ByteArrayOutputStream(compressed.size)
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            out.write(buffer, 0, n)
        }
        deflater.end()
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    fun decode(mask: String): IntArray {
        val compressedBytes = Base64.getDecoder().decode(mask)
        val inflater = Inflater()
        inflater.setInput(compressedBytes)
        val out = ByteArrayOutputStream(compressedBytes.size * 3)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && inflater.needsInput()) break
            out.write(buffer, 0, n)
        }
        inflater.end()
        val compressedString = out.toByteArray().toString(Charsets.US_ASCII)
        return CompressedRle.decode(compressedString)
    }
}
