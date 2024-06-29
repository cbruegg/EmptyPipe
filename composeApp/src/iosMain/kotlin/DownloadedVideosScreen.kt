import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import platform.Foundation.NSURL

@Composable
fun DownloadedVideosScreen(modifier: Modifier = Modifier, downloadManager: DownloadManager) {
    Column(modifier) {
        AvailableFiles(downloadManager)
    }
}
@Composable
private fun AvailableFiles(downloadManager: DownloadManager) {
    var downloadedVideos: List<DownloadManager.VideoDownload>? by remember { mutableStateOf(null) }

    // TODO Refresh whenever a download completes
    LaunchedEffect(Unit) {
        downloadedVideos = downloadManager.findDownloads()
    }

    // TODO Move this out?
    var selectedVideo: DownloadManager.VideoDownload? by remember { mutableStateOf(null) }
    selectedVideo?.let { video ->
        val selectedVideoUrl = NSURL.fileURLWithPath(video.video.toString())
        val selectedAudioUrl = NSURL.fileURLWithPath(video.audio.toString())
        LaunchedEffect(selectedVideoUrl) {
            println(selectedVideoUrl)
        }

        VideoPlayer(
            modifier = Modifier.fillMaxWidth()
                .height(180.dp), // TODO Set height to video aspect ratio
            selectedVideoUrl,
            selectedAudioUrl,
            video.videoMimeType,
            video.audioMimeType
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(downloadedVideos ?: emptyList(), key = { it.id }) { video ->
            Button(onClick = {
                println(video.title)
                selectedVideo = video
            }) {
                Text(video.title)
            }
        }
    }
}
