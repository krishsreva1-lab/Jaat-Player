package com.krish.jaatplayer.recognition

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import timber.log.Timber
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.krish.jaatplayer.MainActivity
import com.krish.jaatplayer.R
import com.krish.jaatplayer.db.DatabaseDao
import com.krish.jaatplayer.db.entities.RecognitionHistory
import com.krish.jaatplayer.widget.MusicRecognizerWidgetService
import com.music.shazamkit.models.RecognitionResult
import com.music.shazamkit.models.RecognitionStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecognitionServiceEntryPoint {
    fun databaseDao(): DatabaseDao
}

class RecognitionForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var recognitionJob: Job? = null
    private var statusJob: Job? = null
    private var keepNotificationOnStop = false
    private var terminalStateHandled = false

    // True while the floating "over other apps" overlay is the UI for this run. When it is,
    // the mandatory foreground-service notification is kept as quiet as possible (it used to
    // be the only UI, which the system showed as the status-bar capsule).
    private var useOverlay = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).d("onStartCommand: flags=%d, startId=%d", flags, startId)
        instance = this
        useOverlay = RecognitionOverlay.canShow(this)
        if (!startInForeground()) return START_NOT_STICKY
        startRecognitionIfNeeded()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Timber.tag(TAG).d("Service destroyed (keepNotification=%b)", keepNotificationOnStop)
        recognitionJob?.cancel()
        statusJob?.cancel()
        serviceScope.cancel()
        if (instance === this) instance = null
        // The result/error overlay deliberately outlives this service (it stays until the
        // user taps the cross). Only tear it down if we die mid-recognition.
        if (useOverlay && !terminalStateHandled) RecognitionOverlay.dismiss()
        if (!keepNotificationOnStop) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        super.onDestroy()
    }

    private fun startInForeground(): Boolean {
        val notification =
            buildNotification(
                title = getString(R.string.recognize_music),
                contentText = getString(R.string.recognition_notification_listening),
                isTerminal = false,
                contentIntent = null,
                largeIcon = null,
                actionIntent = null,
                actionTitle = null,
            )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            return true
        } catch (foregroundTypeException: SecurityException) {
            Timber.w(foregroundTypeException, "Unable to start microphone foreground service")
            stopSelf()
            return false
        } catch (runtimeException: RuntimeException) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    runtimeException::class.java.name ==
                    "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                Timber.w(runtimeException, "Unable to start microphone foreground service")
                stopSelf()
                return false
            }
            throw runtimeException
        }
    }

    private fun startRecognitionIfNeeded() {
        if (recognitionJob?.isActive == true) return
        Timber.tag(TAG).d("Starting recognition flow")

        keepNotificationOnStop = false
        terminalStateHandled = false
        MusicRecognitionService.reset()
        Timber.tag(TAG).d("MusicRecognitionService reset")

        if (useOverlay) showOverlayListening()

        statusJob?.cancel()
        statusJob =
            serviceScope.launch {
                MusicRecognitionService.recognitionStatus.collect { status ->
                    when (status) {
                        is RecognitionStatus.Ready -> Unit
                        else -> renderStatus(status)
                    }
                }
            }

        recognitionJob =
            serviceScope.launch {
                val result = MusicRecognitionService.recognize(this@RecognitionForegroundService)
                if (result is RecognitionStatus.Error &&
                    MusicRecognitionService.recognitionStatus.value !is RecognitionStatus.Error
                ) {
                    renderStatus(result)
                }
            }
    }

    private fun renderStatus(status: RecognitionStatus) {
        when (status) {
            is RecognitionStatus.Listening -> {
                Timber.tag(TAG).d("Status: Listening")
                if (useOverlay && showOverlayListening()) return
                updateNotification(
                    title = getString(R.string.recognize_music),
                    contentText = getString(R.string.recognition_notification_listening),
                    isTerminal = false,
                    contentIntent = null,
                    largeIcon = null,
                    actionIntent = null,
                    actionTitle = null,
                )
            }

            is RecognitionStatus.Processing -> {
                Timber.tag(TAG).d("Status: Processing")
                if (useOverlay &&
                    overlayOk(
                        RecognitionOverlay.showProcessing(
                            applicationContext,
                            getString(R.string.recognition_notification_processing),
                        ),
                    )
                ) return
                updateNotification(
                    title = getString(R.string.recognize_music),
                    contentText = getString(R.string.recognition_notification_processing),
                    isTerminal = false,
                    contentIntent = null,
                    largeIcon = null,
                    actionIntent = null,
                    actionTitle = null,
                )
            }

            is RecognitionStatus.Success -> {
                Timber.tag(TAG).i("Status: Success — '%s' by %s", status.result.title, status.result.artist)
                handleSuccess(status.result)
            }

            is RecognitionStatus.NoMatch -> {
                if (terminalStateHandled) return
                terminalStateHandled = true
                Timber.tag(TAG).i("Status: No match")
                if (useOverlay &&
                    overlayOk(
                        RecognitionOverlay.showMessage(
                            applicationContext,
                            getString(R.string.recognition_notification_no_match),
                        ),
                    )
                ) {
                    finishWithPersistentResult()
                    return
                }
                updateNotification(
                    title = getString(R.string.recognize_music),
                    contentText = getString(R.string.recognition_notification_no_match),
                    isTerminal = true,
                    contentIntent = null,
                    largeIcon = null,
                    actionIntent = null,
                    actionTitle = null,
                )
                finishWithPersistentResult()
            }

            is RecognitionStatus.Error -> {
                if (terminalStateHandled) return
                terminalStateHandled = true
                Timber.tag(TAG).w("Status: Error — %s", status.message)
                if (useOverlay &&
                    overlayOk(
                        RecognitionOverlay.showMessage(
                            applicationContext,
                            getString(R.string.recognition_notification_failed),
                        ),
                    )
                ) {
                    finishWithPersistentResult()
                    return
                }
                updateNotification(
                    title = getString(R.string.recognize_music),
                    contentText = getString(R.string.recognition_notification_failed),
                    isTerminal = true,
                    contentIntent = null,
                    largeIcon = null,
                    actionIntent = null,
                    actionTitle = null,
                )
                finishWithPersistentResult()
            }

            is RecognitionStatus.Ready -> Unit
        }
    }

    private fun updateNotification(
        title: String,
        contentText: String,
        isTerminal: Boolean,
        contentIntent: PendingIntent?,
        largeIcon: Bitmap?,
        actionIntent: PendingIntent?,
        actionTitle: String?,
    ) {
        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID,
            buildNotification(
                title = title,
                contentText = contentText,
                isTerminal = isTerminal,
                contentIntent = contentIntent,
                largeIcon = largeIcon,
                actionIntent = actionIntent,
                actionTitle = actionTitle,
            ),
        )
    }

    private fun buildNotification(
        title: String,
        contentText: String,
        isTerminal: Boolean,
        contentIntent: PendingIntent?,
        largeIcon: Bitmap?,
        actionIntent: PendingIntent?,
        actionTitle: String?,
    ) =
        NotificationCompat.Builder(this, if (useOverlay) OVERLAY_CHANNEL_ID else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_mic)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(if (useOverlay) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(!isTerminal)
            .setAutoCancel(isTerminal)
            .setContentIntent(contentIntent)
            .setLargeIcon(largeIcon)
            .apply {
                if (actionIntent != null && actionTitle != null) {
                    addAction(0, actionTitle, actionIntent)
                }
            }
            .build()

    private fun handleSuccess(result: RecognitionResult) {
        if (terminalStateHandled) return
        terminalStateHandled = true

        val pendingIntent = createResultPendingIntent(result)

        // Overlay mode: show the song on the floating bubble; it stays until the user taps
        // the cross, so no result notification is posted at all.
        val overlayShown =
            useOverlay &&
                overlayOk(
                    RecognitionOverlay.showResult(
                        context = applicationContext,
                        title = result.title,
                        artist = result.artist,
                        onOpen = { runCatching { pendingIntent.send() } },
                    ),
                )

        if (!overlayShown) {
            updateNotification(
                title = result.title,
                contentText = result.artist,
                isTerminal = true,
                contentIntent = pendingIntent,
                largeIcon = null,
                actionIntent = pendingIntent,
                actionTitle = getString(R.string.listen_on_jaatplayer),
            )
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val dao = EntryPointAccessors.fromApplication(
                    applicationContext,
                    RecognitionServiceEntryPoint::class.java
                ).databaseDao()
                dao.insert(
                    RecognitionHistory(
                        trackId = result.trackId,
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        coverArtUrl = result.coverArtUrl,
                        coverArtHqUrl = result.coverArtHqUrl,
                        genre = result.genre,
                        releaseDate = result.releaseDate,
                        label = result.label,
                        shazamUrl = result.shazamUrl,
                        appleMusicUrl = result.appleMusicUrl,
                        spotifyUrl = result.spotifyUrl,
                        isrc = result.isrc,
                        youtubeVideoId = result.youtubeVideoId,
                        recognizedAt = LocalDateTime.now()
                    )
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to save recognition history to DB")
            }

            val coverUrl = result.coverArtHqUrl ?: result.coverArtUrl
            val coverBitmap =
                if (coverUrl == null) {
                    null
                } else {
                    withTimeoutOrNull(1_500L) {
                        loadBitmap(coverUrl)
                    }
                }

            if (coverBitmap != null) {
                if (overlayShown) {
                    withContext(Dispatchers.Main) { RecognitionOverlay.setCover(coverBitmap) }
                } else {
                    updateNotification(
                        title = result.title,
                        contentText = result.artist,
                        isTerminal = true,
                        contentIntent = pendingIntent,
                        largeIcon = coverBitmap,
                        actionIntent = pendingIntent,
                        actionTitle = getString(R.string.listen_on_jaatplayer),
                    )
                }
            }
            finishWithPersistentResult()
        }
    }

    private suspend fun loadBitmap(url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            Timber.tag(TAG).d("Loading cover art bitmap from %s", url)
            runCatching {
                val connection = (URL(url).openConnection() as? HttpURLConnection)
                    ?: return@runCatching null
                try {
                    connection.connectTimeout = BITMAP_CONNECT_TIMEOUT_MS
                    connection.readTimeout = BITMAP_READ_TIMEOUT_MS
                    connection.instanceFollowRedirects = true
                    connection.doInput = true
                    connection.connect()
                    connection.inputStream.use(BitmapFactory::decodeStream)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

    private fun createResultPendingIntent(result: RecognitionResult): PendingIntent {
        val launchIntent =
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_RECOGNITION
                putExtra(EXTRA_RECOGNITION_TRACK_ID, result.trackId)
                putExtra(EXTRA_RECOGNITION_TITLE, result.title)
                putExtra(EXTRA_RECOGNITION_ARTIST, result.artist)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        return PendingIntent.getActivity(
            this,
            RESULT_PENDING_INTENT_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun finishWithPersistentResult() {
        if (useOverlay) {
            // The result lives on the overlay; drop the service and its (quiet) notification.
            Timber.tag(TAG).d("Finishing; result stays on the overlay until dismissed")
            keepNotificationOnStop = false
            stopSelf()
            return
        }
        Timber.tag(TAG).d("Finishing with persistent notification")
        keepNotificationOnStop = true
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun showOverlayListening(): Boolean =
        overlayOk(
            RecognitionOverlay.showListening(applicationContext) { cancelFromOverlay() },
        )

    /** If the overlay window couldn't be added, fall back to the notification UI. */
    private fun overlayOk(shown: Boolean): Boolean {
        if (!shown) useOverlay = false
        return shown
    }

    private fun cancelRecognition() {
        Timber.tag(TAG).d("Recognition cancelled from overlay")
        terminalStateHandled = true
        recognitionJob?.cancel()
        MusicRecognitionService.reset()
        RecognitionOverlay.dismiss()
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recognition_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.recognition_notification_channel_desc)
                setShowBadge(false)
            }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        // Minimal-importance channel used while the floating overlay is the real UI: the
        // foreground-service notification still has to exist but should stay out of sight.
        manager.createNotificationChannel(
            NotificationChannel(
                OVERLAY_CHANNEL_ID,
                getString(R.string.recognition_notification_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.recognition_notification_channel_desc)
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val EXTRA_RECOGNITION_TRACK_ID = "recognition_track_id"
        const val EXTRA_RECOGNITION_TITLE = "recognition_title"
        const val EXTRA_RECOGNITION_ARTIST = "recognition_artist"

        private const val CHANNEL_ID = "recognition_channel"
        private const val OVERLAY_CHANNEL_ID = "recognition_overlay_channel"

        @Volatile
        private var instance: RecognitionForegroundService? = null

        /** Called by the overlay's Cancel button (main thread). */
        fun cancelFromOverlay() {
            val service = instance
            if (service != null) service.cancelRecognition() else RecognitionOverlay.dismiss()
        }
        private const val NOTIFICATION_ID = 9100
        private const val RESULT_PENDING_INTENT_REQUEST_CODE = 9101
        private const val TAG = "RecognitionFgService"
        private const val BITMAP_CONNECT_TIMEOUT_MS = 1_200
        private const val BITMAP_READ_TIMEOUT_MS = 1_200
    }
}
