package com.example.annotator.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.annotator.core.formats.DatasetImage
import com.example.annotator.core.formats.Canonical
import com.example.annotator.core.model.Index
import com.example.annotator.core.model.Project
import com.example.annotator.core.model.ProjectState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * `.annotator/` project storage (SPEC section 3): project.json, state.json, index.json, and
 * the per-image canonical annotation files, written atomically via [SafFiles].
 */
class ProjectStore(private val context: Context, private val treeUri: Uri) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val rootDocId: String by lazy { DocumentsContract.getTreeDocumentId(treeUri) }
    private val annotatorDocId: String by lazy { SafFiles.findOrCreateDir(context, treeUri, rootDocId, ".annotator") }
    private val annotationsDocId: String by lazy { SafFiles.findOrCreateDir(context, treeUri, annotatorDocId, "annotations") }
    private val exportDocId: String by lazy { SafFiles.findOrCreateDir(context, treeUri, annotatorDocId, "export") }

    fun readProject(): Project? {
        val docId = SafFiles.findChild(context, treeUri, annotatorDocId, "project.json")?.first ?: return null
        val text = SafFiles.readText(context, treeUri, docId) ?: return null
        return runCatching { json.decodeFromString(Project.serializer(), text) }.getOrNull()
    }

    fun writeProject(project: Project) {
        val text = json.encodeToString(Project.serializer(), project)
        SafFiles.writeTextAtomic(context, treeUri, annotatorDocId, "project.json", text)
    }

    fun readState(): ProjectState {
        val docId = SafFiles.findChild(context, treeUri, annotatorDocId, "state.json")?.first
        val text = docId?.let { SafFiles.readText(context, treeUri, it) }
        return text?.let { runCatching { json.decodeFromString(ProjectState.serializer(), it) }.getOrNull() } ?: ProjectState()
    }

    fun writeState(state: ProjectState) {
        val text = json.encodeToString(ProjectState.serializer(), state)
        SafFiles.writeTextAtomic(context, treeUri, annotatorDocId, "state.json", text)
    }

    fun readIndex(): Index? {
        val docId = SafFiles.findChild(context, treeUri, annotatorDocId, "index.json")?.first ?: return null
        val text = SafFiles.readText(context, treeUri, docId) ?: return null
        return runCatching { json.decodeFromString(Index.serializer(), text) }.getOrNull()
    }

    fun writeIndex(index: Index) {
        val text = json.encodeToString(Index.serializer(), index)
        SafFiles.writeTextAtomic(context, treeUri, annotatorDocId, "index.json", text)
    }

    /** The canonical annotation file for an image exists at `annotations/<relative path>.json`. */
    fun readCanonical(imageRelativePath: String): DatasetImage? {
        val (dirPath, fileName) = splitPath(imageRelativePath)
        val dirDocId = findDirIfExists(annotationsDocId, dirPath) ?: return null
        val docId = SafFiles.findChild(context, treeUri, dirDocId, "$fileName.json")?.first ?: return null
        val text = SafFiles.readText(context, treeUri, docId) ?: return null
        return runCatching { Canonical.read(text) }.getOrNull()
    }

    fun writeCanonical(image: DatasetImage) {
        val (dirPath, fileName) = splitPath(image.path)
        val dirDocId = SafFiles.ensureDirPath(context, treeUri, annotationsDocId, dirPath)
        val text = Canonical.write(image)
        SafFiles.writeTextAtomic(context, treeUri, dirDocId, "$fileName.json", text)
    }

    /**
     * Absolute filesystem path of the opened dataset folder. SAF tree URIs don't guarantee a
     * real filesystem path exists, but for local storage volumes the tree document id
     * ("primary:DATASET" or "1234-5678:Folder") reliably maps to one: "primary" is internal
     * storage, anything else is an SD card / USB volume id. Falls back to the raw SAF URI if the
     * id doesn't match that shape (e.g. a cloud provider).
     */
    fun rootDisplayPath(): String {
        val docId = rootDocId
        val colon = docId.indexOf(':')
        if (colon < 0) return treeUri.toString()
        val volumeId = docId.substring(0, colon)
        val relativePath = docId.substring(colon + 1)
        val base = if (volumeId == "primary") "/storage/emulated/0" else "/storage/$volumeId"
        return if (relativePath.isEmpty()) base else "$base/$relativePath"
    }

    /** Leaf name of the opened dataset folder, e.g. "DATASET" for "primary:Pictures/DATASET". */
    fun rootDisplayName(): String {
        val docId = rootDocId
        val relativePath = docId.substringAfter(':', docId)
        return relativePath.substringAfterLast('/').ifEmpty { "dataset" }
    }

    /** Absolute filesystem path of an export, for display after an export finishes. */
    fun exportDisplayPath(formatFolder: String): String = "${rootDisplayPath()}/.annotator/export/$formatFolder"

    /**
     * Zips every file written for [formatFolder] together with the given source images (export
     * relative path to source document id -- see [com.example.annotator.editor.ExportOutcome]),
     * into `<dataset root>/zippedExports/<dataset name>_<yyyyMMdd_HHmmss>.zip`. Images are copied
     * in at the same relative path the export's own files reference them by (e.g.
     * "images/train/foo.jpg"), so the zip is a complete, trainable dataset on its own.
     */
    fun zipExport(formatFolder: String, images: List<Pair<String, String>>, timestampMillis: Long): String {
        val formatDocId = SafFiles.findChild(context, treeUri, exportDocId, formatFolder)?.first
            ?: error("Export folder '$formatFolder' not found -- run an export first")
        val exportFiles = SafFiles.listFilesRecursive(context, treeUri, formatDocId)

        val zippedExportsDocId = SafFiles.findOrCreateDir(context, treeUri, rootDocId, "zippedExports")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis))
        val zipName = "${rootDisplayName()}_$stamp.zip"
        val zipDocId = SafFiles.findOrCreateFile(context, treeUri, zippedExportsDocId, zipName, mime = "application/zip")

        SafFiles.openOutputStream(context, treeUri, zipDocId)?.use { out ->
            ZipOutputStream(out).use { zip ->
                for ((relativePath, docId) in exportFiles) {
                    zip.putNextEntry(ZipEntry(relativePath))
                    SafFiles.openInputStream(context, treeUri, docId)?.use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                for ((exportPath, sourceDocId) in images) {
                    zip.putNextEntry(ZipEntry(exportPath))
                    SafFiles.openInputStream(context, treeUri, sourceDocId)?.use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Could not open output stream for '$zipName'")

        return "${rootDisplayPath()}/zippedExports/$zipName"
    }

    fun writeLastExport(info: LastExportInfo) {
        val text = json.encodeToString(LastExportInfo.serializer(), info)
        SafFiles.writeTextAtomic(context, treeUri, annotatorDocId, "last_export.json", text)
    }

    fun readLastExport(): LastExportInfo? {
        val docId = SafFiles.findChild(context, treeUri, annotatorDocId, "last_export.json")?.first ?: return null
        val text = SafFiles.readText(context, treeUri, docId) ?: return null
        return runCatching { json.decodeFromString(LastExportInfo.serializer(), text) }.getOrNull()
    }

    /** Writes one export output file under `.annotator/export/<formatFolder>/<relativePath>`. */
    fun writeExportFile(formatFolder: String, relativePath: String, text: String) {
        val formatDocId = SafFiles.findOrCreateDir(context, treeUri, exportDocId, formatFolder)
        val (dirPath, fileName) = splitPath(relativePath)
        val dirDocId = SafFiles.ensureDirPath(context, treeUri, formatDocId, dirPath)
        SafFiles.writeTextAtomic(context, treeUri, dirDocId, fileName, text)
    }

    private fun splitPath(relativePath: String): Pair<String, String> {
        val lastSlash = relativePath.lastIndexOf("/")
        return if (lastSlash < 0) "" to relativePath else relativePath.substring(0, lastSlash) to relativePath.substring(lastSlash + 1)
    }

    private fun findDirIfExists(rootId: String, relativeDirPath: String): String? {
        var current = rootId
        if (relativeDirPath.isEmpty()) return current
        for (segment in relativeDirPath.split("/")) {
            if (segment.isEmpty()) continue
            val (id, mime) = SafFiles.findChild(context, treeUri, current, segment) ?: return null
            if (mime != DocumentsContract.Document.MIME_TYPE_DIR) return null
            current = id
        }
        return current
    }
}

@Serializable
data class LastExportInfo(
    val format: String,
    val destinationPath: String,
    val fileCount: Int,
    val warningCount: Int,
    val timestampMillis: Long,
)
