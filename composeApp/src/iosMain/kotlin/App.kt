import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import platform.Foundation.NSURL

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val downloadManager = DownloadManager()

    MaterialTheme {
        var downloadOptions: PipedVideoDownloadOptions? by remember { mutableStateOf(null) }
        var downloadFailure: Exception? by remember { mutableStateOf(null) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            var fetchDownloadOptionsError: Exception? by remember { mutableStateOf(null) }
            Button(onClick = {
                scope.launch {
                    fetchDownloadOptionsError = null
                    try {
                        downloadOptions = fetchYouTubeDownloadOptions(
                            url = "https://www.youtube.com/watch?v=v_normU8p-I",
                            appConfiguration = AppConfiguration(
                                pipedApiInstanceUrl = "***REMOVED***"
                            )
                        )
                    } catch (e: Exception) {
                        fetchDownloadOptionsError = null
                    }
                }
            }) {
                Text("Click me!")
            }
            fetchDownloadOptionsError?.let { error ->
                Text("Error: ${error.message}")
            }
            AnimatedVisibility(downloadOptions != null) {
                downloadOptions?.let { options ->
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        var selectedVideoIndex by remember { mutableStateOf(0) }
                        var selectedAudioIndex by remember { mutableStateOf(0) }
                        var videoExpanded by remember { mutableStateOf(false) }
                        var audioExpanded by remember { mutableStateOf(false) }

                        var videoDownloadProgress by remember { mutableStateOf(-1) }
                        var audioDownloadProgress by remember { mutableStateOf(-1) }

                        StreamSelectorDropdown(
                            videoExpanded,
                            { videoExpanded = it },
                            options.metadata.videoStreams,
                            selectedVideoIndex,
                            { selectedVideoIndex = it })
                        StreamSelectorDropdown(
                            audioExpanded,
                            { audioExpanded = it },
                            options.metadata.audioStreams,
                            selectedAudioIndex,
                            { selectedAudioIndex = it })
                        Button(onClick = {
                            scope.launch {
                                try {
                                    downloadFailure = null
                                    videoDownloadProgress = 0
                                    audioDownloadProgress = 0
                                    downloadManager.download(
                                        options,
                                        selectedVideoIndex,
                                        selectedAudioIndex,
                                        videoProgressPercentageCallback = { percentage ->
                                            println("Video $percentage %")
                                            videoDownloadProgress = percentage
                                        },
                                        audioProgressPercentageCallback = { percentage ->
                                            println("Audio $percentage %")
                                            audioDownloadProgress = percentage
                                        }
                                    )
                                } catch (e: Exception) {
                                    downloadFailure = e
                                }
                                videoDownloadProgress = -1
                                audioDownloadProgress = -1
                            }
                        }) {
                            Text("Download")
                        }
                        downloadFailure?.let { failure ->
                            Text("Download failed: ${failure.message}")
                        }

                        if (videoDownloadProgress != -1 && audioDownloadProgress != -1) {
                            val totalDownloadProgress =
                                videoDownloadProgress + audioDownloadProgress
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                progress = totalDownloadProgress / 200f
                            )
                        }
                    }
                }
            }
            AvailableFiles(downloadManager)
        }
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun StreamSelectorDropdown(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Stream>,
    selectedOption: Int,
    onOptionSelected: (Int) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        TextField(
            value = options[selectedOption].description,
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth(),
            onValueChange = {}
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEachIndexed { index, audioStream ->
                DropdownMenuItem(
                    onClick = {
                        onOptionSelected(index)
                        onExpandedChange(false)
                    }
                ) {
                    Text(audioStream.description)
                }
            }
        }
    }
}

private val Stream.description get() = "$quality - $format - ${codec ?: "unknown"}"

// TODO:
//  - Clean up code
//  - Separate screen for video playback (just full-screen - maybe just immediately enter AVPlayer full-screen mode?)
//  - URL bar to paste video URL
//  - Feature to delete videos
//  - Show video thumbnail
//  - Notarize and create AltStore source
//  - yt-dlp backend