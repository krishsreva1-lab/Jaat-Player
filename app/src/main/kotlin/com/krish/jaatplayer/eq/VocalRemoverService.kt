package com.krish.jaatplayer.eq

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.krish.jaatplayer.eq.audio.VocalRemoverAudioProcessor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VocalRemoverService @Inject constructor() {

    @SuppressLint("UnsafeOptInUsageError")
    private val audioProcessors = mutableListOf<VocalRemoverAudioProcessor>()
    private var pendingMode: VocalRemoverAudioProcessor.Mode? = null

    companion object {
        private const val TAG = "VocalRemoverService"
    }

    @OptIn(UnstableApi::class)
    fun addAudioProcessor(processor: VocalRemoverAudioProcessor) {
        audioProcessors.add(processor)
        pendingMode?.let { processor.mode = it }
        Timber.tag(TAG).d("Audio processor added. Total: ${audioProcessors.size}")
    }

    fun removeAudioProcessor(processor: VocalRemoverAudioProcessor) {
        audioProcessors.remove(processor)
    }

    @OptIn(UnstableApi::class)
    fun setMode(mode: VocalRemoverAudioProcessor.Mode): Result<Unit> {
        pendingMode = mode

        if (audioProcessors.isEmpty()) {
            Timber.tag(TAG).w("No audio processors set yet. Storing mode as pending: $mode")
            return Result.success(Unit)
        }

        var success = true
        var lastError: Exception? = null

        audioProcessors.forEach { processor ->
            try {
                processor.mode = mode
            } catch (e: Exception) {
                success = false
                lastError = e
            }
        }

        return if (success) Result.success(Unit) else Result.failure(lastError ?: Exception("Unknown error"))
    }

    fun isInitialized(): Boolean = audioProcessors.isNotEmpty()

    @OptIn(UnstableApi::class)
    fun currentMode(): VocalRemoverAudioProcessor.Mode =
        pendingMode ?: audioProcessors.firstOrNull()?.mode ?: VocalRemoverAudioProcessor.Mode.OFF

    fun release() {
        audioProcessors.clear()
        Timber.tag(TAG).d("Audio processor references cleared")
    }
}
