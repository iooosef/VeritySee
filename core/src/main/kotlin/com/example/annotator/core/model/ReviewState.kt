package com.example.annotator.core.model

import kotlinx.serialization.Serializable

/** Per-image review status, part of `state.json` (FORMATS/SPEC section 3). */
@Serializable
data class ReviewState(
    val reviewed: Boolean = false,
    val reviewedAt: String? = null,
    val lastEditedAt: String? = null,
)

@Serializable
data class ProjectState(
    val version: Int = 1,
    val lastOpened: String? = null,
    val images: Map<String, ReviewState> = emptyMap(),
)
