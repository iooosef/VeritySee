package com.example.annotator.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.annotator.core.editing.ChangeClassCommand
import com.example.annotator.core.editing.Command
import com.example.annotator.core.editing.CompositeCommand
import com.example.annotator.core.editing.CreateAnnotationCommand
import com.example.annotator.core.editing.DeleteAnnotationCommand
import com.example.annotator.core.editing.UndoStack
import com.example.annotator.core.editing.buildBulkDeleteCommand
import com.example.annotator.core.formats.DatasetExporter
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.formats.DatasetSplit
import com.example.annotator.core.formats.DetectedFormat
import com.example.annotator.core.formats.ExportFormat
import com.example.annotator.core.formats.ExportOptions
import com.example.annotator.core.formats.ExportResult
import com.example.annotator.core.formats.ExportScope
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.ProjectState
import com.example.annotator.core.model.ReviewState
import com.example.annotator.core.model.Shape
import com.example.annotator.storage.Autosave
import com.example.annotator.storage.DatasetOpener
import com.example.annotator.storage.IndexedDocument
import com.example.annotator.storage.LastExportInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.time.Instant

/** Editor screen state and navigation (SPEC section 4.3), backed by [DatasetOpener] from M4. */
/**
 * Export result plus a human readable destination, and enough information to zip the export
 * together with its source images afterward ([EditorViewModel.zipLastExport]) without redoing
 * the scope/split work.
 */
data class ExportOutcome(
    val result: ExportResult,
    val destinationPath: String,
    val formatFolder: String,
    val imageExportPaths: List<Pair<String, String>>,
)

class EditorViewModel(val opener: DatasetOpener, private val scope: CoroutineScope) {

    var allFiles by mutableStateOf<List<IndexedDocument>>(emptyList())
        private set
    var images by mutableStateOf<List<IndexedDocument>>(emptyList())
        private set
    var format by mutableStateOf(DetectedFormat.NONE)
        private set
    var currentIndex by mutableStateOf(0)
        private set
    var reviewState by mutableStateOf(ProjectState())
        private set
    var currentAnnotations by mutableStateOf<DatasetImage?>(null)
        private set
    var classes by mutableStateOf<Map<Int, ClassDef>>(emptyMap())
        private set

    var overlaysVisible by mutableStateOf(true)
    // Fixed, not user-adjustable (SPEC 4.3 panel intentionally omits this control for now).
    val overlayOpacity: Float = 0.35f

    var selectedAnnotationId by mutableStateOf<String?>(null)
    // Multi-select (SPEC section 8): separate from the single selection above, which drives
    // single-target tools (brush/pencil/box-resize). Only populated while Box-select/Lasso is
    // the active tool.
    var selectedAnnotationIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var hiddenClassIds by mutableStateOf<Set<Int>>(emptySet())
        private set
    var hiddenAnnotationIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var recentClassIds by mutableStateOf<List<Int>>(emptyList())
        private set

