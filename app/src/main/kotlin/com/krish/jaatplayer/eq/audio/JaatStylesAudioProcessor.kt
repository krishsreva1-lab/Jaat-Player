package com.krish.jaatplayer.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Real-time audio DSP processor implementing the "Jaat Styles" 8D Spatial effect.
 *
 * The DJ Filter Sweep, Adaptive Bass Drop and Auto-Mashup modes have been removed
 * (per request) — Adaptive Bass and the Mashup "chopper" never actually mixed
 * multiple songs together (true multi-song mashup mixing needs a whole separate
 * multi-track playback engine, not an AudioProcessor on a single stream), and DJ
 * Filter Sweep was just a low-pass sweep. Only the 8D Spatial effect remains, and
 * it's been rebuilt to be a *real* 8D effect instead of simple left-right panning:
 *
 * - ILD (interaural level difference): the existing left/right gain panning.
 * - ITD (interaural time difference): a short, continuously variable delay line on
 *   whichever ear is "far" from the sound's position — real ears hear a delayed
 *   copy from the far side, not just a quieter one. This is most of what makes a
 *   panned sound feel like it's actually moving *around* your head instead of just
 *   sliding left-right inside it.
 * - Front/back directional filtering: sounds coming from behind lose high
 *   frequencies (your outer ear/head shadow filters them) and sounds in front stay
 *   bright. Modulating a low-pass filter by the "depth" (front/back) position is
 *   what sells the front-vs-behind illusion, since ILD/ITD alone only really
 *   convey left/right.
 *
 * Position is either automatic (a slow LFO continuously orbits all the way around:
 * left -> front -> right -> behind -> left again) or a manual override, set by
 * [manualPan] (-1 = full left, +1 = full right) and [manualDepth] (-1 = fully
 * behind, +1 = fully in front) when [manualPositionEnabled] is true.
 */
@UnstableApi
class JaatStylesAudioProcessor : AudioProcessor {

    @Volatile
    var isStyleEnabled: Boolean = false

    @Volatile
    var intensity: Float = 0.7f

    /** When true, [manualPan]/[manualDepth] are used instead of the auto-orbit LFO. */
    @Volatile
    var manualPositionEnabled: Boolean = false

    /** -1f (full left) .. 0f (center) .. +1f (full right). */
    @Volatile
    var manualPan: Float = 0f

    /** -1f (fully behind) .. 0f (beside) .. +1f (fully in front). */
    @Volatile
    var manualDepth: Float = 0f

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isConfigured = false

    private var inputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    // 8D Spatial Orbit LFO (used only when manualPositionEnabled == false)
    private var orbitPhase = 0.0

    // --- ITD delay lines (one per ear) ---
    // Real human ITD tops out around ~660us. We exaggerate slightly (up to ~1.1ms)
    // since headphone listeners find a subtler ITD hard to notice; still short
    // enough to sound like a delay/position cue rather than an audible echo.
    private var maxDelaySamplesF = 0.0
    private var delayBufL: DoubleArray = DoubleArray(0)
    private var delayBufR: DoubleArray = DoubleArray(0)
    private var delayWriteIdx = 0

    // --- Front/back directional low-pass (one-pole) per ear ---
    private var dirFilterL = 0.0
    private var dirFilterR = 0.0

    data class JaatStylesDebugInfo(
        val isEnabled: Boolean,
        val intensityPercent: Int,
        val manualPositionEnabled: Boolean,
        val orbitAngleDeg: Int,
        val panPercent: Int,
        val depthPercent: Int,
    )

