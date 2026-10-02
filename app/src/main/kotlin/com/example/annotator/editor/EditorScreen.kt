package com.example.annotator.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.annotator.core.codec.Rle
import com.example.annotator.core.editing.ChangeShapeCommand
import com.example.annotator.core.editing.CreateAnnotationCommand
import com.example.annotator.core.model.Annotation
import com.example.annotator.core.model.Shape
import com.example.annotator.core.model.Source
import com.example.annotator.ui.ClassManagementScreen
import com.example.annotator.ui.ClassPickerSheet
import com.example.annotator.ui.ExportScreen
import com.example.annotator.ui.PanelContent
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onOpenImageList: () -> Unit,
    onLostPermission: () -> Unit = {},
    onReturnToMenu: () -> Unit = {},
) {
    val classes = viewModel.classes
    val context = LocalContext.current
    var transform by remember { mutableStateOf(ViewportTransform(baseScale = 1f)) }
    var resetSignal by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var classManagementOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var classCounts by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var brushSizePx by remember { mutableStateOf(24f) }
    var isAdjustingBrushSize by remember { mutableStateOf(false) }
    // A box or mask drawn but not yet assigned a class (SPEC 4.4): the class picker shows,
    // and dismissing it without picking discards the just-created annotation.
    var pendingNewShape by remember { mutableStateOf<Shape?>(null) }
    var classPickerForSelected by remember { mutableStateOf(false) }
    // Plain boolean instead of ModalNavigationDrawer's DrawerState: that was reproducibly
    // showing the panel open on every launch despite initialValue = Closed and an explicit
    // forced close, with no code path found that ever called .open(). A fresh
    // remember { mutableStateOf(false) } cannot start true, which sidesteps the mystery
    // entirely instead of continuing to patch something whose internal state can't be trusted.
    var panelOpen by remember { mutableStateOf(false) }
    // Tablet layout (SPEC 4.3): a side panel instead of the phone's overlay -- no scrim, no
    // tap-outside-to-dismiss. Still collapsible via its own handle/button so the canvas can take
    // the full width when the panel isn't needed.
    val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    var tabletPanelOpen by remember { mutableStateOf(true) }
    var adjustmentsOpen by remember { mutableStateOf(false) }
    // Owned here (not ImageCanvas) so it persists across images within the session (SPEC 6).
    var adjustments by remember { mutableStateOf(com.example.annotator.render.ImageAdjustments()) }

    LaunchedEffect(classManagementOpen) {
        if (classManagementOpen) classCounts = viewModel.classCounts()
    }

    if (classManagementOpen) {
        ClassManagementScreen(
            classes = classes.values.toList(),
            counts = classCounts,
            rootPath = viewModel.rootDisplayPath(),
            totalImages = viewModel.images.size,
            reviewedImages = viewModel.reviewedCount(),
            loadLastExport = { viewModel.lastExportInfo() },
            onAdd = viewModel::addClass,
            onRename = viewModel::renameClass,
            onRecolor = viewModel::recolorClass,
            onMerge = { fromId, toId -> viewModel.mergeClasses(fromId, toId) },
            onDelete = { id -> viewModel.deleteClassIfUnused(id) {} },
            onBack = { classManagementOpen = false },
        )
        return
    }

    if (exportOpen) {
        ExportScreen(
            onExport = { options, onProgress -> viewModel.exportDataset(options, onProgress) },
            onZip = { outcome -> viewModel.zipLastExport(outcome) },
            onBack = { exportOpen = false },
        )
        return
    }

    val image = viewModel.currentImage
    val imageUri = image?.let { viewModel.opener.documentUriFor(it.documentId) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(image?.relativePath?.substringAfterLast("/") ?: "", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${viewModel.currentIndex + 1}/${viewModel.images.size}  ${viewModel.reviewedCount()}/${viewModel.images.size} reviewed",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.prev() }, enabled = viewModel.currentIndex > 0) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                        }
                    },
                    actions = {
                        val reviewed = viewModel.reviewState.images[image?.relativePath]?.reviewed ?: false
                        IconButton(onClick = { viewModel.toggleReviewed() }) {
                            Icon(
                                if (reviewed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = if (reviewed) "Reviewed, tap to mark unreviewed" else "Not reviewed, tap to mark reviewed",
                                tint = if (reviewed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        IconButton(onClick = { viewModel.overlaysVisible = !viewModel.overlaysVisible }) {
                            // When overlays are visible, the button's action is "hide" -> crossed eye.
                            // When overlays are hidden, the button's action is "show" -> plain eye.
                            Icon(
                                if (viewModel.overlaysVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (viewModel.overlaysVisible) "Hide overlays" else "Show overlays",
                            )
                        }
                        IconButton(onClick = { viewModel.next() }, enabled = viewModel.currentIndex < viewModel.images.lastIndex) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Jump to last edited") }, onClick = { menuExpanded = false; viewModel.jumpToLastEdited() })
                            DropdownMenuItem(text = { Text("Jump to next unreviewed") }, onClick = { menuExpanded = false; viewModel.jumpToNextUnreviewed() })
                            DropdownMenuItem(text = { Text("Image list") }, onClick = { menuExpanded = false; onOpenImageList() })
                            DropdownMenuItem(text = { Text("Export") }, onClick = { menuExpanded = false; exportOpen = true })
                            DropdownMenuItem(text = { Text("Project settings") }, onClick = { menuExpanded = false; classManagementOpen = true })
                            DropdownMenuItem(text = { Text("Return to Menu") }, onClick = { menuExpanded = false; onReturnToMenu() })
                        }
                    },
                )
            },
            bottomBar = {
                // targetSdk 35 enforces edge-to-edge by default (content can draw under the
                // system nav bar); Material3's own NavigationBar/BottomAppBar reserve space for
                // it automatically, but this is a plain custom Surface, so it needs to do the
                // same explicitly or its bottom row sits behind the nav bar.
                Column(modifier = Modifier.navigationBarsPadding()) {
                    if (viewModel.currentTool == Tool.BRUSH || viewModel.currentTool == Tool.ERASER) {
                        BrushSizeRow(
                            sizePx = brushSizePx,
                            onSizeChange = { brushSizePx = it; isAdjustingBrushSize = true },
                            onSizeChangeFinished = { isAdjustingBrushSize = false },
                        )
                    }
                    Toolbar(
                        currentTool = viewModel.currentTool,
                        onToolSelected = { viewModel.selectTool(it) },
                        canUndo = viewModel.canUndo,
                        canRedo = viewModel.canRedo,
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                ImageCanvas(
                    context = context,
                    imageUri = imageUri,
                    dataset = viewModel.currentAnnotations,
                    classes = classes,
                    overlaysVisible = viewModel.overlaysVisible,
                    overlayOpacity = viewModel.overlayOpacity,
                    hiddenClassIds = viewModel.hiddenClassIds,
                    hiddenAnnotationIds = viewModel.hiddenAnnotationIds,
                    selectedAnnotationId = viewModel.selectedAnnotationId,
                    selectedAnnotationIds = viewModel.selectedAnnotationIds,
                    onAnnotationTap = { viewModel.selectAnnotation(it) },
                    tool = viewModel.currentTool,
                    brushSizeScreenPx = brushSizePx,
                    onCreateBoxAnnotation = { box -> pendingNewShape = box },
                    onMoveResizeBox = { annotationId, newBox ->
                        val before = viewModel.currentAnnotations?.annotations?.firstOrNull { it.id == annotationId }?.shape
                        if (before != null) viewModel.execute(ChangeShapeCommand(annotationId, before, newBox))
                    },
                    onCommitMaskStroke = { annotationId, rle ->
                        if (annotationId != null) {
                            val before = viewModel.currentAnnotations?.annotations?.firstOrNull { it.id == annotationId }?.shape
                            if (before != null) viewModel.execute(ChangeShapeCommand(annotationId, before, Shape.Mask(rle)))
                        } else {
                            pendingNewShape = Shape.Mask(rle)
                        }
                    },
                    onMultiSelect = { viewModel.setMultiSelection(it) },
                    pendingShape = pendingNewShape,
                    adjustments = adjustments,
                    transform = transform,
                    resetSignal = resetSignal,
                    onTransformChange = { transform = it },
                )

                if (isAdjustingBrushSize) {
                    BrushSizePreview(radiusPx = brushSizePx, modifier = Modifier.align(Alignment.Center))
                }

                ZoomChip(
                    transform = transform,
                    onZoomIn = { transform = transform.zoomed(1.25f, androidx.compose.ui.geometry.Offset.Zero) },
                    onZoomOut = { transform = transform.zoomed(0.8f, androidx.compose.ui.geometry.Offset.Zero) },
                    onReset = { resetSignal++ },
                    onToggleAdjustments = { adjustmentsOpen = !adjustmentsOpen },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )

                if (adjustmentsOpen) {
                    AdjustmentsPanel(
                        adjustments = adjustments,
                        onAdjustmentsChange = { adjustments = it },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 72.dp),
                    )
                }

                // Visible slidable handle for the left "Labels" panel: on phones it opens the
                // overlay drawer, on tablets it reopens the side panel once collapsed.
                if (!isTablet) {
                    DrawerHandle(
                        onClick = { panelOpen = true },
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                } else if (!tabletPanelOpen) {
                    DrawerHandle(
                        onClick = { tabletPanelOpen = true },
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }

                if (viewModel.selectedAnnotationIds.isNotEmpty()) {
                    ContextualSelectionBar(
                        count = viewModel.selectedAnnotationIds.size,
                        onChangeClass = { classPickerForSelected = true },
                        onDelete = { viewModel.bulkDelete() },
                        onDeselect = { viewModel.clearMultiSelection() },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    )
                } else {
                    viewModel.selectedAnnotationId?.let { selectedId ->
                        ContextualSelectionBar(
                            count = 1,
                            onChangeClass = { classPickerForSelected = true },
                            onDelete = { viewModel.deleteAnnotation(selectedId) },
                            onDeselect = { viewModel.selectAnnotation(null) },
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        val tabletPanelVisible = isTablet && tabletPanelOpen
        if (panelOpen || tabletPanelVisible) {
            if (!isTablet) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(onClick = { panelOpen = false }),
                )
            }
            Surface(
                modifier = Modifier.fillMaxHeight().width(320.dp).align(Alignment.CenterStart),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 3.dp,
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    if (isTablet) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { tabletPanelOpen = false }) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Collapse labels panel")
                            }
                        }
                    }
                    PanelContent(
                        dataset = viewModel.currentAnnotations,
                        classes = classes,
                        hiddenClassIds = viewModel.hiddenClassIds,
                        hiddenAnnotationIds = viewModel.hiddenAnnotationIds,
                        selectedAnnotationId = viewModel.selectedAnnotationId,
                        recentClassIds = viewModel.recentClassIds,
                        onToggleClassVisibility = viewModel::toggleClassVisibility,
                        onToggleAnnotationVisibility = viewModel::toggleAnnotationVisibility,
                        onSelectAnnotation = { viewModel.selectAnnotation(it) },
                        onDeleteAnnotation = viewModel::deleteAnnotation,
                        onChangeAnnotationClass = viewModel::changeAnnotationClass,
                        onCreateClass = { name, onCreated -> viewModel.addClass(name, onCreated) },
                        onManageClasses = {
                            panelOpen = false
                            classManagementOpen = true
                        },
                    )
                }
            }
        }
    }

    pendingNewShape?.let { shape ->
        ClassPickerSheet(
            classes = classes.values.toList(),
            recentClassIds = viewModel.recentClassIds,
            onPick = { classDef ->
                createPendingAnnotation(viewModel, shape, classDef.id)
                pendingNewShape = null
            },
            onCreate = { name ->
                viewModel.addClass(name) { created ->
                    createPendingAnnotation(viewModel, shape, created.id)
                    pendingNewShape = null
                }
            },
            onDismiss = { pendingNewShape = null }, // discards the just-created annotation (SPEC 4.4)
        )
    }

    if (classPickerForSelected) {
        val bulkIds = viewModel.selectedAnnotationIds
        val selectedId = viewModel.selectedAnnotationId
        if (bulkIds.isEmpty() && selectedId == null) {
            classPickerForSelected = false
        } else {
            ClassPickerSheet(
                classes = classes.values.toList(),
                recentClassIds = viewModel.recentClassIds,
                onPick = { classDef ->
                    if (bulkIds.isNotEmpty()) viewModel.bulkChangeClass(classDef.id) else selectedId?.let { viewModel.changeAnnotationClass(it, classDef.id) }
                    classPickerForSelected = false
                },
                onCreate = { name ->
                    viewModel.addClass(name) { created ->
                        if (bulkIds.isNotEmpty()) viewModel.bulkChangeClass(created.id) else selectedId?.let { viewModel.changeAnnotationClass(it, created.id) }
                        classPickerForSelected = false
                    }
                },
                onDismiss = { classPickerForSelected = false },
            )
        }
    }

    // Lost folder permission (SPEC section 8): return to Home with a "Re-open folder" prompt.
    viewModel.fatalError?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text("Can't access this folder") },
            text = { Text("$message Please re-open it from Home.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = onLostPermission) { Text("Re-open folder") } },
        )
    }
}

private fun createPendingAnnotation(viewModel: EditorViewModel, shape: Shape, classId: Int) {
    val annotation = Annotation(id = UUID.randomUUID().toString(), classId = classId, shape = shape, source = Source.USER)
    viewModel.markClassUsed(classId)
    viewModel.execute(CreateAnnotationCommand(annotation))
    viewModel.selectAnnotation(annotation.id)
}

@Composable
private fun ContextualSelectionBar(
    count: Int,
    onChangeClass: () -> Unit,
    onDelete: () -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
            Text("$count selected", modifier = Modifier.padding(horizontal = 8.dp))
            androidx.compose.material3.TextButton(onClick = onChangeClass) { Text("Change class") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
            IconButton(onClick = onDeselect) {
                Icon(Icons.Filled.Check, contentDescription = "Deselect")
            }
        }
    }
}

