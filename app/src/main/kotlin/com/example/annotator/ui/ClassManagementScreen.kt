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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.annotator.core.formats.ColorPalette
import com.example.annotator.core.model.ClassDef
import com.example.annotator.storage.LastExportInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Project settings: class list with counts, add/rename/recolor/merge/delete (SPEC 4.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassManagementScreen(
    classes: List<ClassDef>,
    counts: Map<Int, Int>,
    rootPath: String,
    totalImages: Int,
    reviewedImages: Int,
    loadLastExport: suspend () -> LastExportInfo?,
    onAdd: (String) -> Unit,
    onRename: (Int, String) -> Unit,
    onRecolor: (Int, Int) -> Unit,
    onMerge: (fromId: Int, toId: Int) -> Unit,
    onDelete: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var addDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ClassDef?>(null) }
    var mergeSource by remember { mutableStateOf<ClassDef?>(null) }
    var lastExport by remember { mutableStateOf<LastExportInfo?>(null) }

    LaunchedEffect(Unit) { lastExport = loadLastExport() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { Button(onClick = { addDialogOpen = true }) { Text("Add class") } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                ProjectInfoSection(rootPath, totalImages, reviewedImages, lastExport)
            }
            item {
                Text("Classes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp))
            }
            items(classes) { classDef ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(classDef.color)),
                        )
                        Text("${classDef.name} (${counts[classDef.id] ?: 0})", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row {
                        TextButton(onClick = { renameTarget = classDef }) { Text("Rename") }
                        TextButton(onClick = {
                            onRecolor(classDef.id, ColorPalette.colorFor((classDef.color + 1)))
                        }) { Text("Recolor") }
                        TextButton(onClick = { mergeSource = classDef }) { Text("Merge into…") }
                        IconButton(
                            onClick = { onDelete(classDef.id) },
                            enabled = (counts[classDef.id] ?: 0) == 0,
                        ) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
    }

    if (addDialogOpen) {
        TextInputDialog(
            title = "New class",
            onConfirm = { name -> onAdd(name); addDialogOpen = false },
            onDismiss = { addDialogOpen = false },
        )
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = "Rename ${target.name}",
            initialValue = target.name,
            onConfirm = { name -> onRename(target.id, name); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }

    mergeSource?.let { source ->
        AlertDialog(
            onDismissRequest = { mergeSource = null },
            title = { Text("Merge '${source.name}' into…") },
            text = {
                LazyColumn {
                    items(classes.filter { it.id != source.id }) { target ->
                        Text(
                            target.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMerge(source.id, target.id); mergeSource = null }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { mergeSource = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectInfoSection(rootPath: String, totalImages: Int, reviewedImages: Int, lastExport: LastExportInfo?) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Project", style = MaterialTheme.typography.titleSmall)
        Text(rootPath, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Text("$reviewedImages/$totalImages images reviewed", style = MaterialTheme.typography.bodySmall)
        Text(
            if (lastExport != null) {
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastExport.timestampMillis))
                "Last export: ${lastExport.format} • ${lastExport.fileCount} files" +
                    (if (lastExport.warningCount > 0) " • ${lastExport.warningCount} warnings" else "") +
                    " • $date\n${lastExport.destinationPath}"
            } else {
                "Last export: none yet"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun TextInputDialog(title: String, initialValue: String = "", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }) },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
