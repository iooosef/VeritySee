package com.example.annotator.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.annotator.storage.DatasetOpener
import com.example.annotator.storage.RecentFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(recentFolders: RecentFolders, onOpenFolderClick: () -> Unit, onRecentFolderClick: (Uri) -> Unit) {
    val recents by recentFolders.recentUris.collectAsState(initial = emptyList())

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
    }
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
