package com.example.annotator.core.formats

import kotlin.random.Random

data class SplitResult(val train: List<String>, val val_: List<String>, val test: List<String>)

/** Seeded train/val/test split over a list of image paths. */
object DatasetSplit {

    fun split(paths: List<String>, trainFraction: Double, valFraction: Double, seed: Long): SplitResult {
        require(trainFraction + valFraction <= 1.0) { "train + val fraction must not exceed 1.0" }
        val shuffled = paths.shuffled(Random(seed))
        val trainCount = (shuffled.size * trainFraction).toInt()
        val valCount = (shuffled.size * valFraction).toInt()
        val train = shuffled.subList(0, trainCount)
        val valSplit = shuffled.subList(trainCount, trainCount + valCount)
        val test = shuffled.subList(trainCount + valCount, shuffled.size)
        return SplitResult(train, valSplit, test)
    }
}
