import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask

private const val VIDEO_FILE_NAME = "video.dat"
private const val AUDIO_FILE_NAME = "audio.dat"
private const val TITLE_FILE_NAME = "title.txt"
private const val MIME_TYPE_FILE_NAME = "mime_type.txt"

private const val VIDEO_DIR_PREFIX = "video-"

class DownloadManager {
    data class VideoDownload(
        val video: Path,
        val audio: Path,
        val title: String,
        val id: String,
        val videoMimeType: String,
        val audioMimeType: String
    )

    private val fs = FileSystem.SYSTEM

    private val documentDir = (NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true
    )[0] as NSString).toString().toPath()

    suspend fun findDownloads(): List<VideoDownload> {
        return withContext(Dispatchers.IO) {
            fs.list(documentDir)
                .filter { file -> file.name.startsWith(VIDEO_DIR_PREFIX) }
                .map { videoDir ->
                    val mimeTypes = fs.read(videoDir / MIME_TYPE_FILE_NAME) { readUtf8() }
                    val (videoMimeType, audioMimeType) = mimeTypes.split(' ')
                    VideoDownload(
                        video = videoDir / VIDEO_FILE_NAME,
                        audio = videoDir / AUDIO_FILE_NAME,
                        title = fs.read(videoDir / TITLE_FILE_NAME) { readUtf8() },
                        id = videoDir.name.removePrefix(VIDEO_DIR_PREFIX),
                        videoMimeType = videoMimeType,
                        audioMimeType = audioMimeType
                    )
                }
        }
    }

    private data class VideoDownloadFileDescriptor(
        val video: Path,
        val audio: Path,
        val title: Path,
        val mimeType: Path
    )

    private fun prepareDownload(videoId: String): VideoDownloadFileDescriptor {
        val videoDir = documentDir / "$VIDEO_DIR_PREFIX$videoId"
        val videoFile = videoDir / VIDEO_FILE_NAME
        val audioFile = videoDir / AUDIO_FILE_NAME
        val titleFile = videoDir / TITLE_FILE_NAME
        val mimeTypeFile = videoDir / MIME_TYPE_FILE_NAME

        fs.createDirectories(videoDir)

        return VideoDownloadFileDescriptor(videoFile, audioFile, titleFile, mimeTypeFile)
    }

    suspend fun download(
        options: PipedVideoDownloadOptions,
        selectedVideoStreamIndex: Int,
        selectedAudioStreamIndex: Int,
        videoProgressPercentageCallback: (Int) -> Unit,
        audioProgressPercentageCallback: (Int) -> Unit
    ) {
        val (videoFile, audioFile, titleFile, mimeTypeFile) = prepareDownload(options.videoId)

        fs.write(titleFile) {
            writeUtf8(options.title)
        }

        fs.write(mimeTypeFile) {
            val videoMimeType = options.metadata.videoStreams[selectedVideoStreamIndex].mimeType
            val audioMimeType = options.metadata.audioStreams[selectedAudioStreamIndex].mimeType
            writeUtf8("$videoMimeType $audioMimeType")
        }

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
                    videoProgressPercentageCallback,
                    audioProgressPercentageCallback
                )
            }
        }
    }
}