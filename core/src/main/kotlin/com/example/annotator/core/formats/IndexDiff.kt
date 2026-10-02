package com.example.annotator.core.formats

import com.example.annotator.core.model.IndexEntry

data class IndexDiffResult(val added: List<String>, val removed: List<String>, val changed: List<String>, val unchanged: List<String>)

/** Pure comparison of a cached index against a fresh folder listing (SPEC section 7). */
object IndexDiff {

    fun diff(cached: List<IndexEntry>, current: List<IndexEntry>): IndexDiffResult {
        val cachedByPath = cached.associateBy { it.path }
        val currentByPath = current.associateBy { it.path }

        val added = mutableListOf<String>()
        val changed = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        for ((path, entry) in currentByPath) {
            val old = cachedByPath[path]
            when {
                old == null -> added.add(path)
                old.size != entry.size || old.lastModified != entry.lastModified -> changed.add(path)
                else -> unchanged.add(path)
            }
        }
        val removed = (cachedByPath.keys - currentByPath.keys).toList()
        return IndexDiffResult(added, removed, changed, unchanged)
    }
}
