package com.example.annotator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.annotator.storage.DatasetOpener
import com.example.annotator.storage.RecentFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GITHUB_URL = "https://github.com/iooosef/VeritySee"

@Composable
fun HomeScreen(recentFolders: RecentFolders, onOpenFolderClick: () -> Unit, onRecentFolderClick: (Uri) -> Unit) {
    val recents by recentFolders.recentUris.collectAsState(initial = emptyList())
    var aboutOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("VeritySee", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onOpenFolderClick, modifier = Modifier.fillMaxWidth()) {
                Text("Open folder")
            }
            if (recents.isNotEmpty()) {
                Text("Recent", style = MaterialTheme.typography.titleSmall)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recents) { uriString ->
                        RecentFolderRow(uriString, onClick = { onRecentFolderClick(Uri.parse(uriString)) })
                    }
                }
            }
        }
        TextButton(
            onClick = { aboutOpen = true },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            Text("About this app")
        }
    }

    if (aboutOpen) {
        AboutDialog(onDismiss = { aboutOpen = false })
    }
}

/** Brief summary pulled from README.md's "Why this exists" / "What it does" sections. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About VeritySee") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("An Android app for reviewing and editing image segmentation datasets (YOLO, COCO, SAM) on your phone or tablet.")
                Text(
                    "Open a folder, see the auto-labeled annotations, fix them with box/brush/pencil, " +
                        "track what's been reviewed, and export clean YOLO/COCO/SAM datasets. Your original " +
                        "label files are never modified.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))) }) {
                Text("View on GitHub")
            }
        },
    )
}

@Composable
private fun RecentFolderRow(uriString: String, onClick: () -> Unit) {
    val context = LocalContext.current
    var progress by remember(uriString) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(uriString) {
        progress = withContext(Dispatchers.IO) {
            runCatching {
                val opener = DatasetOpener(context, Uri.parse(uriString))
                val allFiles = opener.listAll()
                val images = opener.images(allFiles)
                val state = opener.projectStore.readState()
                val reviewed = images.count { state.images[it.relativePath]?.reviewed == true }
                reviewed to images.size
            }.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(folderDisplayName(uriString))
        // "List of recently opened folders with progress, e.g. 312 / 500 reviewed" (SPEC 4.1).
        progress?.let { (reviewed, total) ->
            Text("$reviewed/$total reviewed", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** A readable folder name from a SAF tree URI, e.g. "content://.../tree/primary%3ADATASET" -> "DATASET". */
private fun folderDisplayName(uriString: String): String {
    val uri = Uri.parse(uriString)
    val lastSegment = uri.lastPathSegment ?: return uriString
    return lastSegment.substringAfterLast(':').substringAfterLast('/')
}
