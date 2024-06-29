import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class AppTab(val icon: ImageVector, val description: String) {
    Download(Icons.Sharp.Download, "Download"), DownloadedVideos(Icons.Sharp.PlayCircle, "Videos")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App() {
    val downloadManager = DownloadManager()

    MaterialTheme {
        var selectedAppTab by remember { mutableStateOf(AppTab.Download) }
        val pagerState = rememberPagerState(pageCount = { 2 })
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().weight(1f),
                userScrollEnabled = false,
                key = { AppTab.entries[it].name },
                beyondBoundsPageCount = 1 // don't forget state of other page
            ) { page ->
                when (AppTab.entries[page]) {
                    AppTab.Download ->
                        DownloadScreen(Modifier.fillMaxSize(), downloadManager)

                    AppTab.DownloadedVideos ->
                        DownloadedVideosScreen(Modifier.fillMaxSize(), downloadManager)
                }
            }
            BottomNavigation(modifier = Modifier.fillMaxWidth()) {
                AppTab.entries.forEach { tab ->
                    BottomNavigationItem(
                        selected = tab == selectedAppTab,
                        onClick = {
                            selectedAppTab = tab
                            scope.launch { pagerState.scrollToPage(AppTab.entries.indexOf(tab)) }
                        },
                        label = { Text(tab.description) },
                        icon = { Icon(tab.icon, contentDescription = tab.description) }
                    )
                }
            }
        }
    }
}

// TODO:
//  - Separate screen for video playback (just full-screen - maybe just immediately enter AVPlayer full-screen mode?)
//  - Notarize and create AltStore source
//  - yt-dlp backend
//  - Configurable Piped instance
//  - BEFORE SOURCE PUBLICATION: WIPE "***REMOVED***" FROM GIT HISTORY
//  - Design icon
//  - Add "share to EmptyPipe" iOS action