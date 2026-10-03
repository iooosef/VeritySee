package com.example.annotator.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.annotator.core.codec.Rle
import com.example.annotator.core.codec.RleCodec
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.geometry.ContourTracer
import com.example.annotator.core.geometry.Polygon
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.Shape
import com.example.annotator.render.DownsampledImageLoader
import com.example.annotator.render.ImageAdjustmentRenderer
import com.example.annotator.render.ImageAdjustments
import com.example.annotator.render.MaskOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val HANDLE_RADIUS_DP = 16f

@Composable
fun ImageCanvas(
    context: Context,
    imageUri: Uri?,
    dataset: DatasetImage?,
    classes: Map<Int, ClassDef>,
    overlaysVisible: Boolean,
    overlayOpacity: Float,
    hiddenClassIds: Set<Int> = emptySet(),
    hiddenAnnotationIds: Set<String> = emptySet(),
    selectedAnnotationId: String? = null,
    selectedAnnotationIds: Set<String> = emptySet(),
    onAnnotationTap: (String?) -> Unit = {},
    tool: Tool = Tool.PAN,
    brushSizeScreenPx: Float = 24f,
    onCreateBoxAnnotation: (Shape.Box) -> Unit = {},
    onMoveResizeBox: (annotationId: String, Shape.Box) -> Unit = { _, _ -> },
    onCommitMaskStroke: (annotationId: String?, Rle) -> Unit = { _, _ -> },
    onMultiSelect: (Set<String>) -> Unit = {},
    // A just-drawn shape waiting on the class picker (SPEC 4.4): not yet a real annotation, so
    // it isn't in `dataset` and would otherwise vanish the instant the stroke ends.
    pendingShape: Shape? = null,
    adjustments: ImageAdjustments = ImageAdjustments(),
    transform: ViewportTransform,
    resetSignal: Int = 0,
    onTransformChange: (ViewportTransform) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
) {
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var displayBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(imageUri) { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(imageUri) {
        loadFailed = false
        val decoded = imageUri?.let { DownsampledImageLoader.decode(context, it) }
        bitmap = decoded
        // Unreadable image (SPEC section 8): show a placeholder instead of a blank canvas.
        if (imageUri != null && decoded == null) loadFailed = true
    }

    // Brightness/contrast/gamma (SPEC section 6): debounced 50ms, computed off the main thread
    // into a cached preview bitmap. View only -- never written back to the source bitmap.
    LaunchedEffect(bitmap, adjustments) {
        val bmp = bitmap ?: return@LaunchedEffect
        if (adjustments.isIdentity) {
            displayBitmap = bmp
            return@LaunchedEffect
        }
        delay(50)
        displayBitmap = withContext(Dispatchers.Default) { ImageAdjustmentRenderer.apply(bmp, adjustments) }
    }

    // First fit for a newly opened image, once the canvas has a real size. Deliberately not
    // keyed on canvasSize alone -- the bottom toolbar changes height when switching to
    // Brush/Pencil (size slider appears), which resizes the canvas and must NOT re-fit an
    // already-fitted image and blow away the user's pan/zoom.
    var hasFitThisImage by remember(imageUri) { mutableStateOf(false) }
    LaunchedEffect(bitmap, canvasSize) {
        val bmp = bitmap ?: return@LaunchedEffect
        if (canvasSize == Size.Zero || hasFitThisImage) return@LaunchedEffect
        onTransformChange(ViewportTransform.fitToScreen(bmp.width, bmp.height, canvasSize.width, canvasSize.height))
        hasFitThisImage = true
    }

    // Explicit "reset view" request (zoom chip fit button).
    LaunchedEffect(resetSignal) {
        if (resetSignal == 0) return@LaunchedEffect
        val bmp = bitmap ?: return@LaunchedEffect
        if (canvasSize == Size.Zero) return@LaunchedEffect
        onTransformChange(ViewportTransform.fitToScreen(bmp.width, bmp.height, canvasSize.width, canvasSize.height))
    }

    val latestTransform by rememberUpdatedState(transform)
    val latestBrushSizeScreenPx by rememberUpdatedState(brushSizeScreenPx)
    val latestOnTransformChange by rememberUpdatedState(onTransformChange)
    val latestDataset by rememberUpdatedState(dataset)
    val latestHiddenClassIds by rememberUpdatedState(hiddenClassIds)
    val latestHiddenAnnotationIds by rememberUpdatedState(hiddenAnnotationIds)
    val latestOnAnnotationTap by rememberUpdatedState(onAnnotationTap)
    val latestTool by rememberUpdatedState(tool)
    val latestOnUndo by rememberUpdatedState(onUndo)
    val latestOnRedo by rememberUpdatedState(onRedo)

    // Plain memoization tables (not Compose state) keyed by annotation id, reused across
    // recompositions: re-rasterizing/re-tracing every mask whenever *any* one annotation
    // changes doesn't scale to datasets with many masks per image (SPEC section 6: "re-rasterize
    // only the annotation that changed"). A cache hit requires the Rle to be structurally equal
    // to what produced the cached entry (Rle has a real equals/hashCode from M1), so edits to
    // one mask only regenerate that mask's bitmap/outline, not every other one.
    val maskBitmapCache = remember { mutableMapOf<String, Pair<Rle, Bitmap>>() }
    val maskOutlineCache = remember { mutableMapOf<String, Pair<Rle, List<Polygon>>>() }

    // Rasterizing + contour-tracing every mask is too slow to run inline during composition
    // once a dataset has 100+ masks (it visibly freezes the frame) -- compute off the main
    // thread instead and let the overlays pop in once ready. `overlaysRendering` drives a
    // small non-blocking indicator while that's in flight.
    var maskBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var maskOutlines by remember { mutableStateOf<Map<String, List<Polygon>>>(emptyMap()) }
    // Mask's own bounding box in image pixel coordinates, derived from its outline -- used to
    // cull off-screen masks from the draw loop below (SPEC section 6: datasets with hundreds of
    // annotations per image must not pay a per-frame draw cost for annotations that aren't
    // currently visible).
    var maskBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var overlaysRendering by remember { mutableStateOf(false) }

    LaunchedEffect(dataset) {
        val currentMaskIds = dataset?.annotations.orEmpty().mapNotNull { (it.shape as? Shape.Mask)?.let { _ -> it.id } }.toSet()
        maskBitmapCache.keys.retainAll(currentMaskIds)
        maskOutlineCache.keys.retainAll(currentMaskIds)

        overlaysRendering = true
        val (bitmaps, outlines, bounds) = withContext(Dispatchers.Default) {
            val bitmaps = dataset?.annotations.orEmpty().mapNotNull { ann ->
                val mask = ann.shape as? Shape.Mask ?: return@mapNotNull null
                val cached = maskBitmapCache[ann.id]
                val bitmap = if (cached != null && cached.first == mask.rle) {
                    cached.second
                } else {
                    MaskOverlay.toAlpha8Bitmap(mask.rle).also { maskBitmapCache[ann.id] = mask.rle to it }
                }
                ann.id to bitmap
            }.toMap()

            // Outer boundary of each mask, in image pixel coordinates, for a solid 1px outline
            // on top of the semi-transparent fill (SPEC 4.3).
            val outlines = dataset?.annotations.orEmpty().mapNotNull { ann ->
                val mask = ann.shape as? Shape.Mask ?: return@mapNotNull null
                val cached = maskOutlineCache[ann.id]
                val outline = if (cached != null && cached.first == mask.rle) {
                    cached.second
                } else {
                    val grid = RleCodec.decode(mask.rle)
                    ContourTracer.trace(grid).map { it.points }.also { maskOutlineCache[ann.id] = mask.rle to it }
                }
                ann.id to outline
            }.toMap()

            val bounds = outlines.mapValues { (_, polygons) -> boundingBox(polygons) }

            Triple(bitmaps, outlines, bounds)
        }
        maskBitmaps = bitmaps
        maskOutlines = outlines
        maskBounds = bounds
        overlaysRendering = false
    }

    val pendingMaskBitmap = remember(pendingShape) { (pendingShape as? Shape.Mask)?.let { MaskOverlay.toAlpha8Bitmap(it.rle) } }

    // --- Live preview state, written by the active tool's stroke handler, read by drawing. ---
    var boxPreview by remember { mutableStateOf<Shape.Box?>(null) }
    var brushPreviewPoint by remember { mutableStateOf<Offset?>(null) }
    var pencilPreviewPath by remember { mutableStateOf<List<Offset>?>(null) }

    val selectedAnnotation = dataset?.annotations?.firstOrNull { it.id == selectedAnnotationId }
    val targetMaskRle = (selectedAnnotation?.shape as? Shape.Mask)?.rle

    val strokeHandler = remember(tool, selectedAnnotationId, dataset) {
        when (tool) {
            Tool.BOUNDING_BOX -> BoundingBoxToolHandler(
                onCreate = onCreateBoxAnnotation,
                onPreview = { boxPreview = it },
            )
            Tool.SELECTION -> SelectionDragHandler(
                selectedBox = { (dataset?.annotations?.firstOrNull { it.id == selectedAnnotationId }?.shape as? Shape.Box) },
                handleRadiusImagePx = { HANDLE_RADIUS_DP / latestTransform.totalScale },
                onPreview = { boxPreview = it },
                onCommit = { box -> selectedAnnotationId?.let { onMoveResizeBox(it, box) } },
            )
            Tool.BRUSH -> BrushToolHandler(
                targetAnnotationId = selectedAnnotationId.takeIf { selectedAnnotation?.shape is Shape.Mask },
                existingRle = targetMaskRle,
                imageSize = dataset?.size ?: com.example.annotator.core.model.ImageSize(0, 0),
                erase = false,
                radiusImagePx = { latestBrushSizeScreenPx / latestTransform.totalScale },
                onPreviewPoint = { brushPreviewPoint = it },
                onCommit = onCommitMaskStroke,
            )
            Tool.ERASER -> BrushToolHandler(
                targetAnnotationId = selectedAnnotationId.takeIf { selectedAnnotation?.shape is Shape.Mask },
                existingRle = targetMaskRle,
                imageSize = dataset?.size ?: com.example.annotator.core.model.ImageSize(0, 0),
                erase = true,
                radiusImagePx = { latestBrushSizeScreenPx / latestTransform.totalScale },
                onPreviewPoint = { brushPreviewPoint = it },
                onCommit = onCommitMaskStroke,
            )
            Tool.PENCIL -> PencilToolHandler(
                targetAnnotationId = selectedAnnotationId.takeIf { selectedAnnotation?.shape is Shape.Mask },
                existingRle = targetMaskRle,
                imageSize = dataset?.size ?: com.example.annotator.core.model.ImageSize(0, 0),
                erase = false,
                onPreviewPath = { pts -> pencilPreviewPath = pts },
                onCommit = onCommitMaskStroke,
            )
            Tool.KNIFE -> PencilToolHandler(
                targetAnnotationId = selectedAnnotationId.takeIf { selectedAnnotation?.shape is Shape.Mask },
                existingRle = targetMaskRle,
                imageSize = dataset?.size ?: com.example.annotator.core.model.ImageSize(0, 0),
                erase = true,
                onPreviewPath = { pts -> pencilPreviewPath = pts },
                onCommit = onCommitMaskStroke,
            )
            Tool.BOX_SELECT -> BoxSelectToolHandler(
                dataset = { latestDataset },
                hiddenClassIds = { latestHiddenClassIds },
                hiddenAnnotationIds = { latestHiddenAnnotationIds },
                onPreview = { boxPreview = it },
                onSelect = onMultiSelect,
            )
            Tool.LASSO -> LassoToolHandler(
                dataset = { latestDataset },
                hiddenClassIds = { latestHiddenClassIds },
                hiddenAnnotationIds = { latestHiddenAnnotationIds },
                onPreviewPath = { pts -> pencilPreviewPath = pts },
                onSelect = onMultiSelect,
            )
            Tool.PAN -> null
        }
    }

    // Stable wrapper so the long-lived gesture coroutine (keyed on Unit) always dispatches to
    // the latest stroke handler, even though `remember` above may swap the instance out --
    // the same stale-closure pitfall as pan/zoom, fixed the same way.
    val latestStrokeHandler = rememberUpdatedState(strokeHandler)
    val tapHandlingStroke = remember {
        object : ToolStrokeHandler {
            override fun onTap(imagePoint: Offset) {
                // Only the Selection tool changes selection on tap -- a stray tap while
                // painting with Brush/Pencil must not deselect the annotation being edited.
                if (latestTool == Tool.SELECTION) {
                    val hit = hitTest(latestDataset, imagePoint, latestHiddenClassIds, latestHiddenAnnotationIds)
                    latestOnAnnotationTap(hit)
                }
                latestStrokeHandler.value?.onTap(imagePoint)
            }
            override fun onStart(imagePoint: Offset) { latestStrokeHandler.value?.onStart(imagePoint) }
            override fun onMove(imagePoint: Offset) { latestStrokeHandler.value?.onMove(imagePoint) }
            override fun onEnd() { latestStrokeHandler.value?.onEnd() }
            override fun onCancel() { latestStrokeHandler.value?.onCancel() }
        }
    }

    if (loadFailed) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Couldn't load this image", color = Color.White)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectToolGestures(
                    tool = { latestTool },
                    transform = { latestTransform },
                    onTransformChange = latestOnTransformChange,
                    stroke = tapHandlingStroke,
                    onUndo = { latestOnUndo() },
                    onRedo = { latestOnRedo() },
                )
            },
    ) {
        canvasSize = size

        val bmp = bitmap ?: return@Canvas
        // Falls back to the unadjusted bitmap during the debounce window so there's no blank
        // frame while a new adjustment value is still being computed off-thread.
        val image = (displayBitmap ?: bmp).asImageBitmap()
        val dstOffset = Offset(transform.offset.x, transform.offset.y)
        val dstSize = Size(bmp.width * transform.totalScale, bmp.height * transform.totalScale)

        // Viewport in image pixel coordinates, for culling annotations that aren't currently
        // on screen out of the per-frame draw loop below -- a dataset can have hundreds of
        // annotations on one image, and most won't be visible at once while zoomed/panned in.
        val viewportTopLeft = transform.screenToImage(Offset.Zero)
        val viewportBottomRight = transform.screenToImage(Offset(size.width, size.height))
        val viewportImageRect = Rect(viewportTopLeft.x, viewportTopLeft.y, viewportBottomRight.x, viewportBottomRight.y)

        drawImage(
            image = image,
            dstOffset = androidx.compose.ui.unit.IntOffset(dstOffset.x.toInt(), dstOffset.y.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(dstSize.width.toInt(), dstSize.height.toInt()),
        )

        if (overlaysVisible) {
            dataset?.annotations?.forEach { annotation ->
                if (annotation.classId in hiddenClassIds || annotation.id in hiddenAnnotationIds) return@forEach
                val classDef = classes[annotation.classId]
                val color = classDef?.let { Color(it.color) } ?: Color.Magenta
                val selected = annotation.id == selectedAnnotationId || annotation.id in selectedAnnotationIds
                val outlineWidth = if (selected) 3.dp.toPx() else 1.dp.toPx()
                val selectionDash = if (selected) PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())) else null
                when (val shape = annotation.shape) {
                    is Shape.Mask -> {
                        val bounds = maskBounds[annotation.id]
                        if (bounds != null && !bounds.overlaps(viewportImageRect)) return@forEach
                        val maskBmp = maskBitmaps[annotation.id] ?: return@forEach
                        val maskImage = maskBmp.asImageBitmap()
                        drawImage(
                            image = maskImage,
                            dstOffset = androidx.compose.ui.unit.IntOffset(dstOffset.x.toInt(), dstOffset.y.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(dstSize.width.toInt(), dstSize.height.toInt()),
                            colorFilter = ColorFilter.tint(color.copy(alpha = overlayOpacity), BlendMode.SrcIn),
                        )
                        maskOutlines[annotation.id]?.forEach { outline ->
                            drawPath(
                                path = outlinePath(outline, transform),
                                color = color,
                                style = Stroke(width = outlineWidth, pathEffect = selectionDash),
                            )
                        }
                    }
                    is Shape.Box -> {
                        val boxRect = Rect(shape.x.toFloat(), shape.y.toFloat(), (shape.x + shape.w).toFloat(), (shape.y + shape.h).toFloat())
                        if (!boxRect.overlaps(viewportImageRect)) return@forEach
                        val topLeft = transform.imageToScreen(Offset(shape.x.toFloat(), shape.y.toFloat()))
                        val boxSize = Size(shape.w.toFloat() * transform.totalScale, shape.h.toFloat() * transform.totalScale)
                        drawRect(
                            color = color,
                            topLeft = topLeft,
                            size = boxSize,
                            style = Stroke(width = if (selected) 4.dp.toPx() else 2.dp.toPx(), pathEffect = selectionDash),
                        )
                        if (selected && tool == Tool.SELECTION) {
                            drawHandles(topLeft, boxSize, color)
                        }
                    }
                }
            }
        }

        // Just-drawn shape still awaiting a class pick (SPEC 4.4) -- shown in a neutral color
        // since it has no class yet, so it stays visible instead of disappearing on stroke end.
        pendingShape?.let { shape ->
            when (shape) {
                is Shape.Mask -> {
                    pendingMaskBitmap?.let { maskBmp ->
                        drawImage(
                            image = maskBmp.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(dstOffset.x.toInt(), dstOffset.y.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(dstSize.width.toInt(), dstSize.height.toInt()),
                            colorFilter = ColorFilter.tint(Color.Yellow.copy(alpha = overlayOpacity), BlendMode.SrcIn),
                        )
                    }
                }
                is Shape.Box -> {
                    val topLeft = transform.imageToScreen(Offset(shape.x.toFloat(), shape.y.toFloat()))
                    val boxSize = Size(shape.w.toFloat() * transform.totalScale, shape.h.toFloat() * transform.totalScale)
                    drawRect(color = Color.Yellow, topLeft = topLeft, size = boxSize, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }

        // Live previews for in-progress edits.
        boxPreview?.let { box ->
            val topLeft = transform.imageToScreen(Offset(box.x.toFloat(), box.y.toFloat()))
            val boxSize = Size(box.w.toFloat() * transform.totalScale, box.h.toFloat() * transform.totalScale)
            drawRect(color = Color.White, topLeft = topLeft, size = boxSize, style = Stroke(width = 2.dp.toPx()))
        }

        // Brush: show the actual in-progress paint (not just a cursor), so strokes are visible
        // while drawing instead of only appearing after the finger lifts. `brushPreviewPoint`
        // changing on every paint call is what drives this block to re-read the live bitmap.
        if (brushPreviewPoint != null && strokeHandler is BrushToolHandler) {
            strokeHandler.currentBitmap()?.let { liveBmp ->
                val liveImage = liveBmp.asImageBitmap()
                val previewColor = selectedAnnotation?.let { ann -> classes[ann.classId]?.let { Color(it.color) } } ?: Color.Yellow
                drawImage(
                    image = liveImage,
                    dstOffset = androidx.compose.ui.unit.IntOffset(dstOffset.x.toInt(), dstOffset.y.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(dstSize.width.toInt(), dstSize.height.toInt()),
                    colorFilter = ColorFilter.tint(previewColor.copy(alpha = 0.8f), BlendMode.SrcIn),
                )
            }
        }
        brushPreviewPoint?.let { point ->
            val screenPoint = transform.imageToScreen(point)
            // Dual stroke (dark + light) so the cursor stays visible over any background color.
            drawCircle(color = Color.Black, radius = brushSizeScreenPx, center = screenPoint, style = Stroke(width = 3.dp.toPx()))
            drawCircle(color = Color.White, radius = brushSizeScreenPx, center = screenPoint, style = Stroke(width = 1.5.dp.toPx()))
        }

        pencilPreviewPath?.let { path ->
            if (path.size >= 2) {
                val p = Path()
                val first = transform.imageToScreen(path[0])
                p.moveTo(first.x, first.y)
                for (i in 1 until path.size) {
                    val sp = transform.imageToScreen(path[i])
                    p.lineTo(sp.x, sp.y)
                }
                drawPath(path = p, color = Color.Black, style = Stroke(width = 4.dp.toPx()))
                drawPath(path = p, color = Color.Yellow, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }

    if (overlaysRendering) {
        androidx.compose.material3.Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            tonalElevation = 3.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("Rendering overlays…")
            }
        }
    }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandles(topLeft: Offset, size: Size, color: Color) {
    val radius = HANDLE_RADIUS_DP.dp.toPx() / 2
    val corners = listOf(
        topLeft,
        Offset(topLeft.x + size.width, topLeft.y),
        Offset(topLeft.x + size.width, topLeft.y + size.height),
        Offset(topLeft.x, topLeft.y + size.height),
    )
    for (corner in corners) {
        drawCircle(color = color, radius = radius, center = corner)
        drawCircle(color = Color.White, radius = radius, center = corner, style = Stroke(width = 1.dp.toPx()))
    }
}

/** Topmost annotation under an image-space tap point: mask hit test first, then box (SPEC section 5). */
private fun hitTest(
    dataset: DatasetImage?,
    imagePoint: Offset,
    hiddenClassIds: Set<Int>,
    hiddenAnnotationIds: Set<String>,
): String? {
    val annotations = dataset?.annotations.orEmpty()
        .filterNot { it.classId in hiddenClassIds || it.id in hiddenAnnotationIds }
    val col = imagePoint.x.toInt()
    val row = imagePoint.y.toInt()

    val maskHit = annotations.lastOrNull { annotation ->
        val shape = annotation.shape
        shape is Shape.Mask && col >= 0 && row >= 0 && col < shape.rle.width && row < shape.rle.height && shape.rle.contains(col, row)
    }
    if (maskHit != null) return maskHit.id

    val boxHit = annotations.lastOrNull { annotation ->
        val shape = annotation.shape
        shape is Shape.Box &&
            imagePoint.x >= shape.x && imagePoint.x <= shape.x + shape.w &&
            imagePoint.y >= shape.y && imagePoint.y <= shape.y + shape.h
    }
    return boxHit?.id
}

/** Smallest axis-aligned rect (image pixel coords) covering every point of every ring in
 * [polygons]; [Rect.Zero] for an empty mask -- harmless either way since drawing a mask with no
 * foreground pixels produces no visible pixels regardless of whether it gets culled. */
private fun boundingBox(polygons: List<Polygon>): Rect {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (polygon in polygons) {
        for (point in polygon) {
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
    }
    if (minX > maxX || minY > maxY) return Rect.Zero
    return Rect(minX, minY, maxX, maxY)
}

private fun outlinePath(outline: Polygon, transform: ViewportTransform): Path {
    val path = Path()
    if (outline.isEmpty()) return path
    val first = transform.imageToScreen(Offset(outline[0].x.toFloat(), outline[0].y.toFloat()))
    path.moveTo(first.x, first.y)
    for (i in 1 until outline.size) {
        val p = transform.imageToScreen(Offset(outline[i].x.toFloat(), outline[i].y.toFloat()))
        path.lineTo(p.x, p.y)
    }
    path.close()
    return path
}
