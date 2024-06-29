import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
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
private const val THUMBNAIL_FILE_NAME = "thumbnail.jpg"

private const val VIDEO_DIR_PREFIX = "video-"

class DownloadManager {
    data class VideoDownload(
        val video: Path,
        val audio: Path,
        val title: String,
        val id: String,
        val videoMimeType: String,
        val audioMimeType: String,
        val thumbnail: Path
    )

    private val fs = FileSystem.SYSTEM

    private val documentDir = (NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true
    )[0] as NSString).toString().toPath()

    private val changeEvents =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun monitorDownloads(scope: CoroutineScope): StateFlow<List<VideoDownload>?> {
        return changeEvents
            .onSubscription { emit(Unit) }
            .map { findDownloads() }
            .stateIn(
                scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )
    }

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
                        audioMimeType = audioMimeType,
                        thumbnail = videoDir / THUMBNAIL_FILE_NAME
                    )
                }
        }
    }

    private data class VideoDownloadFileDescriptor(
        val video: Path,
        val audio: Path,
        val title: Path,
        val mimeType: Path,
        val thumbnail: Path
    )

    private fun prepareDownload(videoId: String): VideoDownloadFileDescriptor {
        val videoDir = documentDir / "$VIDEO_DIR_PREFIX$videoId"
        val videoFile = videoDir / VIDEO_FILE_NAME
        val audioFile = videoDir / AUDIO_FILE_NAME
        val titleFile = videoDir / TITLE_FILE_NAME
        val mimeTypeFile = videoDir / MIME_TYPE_FILE_NAME
        val thumbnailFile = videoDir / THUMBNAIL_FILE_NAME

        fs.createDirectories(videoDir)

        return VideoDownloadFileDescriptor(videoFile, audioFile, titleFile, mimeTypeFile, thumbnailFile)
    }

    suspend fun download(
        options: PipedVideoDownloadOptions,
        selectedVideoStreamIndex: Int,
        selectedAudioStreamIndex: Int,
        videoProgressPercentageCallback: (Int) -> Unit,
        audioProgressPercentageCallback: (Int) -> Unit
    ) {
        val (videoFile, audioFile, titleFile, mimeTypeFile, thumbnailFile) = prepareDownload(options.videoId)

        try {
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
                    fs.write(thumbnailFile) {
                        val thumbnailSink = this
                        downloadYouTubeVideo(
                            options,
                            selectedVideoStreamIndex,
                            selectedAudioStreamIndex,
                            videoSink,
                            audioSink,
                            thumbnailSink,
                            videoProgressPercentageCallback,
                            audioProgressPercentageCallback
                        )
                    }
                }
            }

            changeEvents.emit(Unit)
        } catch (e: Exception) {
            val videoDir = videoFile.parent!!
            fs.deleteRecursively(videoDir)
            throw e
        }
    }

    fun delete(toDelete: VideoDownload) {
        val videoDir = toDelete.video.parent!!
        fs.deleteRecursively(videoDir)
        changeEvents.tryEmit(Unit)
    }
}