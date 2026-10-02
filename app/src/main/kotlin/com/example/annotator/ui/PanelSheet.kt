package com.example.annotator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.Shape

/** The "Labels" panel content (SPEC 4.3): Classes and Annotations tabs. Hosted in a left drawer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelContent(
    dataset: DatasetImage?,
    classes: Map<Int, ClassDef>,
    hiddenClassIds: Set<Int>,
    hiddenAnnotationIds: Set<String>,
    selectedAnnotationId: String?,
    recentClassIds: List<Int>,
    onToggleClassVisibility: (Int) -> Unit,
    onToggleAnnotationVisibility: (String) -> Unit,
    onSelectAnnotation: (String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onChangeAnnotationClass: (annotationId: String, newClassId: Int) -> Unit,
    onCreateClass: (name: String, onCreated: (ClassDef) -> Unit) -> Unit,
    onManageClasses: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var classPickerForAnnotation by remember { mutableStateOf<String?>(null) }

    Column {
        Text("Labels", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        OutlinedButton(onClick = onManageClasses, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Manage classes")
        }

        val annotations = dataset?.annotations.orEmpty()

        TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 12.dp)) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Classes") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Annotations (${annotations.size})") })
        }
        when (tab) {
            0 -> {
                val counts = annotations.groupingBy { it.classId }.eachCount()
                LazyColumn {
                    items(classes.values.toList()) { classDef ->
                        ClassRow(
                            classDef = classDef,
                            count = counts[classDef.id] ?: 0,
                            hidden = classDef.id in hiddenClassIds,
                            onToggleVisibility = { onToggleClassVisibility(classDef.id) },
                        )
                    }
                }
            }
            1 -> {
                LazyColumn {
                    items(annotations) { annotation ->
                        AnnotationRow(
                            classDef = classes[annotation.classId],
                            type = if (annotation.shape is Shape.Mask) "mask" else "box",
                            area = when (val s = annotation.shape) {
                                is Shape.Mask -> s.rle.area().toDouble()
                                is Shape.Box -> s.w * s.h
                            },
                            selected = annotation.id == selectedAnnotationId,
                            hidden = annotation.id in hiddenAnnotationIds,
                            onClick = { onSelectAnnotation(annotation.id) },
                            onToggleVisibility = { onToggleAnnotationVisibility(annotation.id) },
                            onDelete = { onDeleteAnnotation(annotation.id) },
                            onChangeClass = { classPickerForAnnotation = annotation.id },
                        )
                    }
                }
            }
        }
    }

    classPickerForAnnotation?.let { annotationId ->
        ClassPickerSheet(
            classes = classes.values.toList(),
            recentClassIds = recentClassIds,
            onPick = { classDef -> onChangeAnnotationClass(annotationId, classDef.id); classPickerForAnnotation = null },
            onCreate = { name ->
                onCreateClass(name) { created ->
                    onChangeAnnotationClass(annotationId, created.id)
                    classPickerForAnnotation = null
                }
            },
            onDismiss = { classPickerForAnnotation = null },
        )
    }
}

@Composable
private fun ClassRow(classDef: ClassDef, count: Int, hidden: Boolean, onToggleVisibility: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(classDef.color)),
            )
            Text(classDef.name, modifier = Modifier.padding(start = 8.dp))
            Text("($count)", modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onToggleVisibility) {
            Icon(
                if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (hidden) "Show" else "Hide",
            )
        }
    }
}

@Composable
private fun AnnotationRow(
    classDef: ClassDef?,
    type: String,
    area: Double,
    selected: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
    onToggleVisibility: () -> Unit,
    onDelete: () -> Unit,
    onChangeClass: () -> Unit,
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${classDef?.name ?: "?"} ($type, area=${area.toInt()})")
        Row {
            if (selected) {
                IconButton(onClick = onChangeClass) { Text("Class") }
            }
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (hidden) "Show" else "Hide",
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}