/** Shown over the canvas center while dragging the brush/pencil size slider, so the user sees
 * the actual stroke diameter before committing to it instead of guessing from the px number. */
@Composable
private fun BrushSizePreview(radiusPx: Float, modifier: Modifier = Modifier) {
    // A fixed box comfortably larger than the slider's max radius (120px); drawCircle centers
    // itself in the canvas by default, so the raw px radius just has to fit inside it.
    androidx.compose.foundation.Canvas(modifier = modifier.size(280.dp)) {
        drawCircle(color = Color.White.copy(alpha = 0.25f), radius = radiusPx)
        drawCircle(color = Color.Black, radius = radiusPx, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = Color.White, radius = radiusPx, style = Stroke(width = 1.5.dp.toPx()))
    }
}

@Composable
private fun DrawerHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(20.dp)
            .fillMaxHeight(0.15f),
        shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open labels panel")
        }
    }
}

@Composable
private fun ZoomChip(
    transform: ViewportTransform,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onToggleAdjustments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onZoomOut, modifier = Modifier.size(32.dp)) { Text("-") }
            Text("${transform.zoomPercent()}%")
            IconButton(onClick = onZoomIn, modifier = Modifier.size(32.dp)) { Text("+") }
            IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.FitScreen, contentDescription = "Fit to screen")
            }
            IconButton(onClick = onToggleAdjustments, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Tune, contentDescription = "Image adjustments")
            }
        }
    }
}

