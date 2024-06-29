import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVURLAssetOverrideMIMETypeKey
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.play
import platform.AVFoundation.tracksWithMediaType
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRect
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMPersistentTrackID_Invalid
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
fun VideoPlayer(
    modifier: Modifier,
    videoUrl: NSURL,
    audioUrl: NSURL,
    videoMimeType: String,
    audioMimeType: String
) {
    val playerItemResult = remember(videoUrl, videoMimeType, audioUrl, audioMimeType) {
        createPlayerItem(videoUrl, videoMimeType, audioUrl, audioMimeType)
    }
    when (playerItemResult) {
        PlayerItemResult.Failure.VideoTrack -> {
            Text("Incompatible video track format!")
            return
        }
        PlayerItemResult.Failure.AudioTrack -> {
            Text("Incompatible audio track format!")
            return
        }
        is PlayerItemResult.Success -> {} // continue
    }

    val playerItem = playerItemResult.playerItem
    val player = remember(playerItem) { AVPlayer(playerItem) }
    val playerLayer = remember { AVPlayerLayer() }
    val avPlayerViewController = remember {
        AVPlayerViewController().apply {
            showsPlaybackControls = true
            allowsPictureInPicturePlayback = true
            canStartPictureInPictureAutomaticallyFromInline = true
        }
    }
    avPlayerViewController.player = player

    playerLayer.player = player
    // Use a UIKitView to integrate with your existing UIKit views
    UIKitView(
        factory = {
            // Create a UIView to hold the AVPlayerLayer
            val playerContainer = UIView()
            playerContainer.addSubview(avPlayerViewController.view)
            // Return the playerContainer as the root UIView
            playerContainer
        },
        onResize = { view: UIView, rect: CValue<CGRect> ->
            CATransaction.begin()
            CATransaction.setValue(true, kCATransactionDisableActions)
            view.layer.setFrame(rect)
            playerLayer.setFrame(rect)
            avPlayerViewController.view.layer.frame = rect
            CATransaction.commit()
        },
        update = { view ->
            player.play()
            avPlayerViewController.player!!.play()
        },
        modifier = modifier
    )
}

sealed interface PlayerItemResult {
    data class Success(val playerItem: AVPlayerItem) : PlayerItemResult
    enum class Failure : PlayerItemResult {
        VideoTrack, AudioTrack
    }
}
@OptIn(ExperimentalForeignApi::class)
private fun createPlayerItem(
    videoUrl: NSURL,
    videoMimeType: String,
    audioUrl: NSURL,
    audioMimeType: String
): PlayerItemResult {
    val videoAsset = AVURLAsset.URLAssetWithURL(
        videoUrl, mapOf(AVURLAssetOverrideMIMETypeKey to videoMimeType)
    )
    val audioAsset = AVURLAsset.URLAssetWithURL(
        audioUrl, mapOf(AVURLAssetOverrideMIMETypeKey to audioMimeType)
    )
    val duration = videoAsset.duration
    val composition = AVMutableComposition()

    val videoAssetTrack =
        videoAsset.tracksWithMediaType(AVMediaTypeVideo).getOrNull(0) as AVAssetTrack?
            ?: return PlayerItemResult.Failure.VideoTrack
    val audioAssetTrack =
        audioAsset.tracksWithMediaType(AVMediaTypeAudio).getOrNull(0) as AVAssetTrack?
            ?: return PlayerItemResult.Failure.AudioTrack

    val videoTrack =
        composition.addMutableTrackWithMediaType(AVMediaTypeVideo, kCMPersistentTrackID_Invalid)
            ?:return PlayerItemResult.Failure.VideoTrack
    videoTrack.insertTimeRange(
        CMTimeRangeMake(kCMTimeZero.readValue(), duration),
        videoAssetTrack,
        kCMTimeZero.readValue(),
        null
    )

    val audioTrack =
        composition.addMutableTrackWithMediaType(AVMediaTypeAudio, kCMPersistentTrackID_Invalid)
            ?: return PlayerItemResult.Failure.AudioTrack
    audioTrack.insertTimeRange(
        CMTimeRangeMake(kCMTimeZero.readValue(), duration),
        audioAssetTrack,
        kCMTimeZero.readValue(),
        null
    )

    return PlayerItemResult.Success(AVPlayerItem.playerItemWithAsset(composition))
}