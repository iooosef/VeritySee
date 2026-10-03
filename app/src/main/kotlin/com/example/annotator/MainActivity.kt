package com.example.annotator

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.annotator.editor.EditorScreen
import com.example.annotator.editor.EditorViewModel
import com.example.annotator.storage.DatasetOpener
import com.example.annotator.storage.RecentFolders
import com.example.annotator.storage.SafFolderPicker
import com.example.annotator.storage.rememberFolderPickerLauncher
import com.example.annotator.ui.HomeScreen
import com.example.annotator.ui.ImageListScreen
import kotlinx.coroutines.launch

private val VeritySeeYellow = Color(0xFFFFD600)
private val VeritySeeYellowDark = Color(0xFFF9A825)
private val VeritySeeYellowLight = Color(0xFFFFF9C4)

private val VeritySeeColorScheme = lightColorScheme(
    primary = VeritySeeYellowDark,
    onPrimary = Color.Black,
    primaryContainer = VeritySeeYellow,
    onPrimaryContainer = Color.Black,
    secondary = VeritySeeYellowDark,
    background = VeritySeeYellowLight,
    onBackground = Color.Black,
    surface = VeritySeeYellow,
    onSurface = Color.Black,
)

private sealed interface Screen {
    data object Home : Screen
    data object Editor : Screen
    data object ImageList : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = VeritySeeColorScheme) {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentFolders = remember { RecentFolders(context) }

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var viewModel by remember { mutableStateOf<EditorViewModel?>(null) }

    fun openFolder(uri: Uri) {
        SafFolderPicker.takePersistableAccess(context, uri)
        scope.launch { recentFolders.addRecent(uri.toString()) }
        val opener = DatasetOpener(context, uri)
        val vm = EditorViewModel(opener, scope)
        viewModel = vm
        scope.launch {
            vm.load()
            screen = Screen.Editor
        }
    }

    val pickerLauncher = rememberFolderPickerLauncher { uri -> openFolder(uri) }

    when (val current = screen) {
        is Screen.Home -> {
            val loadingLabel = viewModel?.loadProgress
            if (loadingLabel != null) {
                LoadingScreen(label = loadingLabel)
            } else {
                HomeScreen(
                    recentFolders = recentFolders,
                    onOpenFolderClick = { pickerLauncher.launch(null) },
                    onRecentFolderClick = { uri -> openFolder(uri) },
                )
            }
        }
        is Screen.Editor -> viewModel?.let { vm ->
            EditorScreen(
                viewModel = vm,
                onOpenImageList = { screen = Screen.ImageList },
                onLostPermission = { screen = Screen.Home },
                onReturnToMenu = { screen = Screen.Home },
            )
        }
        is Screen.ImageList -> viewModel?.let { vm ->
            ImageListScreen(
                opener = vm.opener,
                images = vm.images,
                reviewState = vm.reviewState,
                onImageClick = { index -> vm.jumpTo(index); screen = Screen.Editor },
                onBack = { screen = Screen.Editor },
            )
        }
    }
}

@Composable
private fun LoadingScreen(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(label)
        }
    }
}
