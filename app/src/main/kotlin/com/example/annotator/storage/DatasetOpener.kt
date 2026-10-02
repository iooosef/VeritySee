package com.example.annotator.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.formats.DetectedFormat
import com.example.annotator.core.formats.FormatDetector
import com.example.annotator.core.formats.ImportResult
import com.example.annotator.core.formats.coco.CocoImporter
import com.example.annotator.core.formats.sam.SamImporter
import com.example.annotator.core.formats.yolo.YoloImporter
import com.example.annotator.core.formats.ClassOps
import com.example.annotator.core.formats.ColorPalette
import com.example.annotator.core.formats.yolo.YoloClasses
import com.example.annotator.core.model.ClassDef
import com.example.annotator.core.model.ImageSize
import com.example.annotator.core.model.Index
import com.example.annotator.core.model.IndexEntry
import com.example.annotator.core.model.Project

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "bmp", "webp")

/**
 * Opens a dataset folder: fast indexing (SPEC section 7), format detection, and lazy
 * per-image import that falls back to the original labels until a canonical file exists
 * (SPEC section 3).
 */
class DatasetOpener(private val context: Context, val treeUri: Uri) {

    val projectStore = ProjectStore(context, treeUri)
    private var cachedWholeFormatImport: ImportResult? = null

    /** Lists every file under the tree once, fast, via [DocumentIndex] (not DocumentFile). */
    fun listAll(): List<IndexedDocument> = DocumentIndex.listAll(context, treeUri)

    fun documentUriFor(documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    fun images(allFiles: List<IndexedDocument>): List<IndexedDocument> =
        allFiles.filter { it.relativePath.substringAfterLast(".", "").lowercase() in IMAGE_EXTENSIONS }

    /** Detects the format, then persists it to project.json if this is the first open. */
    fun detectAndRecordFormat(allFiles: List<IndexedDocument>): DetectedFormat {
        val existing = projectStore.readProject()
        if (existing != null) return DetectedFormat.valueOf(existing.detectedFormat)

        val byPath = allFiles.associateBy { it.relativePath }
        val format = FormatDetector.detect(allFiles.map { it.relativePath }) { path ->
            byPath[path]?.let { SafFiles.readText(context, treeUri, it.documentId) }
        }
        projectStore.writeProject(Project(detectedFormat = format.name))
        return format
    }

    /** Updates `index.json`, returning which paths are new/changed/removed since last open. */
    fun refreshIndex(allFiles: List<IndexedDocument>): com.example.annotator.core.formats.IndexDiffResult {
        val cached = projectStore.readIndex()?.entries.orEmpty()
        val current = allFiles.map { IndexEntry(it.relativePath, it.size, it.lastModified) }
        val diff = com.example.annotator.core.formats.IndexDiff.diff(cached, current)
        projectStore.writeIndex(Index(entries = current))
        return diff
    }

    /**
     * Annotations for one image: canonical file if it exists, otherwise a lazy import from
     * the detected original format (not yet written to canonical -- call [commitImport] to
     * persist it once the image has actually been opened).
     */
    fun annotationsFor(image: IndexedDocument, format: DetectedFormat, allFiles: List<IndexedDocument>): DatasetImage {
        projectStore.readCanonical(image.relativePath)?.let { return it }

        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, image.documentId)
        val size = ImageBounds.read(context, uri) ?: ImageSize(0, 0)

        return when (format) {
            DetectedFormat.YOLO -> importYoloOne(image, size, allFiles)
            DetectedFormat.COCO -> importFromSharedJson(image, allFiles) ?: DatasetImage(image.relativePath, size, emptyList())
            DetectedFormat.SAM -> importSamOne(image, allFiles) ?: DatasetImage(image.relativePath, size, emptyList())
            DetectedFormat.NONE -> DatasetImage(image.relativePath, size, emptyList())
        }
    }

    fun commitImport(image: DatasetImage) = projectStore.writeCanonical(image)

