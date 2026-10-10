package com.krish.jaatplayer.quicksettings

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.krish.jaatplayer.R
import com.krish.jaatplayer.recognition.RecognitionForegroundService
import com.krish.jaatplayer.recognition.RecognitionLaunchActivity

class MusicRecognizerTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            icon = Icon.createWithResource(this@MusicRecognizerTileService, R.drawable.ic_shazam_diamond)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val hasMicPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val hasOverlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

        if (hasMicPermission && hasOverlayPermission) {
            // The whole point: start the recognizer directly as a background service, with
            // NO Activity at all. Whatever app is currently in the foreground (Instagram,
            // etc.) never gets interrupted or switched away from — the floating bubble from
            // RecognitionForegroundService/RecognitionOverlayController shows on top of it.
            val serviceIntent = Intent(this, RecognitionForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                // Fall back to the Activity path below if the direct start is ever refused.
                launchViaActivity()
            }
            collapseStatusBar()
            return
        }

        // First-time setup (permissions not granted yet) still needs an Activity, since
        // Android can only show permission/overlay-settings prompts from one.
        launchViaActivity()
    }

    private fun launchViaActivity() {
        val launchIntent =
            Intent(this, RecognitionLaunchActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    private fun collapseStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Nothing extra needed — starting a service doesn't auto-expand anything,
            // and startActivityAndCollapse() (used only in the fallback path) is what
            // actually requires the newer collapse APIs.
        }
    }
}
