import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.BufferedSink
import kotlin.concurrent.AtomicInt

private val http = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }
}

private val youtubeVideoIdRegex = Regex("""https://(?:.*\.)youtube.com/watch\?.*v=([\w-]+)""")

suspend fun fetchYouTubeDownloadOptions(
    url: String,
    appConfiguration: AppConfiguration
): PipedVideoDownloadOptions {
    val videoId = youtubeVideoIdRegex.find(url)?.groupValues?.get(1) ?: error("Invalid URL: $url")
    val pipedVideoMetadataUrl = "${appConfiguration.pipedApiInstanceUrl}/streams/$videoId"
    println("Getting metadata from $pipedVideoMetadataUrl")
    val pipedVideoMetadata = http.get(pipedVideoMetadataUrl).body<PipedVideoMetadata>()
    return PipedVideoDownloadOptions(
        title = pipedVideoMetadata.title,
        videoId = videoId,
        metadata = pipedVideoMetadata.withoutIncompatibleStreams()
    )
}

// iOS does not support WEBM as of 2024
private fun PipedVideoMetadata.withoutIncompatibleStreams(): PipedVideoMetadata {
    return copy(
        videoStreams = videoStreams.filter { !it.format.contains("WEBM", ignoreCase = true) },
        audioStreams = audioStreams.filter { !it.format.contains("WEBM", ignoreCase = true) }
    )
}

suspend fun downloadYouTubeVideo(
    pipedVideoDownloadOptions: PipedVideoDownloadOptions,
    videoStreamIdx: Int,
    audioStreamIdx: Int,
    videoStreamChannel: BufferedSink,
    audioStreamChannel: BufferedSink,
    videoProgressPercentageCallback: (Int) -> Unit,
    audioProgressPercentageCallback: (Int) -> Unit
) {
    val videoStreamUrl = pipedVideoDownloadOptions.metadata.videoStreams[videoStreamIdx].url
    val audioStreamUrl = pipedVideoDownloadOptions.metadata.audioStreams[audioStreamIdx].url
    val lastVideoPercentage = AtomicInt(-1)
    val lastAudioPercentage = AtomicInt(-1)
    coroutineScope {
        launch {
            http.prepareGet(videoStreamUrl) {
                onDownload { bytesSentTotal, contentLength ->
                    val progressPercentage =
                        (bytesSentTotal.toFloat() / contentLength.toFloat() * 100).toInt()
                    if (lastVideoPercentage.getAndSet(progressPercentage) != progressPercentage) {
                        videoProgressPercentageCallback(progressPercentage)
                    }
                }
            }.execute { httpResponse ->
                httpResponse.bodyAsChannel().copyTo(videoStreamChannel)
            }
        }

        launch {
            http.prepareGet(audioStreamUrl) {
                onDownload { bytesSentTotal, contentLength ->
                    val progressPercentage =
                        (bytesSentTotal.toFloat() / contentLength.toFloat() * 100).toInt()
                    if (lastAudioPercentage.getAndSet(progressPercentage) != progressPercentage) {
                        audioProgressPercentageCallback(progressPercentage)
                    }
                }
            }.execute { httpResponse ->
                httpResponse.bodyAsChannel().copyTo(audioStreamChannel)
            }
        }
    }
}

private suspend fun ByteReadChannel.copyTo(dst: BufferedSink) {
    val buf = ByteArray(8192)
    while (true) {
        val bytesRead = readAvailable(buf)
        if (bytesRead == -1) {
            break
        }
        dst.write(buf, 0, bytesRead)
    }
}

data class PipedVideoDownloadOptions(
    val title: String,
    val videoId: String,
    val metadata: PipedVideoMetadata
)

@Serializable
data class PipedVideoMetadata(
    val title: String,
    val videoStreams: List<VideoStream>,
    val audioStreams: List<AudioStream>
)

@Serializable
data class VideoStream(
    override val url: String,
    override val format: String,
    override val quality: String,
    override val codec: String?,
    override val mimeType: String,
    val videoOnly: Boolean
) : Stream

@Serializable
data class AudioStream(
    override val url: String,
    override val format: String,
    override val quality: String,
    override val codec: String?,
    override val mimeType: String
) : Stream

interface Stream {
    val url: String
    val format: String
    val quality: String
    val codec: String?
    val mimeType: String
}