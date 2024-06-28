import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.MaterialTheme
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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class, ExperimentalMaterialApi::class)
@Composable
fun App() {
    val scope = rememberCoroutineScope()

    MaterialTheme {
        var downloadOptions: PipedVideoDownloadOptions? by remember { mutableStateOf(null) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = {
                scope.launch {
                    downloadOptions = fetchYouTubeDownloadOptions(
                        url = "https://www.youtube.com/watch?v=Aw4DJBdV4C0",
                        appConfiguration = AppConfiguration(
                            pipedApiInstanceUrl = "***REMOVED***"
                        )
                    )
                }
            }) {
                Text("Click me!")
            }
            AnimatedVisibility(downloadOptions != null) {
                downloadOptions?.let { options ->
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val videoOptions =
                            options.usableVideoStreamIndices.map { options.metadata.videoStreams[it] }
                        val audioOptions =
                            options.usableAudioStreamIndices.map { options.metadata.audioStreams[it] }

                        // dropdown for videoOptions
                        var selectedVideoIndex by remember { mutableStateOf(0) }
                        var selectedAudioIndex by remember { mutableStateOf(0) }
                        var videoExpanded by remember { mutableStateOf(false) }
                        var audioExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = videoExpanded,
                            onExpandedChange = { videoExpanded = it }
                        ) {
                            TextField(
                                value = videoOptions[selectedVideoIndex].description,
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = videoExpanded) },
                                modifier = Modifier.fillMaxWidth(),
                                onValueChange = {}
                            )

                            ExposedDropdownMenu(
                                expanded = videoExpanded,
                                onDismissRequest = { videoExpanded = false }
                            ) {
                                videoOptions.forEachIndexed { index, videoStream ->
                                    DropdownMenuItem(
                                        onClick = {
                                            selectedVideoIndex = index
                                            videoExpanded = false}
                                    ) {
                                        Text(videoStream.description)
                                    }
                                }
                            }
                        }
                        ExposedDropdownMenuBox(
                            expanded = audioExpanded,
                            onExpandedChange = { audioExpanded = it }
                        ) {
                            TextField(
                                value = audioOptions[selectedAudioIndex].description,
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = audioExpanded) },
                                modifier = Modifier.fillMaxWidth(),
                                onValueChange = {}
                            )

                            ExposedDropdownMenu(
                                expanded = audioExpanded,
                                onDismissRequest = { audioExpanded = false }
                            ) {
                                audioOptions.forEachIndexed { index, audioStream ->
                                    DropdownMenuItem(
                                        onClick = {
                                            selectedAudioIndex = index
                                            audioExpanded = false}
                                    ) {
                                        Text(audioStream.description)
                                    }
                                }
                            }
                        }
                        Button(onClick = {
                            val selectedVideoStreamIndex =
                                options.usableVideoStreamIndices[selectedVideoIndex]
                            val selectedAudioStreamIndex =
                                options.usableAudioStreamIndices[selectedAudioIndex]

                            val documentDir = (NSSearchPathForDirectoriesInDomains(
                                NSDocumentDirectory,
                                NSUserDomainMask,
                                true
                            )[0] as NSString).toString().toPath()
                            val videoDir = documentDir / "video-${options.videoId}"
                            val videoFile = videoDir / "video.dat"
                            val audioFile = videoDir / "audio.dat"

                            val fs = FileSystem.SYSTEM
                            fs.createDirectories(videoDir)
                            scope.launch {
                                fs.write(videoFile) {
                                    val videoSink = this
                                    fs.write(audioFile) {
                                        val audioSink = this
                                        downloadYouTubeVideo(
                                            options,
                                            selectedVideoStreamIndex,
                                            selectedAudioStreamIndex,
                                            videoSink,
                                            audioSink,
                                            videoProgressPercentageCallback = { percentage -> println("Video $percentage %") },
                                            audioProgressPercentageCallback = { percentage -> println("Audio $percentage %") }
                                        )
                                    }
                                }
                            }

                        }) {
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}

private val VideoStream.description get() = "$quality - $format - ${codec ?: "unknown"}"
private val AudioStream.description get() = "$quality - $format - ${codec ?: "unknown"}"