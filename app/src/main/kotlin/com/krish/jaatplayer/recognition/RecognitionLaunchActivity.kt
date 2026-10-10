package com.krish.jaatplayer.recognition

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.krish.jaatplayer.MainActivity

class RecognitionLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleRecognitionLaunch()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleRecognitionLaunch()
    }

    private fun handleRecognitionLaunch() {
        if (!hasRecordPermission()) {
            openRecognitionPermissionFlow()
        } else if (shouldAskForOverlayPermission()) {
            askForOverlayPermission()
        } else {
            startRecognitionService()
        }
        finish()
    }

    // The floating recognition card needs "Display over other apps". Ask once; if the user
    // declines we keep working with the notification instead of nagging on every tap.
    private fun shouldAskForOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (Settings.canDrawOverlays(this)) return false
        return !getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_OVERLAY_PROMPTED, false)
    }

    private fun askForOverlayPermission() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OVERLAY_PROMPTED, true)
            .apply()
        Toast.makeText(
            this,
            "Allow \"Display over other apps\" for Jaat Player, then tap recognize again",
            Toast.LENGTH_LONG,
        ).show()
        val intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // No settings screen for this OEM - just carry on with the notification UI.
            startRecognitionService()
        }
    }

    private companion object {
        const val PREFS_NAME = "recognition_overlay_prefs"
        const val KEY_OVERLAY_PROMPTED = "overlay_permission_prompted"
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun openRecognitionPermissionFlow() {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_RECOGNITION
                putExtra(MainActivity.EXTRA_AUTO_START_RECOGNITION, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        startActivity(intent)
    }

    private fun startRecognitionService() {
        if (!hasRecordPermission()) return

        val serviceIntent = Intent(this, RecognitionForegroundService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForegroundService(serviceIntent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                    // Ignored
                } else {
                    throw e
                }
            }
        } else {
            startService(serviceIntent)
        }
    }
}