    /**
     * The dataset's classes: read from project.json if already recorded, otherwise derived
     * once from the detected format's own class list (data.yaml/classes.txt for YOLO, the
     * shared JSON's categories for COCO, a single default "object" class for SAM) and
     * persisted back to project.json so later opens don't redo the work.
     *
     * When YOLO has no data.yaml/classes.txt, the class ids actually used are discovered by
     * scanning every label file's class-id tokens (lightweight: no image size, no polygon
     * rasterization) rather than assuming only class 0 exists -- a dataset using class index 2
     * but no yaml would otherwise end up with a single bogus "object" class.
     */
    fun ensureClasses(format: DetectedFormat, allFiles: List<IndexedDocument>): List<ClassDef> {
        val project = projectStore.readProject()
        if (project != null && project.classes.isNotEmpty()) return project.classes

        val byPath = allFiles.associateBy { it.relativePath }
        val names: List<String> = when (format) {
            DetectedFormat.YOLO -> {
                val yamlPath = byPath.keys.firstOrNull { it.endsWith("data.yaml") || it.endsWith("data.yml") }
                val classesPath = byPath.keys.firstOrNull { it.endsWith("classes.txt") }
                when {
                    yamlPath != null -> YoloClasses.parseDataYaml(SafFiles.readText(context, treeUri, byPath.getValue(yamlPath).documentId) ?: "")
                    classesPath != null -> YoloClasses.parseClassesTxt(SafFiles.readText(context, treeUri, byPath.getValue(classesPath).documentId) ?: "")
                    else -> namesFromUsedYoloClassIds(allFiles)
                }
            }
            DetectedFormat.COCO -> {
                val jsonDoc = allFiles.firstOrNull { it.relativePath.endsWith(".json") }
                val text = jsonDoc?.let { SafFiles.readText(context, treeUri, it.documentId) }
                text?.let { CocoImporter.importFile(it).also { r -> cachedWholeFormatImport = r }.classes.map { c -> c.name } } ?: emptyList()
            }
            DetectedFormat.SAM, DetectedFormat.NONE -> emptyList()
        }
        val classes = if (names.isEmpty()) {
            listOf(ClassDef(0, "object", ColorPalette.colorFor(0)))
        } else {
            names.mapIndexed { id, name -> ClassDef(id, name, ColorPalette.colorFor(id)) }
        }

        val current = project ?: Project(detectedFormat = format.name)
        projectStore.writeProject(current.copy(classes = classes))
        return classes
    }

    private fun namesFromUsedYoloClassIds(allFiles: List<IndexedDocument>): List<String> {
        val labelTexts = allFiles
            .filter { it.relativePath.contains("/labels/") || it.relativePath.startsWith("labels/") }
            .filter { it.relativePath.endsWith(".txt") }
            .mapNotNull { SafFiles.readText(context, treeUri, it.documentId) }
        val usedIds = YoloImporter.countClassIds(labelTexts).keys
        if (usedIds.isEmpty()) return emptyList()
        val maxId = usedIds.max()
        return (0..maxId).map { "class_$it" }
    }

    /**
     * Class usage counts across the whole dataset, without importing full shapes -- just
     * tallies class id references (label-file tokens for YOLO, category_id for COCO/SAM).
     * Used for class management; deliberately much cheaper than a full dataset import.
     */
    fun classCounts(allFiles: List<IndexedDocument>, format: DetectedFormat): Map<Int, Int> {
        val byPath = allFiles.associateBy { it.relativePath }
        return when (format) {
            DetectedFormat.YOLO -> {
                val labelTexts = allFiles
                    .filter { it.relativePath.contains("/labels/") || it.relativePath.startsWith("labels/") }
                    .filter { it.relativePath.endsWith(".txt") }
                    .mapNotNull { SafFiles.readText(context, treeUri, it.documentId) }
                YoloImporter.countClassIds(labelTexts)
            }
            DetectedFormat.COCO -> {
                val jsonDoc = allFiles.firstOrNull { it.relativePath.endsWith(".json") }
                val text = jsonDoc?.let { SafFiles.readText(context, treeUri, it.documentId) }
                text?.let { CocoImporter.countClassIds(it) } ?: emptyMap()
            }
            DetectedFormat.SAM -> {
                val counts = mutableMapOf<Int, Int>()
                for (image in images(allFiles)) {
                    val jsonPath = image.relativePath.substringBeforeLast(".") + ".json"
                    val text = byPath[jsonPath]?.let { SafFiles.readText(context, treeUri, it.documentId) } ?: continue
                    for ((id, count) in SamImporter.countClassIds(text)) {
                        counts[id] = (counts[id] ?: 0) + count
                    }
                }
                counts
            }
            DetectedFormat.NONE -> emptyMap()
        }
    }

