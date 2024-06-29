import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.jetbrains.skia.Image
import platform.Foundation.NSURL

@Composable
fun DownloadedVideosScreen(modifier: Modifier = Modifier, downloadManager: DownloadManager) {
    Column(modifier) {
        AvailableFiles(downloadManager)
    }
}

@Composable
private fun AvailableFiles(downloadManager: DownloadManager) {
    val scope = rememberCoroutineScope()
    val downloadedVideos by downloadManager.monitorDownloads(scope).collectAsStateWithLifecycle()

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

    var videoStagedForDeletion by remember { mutableStateOf<DownloadManager.VideoDownload?>(null) }

    videoStagedForDeletion?.let { toDelete ->
        ConfirmDeletionDialog(
            toDelete,
            { videoStagedForDeletion = null },
            { downloadManager.delete(toDelete) }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(downloadedVideos ?: emptyList(), key = { it.id }) { video ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    println(video.title)
                    selectedVideo = video
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ImageFromDisk(
                    modifier = Modifier.width(160.dp).height(90.dp).padding(4.dp),
                    path = video.thumbnail
                )
                Text(video.title, modifier = Modifier.weight(1f).padding(4.dp))
                Button(onClick = { videoStagedForDeletion = video }, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.Sharp.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeletionDialog(
    toDelete: DownloadManager.VideoDownload,
    dismiss: () -> Unit,
    delete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Confirm Deletion") },
        text = { Text("Are you sure you want to delete the video \"${toDelete.title}\"? This action cannot be undone.") },
        confirmButton = {
            Button(onClick = {
                delete()
                dismiss()
            }) {
                Text("Delete")
            }
        },
        dismissButton = {
            Button(onClick = dismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ImageFromDisk(modifier: Modifier = Modifier, path: Path) {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        val bytes = withContext(Dispatchers.IO) { FileSystem.SYSTEM.read(path) { readByteArray() } }
        image = Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }

    image?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = modifier
        )
    } ?: run {
        Box(modifier)
    }
}
