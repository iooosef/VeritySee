package com.example.annotator.core.model

import kotlinx.serialization.Serializable

/** A class definition. [color] is packed ARGB. */
@Serializable
data class ClassDef(val id: Int, val name: String, val color: Int)
