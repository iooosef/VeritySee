package com.example.annotator.core.formats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatasetSplitTest {

    private val paths = (1..100).map { "img_$it.jpg" }

    @Test
    fun `splits into the requested proportions`() {
        val result = DatasetSplit.split(paths, trainFraction = 0.7, valFraction = 0.2, seed = 42)
        assertEquals(70, result.train.size)
        assertEquals(20, result.val_.size)
        assertEquals(10, result.test.size)
    }

    @Test
    fun `every path appears exactly once across the split`() {
        val result = DatasetSplit.split(paths, trainFraction = 0.7, valFraction = 0.2, seed = 42)
        val all = result.train + result.val_ + result.test
        assertEquals(paths.toSet(), all.toSet())
        assertEquals(paths.size, all.size)
    }

    @Test
    fun `same seed gives the same split`() {
        val a = DatasetSplit.split(paths, 0.7, 0.2, seed = 7)
        val b = DatasetSplit.split(paths, 0.7, 0.2, seed = 7)
        assertEquals(a.train, b.train)
        assertEquals(a.val_, b.val_)
        assertEquals(a.test, b.test)
    }

    @Test
    fun `different seeds give different orderings`() {
        val a = DatasetSplit.split(paths, 0.7, 0.2, seed = 1)
        val b = DatasetSplit.split(paths, 0.7, 0.2, seed = 2)
        assertTrue(a.train != b.train)
    }
}
