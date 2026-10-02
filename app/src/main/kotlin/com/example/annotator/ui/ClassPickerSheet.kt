package com.example.annotator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.annotator.core.model.ClassDef

/**
 * Class picker (SPEC 4.4): search, recently used pinned on top, "Create class '<query>'"
 * when there is no exact name match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassPickerSheet(
    classes: List<ClassDef>,
    recentClassIds: List<Int>,
    onPick: (ClassDef) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val recents = recentClassIds.mapNotNull { id -> classes.firstOrNull { it.id == id } }
    val filtered = if (query.isBlank()) {
        classes
    } else {
        classes.filter { it.name.contains(query, ignoreCase = true) }
    }
    val hasExactMatch = classes.any { it.name.equals(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search classes") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (query.isBlank() && recents.isNotEmpty()) {
                Text("Recent", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 12.dp))
                LazyColumn {
                    items(recents) { classDef -> ClassPickerRow(classDef, onClick = { onPick(classDef) }) }
                }
            }

            LazyColumn {
                items(filtered) { classDef -> ClassPickerRow(classDef, onClick = { onPick(classDef) }) }
                if (query.isNotBlank() && !hasExactMatch) {
                    item {
                        Text(
                            "Create class '$query'",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCreate(query) }
                                .padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassPickerRow(classDef: ClassDef, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(classDef.color)),
        )
        Text(classDef.name, modifier = Modifier.padding(start = 8.dp))
    }
}
