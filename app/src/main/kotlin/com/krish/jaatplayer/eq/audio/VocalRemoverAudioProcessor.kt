package com.krish.jaatplayer.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Three-way switch applied in realtime, right in the same audio chain as the EQ/reverb/jaat
 * processors, so it works instantly on whatever is currently playing (and on both players
 * during a crossfade, since it's registered on every player the same way the EQ is).
 *
 * OFF: untouched passthrough.
 *
 * REMOVE_VOCALS ("karaoke" mode): most songs place the lead vocal dead-center between the
 * left and right speakers, panned identically to both. Subtracting the right channel from the
 * left (and vice versa) cancels out anything that's identical in both channels — which removes
 * the vocal along with anything else mixed dead-center (often some of the bass/kick too). This
 * is the same trick every "vocal remover" toggle uses; it's not a true AI stem-separation, so a
 * song mixed with a wide or doubled vocal will only partially cancel, but on a typical center-
 * panned pop/movie mix it's a clean instant "instrumental" toggle.
 *
 * ISOLATE_VOCALS ("music remover" — push the vocal forward): true isolation of "only the
 * voice" out of an already-mixed stereo file isn't physically possible without an AI model that
 * separates stems (that needs a trained neural network running per-song, not a realtime filter,
 * so it can't be instant). What this mode does instead, fully in realtime: it takes the
 * center-channel content (L+R, the same place the vocal usually lives) and band-passes it to
 * the frequency range a human voice actually occupies, cutting away the deep bass and the very
 * top end where cymbals/synths live. That pushes the vocal forward and thins out a lot of the
 * instrumental, but low male vocals sharing the kick/bass register, or wide-mixed instruments,
 * will still bleed through — it's a strong "vocal-forward" effect, not a perfect a cappella.
 */
@UnstableApi
class VocalRemoverAudioProcessor : AudioProcessor {

    enum class Mode { OFF, REMOVE_VOCALS, ISOLATE_VOCALS }

    @Volatile
    var mode: Mode = Mode.OFF
        set(value) {
            field = value
            targetMix = if (value == Mode.OFF) 0f else 1f
            activeShapeIsIsolate = value == Mode.ISOLATE_VOCALS
        }

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false

    private var inputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    // Crossfades smoothly between dry and the effect over ~120ms so switching modes mid-song
    // never clicks or pops - same per-sample-ease trick used by the automix ducking processor.
    @Volatile
    private var targetMix: Float = 0f
    private var currentMix: Float = 0f
    private var activeShapeIsIsolate: Boolean = false

    // Band-pass for ISOLATE_VOCALS: a high-pass then a low-pass in series, tuned to the range
    // where vocal presence/intelligibility lives. Coefficients only depend on sample rate, so
    // they're computed once in configure() and never touched again (changing biquad
    // coefficients while their memory holds old state is what causes zipper clicks).
    private var hp: OnePoleCascadeBiquad? = null
    private var lp: OnePoleCascadeBiquad? = null

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        private const val MIX_EASE_MS = 120.0
        private const val HIGH_PASS_HZ = 220.0
        private const val LOW_PASS_HZ = 3800.0
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        hp = OnePoleCascadeBiquad.highPass(sampleRate, HIGH_PASS_HZ)
        lp = OnePoleCascadeBiquad.lowPass(sampleRate, LOW_PASS_HZ)
        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        if (outputBuffer.capacity() < inputSize) {
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        if (channelCount != 2) {
            // Nothing sensible to do without two channels to compare - pass through untouched.
            repeat(inputSize / 2) { outputBuffer.putShort(inputBuffer.getShort()) }
            outputBuffer.flip()
            return
        }

        val easePerSample = (1000.0 / (MIX_EASE_MS * sampleRate)).toFloat().coerceIn(0f, 1f)
        val isolate = activeShapeIsIsolate
        val hpFilter = hp
        val lpFilter = lp

        val sampleCount = inputSize / 2
        repeat(sampleCount / 2) {
            currentMix += (targetMix - currentMix) * easePerSample

            val inL = inputBuffer.getShort().toDouble() / 32768.0
            val inR = inputBuffer.getShort().toDouble() / 32768.0

            var wetL: Double
            var wetR: Double
            if (isolate) {
                // Center content, band-passed to the vocal presence range.
                val center = (inL + inR) * 0.5
                val bandPassed = lpFilter?.process(hpFilter?.process(center) ?: center) ?: center
                wetL = bandPassed
                wetR = bandPassed
            } else {
                // Cancel anything identical in both channels (the usual home of the vocal).
                wetL = (inL - inR) * 0.5
                wetR = (inR - inL) * 0.5
            }

            val outL = inL + (wetL - inL) * currentMix
            val outR = inR + (wetR - inR) * currentMix

            outputBuffer.putShort((outL * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            outputBuffer.putShort((outR * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
        }

        outputBuffer.flip()
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
        hp?.reset()
        lp?.reset()
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        inputBuffer = EMPTY_BUFFER
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
        currentMix = 0f
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    /** Minimal single-channel RBJ high/low-pass biquad, used mono (on the summed center). */
    private class OnePoleCascadeBiquad private constructor(
        private val b0: Double, private val b1: Double, private val b2: Double,
        private val a1: Double, private val a2: Double,
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(input: Double): Double {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = input; y2 = y1; y1 = output
            return output
        }

        fun reset() {
            x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
        }

        companion object {
            fun highPass(sampleRate: Int, freqHz: Double): OnePoleCascadeBiquad {
                val omega = 2.0 * PI * freqHz / sampleRate
                val sinO = sin(omega); val cosO = cos(omega)
                val alpha = sinO / (2.0 * 0.707)
                val a0 = 1.0 + alpha
                val b0 = (1.0 + cosO) / 2.0 / a0
                val b1 = -(1.0 + cosO) / a0
                val b2 = (1.0 + cosO) / 2.0 / a0
                val a1 = -2.0 * cosO / a0
                val a2 = (1.0 - alpha) / a0
                return OnePoleCascadeBiquad(b0, b1, b2, a1, a2)
            }

            fun lowPass(sampleRate: Int, freqHz: Double): OnePoleCascadeBiquad {
                val omega = 2.0 * PI * freqHz / sampleRate
                val sinO = sin(omega); val cosO = cos(omega)
                val alpha = sinO / (2.0 * 0.707)
                val a0 = 1.0 + alpha
                val b0 = (1.0 - cosO) / 2.0 / a0
                val b1 = (1.0 - cosO) / a0
                val b2 = (1.0 - cosO) / 2.0 / a0
                val a1 = -2.0 * cosO / a0
                val a2 = (1.0 - alpha) / a0
                return OnePoleCascadeBiquad(b0, b1, b2, a1, a2)
            }
        }
    }
}
