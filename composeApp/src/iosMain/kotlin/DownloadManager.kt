import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask

class DownloadManager {
    private val fs = FileSystem.SYSTEM

    private data class TargetFiles(val video: Path, val audio: Path)

    private fun prepareDownload(videoId: String): TargetFiles {
        val documentDir = (NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )[0] as NSString).toString().toPath()
        val videoDir = documentDir / "video-${videoId}"
        val videoFile = videoDir / "video.dat"
        val audioFile = videoDir / "audio.dat"

        fs.createDirectories(videoDir)

        return TargetFiles(videoFile, audioFile)
    }

    fun download(
        scope: CoroutineScope,
        options: PipedVideoDownloadOptions,
        selectedVideoStreamIndex: Int,
        selectedAudioStreamIndex: Int
    ) {
        val (videoFile, audioFile) = prepareDownload(options.videoId)

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
    }
}