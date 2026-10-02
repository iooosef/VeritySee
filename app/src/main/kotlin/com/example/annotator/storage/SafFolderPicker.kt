package com.example.annotator.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** SAF folder picker (SPEC section 7): persists read/write permission on the picked tree. */
object SafFolderPicker {
    fun takePersistableAccess(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(treeUri, flags)
    }
}

@Composable
fun rememberFolderPickerLauncher(onPicked: (Uri) -> Unit): ActivityResultLauncher<Uri?> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            SafFolderPicker.takePersistableAccess(context, uri)
            onPicked(uri)
        }
    }
}
