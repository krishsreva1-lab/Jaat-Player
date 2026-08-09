package com.krish.jaatplayer.jaat.updater.downloadmanager

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList

import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class JaatNotificationProvider(
    private val context: Context,
    notificationIdProvider: DefaultMediaNotificationProvider.NotificationIdProvider,
    private val channelId: String,
    channelNameResourceId: Int,
) : MediaNotification.Provider {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var lastBitmap: Bitmap? = null
    private var lastUri: String? = null


    private val defaultProvider = DefaultMediaNotificationProvider(
        context,
        notificationIdProvider,
        channelId,
        channelNameResourceId
    )

    fun setSmallIcon(iconResId: Int): JaatNotificationProvider {
        defaultProvider.setSmallIcon(iconResId)
        return this
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val player = mediaSession.player
        val metadata = player.currentMediaItem?.mediaMetadata
        val isPlaying = player.isPlaying
        val artworkUri = metadata?.artworkUri

        // Build standard notification first as baseline
        val mediaNotification = defaultProvider.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            onNotificationChangedCallback
        )

        // Try to use the last cached bitmap if URI matches
        if (artworkUri?.toString() != lastUri) {
            lastUri = artworkUri?.toString()
            if (artworkUri != null) {
                serviceScope.launch {
                    val request = ImageRequest.Builder(context)
                        .data(artworkUri)
                        .size(512)
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    val bitmap = result.image?.toBitmap()
                    if (bitmap != null) {
                        lastBitmap = bitmap
                        onNotificationChangedCallback.onNotificationChanged(
                            createNotification(
                                mediaSession,
                                customLayout,
                                actionFactory,
                                onNotificationChangedCallback
                            )
                        )
                    }
                }
            } else {
                lastBitmap = null
            }
        }

        val customViews = CustomNotificationLayout.createCustomViews(
            context,
            mediaSession,
            lastBitmap,
            isPlaying
        )

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.krish.jaatplayer.utils.AppLogo.glyphResBlocking(context))
            .setContentIntent(mediaSession.sessionActivity)
            .setOngoing(player.playWhenReady && player.playbackState != Player.STATE_IDLE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setCustomContentView(customViews)
            .setCustomBigContentView(customViews)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession))

        val shouldBeOngoing = player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED

        val notification = notificationBuilder.build()
        if (shouldBeOngoing) {
            notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
        } else {
            notification.flags = notification.flags and Notification.FLAG_ONGOING_EVENT.inv()
        }

        return MediaNotification(mediaNotification.notificationId, notification)
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        defaultProvider.handleCustomCommand(session, action, extras)
}