    /**
     * Every image's annotations, importing (and committing to canonical) any image that
     * hasn't been opened yet. Deliberately heavier than normal lazy browsing -- used only
     * for operations that need full shapes (merge), not for counts, and not during normal
     * navigation.
     */
    fun allAnnotations(allFiles: List<IndexedDocument>, format: DetectedFormat): List<DatasetImage> {
        return images(allFiles).map { image ->
            val existing = projectStore.readCanonical(image.relativePath)
            existing ?: annotationsFor(image, format, allFiles).also { commitImport(it) }
        }
    }

    fun addClass(name: String): ClassDef {
        val project = projectStore.readProject() ?: Project()
        val nextId = (project.classes.maxOfOrNull { it.id } ?: -1) + 1
        val newClass = ClassDef(nextId, name, ColorPalette.colorFor(nextId))
        projectStore.writeProject(project.copy(classes = project.classes + newClass))
        return newClass
    }

    fun renameClass(id: Int, newName: String) {
        val project = projectStore.readProject() ?: return
        projectStore.writeProject(project.copy(classes = project.classes.map { if (it.id == id) it.copy(name = newName) else it }))
    }

    fun recolorClass(id: Int, newColor: Int) {
        val project = projectStore.readProject() ?: return
        projectStore.writeProject(project.copy(classes = project.classes.map { if (it.id == id) it.copy(color = newColor) else it }))
    }

    /** Only allowed when the class has zero annotations across the whole dataset. */
    fun deleteClass(id: Int, allFiles: List<IndexedDocument>, format: DetectedFormat): Boolean {
        val counts = classCounts(allFiles, format)
        if ((counts[id] ?: 0) > 0) return false
        val project = projectStore.readProject() ?: return false
        projectStore.writeProject(project.copy(classes = project.classes.filter { it.id != id }))
        return true
    }

    /** Reassigns every annotation in [fromId] to [toId] across the whole dataset, then drops [fromId]. */
    fun mergeClasses(fromId: Int, toId: Int, allFiles: List<IndexedDocument>, format: DetectedFormat) {
        for (image in images(allFiles)) {
            val existing = projectStore.readCanonical(image.relativePath) ?: annotationsFor(image, format, allFiles)
            val remapped = ClassOps.remapClass(existing.annotations, fromId, toId)
            if (remapped != existing.annotations || projectStore.readCanonical(image.relativePath) == null) {
                commitImport(existing.copy(annotations = remapped))
            }
        }
        val project = projectStore.readProject() ?: return
        projectStore.writeProject(project.copy(classes = project.classes.filter { it.id != fromId }))
    }

    private fun importYoloOne(image: IndexedDocument, size: ImageSize, allFiles: List<IndexedDocument>): DatasetImage {
        val byPath = allFiles.associateBy { it.relativePath }
        val labelPath = com.example.annotator.core.formats.yolo.YoloLayout.labelPathFor(image.relativePath)
        val textFiles = buildMap {
            labelPath?.let { path -> byPath[path]?.let { put(path, SafFiles.readText(context, treeUri, it.documentId) ?: "") } }
            byPath["data.yaml"]?.let { put("data.yaml", SafFiles.readText(context, treeUri, it.documentId) ?: "") }
            byPath["classes.txt"]?.let { put("classes.txt", SafFiles.readText(context, treeUri, it.documentId) ?: "") }
        }
        val result = YoloImporter.import(listOf(image.relativePath), textFiles) { size }
        return result.images.single()
    }

    /** COCO: one shared JSON file covers the whole dataset (or split); parsed once and cached. */
    private fun importFromSharedJson(image: IndexedDocument, allFiles: List<IndexedDocument>): DatasetImage? {
        val result = cachedWholeFormatImport ?: run {
            val jsonDoc = allFiles.firstOrNull { it.relativePath.endsWith(".json") } ?: return null
            val text = SafFiles.readText(context, treeUri, jsonDoc.documentId) ?: return null
            CocoImporter.importFile(text).also { cachedWholeFormatImport = it }
        }
        return result.images.firstOrNull { it.path == image.relativePath }
    }

    /** SAM (SA-1B): one JSON per image, same base name, next to the image. */
    private fun importSamOne(image: IndexedDocument, allFiles: List<IndexedDocument>): DatasetImage? {
        val jsonPath = image.relativePath.substringBeforeLast(".") + ".json"
        val jsonDoc = allFiles.firstOrNull { it.relativePath == jsonPath } ?: return null
        val text = SafFiles.readText(context, treeUri, jsonDoc.documentId) ?: return null
        return SamImporter.importFile(text).images.firstOrNull()
    }
}
