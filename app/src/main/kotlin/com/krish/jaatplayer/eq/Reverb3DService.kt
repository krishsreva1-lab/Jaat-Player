package com.krish.jaatplayer.eq

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.krish.jaatplayer.eq.audio.Reverb3DAudioProcessor
import com.krish.jaatplayer.eq.audio.Reverb3DPreset
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Reverb3DService @Inject constructor() {

    @SuppressLint("UnsafeOptInUsageError")
    private val audioProcessors = mutableListOf<Reverb3DAudioProcessor>()
    private var pendingPreset: Reverb3DPreset? = null
    private var shouldDisable: Boolean = false

    companion object {
        private const val TAG = "Reverb3DService"
    }

    @OptIn(UnstableApi::class)
    fun addAudioProcessor(processor: Reverb3DAudioProcessor) {
        audioProcessors.add(processor)
        Timber.tag(TAG).d("Audio processor added. Total: ${audioProcessors.size}")

        if (shouldDisable) {
            processor.disable()
        } else if (pendingPreset != null) {
            processor.setPreset(pendingPreset!!)
        }
    }

    fun removeAudioProcessor(processor: Reverb3DAudioProcessor) {
        audioProcessors.remove(processor)
    }

    @OptIn(UnstableApi::class)
    fun applyPreset(preset: Reverb3DPreset): Result<Unit> {
        pendingPreset = preset
        shouldDisable = preset == Reverb3DPreset.NONE

        if (audioProcessors.isEmpty()) {
            Timber.tag(TAG).w("No audio processors set yet. Storing preset as pending: $preset")
            return Result.success(Unit)
        }

        var success = true
        var lastError: Exception? = null

        audioProcessors.forEach { processor ->
            try {
                processor.setPreset(preset)
            } catch (e: Exception) {
                success = false
                lastError = e
            }
        }

        return if (success) Result.success(Unit) else Result.failure(lastError ?: Exception("Unknown error"))
    }

    @OptIn(UnstableApi::class)
    fun disable() {
        shouldDisable = true
        pendingPreset = Reverb3DPreset.NONE

        if (audioProcessors.isEmpty()) {
            Timber.tag(TAG).w("No audio processors set yet. Storing disable as pending")
            return
        }

        audioProcessors.forEach { processor ->
            try {
                processor.disable()
            } catch (e: Exception) {
                Timber.tag(TAG).e("Failed to disable reverb: ${e.message}")
            }
        }
        Timber.tag(TAG).d("Reverb disabled on all processors")
    }

    fun isInitialized(): Boolean = audioProcessors.isNotEmpty()

    @OptIn(UnstableApi::class)
    fun isEnabled(): Boolean = audioProcessors.any { it.isEnabled() }

    fun release() {
        audioProcessors.clear()
        Timber.tag(TAG).d("Audio processor references cleared")
    }
}