    fun getDebugInfo(): JaatStylesDebugInfo {
        val (pan, depth) = currentPanDepth()
        return JaatStylesDebugInfo(
            isEnabled = isStyleEnabled,
            intensityPercent = (intensity * 100).toInt(),
            manualPositionEnabled = manualPositionEnabled,
            orbitAngleDeg = ((orbitPhase * 180.0 / PI).toInt() % 360 + 360) % 360,
            panPercent = (pan * 100).toInt(),
            depthPercent = (depth * 100).toInt(),
        )
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        private const val MAX_ITD_SECONDS = 0.0011 // ~1.1ms
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding == C.ENCODING_PCM_16BIT && channelCount in 1..2 && sampleRate > 0) {
            isConfigured = true
            maxDelaySamplesF = MAX_ITD_SECONDS * sampleRate
            val bufSize = maxDelaySamplesF.toInt() + 4
            delayBufL = DoubleArray(bufSize)
            delayBufR = DoubleArray(bufSize)
            delayWriteIdx = 0
            return inputAudioFormat
        }
        isConfigured = false
        return AudioProcessor.AudioFormat.NOT_SET
    }

    override fun isActive(): Boolean = isConfigured && encoding == C.ENCODING_PCM_16BIT && channelCount in 1..2

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remainingBytes = inputBuffer.remaining()
        if (remainingBytes == 0) return

        if (!isStyleEnabled) {
            if (this.outputBuffer.capacity() < remainingBytes) {
                this.outputBuffer = ByteBuffer.allocateDirect(remainingBytes).order(ByteOrder.nativeOrder())
            } else {
                this.outputBuffer.clear()
            }
            this.outputBuffer.put(inputBuffer)
            this.outputBuffer.flip()
            return
        }

        if (this.inputBuffer.capacity() < remainingBytes) {
            this.inputBuffer = ByteBuffer.allocateDirect(remainingBytes).order(ByteOrder.nativeOrder())
        } else {
            this.inputBuffer.clear()
        }

        val inputShorts = inputBuffer.asShortBuffer()

        while (inputShorts.hasRemaining()) {
            val sampleL = inputShorts.get()
            val sampleR = if (channelCount == 2) inputShorts.get() else sampleL

            val processed = process8DSpatial(sampleL.toDouble(), sampleR.toDouble())
            this.inputBuffer.putShort(processed.first.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            if (channelCount == 2) {
                this.inputBuffer.putShort(processed.second.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }

        inputBuffer.position(inputBuffer.position() + remainingBytes)
        this.inputBuffer.flip()
        this.outputBuffer = this.inputBuffer
    }

    /** Returns the current (pan, depth), each in -1f..+1f. */
    private fun currentPanDepth(): Pair<Double, Double> {
        return if (manualPositionEnabled) {
            manualPan.toDouble().coerceIn(-1.0, 1.0) to manualDepth.toDouble().coerceIn(-1.0, 1.0)
        } else {
            sin(orbitPhase) to cos(orbitPhase)
        }
    }

    private fun process8DSpatial(l: Double, r: Double): Pair<Double, Double> {
        if (!manualPositionEnabled) {
            val speed = 0.12 * (0.5 + intensity) // full 360 deg loop every ~10-20s
            orbitPhase += (2.0 * PI * speed) / sampleRate
            if (orbitPhase > 2.0 * PI) orbitPhase -= 2.0 * PI
        }
        val (pan, depth) = currentPanDepth() // pan: -1 left..+1 right, depth: -1 behind..+1 front

        // --- ILD: equal-power left/right gain with minimum opposite ear bleed ---
        // Scale pan to -0.68..+0.68 so the far-side ear drops to ~-12dB (~25% gain) instead of complete silence
        val effectivePan = pan * 0.68
        val gainL = cos((effectivePan + 1.0) * (PI / 4.0))
        val gainR = sin((effectivePan + 1.0) * (PI / 4.0))

        // --- ITD: delay whichever ear is "far side" from the pan position ---
        // pan > 0 (source right) -> left ear is far -> left is delayed.
        // pan < 0 (source left)  -> right ear is far -> right is delayed.
        val delayLSamples = maxDelaySamplesF * (if (pan > 0) pan else 0.0)
        val delayRSamples = maxDelaySamplesF * (if (pan < 0) -pan else 0.0)

        val bufSize = delayBufL.size
        delayBufL[delayWriteIdx] = l
        delayBufR[delayWriteIdx] = r

        val delayedL = readDelayed(delayBufL, delayWriteIdx, delayLSamples, bufSize)
        val delayedR = readDelayed(delayBufR, delayWriteIdx, delayRSamples, bufSize)

        delayWriteIdx = (delayWriteIdx + 1) % bufSize

        // --- Front/back directional low-pass: duller when behind (depth < 0) ---
        // depth ranges -1..+1; map to a filter coefficient so "front" is fully
        // open (bright, alpha=1 i.e. no filtering) and "behind" rolls off highs.
        val behindAmount = ((-depth + 1.0) * 0.5).coerceIn(0.0, 1.0) // 0 front .. 1 behind
        val alpha = (1.0 - behindAmount * 0.75 * intensity).coerceIn(0.15, 1.0)
        dirFilterL += alpha * (delayedL - dirFilterL)
        dirFilterR += alpha * (delayedR - dirFilterR)

        // A touch of extra attenuation when fully behind, like sound wrapping
        // around the head, so front/back isn't only a tone-color change.
        val distanceAtten = 1.0 - behindAmount * 0.12 * intensity

        val outL = dirFilterL * gainL * distanceAtten
        val outR = dirFilterR * gainR * distanceAtten

        return Pair(outL, outR)
    }

    /** Fractional-delay read (linear interpolation) from a circular buffer. */
    private fun readDelayed(buf: DoubleArray, writeIdx: Int, delaySamples: Double, size: Int): Double {
        if (delaySamples <= 0.0) return buf[writeIdx]
        val exactPos = writeIdx - delaySamples
        val floorPos = floor(exactPos)
        val frac = exactPos - floorPos
        val i0 = (((floorPos.toInt() % size) + size) % size)
        val i1 = (i0 + 1) % size
        return buf[i0] * (1.0 - frac) + buf[i1] * frac
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        dirFilterL = 0.0
        dirFilterR = 0.0
        delayBufL.fill(0.0)
        delayBufR.fill(0.0)
    }

    override fun reset() {
        flush()
        inputBuffer = EMPTY_BUFFER
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isConfigured = false
    }
}
