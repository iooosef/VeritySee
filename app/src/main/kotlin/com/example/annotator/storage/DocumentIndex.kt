package com.example.annotator.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class IndexedDocument(
    val relativePath: String,
    val documentId: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
)

/**
 * Recursively lists every file under a SAF tree using [DocumentsContract] queries directly.
 * `DocumentFile.listFiles()` issues one round trip per child and is too slow for datasets
 * with thousands of files (CLAUDE.md gotcha).
 */
object DocumentIndex {

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    fun listAll(context: Context, treeUri: Uri): List<IndexedDocument> {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val results = mutableListOf<IndexedDocument>()
        val stack = ArrayDeque<Pair<String, String>>()
        stack.addLast(rootDocId to "")

        while (stack.isNotEmpty()) {
            val (docId, prefix) = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx)
                    val mime = cursor.getString(mimeIdx) ?: ""
                    val size = cursor.getLong(sizeIdx)
                    val modified = cursor.getLong(modIdx)
                    val relativePath = if (prefix.isEmpty()) name else "$prefix/$name"

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        stack.addLast(childId to relativePath)
                    } else {
                        results.add(IndexedDocument(relativePath, childId, size, modified, mime))
                    }
                }
            }
        }
        return results
    }
}
