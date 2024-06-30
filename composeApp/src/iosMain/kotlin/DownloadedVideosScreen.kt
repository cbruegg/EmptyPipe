import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.jetbrains.skia.Image
import platform.Foundation.NSURL
import platform.posix.pow
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.roundToInt

// TODO Fix bug that resets the entire screen state when selecting a new video

@Composable
fun DownloadedVideosScreen(modifier: Modifier = Modifier, downloadManager: DownloadManager) {
    Column(modifier) {
        var selectedVideo: DownloadManager.VideoDownload? by remember { mutableStateOf(null) }
        selectedVideo?.let { video ->
            val selectedVideoUrl = NSURL.fileURLWithPath(video.video.toString())
            val selectedAudioUrl = NSURL.fileURLWithPath(video.audio.toString())

            VideoPlayer(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                selectedVideoUrl,
                selectedAudioUrl,
                video.videoMimeType,
                video.audioMimeType
            )
        }

        AvailableFiles(downloadManager,
            onVideoSelected = { selectedVideo = it },
            onDeleted = { if (selectedVideo == it) selectedVideo = null }
        )
    }
}

@Composable
private fun AvailableFiles(
    downloadManager: DownloadManager,
    onVideoSelected: (DownloadManager.VideoDownload) -> Unit,
    onDeleted: (DownloadManager.VideoDownload) -> Unit
) {
    val scope = rememberCoroutineScope()
    val downloadedVideos by downloadManager.monitorDownloads(scope).collectAsStateWithLifecycle()

    var videoStagedForDeletion by remember { mutableStateOf<DownloadManager.VideoDownload?>(null) }

    videoStagedForDeletion?.let { toDelete ->
        ConfirmDeletionDialog(
            toDelete = toDelete,
            dismiss = { videoStagedForDeletion = null },
            delete = {
                downloadManager.delete(toDelete)
                onDeleted(toDelete)
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(downloadedVideos ?: emptyList(), key = { it.id }) { video ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onVideoSelected(video) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ImageFromDisk(
                    modifier = Modifier.width(160.dp).height(90.dp).padding(4.dp),
                    path = video.thumbnail
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(video.title, modifier = Modifier.padding(4.dp))
                    Text(
                        formatBytes(video.bytesOnDisk),
                        modifier = Modifier.padding(4.dp),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Button(
                    onClick = { videoStagedForDeletion = video },
                    modifier = Modifier.padding(4.dp)
                ) {
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = min(units.size, (log10(bytes.toDouble()) / log10(1024.0)).toInt())
    val value = bytes / pow(1024.0, digitGroups.toDouble())
    val roundedValue = (value * 10).roundToInt() / 10.0 // Round to one decimal place
    return "$roundedValue ${units[digitGroups]}"
}