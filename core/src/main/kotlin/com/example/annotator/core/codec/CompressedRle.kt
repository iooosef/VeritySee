package com.example.annotator.core.codec

/**
 * COCO "compressed RLE" string encoding of [Rle.counts]. See FORMATS.md section 1.1.
 * Delta encoding applies only to indices strictly greater than 2, matching pycocotools.
 */
object CompressedRle {

    fun encode(counts: IntArray): String {
        val out = StringBuilder()
        for (i in counts.indices) {
            var x: Long = counts[i].toLong()
            if (i > 2) x -= counts[i - 2].toLong()
            var more = true
            while (more) {
                var c = (x and 0x1fL).toInt()
                x = x shr 5
                more = if (c and 0x10 != 0) x != -1L else x != 0L
                if (more) c = c or 0x20
                out.append((c + 48).toChar())
            }
        }
        return out.toString()
    }

    fun decode(s: String): IntArray {
        val counts = mutableListOf<Int>()
        var p = 0
        var m = 0
        while (p < s.length) {
            var x = 0L
            var k = 0
            var more = true
            while (more) {
                val c = s[p].code - 48
                x = x or ((c and 0x1f).toLong() shl (5 * k))
                more = (c and 0x20) != 0
                p++
                k++
                if (!more && (c and 0x10) != 0) {
                    x = x or (-1L shl (5 * k))
                }
            }
            if (m > 2) x += counts[m - 2]
            counts.add(x.toInt())
            m++
        }
        return counts.toIntArray()
    }
}
