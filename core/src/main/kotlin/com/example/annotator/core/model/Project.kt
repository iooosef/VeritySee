package com.example.annotator.core.model

import kotlinx.serialization.Serializable

/** `.annotator/project.json` (SPEC section 3): classes, detected source format, settings. */
@Serializable
data class Project(
    val version: Int = 1,
    val classes: List<ClassDef> = emptyList(),
    val detectedFormat: String = "NONE",
    val settings: Map<String, String> = emptyMap(),
)

/** One entry in `.annotator/index.json` (SPEC section 7): used to detect changed files on reopen. */
@Serializable
data class IndexEntry(val path: String, val size: Long, val lastModified: Long)

@Serializable
data class Index(val version: Int = 1, val entries: List<IndexEntry> = emptyList())
