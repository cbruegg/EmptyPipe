import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Download
import androidx.compose.material.icons.sharp.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppTab(val icon: ImageVector, val description: String) {
    Download(Icons.Sharp.Download, "Download"), DownloadedVideos(Icons.Sharp.PlayCircle, "Videos")
}

@Composable
fun App() {
    val downloadManager = DownloadManager()

    MaterialTheme {
        var selectedAppTab by remember { mutableStateOf(AppTab.Download) }
        Column(Modifier.fillMaxSize()) {
            when (selectedAppTab) {
                AppTab.Download -> DownloadScreen(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    downloadManager
                )

                AppTab.DownloadedVideos -> DownloadedVideosScreen(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    downloadManager
                )
            }
            BottomNavigation(modifier = Modifier.fillMaxWidth()) {
                AppTab.entries.forEach { tab ->
                    BottomNavigationItem(
                        selected = tab == selectedAppTab,
                        onClick = { selectedAppTab = tab },
                        label = { Text(tab.description) },
                        icon = { Icon(tab.icon, contentDescription = tab.description) }
                    )
                }
            }
        }
    }
}

// TODO:
//  - Clean up code
//  - Separate screen for video playback (just full-screen - maybe just immediately enter AVPlayer full-screen mode?)
//  - URL bar to paste video URL
//  - Feature to delete videos
//  - Show video thumbnail
//  - Notarize and create AltStore source
//  - yt-dlp backend