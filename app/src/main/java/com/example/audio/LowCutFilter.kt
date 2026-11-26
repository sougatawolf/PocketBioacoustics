package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time 2nd-order Butterworth IIR High-Pass (Low-Cut) biquad filter in Direct Form II Transposed.
 * Uses Double-precision coefficients and delay states so filtering remains numerically stable from
 * 8 kHz up to 320 kHz across cutoffs of 20 Hz .. 200 Hz.
 */
class LowCutFilter {

    private var currentCutoffHz: Int = 0
    private var currentSampleRateHz: Int = 0

    // Normalized biquad coefficients
    private var b0: Double = 1.0
    private var b1: Double = 0.0
    private var b2: Double = 0.0
    private var a1: Double = 0.0
    private var a2: Double = 0.0

    // Direct Form II Transposed state registers
    private var z1: Double = 0.0
    private var z2: Double = 0.0

    fun resetState() {
        z1 = 0.0
        z2 = 0.0
    }

    fun configure(cutoffHz: Int, sampleRateHz: Int) {
        val normalizedCutoff = normalizeCutoffHz(cutoffHz)
        if (normalizedCutoff == currentCutoffHz && sampleRateHz == currentSampleRateHz) {
            return
        }

        val wasBypassed = currentCutoffHz == 0
        val rateChanged = currentSampleRateHz != sampleRateHz
        currentCutoffHz = normalizedCutoff
        currentSampleRateHz = sampleRateHz

        if (normalizedCutoff <= 0 || sampleRateHz <= 0) {
            b0 = 1.0
            b1 = 0.0
            b2 = 0.0
            a1 = 0.0
            a2 = 0.0
            resetState()
            return
        }

        // Clamp cutoff safely below Nyquist
        val maxSafeFreq = (sampleRateHz * 0.45).coerceAtLeast(1.0)
        val fc = normalizedCutoff.toDouble().coerceIn(MIN_ACTIVE_CUTOFF_HZ.toDouble(), maxSafeFreq)
        val omega0 = 2.0 * PI * fc / sampleRateHz.toDouble()
        val cosW0 = cos(omega0)
        val sinW0 = sin(omega0)
        val alpha = sinW0 / (2.0 * BUTTERWORTH_Q)

        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosW0) * 0.5) / a0
        b1 = (-(1.0 + cosW0)) / a0
        b2 = ((1.0 + cosW0) * 0.5) / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha) / a0

        if (wasBypassed || rateChanged) {
            resetState()
        }
    }

    fun processPcm16LeInPlace(buffer: ByteArray, lengthBytes: Int) {
        if (currentCutoffHz <= 0 || currentSampleRateHz <= 0 || lengthBytes < 2) {
            return
        }

        var i = 0
        while (i + 1 < lengthBytes) {
            val lo = buffer[i].toInt() and 0xFF
            val hi = buffer[i + 1].toInt()
            val x = ((hi shl 8) or lo).toShort().toDouble()

            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y

            val clamped = y.toInt().coerceIn(-32768, 32767)
            buffer[i] = (clamped and 0xFF).toByte()
            buffer[i + 1] = ((clamped ushr 8) and 0xFF).toByte()
            i += 2
        }
    }

    companion object {
        const val OFF_HZ = 0
        const val MIN_ACTIVE_CUTOFF_HZ = 20
        const val MAX_CUTOFF_HZ = 200
        const val DEFAULT_ACTIVE_CUTOFF_HZ = 90
        val PRESET_CUTOFFS_HZ = listOf(40, 60, 80, 90, 120, 150, 200)

        private val BUTTERWORTH_Q = 1.0 / sqrt(2.0)

        fun normalizeCutoffHz(rawHz: Int): Int {
            return if (rawHz <= 0) OFF_HZ else rawHz.coerceIn(MIN_ACTIVE_CUTOFF_HZ, MAX_CUTOFF_HZ)
        }

        /**
         * Ideal 2nd-order Butterworth high-pass magnitude response in dB at [freqHz]
         * for UI frequency-response curve visualization.
         */
        fun magnitudeResponseDb(freqHz: Double, cutoffHz: Int): Float {
            if (cutoffHz <= 0) return 0f
            val f = freqHz.coerceAtLeast(1.0)
            val ratio = cutoffHz.toDouble() / f
            val ratio4 = ratio * ratio * ratio * ratio
            val mag = 1.0 / sqrt(1.0 + ratio4)
            return if (mag <= 1e-6) -60f else (20.0 * log10(mag)).toFloat().coerceIn(-48f, 0f)
        }
    }
}