    var currentTool by mutableStateOf(Tool.PAN)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    // LRU of 10 images' undo history kept in memory (SPEC section 5); older images' history
    // is simply dropped, not persisted -- undo is a session-scoped editing aid, not data.
    private val undoStacks = object : LinkedHashMap<String, UndoStack>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, UndoStack>?): Boolean = size > 10
    }

    private val stateAutosave = Autosave(scope)

    val currentImage: IndexedDocument? get() = images.getOrNull(currentIndex)

    /** Set when a storage operation fails because folder access was lost (SPEC section 8). */
    var fatalError by mutableStateOf<String?>(null)
        private set

    suspend fun load() {
        try {
            allFiles = withContext(Dispatchers.IO) { opener.listAll() }
            images = opener.images(allFiles)
            format = withContext(Dispatchers.IO) { opener.detectAndRecordFormat(allFiles) }
            classes = withContext(Dispatchers.IO) { opener.ensureClasses(format, allFiles).associateBy { it.id } }
            withContext(Dispatchers.IO) { opener.refreshIndex(allFiles) }
            reviewState = withContext(Dispatchers.IO) { opener.projectStore.readState() }

            val lastPath = reviewState.lastOpened
            currentIndex = lastPath?.let { path -> images.indexOfFirst { it.relativePath == path }.takeIf { it >= 0 } } ?: 0
            loadCurrent()
        } catch (e: SecurityException) {
            fatalError = "Lost access to this folder."
        }
    }

    fun next() {
        if (currentIndex < images.lastIndex) {
            currentIndex++
            loadCurrent()
        }
    }

    fun prev() {
        if (currentIndex > 0) {
            currentIndex--
            loadCurrent()
        }
    }

    fun jumpTo(index: Int) {
        if (index in images.indices) {
            currentIndex = index
            loadCurrent()
        }
    }

    fun jumpToNextUnreviewed() {
        if (images.isEmpty()) return
        val order = images.indices.map { (currentIndex + 1 + it) % images.size }
        val target = order.firstOrNull { reviewState.images[images[it].relativePath]?.reviewed != true }
        if (target != null) jumpTo(target)
    }

    fun jumpToLastEdited() {
        val target = images.indices
            .filter { reviewState.images[images[it].relativePath]?.lastEditedAt != null }
            .maxByOrNull { reviewState.images[images[it].relativePath]!!.lastEditedAt!! }
        if (target != null) jumpTo(target)
    }

    fun toggleReviewed() {
        val image = currentImage ?: return
        val existing = reviewState.images[image.relativePath]
        val nowReviewed = !(existing?.reviewed ?: false)
        val updated = (existing ?: ReviewState()).copy(
            reviewed = nowReviewed,
            reviewedAt = if (nowReviewed) Instant.now().toString() else existing?.reviewedAt,
        )
        reviewState = reviewState.copy(images = reviewState.images + (image.relativePath to updated))
        saveStateDebounced()
    }

    fun reviewedCount(): Int = reviewState.images.values.count { it.reviewed }

    fun rootDisplayPath(): String = opener.projectStore.rootDisplayPath()

    suspend fun lastExportInfo(): LastExportInfo? = withContext(Dispatchers.IO) { opener.projectStore.readLastExport() }

    private fun loadCurrent() {
        val image = currentImage ?: run { currentAnnotations = null; return }
        currentAnnotations = null
        selectedAnnotationId = null
        reviewState = reviewState.copy(lastOpened = image.relativePath)
        saveStateDebounced()
        refreshUndoState()
        scope.launch(Dispatchers.IO) {
            try {
                val annotations = opener.annotationsFor(image, format, allFiles)
                opener.commitImport(annotations)
                withContext(Dispatchers.Main) {
                    if (currentImage?.relativePath == image.relativePath) currentAnnotations = annotations
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) { fatalError = "Lost access to this folder." }
            }
        }
    }

    private fun saveStateDebounced() {
        val snapshot = reviewState
        stateAutosave.schedule { opener.projectStore.writeState(snapshot) }
    }

    /** Called on image change / onPause / export to guarantee state.json is up to date. */
    fun flushState() = stateAutosave.flush()

    // --- Selection and visibility (SPEC 4.3 panel) ---

    fun selectAnnotation(id: String?) {
        selectedAnnotationId = id
        selectedAnnotationIds = emptySet()
    }

    fun setMultiSelection(ids: Set<String>) {
        selectedAnnotationIds = ids
        selectedAnnotationId = null
    }

    fun clearMultiSelection() {
        selectedAnnotationIds = emptySet()
    }

    /**
     * Switching tools clears selection, except moving between Selection/Brush/Eraser/Pencil/Knife
     * keeps the single selection (these all paint or erase the mask you just selected, so
     * clearing there would break that handoff). The multi-selection only survives while
     * Box-select/Lasso stays active -- switching to anything else drops it.
     */
    fun selectTool(tool: Tool) {
        currentTool = tool
        val keepsSingleSelection = setOf(Tool.SELECTION, Tool.BRUSH, Tool.ERASER, Tool.PENCIL, Tool.KNIFE)
        if (tool !in keepsSingleSelection) {
            selectedAnnotationId = null
        }
        if (tool != Tool.BOX_SELECT && tool != Tool.LASSO) {
            selectedAnnotationIds = emptySet()
        }
    }

    fun toggleClassVisibility(classId: Int) {
        hiddenClassIds = if (classId in hiddenClassIds) hiddenClassIds - classId else hiddenClassIds + classId
    }

    fun toggleAnnotationVisibility(annotationId: String) {
        hiddenAnnotationIds = if (annotationId in hiddenAnnotationIds) hiddenAnnotationIds - annotationId else hiddenAnnotationIds + annotationId
    }

    fun changeAnnotationClass(annotationId: String, newClassId: Int) {
        val dataset = currentAnnotations ?: return
        val before = dataset.annotations.firstOrNull { it.id == annotationId }?.classId ?: return
        markClassUsed(newClassId)
        execute(ChangeClassCommand(annotationId, beforeClassId = before, afterClassId = newClassId))
    }

    fun deleteAnnotation(annotationId: String) {
        val dataset = currentAnnotations ?: return
        val index = dataset.annotations.indexOfFirst { it.id == annotationId }
        if (index < 0) return
        execute(DeleteAnnotationCommand(dataset.annotations[index], index))
    }

    fun markClassUsed(classId: Int) {
        recentClassIds = (listOf(classId) + recentClassIds.filterNot { it == classId }).take(8)
    }

    /** Bulk change class for the current multi-selection, as one undoable step (SPEC section 8). */
    fun bulkChangeClass(newClassId: Int) {
        val dataset = currentAnnotations ?: return
        val ids = selectedAnnotationIds
        val commands = dataset.annotations
            .filter { it.id in ids }
            .map { ChangeClassCommand(it.id, beforeClassId = it.classId, afterClassId = newClassId) }
        if (commands.isEmpty()) return
        markClassUsed(newClassId)
        execute(CompositeCommand(commands))
    }

    /** Bulk delete for the current multi-selection, as one undoable step. */
    fun bulkDelete() {
        val dataset = currentAnnotations ?: return
        val command = buildBulkDeleteCommand(dataset.annotations, selectedAnnotationIds) ?: return
        execute(command)
        clearMultiSelection()
    }

    /**
     * Applies [command] through the current image's undo stack. If the result leaves any mask
     * annotation empty, folds an automatic delete of it into the same undo step (SPEC section 7:
     * "empty mask auto delete"), so one undo reverses both atomically.
     */
    fun execute(command: Command) {
        val dataset = currentAnnotations ?: return
        val stack = currentUndoStack()

        val tentative = command.redo(dataset)
        val emptyMaskIds = tentative.annotations
            .filter { (it.shape as? Shape.Mask)?.rle?.isEmpty() == true }
            .map { it.id }
        val finalCommand = if (emptyMaskIds.isEmpty()) {
            command
        } else {
            val deletes = emptyMaskIds.map { id ->
                val index = tentative.annotations.indexOfFirst { it.id == id }
                DeleteAnnotationCommand(tentative.annotations[index], index)
            }
            CompositeCommand(listOf(command) + deletes)
        }

        val updated = stack.execute(finalCommand, dataset)
        currentAnnotations = updated
        pruneSelection(updated)
        refreshUndoState()
        markEditedAndCommit(updated)
    }

    fun undo() {
        val dataset = currentAnnotations ?: return
        val updated = currentUndoStack().undo(dataset) ?: return
        currentAnnotations = updated
        pruneSelection(updated)
        refreshUndoState()
        markEditedAndCommit(updated)
    }

    fun redo() {
        val dataset = currentAnnotations ?: return
        val updated = currentUndoStack().redo(dataset) ?: return
        currentAnnotations = updated
        pruneSelection(updated)
        refreshUndoState()
        markEditedAndCommit(updated)
    }

    /** Drops any selected id(s) that no longer exist in [updated] (e.g. deleted or auto-deleted). */
    private fun pruneSelection(updated: DatasetImage) {
        val existingIds = updated.annotations.map { it.id }.toSet()
        if (selectedAnnotationId != null && selectedAnnotationId !in existingIds) {
            selectedAnnotationId = null
        }
        if (selectedAnnotationIds.isNotEmpty()) {
            selectedAnnotationIds = selectedAnnotationIds.intersect(existingIds)
        }
    }

    private fun currentUndoStack(): UndoStack {
        val path = currentImage?.relativePath ?: "?"
        return undoStacks.getOrPut(path) { UndoStack() }
    }

    private fun refreshUndoState() {
        val stack = currentUndoStack()
        canUndo = stack.canUndo
        canRedo = stack.canRedo
    }

    private fun markEditedAndCommit(dataset: DatasetImage) {
        val image = currentImage ?: return
        val existing = reviewState.images[image.relativePath] ?: ReviewState()
        reviewState = reviewState.copy(images = reviewState.images + (image.relativePath to existing.copy(lastEditedAt = Instant.now().toString())))
        saveStateDebounced()
        scope.launch(Dispatchers.IO) { opener.commitImport(dataset) }
    }

    // --- Class management (SPEC 4.5). Rename/recolor/add are cheap (project.json only);
    // merge/delete scan the whole dataset, so they run as a one-shot background operation. ---

    fun addClass(name: String) {
        scope.launch(Dispatchers.IO) {
            opener.addClass(name)
            withContext(Dispatchers.Main) { refreshClasses() }
        }
    }

    fun addClass(name: String, onCreated: (ClassDef) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val created = opener.addClass(name)
            withContext(Dispatchers.Main) {
                refreshClasses()
                onCreated(created)
            }
        }
    }

    fun renameClass(id: Int, newName: String) {
        scope.launch(Dispatchers.IO) {
            opener.renameClass(id, newName)
            withContext(Dispatchers.Main) { refreshClasses() }
        }
    }

    fun recolorClass(id: Int, newColor: Int) {
        scope.launch(Dispatchers.IO) {
            opener.recolorClass(id, newColor)
            withContext(Dispatchers.Main) { refreshClasses() }
        }
    }

    suspend fun classCounts(): Map<Int, Int> = withContext(Dispatchers.IO) { opener.classCounts(allFiles, format) }

    /**
     * Full dataset export (SPEC section 4.6): a full import of every unopened image (same
     * one-shot heavy path as class management), scope filter, the chosen format/split, then
     * writes to `.annotator/export/<formatFolder>/`. Cooperatively cancellable -- the caller
     * cancels the coroutine this runs in to abort mid-export.
     */
    suspend fun exportDataset(options: ExportOptions, onProgress: (written: Int, total: Int, path: String) -> Unit): ExportOutcome =
        withContext(Dispatchers.IO) {
            val allImages = opener.allAnnotations(allFiles, format)
            val scoped = when (options.scope) {
                ExportScope.ALL -> allImages
                ExportScope.REVIEWED_ONLY -> allImages.filter { reviewState.images[it.path]?.reviewed == true }
            }
            val result = DatasetExporter.export(scoped, classes.values.toList(), options)
            val formatFolder = when (options.format) {
                ExportFormat.YOLO_DETECT -> "yolo_detect"
                ExportFormat.YOLO_SEGMENT -> "yolo_segment"
                ExportFormat.COCO -> "coco"
                ExportFormat.SAM -> "sam"
            }

            val total = result.files.size
            var written = 0
            for ((path, text) in result.files) {
                coroutineContext.ensureActive()
                opener.projectStore.writeExportFile(formatFolder, path, text)
                written++
                withContext(Dispatchers.Main) { onProgress(written, total, path) }
            }
            val destinationPath = opener.projectStore.exportDisplayPath(formatFolder)
            opener.projectStore.writeLastExport(
                LastExportInfo(
                    format = formatFolder,
                    destinationPath = destinationPath,
                    fileCount = result.files.size,
                    warningCount = result.warnings.size,
                    timestampMillis = System.currentTimeMillis(),
                ),
            )
            // Same split and "images/<split>/<basename>" rename convention the exporters
            // themselves use (DatasetExporter.renameForExport / YoloExporter), so a later zip
            // places each source image exactly where the exported labels/json expect to find it.
            val splitLookup: Map<String, String> = options.split?.let { split ->
                val r = DatasetSplit.split(scoped.map { it.path }, split.trainFraction, split.valFraction, split.seed)
                buildMap {
                    r.train.forEach { put(it, "train") }
                    r.val_.forEach { put(it, "val") }
                    r.test.forEach { put(it, "test") }
                }
            } ?: emptyMap()
            val byRelativePath = allFiles.associateBy { it.relativePath }
            val imageExportPaths = scoped.mapNotNull { img ->
                val doc = byRelativePath[img.path] ?: return@mapNotNull null
                val split = splitLookup[img.path]
                val baseName = img.path.substringAfterLast("/")
                val exportPath = if (split != null) "images/$split/$baseName" else "images/$baseName"
                exportPath to doc.documentId
            }
            ExportOutcome(result, destinationPath, formatFolder, imageExportPaths)
        }

    /** Zips a previously finished export together with its source images (see [ProjectStore.zipExport]). */
    suspend fun zipLastExport(outcome: ExportOutcome): String = withContext(Dispatchers.IO) {
        opener.projectStore.zipExport(outcome.formatFolder, outcome.imageExportPaths, System.currentTimeMillis())
    }

    fun mergeClasses(fromId: Int, toId: Int, onDone: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            opener.mergeClasses(fromId, toId, allFiles, format)
            withContext(Dispatchers.Main) {
                refreshClasses()
                loadCurrent()
                onDone()
            }
        }
    }

    fun deleteClassIfUnused(id: Int, onResult: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val deleted = opener.deleteClass(id, allFiles, format)
            withContext(Dispatchers.Main) {
                if (deleted) refreshClasses()
                onResult(deleted)
            }
        }
    }

    private fun refreshClasses() {
        classes = opener.projectStore.readProject()?.classes?.associateBy { it.id } ?: emptyMap()
    }
}
