package com.example.annotator.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Toolbar(
    currentTool: Tool,
    onToolSelected: (Tool) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Navigation.
            ToolButton(Icons.Filled.PanTool, "Pan", currentTool == Tool.PAN) { onToolSelected(Tool.PAN) }

            ToolbarDivider()

            // Selection: tools that pick existing annotations, never create or edit shapes.
            ToolButton(Icons.Filled.NearMe, "Selection", currentTool == Tool.SELECTION) { onToolSelected(Tool.SELECTION) }
            ToolButton(Icons.Filled.CropFree, "Box select", currentTool == Tool.BOX_SELECT) { onToolSelected(Tool.BOX_SELECT) }
            ToolButton(Icons.Filled.Gesture, "Lasso", currentTool == Tool.LASSO) { onToolSelected(Tool.LASSO) }

            ToolbarDivider()

            // Drawing: tools that create or edit shapes.
            // "New box" rather than just "Box" -- this creates a single box annotation, it is
            // not the multi-object box-select tool above.
            ToolButton(Icons.Filled.BorderStyle, "New box", currentTool == Tool.BOUNDING_BOX) { onToolSelected(Tool.BOUNDING_BOX) }
            ToolButton(Icons.Filled.Brush, "Brush", currentTool == Tool.BRUSH) { onToolSelected(Tool.BRUSH) }
            ToolButton(Icons.Filled.Backspace, "Eraser", currentTool == Tool.ERASER) { onToolSelected(Tool.ERASER) }
            ToolButton(Icons.Filled.Create, "Pencil", currentTool == Tool.PENCIL) { onToolSelected(Tool.PENCIL) }
            ToolButton(Icons.Filled.ContentCut, "Knife", currentTool == Tool.KNIFE) { onToolSelected(Tool.KNIFE) }

            ToolbarDivider()

            // History.
            ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", selected = false, enabled = canUndo, onClick = onUndo)
            ToolButton(Icons.AutoMirrored.Filled.Redo, "Redo", selected = false, enabled = canRedo, onClick = onRedo)
        }
    }
}

/** Short vertical rule separating groups of related tools -- deliberately not full height
 * (unlike a full Divider) so it reads as a gap between groups rather than a hard split of the
 * whole bar. */
@Composable
private fun ToolbarDivider() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
    )
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = if (!enabled) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    } else if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, tint = tint)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

/** Size slider, shown only while Brush or Eraser is active (SPEC 4.3) -- Pencil/Knife have no
 * size setting, their stroke is a traced outline rather than a stamped circle. */
@Composable
fun BrushSizeRow(
    sizePx: Float,
    onSizeChange: (Float) -> Unit,
    onSizeChangeFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Size", modifier = Modifier.padding(end = 12.dp))
            Slider(
                value = sizePx,
                onValueChange = onSizeChange,
                onValueChangeFinished = onSizeChangeFinished,
                valueRange = 4f..120f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("${sizePx.toInt()}px")
        }
    }
}
