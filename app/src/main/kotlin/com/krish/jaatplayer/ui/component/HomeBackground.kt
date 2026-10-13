/**
 * Custom Home background (image / GIF / video), ported from BeatWave (GPL-3.0) for Jaat Player.
 */

package com.krish.jaatplayer.ui.component

import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.krish.jaatplayer.constants.HomeBackgroundAnimateKey
import com.krish.jaatplayer.constants.HomeBackgroundBlurKey
import com.krish.jaatplayer.constants.HomeBackgroundDimKey
import com.krish.jaatplayer.constants.HomeBackgroundEnabledKey
import com.krish.jaatplayer.constants.HomeBackgroundIsVideoKey
import com.krish.jaatplayer.constants.HomeBackgroundPathKey
import com.krish.jaatplayer.constants.HomeBackgroundQualityKey
import com.krish.jaatplayer.utils.rememberPreference
import java.io.File

private const val BackgroundAbsoluteMaxEdge = 2560

/** Pixel size a picked background is stored at: the screen size scaled by [quality], capped. */
@Composable
fun rememberHomeBackgroundTargetSize(quality: Float): Pair<Int, Int> {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(configuration, density, quality) {
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val q = quality.coerceIn(0.3f, 1f)
        val width = (screenWidthPx * q).toInt().coerceIn(1, BackgroundAbsoluteMaxEdge)
        val height = (screenHeightPx * q).toInt().coerceIn(1, BackgroundAbsoluteMaxEdge)
        width to height
    }
}

// The blur-in plays once per app session, not on every visit to Home.
private var blurAnimatedThisSession = false

/**
 * Paints the user's custom Home background behind the current content. Draws nothing unless the
 * feature is switched on and a file has been picked, so with the setting off Home looks exactly
 * as before. Call it as the first child of the screen's Box so the content sits on top of it.
 */
@Composable
fun BoxScope.HomeBackground(
    withGradient: Boolean = false,
    contentLoaded: Boolean = true,
) {
    val (enabled) = rememberPreference(HomeBackgroundEnabledKey, false)
    val (path) = rememberPreference(HomeBackgroundPathKey, "")
    if (!enabled || path.isEmpty()) return

    val (blur) = rememberPreference(HomeBackgroundBlurKey, 20f)
    val (dim) = rememberPreference(HomeBackgroundDimKey, 0.4f)
    val (animate) = rememberPreference(HomeBackgroundAnimateKey, false)
    val (isVideo) = rememberPreference(HomeBackgroundIsVideoKey, false)

    if (isVideo) {
        HomeVideoBackground(path = path, blur = blur, dim = dim, withGradient = withGradient)
        return
    }

    val shouldAnimate = animate && !blurAnimatedThisSession
    var appeared by remember { mutableStateOf(!shouldAnimate) }
    LaunchedEffect(shouldAnimate) {
        if (shouldAnimate) {
            appeared = true
            blurAnimatedThisSession = true
        }
    }
    // Starts sharp and eases into the chosen blur once the content has loaded.
    val animatedBlur by animateFloatAsState(
        targetValue = if (appeared && contentLoaded) blur else 0f,
        animationSpec = tween(durationMillis = 2200, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "homeBgBlur",
    )
    val effectiveBlur = if (animate) animatedBlur else blur

    val context = LocalContext.current
    val (quality) = rememberPreference(HomeBackgroundQualityKey, 1f)
    val (targetWidth, targetHeight) = rememberHomeBackgroundTargetSize(quality)
    val imageRequest = remember(path, targetWidth, targetHeight) {
        ImageRequest.Builder(context)
            .data(File(path))
            .size(targetWidth, targetHeight)
            .build()
    }
    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .matchParentSize()
            .then(if (effectiveBlur > 0f) Modifier.blur(effectiveBlur.dp) else Modifier),
    )
    Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
    if (withGradient) HomeBackgroundGradient()
}

@Composable
private fun BoxScope.HomeBackgroundGradient() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to primary.copy(alpha = 0.55f),
                ),
            ),
    )
}

/** Muted, looping video background. Pauses while the app is in the background. */
@Composable
fun BoxScope.HomeVideoBackground(
    path: String,
    blur: Float,
    dim: Float,
    withGradient: Boolean = false,
) {
    val context = LocalContext.current
    var isReady by remember(path) { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var inForeground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> inForeground = true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> inForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Own muted player: it never touches the music player, audio focus or the media session.
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = inForeground
        }
    }
    LaunchedEffect(inForeground) {
        if (exoPlayer.playWhenReady != inForeground) exoPlayer.playWhenReady = inForeground
    }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isReady = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    LaunchedEffect(path) {
        isReady = false
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        exoPlayer.prepare()
    }

    val alpha by animateFloatAsState(
        targetValue = if (isReady) 1f else 0f,
        animationSpec = tween(600),
        label = "homeVideoAlpha",
    )
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                isEnabled = false
                isClickable = false
                isFocusable = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        exoPlayer.setVideoTextureView(this@apply)
                    }
                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                }
            }
        },
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { this.alpha = alpha }
            .then(if (blur > 0f) Modifier.blur(blur.dp) else Modifier),
    )
    Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
    if (withGradient) HomeBackgroundGradient()
}
