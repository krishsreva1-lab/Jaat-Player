package com.krish.jaatplayer.jaat.updater.downloadmanager

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.media3.session.MediaSession
import com.krish.jaatplayer.R
import com.krish.jaatplayer.utils.BitmapBlurHelper

object CustomNotificationLayout {

    fun createCustomViews(
        context: Context,
        mediaSession: MediaSession,
        thumbnail: Bitmap?,
        isPlaying: Boolean
    ): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_custom_player)

        val metadata = mediaSession.player.currentMediaItem?.mediaMetadata
        remoteViews.setTextViewText(R.id.notification_title, metadata?.title ?: "Unknown")
        remoteViews.setTextViewText(R.id.notification_artist, metadata?.artist ?: "Unknown")

        if (thumbnail != null) {
            remoteViews.setImageViewBitmap(R.id.notification_thumbnail, thumbnail)
            
            // Generate and set blurred background
            // Scale down for faster blurring
            val scaled = Bitmap.createScaledBitmap(thumbnail, 100, 100, true)
            val blurred = BitmapBlurHelper.blur(scaled, 15)
            if (blurred != null) {
                remoteViews.setImageViewBitmap(R.id.notification_background, blurred)
            }
        } else {
            remoteViews.setImageViewResource(R.id.notification_thumbnail, R.drawable.icon)
            remoteViews.setImageViewResource(R.id.notification_background, android.R.color.black)
        }

        // Set icons
        remoteViews.setImageViewResource(
            R.id.notification_play_pause,
            if (isPlaying) R.drawable.pause_applemusic else R.drawable.play_applemusic
        )

        // Set pending intents
        remoteViews.setOnClickPendingIntent(
            R.id.notification_prev,
            createPendingIntent(context, com.krish.jaatplayer.widget.MusicWidgetReceiver.ACTION_PREVIOUS)
        )
        remoteViews.setOnClickPendingIntent(
            R.id.notification_play_pause,
            createPendingIntent(context, com.krish.jaatplayer.widget.MusicWidgetReceiver.ACTION_PLAY_PAUSE)
        )
        remoteViews.setOnClickPendingIntent(
            R.id.notification_next,
            createPendingIntent(context, com.krish.jaatplayer.widget.MusicWidgetReceiver.ACTION_NEXT)
        )

        return remoteViews
    }

    private fun createPendingIntent(context: Context, action: String): PendingIntent {
        val intent = android.content.Intent(action)
        intent.component = android.content.ComponentName(context, "com.krish.jaatplayer.widget.MusicWidgetReceiver")
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
