package com.example.annotator.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Low level SAF helpers: find-or-create documents by path segment, atomic text writes. */
object SafFiles {

    private const val DIR_MIME = DocumentsContract.Document.MIME_TYPE_DIR

    fun findChild(context: Context, treeUri: Uri, parentDocId: String, name: String): Pair<String, String>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == name) {
                    return cursor.getString(idIdx) to cursor.getString(mimeIdx)
                }
            }
        }
        return null
    }

    fun findOrCreateDir(context: Context, treeUri: Uri, parentDocId: String, name: String): String {
        findChild(context, treeUri, parentDocId, name)?.let { (id, mime) ->
            if (mime == DIR_MIME) return id
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        return DocumentsContract.createDocument(context.contentResolver, parentUri, DIR_MIME, name)?.let {
            DocumentsContract.getDocumentId(it)
        } ?: error("could not create directory '$name'")
    }

    /** Creates nested directories for each segment of [relativeDirPath] ("a/b/c"), returns the leaf doc id. */
    fun ensureDirPath(context: Context, treeUri: Uri, rootDocId: String, relativeDirPath: String): String {
        var current = rootDocId
        if (relativeDirPath.isEmpty()) return current
        for (segment in relativeDirPath.split("/")) {
            if (segment.isEmpty()) continue
            current = findOrCreateDir(context, treeUri, current, segment)
        }
        return current
    }

    fun findOrCreateFile(context: Context, treeUri: Uri, parentDocId: String, name: String, mime: String = "application/json"): String {
        findChild(context, treeUri, parentDocId, name)?.let { (id, _) -> return id }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        return DocumentsContract.createDocument(context.contentResolver, parentUri, mime, name)?.let {
            DocumentsContract.getDocumentId(it)
        } ?: error("could not create file '$name'")
    }

    fun readText(context: Context, treeUri: Uri, documentId: String): String? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
    }

    /**
     * Writes [text] to `<name>.tmp` then renames over [name] (SPEC section 7): a crash never
     * leaves a half written file. Falls back to writing the target directly if the provider
     * doesn't support rename.
     */
    fun writeTextAtomic(context: Context, treeUri: Uri, parentDocId: String, name: String, text: String) {
        val tmpName = "$name.tmp"
        val tmpDocId = findOrCreateFile(context, treeUri, parentDocId, tmpName)
        val tmpUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, tmpDocId)
        context.contentResolver.openOutputStream(tmpUri, "wt")?.use { it.write(text.toByteArray()) }

        try {
            findChild(context, treeUri, parentDocId, name)?.let { (existingId, _) ->
                val existingUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, existingId)
                DocumentsContract.deleteDocument(context.contentResolver, existingUri)
            }
            val renamed = DocumentsContract.renameDocument(context.contentResolver, tmpUri, name)
            if (renamed == null) {
                writeDirect(context, treeUri, parentDocId, name, text)
                DocumentsContract.deleteDocument(context.contentResolver, tmpUri)
            }
        } catch (e: IOException) {
            writeDirect(context, treeUri, parentDocId, name, text)
        }
    }

    private fun writeDirect(context: Context, treeUri: Uri, parentDocId: String, name: String, text: String) {
        val docId = findOrCreateFile(context, treeUri, parentDocId, name)
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
    }

    fun openOutputStream(context: Context, treeUri: Uri, docId: String): OutputStream? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return context.contentResolver.openOutputStream(uri, "wt")
    }

    fun openInputStream(context: Context, treeUri: Uri, docId: String): InputStream? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return context.contentResolver.openInputStream(uri)
    }

    /** Every file under [dirDocId], recursively, as (path relative to it) to document id. */
    fun listFilesRecursive(context: Context, treeUri: Uri, dirDocId: String, prefix: String = ""): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx)
                val mime = cursor.getString(mimeIdx)
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                if (mime == DIR_MIME) {
                    result += listFilesRecursive(context, treeUri, id, path)
                } else {
                    result += path to id
                }
            }
        }
        return result
    }
}
