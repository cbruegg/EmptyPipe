import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.Foundation.NSUserDefaults

@Composable
fun DownloadScreen(modifier: Modifier = Modifier, downloadManager: DownloadManager) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var downloadOptions: PipedVideoDownloadOptions? by remember { mutableStateOf(null) }
    var fetchingDownloadOptionsJob: Job? by remember { mutableStateOf(null) }
    var downloadFailure: Exception? by remember { mutableStateOf(null) }
    var fetchDownloadOptionsError: Exception? by remember { mutableStateOf(null) }
    var url by remember { mutableStateOf("") }

    Column(modifier) {
        TextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Row {
            Button(enabled = fetchingDownloadOptionsJob == null,
                onClick = {
                    focusManager.clearFocus()
                    fetchingDownloadOptionsJob = scope.launch {
                        fetchDownloadOptionsError = null
                        try {
                            val appConfiguration =
                                AppConfiguration.loadFrom(NSUserDefaults.standardUserDefaults)
                            check(!appConfiguration.pipedApiInstanceUrl.isNullOrBlank()) { "Piped instance URL is not set" }

                            downloadOptions = fetchYouTubeDownloadOptions(url, appConfiguration)
                            fetchDownloadOptionsError = null
                        } catch (e: CancellationException) {
                            println("Cancelled fetching download options")
                        } catch (e: Exception) {
                            fetchDownloadOptionsError = e
                        } finally {
                            fetchingDownloadOptionsJob = null
                        }
                    }
                }) {
                Text("Fetch download options")
            }
            if (url.isNotEmpty()) {
                Button(
                    enabled = fetchingDownloadOptionsJob == null,
                    onClick = {
                        focusManager.clearFocus()
                        url = ""
                        downloadOptions = null
                        fetchDownloadOptionsError = null
                        downloadFailure = null
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Clear")
                }
            }
            if (fetchingDownloadOptionsJob != null) {
                Button(
                    onClick = {
                        fetchingDownloadOptionsJob?.cancel()
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
        fetchDownloadOptionsError?.let { error ->
            Text("Error: ${error.message}")
        }
        AnimatedVisibility(downloadOptions != null) {
            downloadOptions?.let { options ->
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    var selectedVideoIndex by remember { mutableStateOf(0) }
                    var selectedAudioIndex by remember { mutableStateOf(0) }
                    var videoExpanded by remember { mutableStateOf(false) }
                    var audioExpanded by remember { mutableStateOf(false) }

                    var videoDownloadProgress by remember { mutableStateOf(-1) }
                    var audioDownloadProgress by remember { mutableStateOf(-1) }
                    var downloadJob: Job? by remember { mutableStateOf(null) }

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
                    Row {
                        Button(
                            enabled = videoDownloadProgress == -1 && audioDownloadProgress == -1,
                            onClick = {
                                downloadJob = scope.launch {
                                    try {
                                        downloadFailure = null
                                        videoDownloadProgress = 0
                                        audioDownloadProgress = 0
                                        downloadManager.download(
                                            options,
                                            selectedVideoIndex,
                                            selectedAudioIndex,
                                            videoProgressPercentageCallback = { percentage ->
                                                videoDownloadProgress = percentage
                                            },
                                            audioProgressPercentageCallback = { percentage ->
                                                audioDownloadProgress = percentage
                                            }
                                        )
                                    } catch (e: CancellationException) {
                                        // nothing to do
                                    } catch (e: Exception) {
                                        downloadFailure = e
                                    } finally {
                                        videoDownloadProgress = -1
                                        audioDownloadProgress = -1
                                    }
                                }
                            },
                            content = { Text("Download") })
                        if (videoDownloadProgress != -1 || audioDownloadProgress != -1) {
                            Button(
                                onClick = { downloadJob?.cancel() },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
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
