package com.olbyine.music.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.olbyine.music.OlbyineApplication

internal data class MotionPresentation(
    val cover: String?,
    val background: String?,
    val playing: Boolean,
)

internal val LocalMotionArtwork = compositionLocalOf<MotionPresentation?> { null }

@OptIn(UnstableApi::class)
@Composable
internal fun MotionVideo(url: String, playing: Boolean, modifier: Modifier = Modifier) {
    if (LocalReduceMotion.current) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember { createMotionPlayer(context) }
    var ready by remember(url) { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(700), label = "motion-fade")
    val viewRef = remember { arrayOfNulls<CropTextureView>(1) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                ready = true
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                viewRef[0]?.setVideoSize(videoSize.width, videoSize.height)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    DisposableEffect(url) {
        val app = context.applicationContext as OlbyineApplication
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent(MOTION_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        val dataSource = CacheDataSource.Factory()
            .setCache(app.motionCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        player.setMediaSource(HlsMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(url)))
        player.prepare()
        onDispose { player.stop() }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(playing, resumed) { player.playWhenReady = playing && resumed }

    AndroidView(
        factory = { viewContext ->
            CropTextureView(viewContext).also { view ->
                viewRef[0] = view
                player.setVideoTextureView(view)
            }
        },
        onRelease = { view ->
            player.clearVideoTextureView(view)
            if (viewRef[0] === view) viewRef[0] = null
        },
        modifier = modifier.graphicsLayer { this.alpha = alpha },
    )
}

@OptIn(UnstableApi::class)
private fun createMotionPlayer(context: Context): ExoPlayer {
    val selector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setMaxVideoSize(1440, 1920)
                .setMaxVideoBitrate(10_000_000)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true),
        )
    }
    return ExoPlayer.Builder(context)
        .setTrackSelector(selector)
        .build()
        .apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        }
}

private class CropTextureView(context: Context) : TextureView(context) {
    private var videoWidth = 0
    private var videoHeight = 0
    private val cropMatrix = Matrix()

    init {
        isOpaque = false
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = applyCrop()
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = applyCrop()
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        applyCrop()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyCrop()
    }

    private fun applyCrop() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) return
        val scale = maxOf(viewWidth / videoWidth, viewHeight / videoHeight)
        cropMatrix.reset()
        cropMatrix.setScale(videoWidth * scale / viewWidth, videoHeight * scale / viewHeight, viewWidth / 2f, viewHeight / 2f)
        setTransform(cropMatrix)
    }
}

private const val MOTION_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"
