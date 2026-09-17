package com.krish.jaatplayer.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.krish.jaatplayer.constants.JaatBassSubMode
import com.krish.jaatplayer.constants.JaatStyleMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time audio DSP processor implementing "Jaat Styles" FX:
 * - Adaptive Bass Drop: Operates strictly in the -8 dB to 0 dB range with a minimum 5-second hold timer.
 *   Sub-modes:
 *   - BEAT_ADAPTIVE: Beat/energy analysis with a minimum 5-second hold time on verse attenuation.
 *   - RANDOM_TIMER: Automatically toggles bass down (-8 dB) for at least 5+ seconds periodically without beat analysis.
 * - 8D Spatial Swirl: LFO-driven binaural panning and phase delay for 360° headphone spatial orbit.
 * - DJ Filter Sweep: Low-pass filter cutoff frequency sweep.
 * - Auto-Mashup FX: Rhythmic DJ Sidechain Chop & Filter Pump effect.
 */
@UnstableApi
class JaatStylesAudioProcessor : AudioProcessor {

    @Volatile
    var isStyleEnabled: Boolean = false

    @Volatile
    var mode: JaatStyleMode = JaatStyleMode.BASS_DROP

    @Volatile
    var bassSubMode: JaatBassSubMode = JaatBassSubMode.BEAT_ADAPTIVE

    @Volatile
    var intensity: Float = 0.7f

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isConfigured = false

    private var inputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    // Adaptive Bass state
    private var currentBassGainDb = 0.0
    private var targetBassGainDb = 0.0
    private var bassFilterL = 0.0
    private var bassFilterR = 0.0
    private var bassEnvelope = 0.0
    private var longTermBassAvg = 0.05

    // 5-second minimum hold timer
    private var bassStateHoldSamples = 0L
    private var randomTimerSamples = 0L
    private var randomStateIsBassDown = false

    // Biquad state for bass shelf
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
    private var a1 = 0.0; private var a2 = 0.0
    private var x1L = 0.0; private var x2L = 0.0; private var y1L = 0.0; private var y2L = 0.0
    private var x1R = 0.0; private var x2R = 0.0; private var y1R = 0.0; private var y2R = 0.0

    // 8D Spatial Orbit LFO
    private var orbitPhase = 0.0

    // DJ Filter Sweep LFO
    private var sweepPhase = 0.0
    private var filterL1 = 0.0
    private var filterR1 = 0.0

    // Mashup DJ Chopper
    private var mashupLFO = 0.0
    private var mashupHPF_L = 0.0
    private var mashupHPF_R = 0.0

    data class JaatStylesDebugInfo(
        val isEnabled: Boolean,
        val mode: JaatStyleMode,
        val bassSubMode: JaatBassSubMode,
        val intensityPercent: Int,
        val currentBassGainDb: Double,
        val targetBassGainDb: Double,
        val lowFreqEnergyEnvelope: Double,
        val holdSecondsRemaining: Double,
        val orbitAngleDeg: Int,
        val filterCutoffHz: Int,
    )

