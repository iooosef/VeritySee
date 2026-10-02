package com.example.annotator.core.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProjectSerializationTest {

    private val json = Json

    @Test
    fun `project round trips through json`() {
        val project = Project(classes = listOf(ClassDef(0, "person", 0xFF112233.toInt())), detectedFormat = "COCO")
        val text = json.encodeToString(Project.serializer(), project)
        assertEquals(project, json.decodeFromString(Project.serializer(), text))
    }

    @Test
    fun `project state round trips through json`() {
        val state = ProjectState(
            lastOpened = "train/images/foo.jpg",
            images = mapOf("train/images/foo.jpg" to ReviewState(reviewed = true, reviewedAt = "2026-10-01T15:20:00+08:00")),
        )
        val text = json.encodeToString(ProjectState.serializer(), state)
        assertEquals(state, json.decodeFromString(ProjectState.serializer(), text))
    }

    @Test
    fun `index round trips through json`() {
        val index = Index(entries = listOf(IndexEntry("foo.jpg", 100, 200)))
        val text = json.encodeToString(Index.serializer(), index)
        assertEquals(index, json.decodeFromString(Index.serializer(), text))
    }
}