/**
 * Brightness/contrast/gamma (SPEC section 6): view only, never written to disk or exported.
 * [adjustments] is owned by the caller so it persists across images within the session.
 */
@Composable
private fun AdjustmentsPanel(
    adjustments: com.example.annotator.render.ImageAdjustments,
    onAdjustmentsChange: (com.example.annotator.render.ImageAdjustments) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.width(260.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Brightness", style = MaterialTheme.typography.labelSmall)
            androidx.compose.material3.Slider(
                value = adjustments.brightness,
                onValueChange = { onAdjustmentsChange(adjustments.copy(brightness = it)) },
                valueRange = -1f..1f,
            )
            Text("Contrast", style = MaterialTheme.typography.labelSmall)
            androidx.compose.material3.Slider(
                value = adjustments.contrast,
                onValueChange = { onAdjustmentsChange(adjustments.copy(contrast = it)) },
                valueRange = -1f..1f,
            )
            Text("Gamma", style = MaterialTheme.typography.labelSmall)
            androidx.compose.material3.Slider(
                value = adjustments.gamma,
                onValueChange = { onAdjustmentsChange(adjustments.copy(gamma = it)) },
                valueRange = 0.2f..3f,
            )
            androidx.compose.material3.TextButton(
                onClick = { onAdjustmentsChange(com.example.annotator.render.ImageAdjustments()) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Reset") }
        }
    }
}
