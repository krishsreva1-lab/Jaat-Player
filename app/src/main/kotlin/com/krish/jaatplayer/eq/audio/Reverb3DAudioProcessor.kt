package com.krish.jaatplayer.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class Reverb3DPreset {
    NONE,
    NORMAL,
    CONCERT,
    TECHNO,
}

private data class ReverbParams(
    val roomSize: Double,   // 0..1, maps to comb filter feedback (decay length)
    val damping: Double,    // 0..1, high-frequency damping inside the comb feedback loop
    val wetLevel: Double,   // 0..1, how much reverb signal is mixed in
    val dryLevel: Double,   // 0..1, how much of the original signal is kept
    val width: Double,      // 0..1, stereo width/decorrelation of the reverb tail
    val preDelayMs: Double, // delay before the reverb tail starts, simulates room size/distance
)

private val PRESET_PARAMS = mapOf(
    // Everyday listening: a touch of room ambience without smearing vocals/lyrics. Bumped up
    // slightly from before — still the lightest of the three, but more noticeable than it was.
    Reverb3DPreset.NORMAL to ReverbParams(roomSize = 0.52, damping = 0.50, wetLevel = 0.26, dryLevel = 0.84, width = 0.65, preDelayMs = 9.0),
    // Big, spacious hall/stadium-style tail — long decay, wide stereo image. Eased down slightly
    // from before — still the strongest of the three, but a touch less overwhelming.
    Reverb3DPreset.CONCERT to ReverbParams(roomSize = 0.80, damping = 0.32, wetLevel = 0.36, dryLevel = 0.76, width = 1.0, preDelayMs = 26.0),
    // Tighter, punchier tail suited to electronic/dance music — shorter decay so bass/kick stay
    // defined, but a noticeably wide, energetic spatial image. Sits in the middle:
    // Normal (0.26 wet) < Techno (0.32 wet) < Concert (0.36 wet).
    Reverb3DPreset.TECHNO to ReverbParams(roomSize = 0.55, damping = 0.15, wetLevel = 0.32, dryLevel = 0.80, width = 0.95, preDelayMs = 12.0),
)

/**
 * Real-time Schroeder/Freeverb-style reverb (parallel comb filters + series allpass filters)
 * inserted directly into ExoPlayer's PCM audio pipeline, in the same
 * [DefaultAudioSink.DefaultAudioProcessorChain] as [CustomEqualizerAudioProcessor]. Unlike
 * Android's OS-level [android.media.audiofx.PresetReverb] (an auxiliary-type effect that needs
 * an explicit audio-track send level to be audible and can end up processing near-silence when
 * attached the "simple" way), this processes samples in-app, so it is audible unconditionally.
 */
