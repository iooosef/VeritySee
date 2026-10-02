package com.example.annotator.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.annotator.render.DownsampledImageLoader
import com.example.annotator.storage.DatasetOpener
import com.example.annotator.storage.IndexedDocument
import com.example.annotator.core.model.ProjectState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ImageFilter { ALL, UNREVIEWED, REVIEWED, NO_ANNOTATIONS, EDITED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageListScreen(
    opener: DatasetOpener,
    images: List<IndexedDocument>,
    reviewState: ProjectState,
    onImageClick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var filter by remember { mutableStateOf(ImageFilter.ALL) }

    val filtered = remember(filter, images, reviewState) {
        images.withIndex().filter { (_, image) ->
            val state = reviewState.images[image.relativePath]
            when (filter) {
                ImageFilter.ALL -> true
                ImageFilter.REVIEWED -> state?.reviewed == true
                ImageFilter.UNREVIEWED -> state?.reviewed != true
                ImageFilter.EDITED -> state?.lastEditedAt != null
                ImageFilter.NO_ANNOTATIONS -> opener.projectStore.readCanonical(image.relativePath)?.annotations?.isEmpty() == true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Images") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            FilterRow(filter) { filter = it }
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 96.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered.size) { i ->
                    val (originalIndex, image) = filtered[i]
                    ThumbnailCell(
                        opener = opener,
                        image = image,
                        reviewed = reviewState.images[image.relativePath]?.reviewed == true,
                        onClick = { onImageClick(originalIndex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(selected: ImageFilter, onSelect: (ImageFilter) -> Unit) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.padding(8.dp)) {
        ImageFilter.values().forEach { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

@Composable
private fun ThumbnailCell(opener: DatasetOpener, image: IndexedDocument, reviewed: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(image.documentId) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(image.documentId) {
        bitmap = withContext(Dispatchers.IO) {
            DownsampledImageLoader.decode(context, opener.documentUriFor(image.documentId), maxDimension = 150)
        }
    }

    Box(modifier = Modifier.aspectRatio(1f).padding(2.dp).clickable(onClick = onClick)) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = image.relativePath, modifier = Modifier.fillMaxSize())
        } ?: Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {}
        if (reviewed) {
            Text(
                "OK",
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
