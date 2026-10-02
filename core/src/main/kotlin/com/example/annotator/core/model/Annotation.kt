package com.example.annotator.core.model

import kotlinx.serialization.json.JsonElement

data class Annotation(
    val id: String,
    val classId: Int,
    val shape: Shape,
    val source: Source,
    val extra: Map<String, JsonElement> = emptyMap(),
)