    fun getDebugInfo(): JaatStylesDebugInfo {
        val holdSecs = if (sampleRate > 0) bassStateHoldSamples.toDouble() / sampleRate.toDouble() else 0.0
        return JaatStylesDebugInfo(
            isEnabled = isStyleEnabled,
            mode = mode,
            bassSubMode = bassSubMode,
            intensityPercent = (intensity * 100).toInt(),
            currentBassGainDb = currentBassGainDb,
            targetBassGainDb = targetBassGainDb,
            lowFreqEnergyEnvelope = bassEnvelope,
            holdSecondsRemaining = holdSecs,
            orbitAngleDeg = ((orbitPhase * 180.0 / PI).toInt() % 360 + 360) % 360,
            filterCutoffHz = (200 + (sin(sweepPhase) + 1.0) * 0.5 * 8000.0 * intensity).toInt(),
        )
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        private const val SHELF_FREQ_HZ = 120.0
        private const val MIN_HOLD_SECONDS = 5.0 // Minimum 5 seconds hold time for bass attenuation
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding == C.ENCODING_PCM_16BIT && channelCount in 1..2 && sampleRate > 0) {
            isConfigured = true
            updateShelfCoefficients(currentBassGainDb)
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

            val processed = processSample(sampleL.toDouble(), sampleR.toDouble())
            this.inputBuffer.putShort(processed.first.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            if (channelCount == 2) {
                this.inputBuffer.putShort(processed.second.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }

        inputBuffer.position(inputBuffer.position() + remainingBytes)
        this.inputBuffer.flip()
        this.outputBuffer = this.inputBuffer
    }

    private fun processSample(l: Double, r: Double): Pair<Double, Double> {
        return when (mode) {
            JaatStyleMode.BASS_DROP -> processAdaptiveBass(l, r)
            JaatStyleMode.SPATIAL_8D -> process8DSpatial(l, r)
            JaatStyleMode.FILTER_SWEEP -> processFilterSweep(l, r)
            JaatStyleMode.MASHUP -> processMashupEffect(l, r)
        }
    }

    private fun processAdaptiveBass(l: Double, r: Double): Pair<Double, Double> {
        val minHoldSamples = (MIN_HOLD_SECONDS * sampleRate.toDouble()).toLong()

        when (bassSubMode) {
            JaatBassSubMode.BEAT_ADAPTIVE -> {
                // Normalize PCM sample (-1.0 .. +1.0)
                val normL = l / 32768.0
                val normR = r / 32768.0

                // Low-pass filter for sub-bass (<120Hz)
                bassFilterL += 0.025 * (normL - bassFilterL)
                bassFilterR += 0.025 * (normR - bassFilterR)
                val bassAbs = abs((bassFilterL + bassFilterR) * 0.5)

                // Envelope follower
                if (bassAbs > bassEnvelope) {
                    bassEnvelope += 0.005 * (bassAbs - bassEnvelope)
                } else {
                    bassEnvelope *= 0.99992
                }

                // Long-term average
                longTermBassAvg = longTermBassAvg * 0.99998 + bassAbs * 0.00002

                val dropThreshold = (longTermBassAvg * (1.15 - intensity * 0.35)).coerceAtLeast(0.02)
                val isBeatDrop = bassEnvelope > dropThreshold

                if (bassStateHoldSamples > 0) {
                    bassStateHoldSamples--
                } else {
                    val newTargetDb = if (isBeatDrop) 0.0 else -8.0 * intensity.toDouble()
                    if (newTargetDb != targetBassGainDb) {
                        targetBassGainDb = newTargetDb
                        bassStateHoldSamples = minHoldSamples // Lock for AT LEAST 5 SECONDS!
                    }
                }
            }

            JaatBassSubMode.RANDOM_TIMER -> {
                if (randomTimerSamples > 0) {
                    randomTimerSamples--
                    if (bassStateHoldSamples > 0) bassStateHoldSamples--
                } else {
                    randomStateIsBassDown = !randomStateIsBassDown
                    targetBassGainDb = if (randomStateIsBassDown) -8.0 * intensity.toDouble() else 0.0
                    // Random interval between 5.0 and 9.0 seconds
                    val randomSecs = 5.0 + (Math.random() * 4.0)
                    randomTimerSamples = (randomSecs * sampleRate.toDouble()).toLong()
                    bassStateHoldSamples = (5.0 * sampleRate.toDouble()).toLong()
                }
            }
        }

        // Smooth per-sample gain transition
        if (abs(currentBassGainDb - targetBassGainDb) > 0.01) {
            currentBassGainDb += (targetBassGainDb - currentBassGainDb) * 0.0003
            updateShelfCoefficients(currentBassGainDb)
        }

        // Apply low-shelf filter
        val outL = b0 * l + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L; x1L = l; y2L = y1L; y1L = outL

        val outR = b0 * r + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R; x1R = r; y2R = y1R; y1R = outR

        return Pair(outL, outR)
    }

    private fun process8DSpatial(l: Double, r: Double): Pair<Double, Double> {
        val speed = 0.25 * (0.5 + intensity)
        orbitPhase += (2.0 * PI * speed) / sampleRate
        if (orbitPhase > 2.0 * PI) orbitPhase -= 2.0 * PI

        val pan = sin(orbitPhase) // -1.0 .. +1.0
        val gainL = cos((pan + 1.0) * (PI / 4.0))
        val gainR = sin((pan + 1.0) * (PI / 4.0))

        return Pair(l * gainL, r * gainR)
    }

    private fun processFilterSweep(l: Double, r: Double): Pair<Double, Double> {
        val speed = 0.15 * (0.5 + intensity)
        sweepPhase += (2.0 * PI * speed) / sampleRate
        if (sweepPhase > 2.0 * PI) sweepPhase -= 2.0 * PI

        val sweepAmount = (sin(sweepPhase) + 1.0) * 0.5 // 0.0 .. 1.0
        val alpha = 0.05 + sweepAmount * 0.85 * intensity

        filterL1 += alpha * (l - filterL1)
        filterR1 += alpha * (r - filterR1)

        return Pair(filterL1, filterR1)
    }

    private fun processMashupEffect(l: Double, r: Double): Pair<Double, Double> {
        // Rhythmic DJ Sidechain Chopper & Filter Pump Effect
        val bpmHz = 2.1 * (0.8 + intensity * 0.6)
        mashupLFO += (2.0 * PI * bpmHz) / sampleRate
        if (mashupLFO > 2.0 * PI) mashupLFO -= 2.0 * PI

        val phase = mashupLFO / (2.0 * PI)
        val sidechainDuck = (0.2 + 0.8 * sin(phase * PI)).coerceIn(0.1, 1.0)

        val alphaHP = if (phase > 0.5) 0.1 else 0.95
        mashupHPF_L += alphaHP * (l - mashupHPF_L)
        mashupHPF_R += alphaHP * (r - mashupHPF_R)

        val outL = (l * (1.0 - alphaHP) + (l - mashupHPF_L) * alphaHP) * sidechainDuck
        val outR = (r * (1.0 - alphaHP) + (r - mashupHPF_R) * alphaHP) * sidechainDuck

        return Pair(outL, outR)
    }

    private fun updateShelfCoefficients(gainDb: Double) {
        val clampedGain = gainDb.coerceIn(-12.0, 0.0) // Strictly negative/zero, no positive boost
        val A = sqrt(10.0.pow(clampedGain / 20.0))
        val omega = 2.0 * PI * SHELF_FREQ_HZ / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / 2.0 * sqrt(2.0)
        val sqrtA = sqrt(A)
        val aPlusOne = A + 1.0
        val aMinusOne = A - 1.0
        val twoSqrtAAlpha = 2.0 * sqrtA * alpha

        var rb0 = A * (aPlusOne - aMinusOne * cosOmega + twoSqrtAAlpha)
        var rb1 = 2.0 * A * (aMinusOne - aPlusOne * cosOmega)
        var rb2 = A * (aPlusOne - aMinusOne * cosOmega - twoSqrtAAlpha)
        val ra0 = aPlusOne + aMinusOne * cosOmega + twoSqrtAAlpha
        var ra1 = -2.0 * (aMinusOne + aPlusOne * cosOmega)
        var ra2 = aPlusOne + aMinusOne * cosOmega - twoSqrtAAlpha

        rb0 /= ra0; rb1 /= ra0; rb2 /= ra0; ra1 /= ra0; ra2 /= ra0
        b0 = rb0; b1 = rb1; b2 = rb2; a1 = ra1; a2 = ra2
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
        x1L = 0.0; x2L = 0.0; y1L = 0.0; y2L = 0.0
        x1R = 0.0; x2R = 0.0; y1R = 0.0; y2R = 0.0
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
