package com.example.annotator.core.codec

/** Boolean operations on [Rle] masks, implemented by decoding to grids. */
object RleOps {

    fun union(a: Rle, b: Rle): Rle {
        require(a.width == b.width && a.height == b.height) { "masks must share dimensions" }
        val ga = RleCodec.decode(a)
        val gb = RleCodec.decode(b)
        val result = BooleanGrid(a.width, a.height)
        for (col in 0 until a.width) {
            for (row in 0 until a.height) {
                result[col, row] = ga[col, row] || gb[col, row]
            }
        }
        return RleCodec.encode(result)
    }

    fun subtract(a: Rle, b: Rle): Rle {
        require(a.width == b.width && a.height == b.height) { "masks must share dimensions" }
        val ga = RleCodec.decode(a)
        val gb = RleCodec.decode(b)
        val result = BooleanGrid(a.width, a.height)
        for (col in 0 until a.width) {
            for (row in 0 until a.height) {
                result[col, row] = ga[col, row] && !gb[col, row]
            }
        }
        return RleCodec.encode(result)
    }

    fun isEmpty(rle: Rle): Boolean = rle.isEmpty()
}
