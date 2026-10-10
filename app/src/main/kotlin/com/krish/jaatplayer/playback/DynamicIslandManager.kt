package com.krish.jaatplayer.playback

import android.os.Build
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.krish.jaatplayer.constants.DynamicIslandEnabledKey
import com.krish.jaatplayer.ui.overlay.DynamicIslandView
import com.krish.jaatplayer.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the camera-anchored "Dynamic Island" ([DynamicIslandView]): decides when it should
 * be showing — app backgrounded, something actually playing, the feature is turned on,
 * and the overlay permission is granted — and keeps it in sync with the player (track info,
 * album art, play state and, while expanded, the progress bar).
 */
class DynamicIslandManager(private val service: MusicService) {

    private val island = DynamicIslandView(service)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var isAppInForeground = true
    private var featureEnabled = false
    private var dismissedForTrackId: String? = null

    private var artJob: Job? = null
    private var artForId: String? = null

    private val lifecycleObserver =
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppInForeground = true
                refresh()
            }

            override fun onStop(owner: LifecycleOwner) {
                isAppInForeground = false
                refresh()
            }
        }

    /**
     * Called by [MusicService] (which is itself the Player.Listener and already re-attaches
     * itself to the new player whenever a crossfade swaps `player`) on every track change.
     * A listener attached here directly would stay stuck on the *original* ExoPlayer
     * instance and silently stop firing after the first crossfade.
     */
    fun onTrackChanged() {
        dismissedForTrackId = null
        refresh()
    }

    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        island.onPlayPause = { service.player.playWhenReady = !service.player.playWhenReady }
        island.onNext = { service.player.seekToNext() }
        island.onPrevious = { service.player.seekToPrevious() }
        island.onDismissedBySwipe = {
            dismissedForTrackId = service.currentMediaMetadata.value?.id
            island.hide()
        }
        island.onExpandedChanged = { if (it) pushProgress() }

        service.dataStore.data
            .map { it[DynamicIslandEnabledKey] ?: false }
            .distinctUntilChanged()
            .onEach {
                featureEnabled = it
                refresh()
            }
            .launchIn(scope)

        // Progress bar ticker: only does work while the card is open.
        scope.launch {
            while (isActive) {
                if (island.isShowing && island.isExpanded) pushProgress()
                delay(500)
            }
        }
    }

    fun release() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        artJob?.cancel()
        island.hide()
        scope.cancel()
    }

    private fun pushProgress() {
        val p = service.player
        val duration = p.duration.takeIf { it > 0 } ?: 0L
        island.setProgress(p.currentPosition, duration)
    }

    private fun loadArt(id: String, url: String?) {
        if (artForId == id) return
        artForId = id
        artJob?.cancel()
        island.setArt(null)
        if (url.isNullOrBlank()) return
        artJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val result = service.imageLoader.execute(
                        ImageRequest.Builder(service)
                            .data(url)
                            .allowHardware(false)
                            .build(),
                    )
                    (result as? SuccessResult)?.image?.toBitmap()
                }.getOrNull()
            }
            if (artForId == id) island.setArt(bitmap)
        }
    }

    fun refresh() {
        val metadata = service.currentMediaMetadata.value
        val overlayPermitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(service)

        val shouldShow =
            !isAppInForeground &&
                featureEnabled &&
                overlayPermitted &&
                service.player.isPlaying &&
                metadata != null &&
                metadata.id != dismissedForTrackId

        if (!shouldShow) {
            // Paused / backgrounded-but-not-playing just hides; keep the art cache for resume.
            island.hide()
            return
        }

        loadArt(metadata!!.id, metadata.thumbnailUrl)
        island.show(
            title = metadata.title,
            artist = metadata.artists.joinToString { it.name },
            isPlaying = service.player.isPlaying,
        )
        if (island.isExpanded) pushProgress()
    }
}
