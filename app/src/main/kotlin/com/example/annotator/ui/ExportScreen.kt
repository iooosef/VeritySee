package com.example.annotator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import com.example.annotator.core.formats.ExportFormat
import com.example.annotator.core.formats.ExportOptions
import com.example.annotator.core.formats.ExportScope
import com.example.annotator.core.formats.SplitConfig
import com.example.annotator.core.formats.coco.CocoMaskOption
import com.example.annotator.editor.ExportOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onExport: suspend (ExportOptions, onProgress: (Int, Int, String) -> Unit) -> ExportOutcome,
    onZip: suspend (ExportOutcome) -> String,
    onBack: () -> Unit,
) {
    var format by remember { mutableStateOf(ExportFormat.YOLO_DETECT) }
    var scope by remember { mutableStateOf(ExportScope.ALL) }
    var splitEnabled by remember { mutableStateOf(false) }
    var trainPercent by remember { mutableStateOf(70f) }
    var valPercent by remember { mutableStateOf(20f) }
    var seedText by remember { mutableStateOf("42") }
    var cocoMaskOption by remember { mutableStateOf(CocoMaskOption.POLYGONS) }
    var yoloTolerance by remember { mutableStateOf(1.0f) }

    var exportJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var outcome by remember { mutableStateOf<ExportOutcome?>(null) }
    var summaryDialogOpen by remember { mutableStateOf(false) }
    var zipping by remember { mutableStateOf(false) }
    var zipResultPath by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Format", style = MaterialTheme.typography.titleSmall)
                var formatMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = format.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // OutlinedTextField captures touches itself for cursor/selection even when
                    // readOnly, so a .clickable() on the field never actually fires -- a
                    // transparent overlay on top reliably intercepts the tap instead.
                    Box(
                        modifier = Modifier.matchParentSize().clickable { formatMenuExpanded = true },
                    )
                    DropdownMenu(expanded = formatMenuExpanded, onDismissRequest = { formatMenuExpanded = false }) {
                        ExportFormat.entries.forEach { f ->
                            DropdownMenuItem(text = { Text(f.name) }, onClick = { format = f; formatMenuExpanded = false })
                        }
                    }
                }
            }
            item {
                Text("Scope", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportScope.entries.forEach { s ->
                        FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.name) })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Train/val/test split", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = splitEnabled, onCheckedChange = { splitEnabled = it })
                }
            }
            if (splitEnabled) {
                item {
                    Text("Train: ${trainPercent.toInt()}%")
                    Slider(value = trainPercent, onValueChange = { trainPercent = it.coerceAtMost(100f - valPercent) }, valueRange = 0f..100f)
                    Text("Val: ${valPercent.toInt()}%")
                    Slider(value = valPercent, onValueChange = { valPercent = it.coerceAtMost(100f - trainPercent) }, valueRange = 0f..100f)
                    Text("Test: ${(100 - trainPercent - valPercent).toInt()}%")
                    OutlinedTextField(value = seedText, onValueChange = { seedText = it }, label = { Text("Seed") })
                }
            }
            if (format == ExportFormat.COCO) {
                item {
                    Text("COCO mask format", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CocoMaskOption.entries.forEach { opt ->
                            FilterChip(selected = cocoMaskOption == opt, onClick = { cocoMaskOption = opt }, label = { Text(opt.name) })
                        }
                    }
                    Text(
                        when (cocoMaskOption) {
                            CocoMaskOption.POLYGONS -> "Polygons: mask edges as point outlines. Widest tool support, but holes " +
                                "in a mask are filled and curves are approximated by vertices."
                            CocoMaskOption.COMPRESSED_RLE -> "Compressed RLE: the exact pixel mask, run-length encoded. Lossless " +
                                "and keeps holes, but is an opaque string, not easily hand-edited."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (format == ExportFormat.YOLO_SEGMENT) {
                item {
                    Text("Polygon simplify tolerance: ${"%.1f".format(yoloTolerance)}px")
                    Slider(value = yoloTolerance, onValueChange = { yoloTolerance = it }, valueRange = 0f..5f)
                }
            }
            item {
                Button(
                    onClick = {
                        progress = 0 to 0
                        log = emptyList()
                        outcome = null
                        zipResultPath = null
                        exportJob = coroutineScope.launch {
                            val options = ExportOptions(
                                format = format,
                                scope = scope,
                                split = if (splitEnabled) {
                                    SplitConfig(trainPercent / 100.0, valPercent / 100.0, seedText.toLongOrNull() ?: 42L)
                                } else {
                                    null
                                },
                                cocoMaskOption = cocoMaskOption,
                                yoloSimplifyTolerance = yoloTolerance.toDouble(),
                            )
                            val r = onExport(options) { written, total, path ->
                                progress = written to total
                                // Keep only the most recent entries -- a large export can write
                                // thousands of files and we don't want an unbounded log list.
                                log = (log + "$written/$total  $path").takeLast(100)
                            }
                            outcome = r
                            summaryDialogOpen = true
                            progress = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = exportJob?.isActive != true,
                ) {
                    Text("Export")
                }
            }
            progress?.let { (written, total) ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (total > 0) "Writing $written/$total" else "Preparing export…", modifier = Modifier.weight(1f))
                            TextButton(onClick = { exportJob?.cancel(); progress = null }) { Text("Cancel") }
                        }
                        if (total > 0) {
                            LinearProgressIndicator(progress = { written.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (log.isNotEmpty()) {
                item {
                    Text("Log", style = MaterialTheme.typography.titleSmall)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        log.forEach { line -> Text(line, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            outcome?.let { o ->
                item {
                    Text("Saved to: ${o.destinationPath}", style = MaterialTheme.typography.bodySmall)
                }
            }
            zipResultPath?.let { path ->
                item {
                    Text("Zipped to: $path", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (summaryDialogOpen) {
        outcome?.let { o ->
            ExportSummaryDialog(
                outcome = o,
                zipping = zipping,
                onZip = {
                    zipping = true
                    coroutineScope.launch {
                        zipResultPath = onZip(o)
                        zipping = false
                        summaryDialogOpen = false
                    }
                },
                onDismiss = { summaryDialogOpen = false },
            )
        }
    }
}

@Composable
private fun ExportSummaryDialog(outcome: ExportOutcome, zipping: Boolean, onZip: () -> Unit, onDismiss: () -> Unit) {
    val result = outcome.result
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export complete") },
        text = {
            Column {
                Text("${result.files.size} files written.")
                if (result.warnings.isNotEmpty()) {
                    Text("Warnings:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    val counted = result.warnings.groupingBy { it }.eachCount()
                    LazyColumn {
                        items(counted.entries.toList()) { (warning, count) ->
                            Text("• $warning" + if (count > 1) " (x$count)" else "")
                        }
                    }
                }
                Text(
                    "Zip this export together with the images into a single shareable file?",
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (zipping) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Zipping…")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !zipping, onClick = onZip) { Text("Zip now") }
        },
        dismissButton = {
            TextButton(enabled = !zipping, onClick = onDismiss) { Text("Not now") }
        },
    )
}
