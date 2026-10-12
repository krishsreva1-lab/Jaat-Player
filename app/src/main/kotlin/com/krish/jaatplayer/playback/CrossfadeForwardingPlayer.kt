package com.krish.jaatplayer.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer

/**
 * Wraps the player that is handed to the MediaSession so skips that come from outside the app UI
 * (notification, lock screen, headset, Android Auto) also use the manual crossfade.
 * Everything not overridden here is forwarded untouched to the real player.
 */
class CrossfadeForwardingPlayer(
    rawPlayer: ExoPlayer,
    private val service: MusicService,
) : ForwardingPlayer(rawPlayer) {

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        if (service.canManualCrossfade && mediaItemIndex != currentMediaItemIndex) {
            service.startManualTrackTransition(mediaItemIndex)
        } else {
            super.seekToDefaultPosition(mediaItemIndex)
        }
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (service.canManualCrossfade && mediaItemIndex != currentMediaItemIndex) {
            service.startManualTrackTransition(mediaItemIndex)
        } else {
            super.seekTo(mediaItemIndex, positionMs)
        }
    }

    override fun seekToNext() {
        if (service.canManualCrossfade) {
            val target = nextMediaItemIndex
            if (target != C.INDEX_UNSET) {
                service.startManualTrackTransition(target)
                return
            }
        }
        super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        if (service.canManualCrossfade) {
            val target = nextMediaItemIndex
            if (target != C.INDEX_UNSET) {
                service.startManualTrackTransition(target)
                return
            }
        }
        super.seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        if (service.canManualCrossfade && currentPosition <= 3000) {
            val target = previousMediaItemIndex
            if (target != C.INDEX_UNSET) {
                service.startManualTrackTransition(target)
                return
            }
        }
        super.seekToPrevious()
    }

    override fun seekToPreviousMediaItem() {
        if (service.canManualCrossfade && currentPosition <= 3000) {
            val target = previousMediaItemIndex
            if (target != C.INDEX_UNSET) {
                service.startManualTrackTransition(target)
                return
            }
        }
        super.seekToPreviousMediaItem()
    }
}