@UnstableApi
class Reverb3DAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false

    @Volatile
    private var preset: Reverb3DPreset = Reverb3DPreset.NONE
    private var pendingPreset: Reverb3DPreset? = null
    private var params: ReverbParams = PRESET_PARAMS[Reverb3DPreset.NORMAL]!!

    private var inputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var combsLeft: Array<CombFilter> = emptyArray()
    private var combsRight: Array<CombFilter> = emptyArray()
    private var allpassLeft: Array<AllpassFilter> = emptyArray()
    private var allpassRight: Array<AllpassFilter> = emptyArray()
    private var preDelayLeft: DelayLine? = null
    private var preDelayRight: DelayLine? = null

    companion object {
        private const val TAG = "Reverb3DAudioProcessor"
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        // Freeverb comb/allpass tunings in ms, scaled to the actual sample rate at build time.
        // The right channel gets a small offset so the tail decorrelates between ears instead of
        // sounding like a single mono echo panned to both sides.
        private val COMB_TUNING_MS = doubleArrayOf(25.31, 26.94, 28.96, 30.75)
        private val ALLPASS_TUNING_MS = doubleArrayOf(5.10, 7.65)
        private const val STEREO_SPREAD_MS = 0.52
    }

    @Synchronized
    fun setPreset(newPreset: Reverb3DPreset) {
        if (sampleRate == 0) {
            pendingPreset = newPreset
            Timber.tag(TAG).d("Processor not configured yet, storing preset as pending: $newPreset")
            return
        }
        preset = newPreset
        if (newPreset != Reverb3DPreset.NONE) {
            params = PRESET_PARAMS[newPreset] ?: PRESET_PARAMS[Reverb3DPreset.NORMAL]!!
            buildFilters()
        }
        Timber.tag(TAG).d("Reverb preset set to $newPreset")
    }

    @Synchronized
    fun disable() {
        preset = Reverb3DPreset.NONE
        pendingPreset = null
        Timber.tag(TAG).d("Reverb disabled")
    }

    fun isEnabled(): Boolean = preset != Reverb3DPreset.NONE

    private fun buildFilters() {
        if (sampleRate == 0) return

        val feedback = 0.70 + params.roomSize * 0.28 // kept under 1.0 so combs stay stable
        val damp = params.damping

        combsLeft = COMB_TUNING_MS.map { ms -> CombFilter(msToSamples(ms), feedback, damp) }.toTypedArray()
        combsRight = COMB_TUNING_MS.map { ms -> CombFilter(msToSamples(ms + STEREO_SPREAD_MS), feedback, damp) }.toTypedArray()

        allpassLeft = ALLPASS_TUNING_MS.map { ms -> AllpassFilter(msToSamples(ms), 0.5) }.toTypedArray()
        allpassRight = ALLPASS_TUNING_MS.map { ms -> AllpassFilter(msToSamples(ms + STEREO_SPREAD_MS), 0.5) }.toTypedArray()

        val preDelaySamples = msToSamples(params.preDelayMs)
        preDelayLeft = DelayLine(preDelaySamples)
        preDelayRight = DelayLine(preDelaySamples)
    }

    private fun msToSamples(ms: Double): Int =
        ((ms / 1000.0) * sampleRate).toInt().coerceAtLeast(1)

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        pendingPreset?.let {
            pendingPreset = null
            setPreset(it)
        }

        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (preset == Reverb3DPreset.NONE || combsLeft.isEmpty()) {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return
            if (outputBuffer.capacity() < remaining) {
                outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
            } else {
                outputBuffer.clear()
            }
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        if (outputBuffer === EMPTY_BUFFER || outputBuffer.capacity() < inputSize) {
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        when (encoding) {
            C.ENCODING_PCM_16BIT -> processBuffer16Bit(inputBuffer, outputBuffer)
            else -> outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
    }

    private fun processBuffer16Bit(input: ByteBuffer, output: ByteBuffer) {
        val sampleCount = input.remaining() / 2
        val frames = sampleCount / channelCount.coerceAtLeast(1)

        repeat(frames) {
            if (channelCount == 2) {
                val left = input.getShort().toDouble() / 32768.0
                val right = input.getShort().toDouble() / 32768.0

                val delayedLeft = preDelayLeft?.process(left) ?: left
                val delayedRight = preDelayRight?.process(right) ?: right

                var wetLeft = 0.0
                for (comb in combsLeft) wetLeft += comb.process(delayedLeft)
                var wetRight = 0.0
                for (comb in combsRight) wetRight += comb.process(delayedRight)

                wetLeft /= combsLeft.size
                wetRight /= combsRight.size

                for (ap in allpassLeft) wetLeft = ap.process(wetLeft)
                for (ap in allpassRight) wetRight = ap.process(wetRight)

                // Stereo width: blend each channel's wet signal with a bit of the other side.
                val w = params.width
                val mixedWetLeft = wetLeft * (0.5 + w * 0.5) + wetRight * (0.5 - w * 0.5)
                val mixedWetRight = wetRight * (0.5 + w * 0.5) + wetLeft * (0.5 - w * 0.5)

                val outLeft = left * params.dryLevel + mixedWetLeft * params.wetLevel
                val outRight = right * params.dryLevel + mixedWetRight * params.wetLevel

                output.putShort((outLeft * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                output.putShort((outRight * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            } else {
                val mono = input.getShort().toDouble() / 32768.0
                val delayed = preDelayLeft?.process(mono) ?: mono
                var wet = 0.0
                for (comb in combsLeft) wet += comb.process(delayed)
                wet /= combsLeft.size
                for (ap in allpassLeft) wet = ap.process(wet)
                val out = mono * params.dryLevel + wet * params.wetLevel
                output.putShort((out * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
        }
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        combsLeft.forEach { it.reset() }
        combsRight.forEach { it.reset() }
        allpassLeft.forEach { it.reset() }
        allpassRight.forEach { it.reset() }
        preDelayLeft?.reset()
        preDelayRight?.reset()
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        inputBuffer = EMPTY_BUFFER
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }
}

/** Feedback comb filter with a one-pole low-pass in the feedback path (damping), per Freeverb. */
private class CombFilter(delaySamples: Int, private val feedback: Double, private val damping: Double) {
    private val buffer = DoubleArray(delaySamples.coerceAtLeast(1))
    private var index = 0
    private var filterStore = 0.0

    fun process(input: Double): Double {
        val output = buffer[index]
        filterStore = output * (1.0 - damping) + filterStore * damping
        buffer[index] = input + filterStore * feedback
        index = (index + 1) % buffer.size
        return output
    }

    fun reset() {
        buffer.fill(0.0)
        filterStore = 0.0
        index = 0
    }
}

/** Allpass filter, per Freeverb — diffuses the comb output into a smoother tail. */
private class AllpassFilter(delaySamples: Int, private val feedback: Double) {
    private val buffer = DoubleArray(delaySamples.coerceAtLeast(1))
    private var index = 0

    fun process(input: Double): Double {
        val bufOut = buffer[index]
        val output = -input + bufOut
        buffer[index] = input + bufOut * feedback
        index = (index + 1) % buffer.size
        return output
    }

    fun reset() {
        buffer.fill(0.0)
        index = 0
    }
}

/** Simple circular delay line used for pre-delay. */
private class DelayLine(size: Int) {
    private val buffer = DoubleArray(size.coerceAtLeast(1))
    private var index = 0

    fun process(input: Double): Double {
        val output = buffer[index]
        buffer[index] = input
        index = (index + 1) % buffer.size
        return output
    }

    fun reset() {
        buffer.fill(0.0)
        index = 0
    }
}
