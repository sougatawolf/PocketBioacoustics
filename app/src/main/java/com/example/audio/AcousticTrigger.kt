package com.example.audio

import kotlin.math.roundToInt

/**
 * Configuration for background frequency & amplitude triggered recording with pre-recording buffer
 * and configurable silence auto-stop timeout (1..30 min).
 */
data class AcousticTriggerConfig(
    val enabled: Boolean = false,
    val entireFrequencyRange: Boolean = true,
    val minFreqHz: Int = DEFAULT_MIN_FREQ_HZ,
    val maxFreqHz: Int = DEFAULT_MAX_FREQ_HZ,
    val amplitudeThresholdDb: Float = DEFAULT_THRESHOLD_DB,
    val preRecordSeconds: Int = DEFAULT_PRE_RECORD_SECONDS,
    val autoStopSilenceMinutes: Int = DEFAULT_AUTO_STOP_SILENCE_MINUTES
) {
    fun normalizedForSampleRate(sampleRateHz: Int): AcousticTriggerConfig {
        val nyquist = (sampleRateHz / 2).coerceAtLeast(MIN_BAND_WIDTH_HZ + 100)
        val clampedLow = minFreqHz.coerceIn(0, nyquist - MIN_BAND_WIDTH_HZ)
        val clampedHigh = maxFreqHz.coerceIn(clampedLow + MIN_BAND_WIDTH_HZ, nyquist)
        val clampedDb = amplitudeThresholdDb.coerceIn(MIN_THRESHOLD_DB, MAX_THRESHOLD_DB)
        val clampedPre = preRecordSeconds.coerceIn(0, MAX_PRE_RECORD_SECONDS)
        val clampedSilence = autoStopSilenceMinutes.coerceIn(
            MIN_AUTO_STOP_SILENCE_MINUTES,
            MAX_AUTO_STOP_SILENCE_MINUTES
        )
        return copy(
            minFreqHz = clampedLow,
            maxFreqHz = clampedHigh,
            amplitudeThresholdDb = clampedDb,
            preRecordSeconds = clampedPre,
            autoStopSilenceMinutes = clampedSilence
        )
    }

    fun bandSummaryLabel(sampleRateHz: Int): String {
        val nyquist = (sampleRateHz / 2).coerceAtLeast(1000)
        return if (entireFrequencyRange) {
            "0–${SpectrogramZoomMath.formatFreqAxisHz(nyquist)}"
        } else {
            val norm = normalizedForSampleRate(sampleRateHz)
            "${SpectrogramZoomMath.formatFreqAxisHz(norm.minFreqHz)}–${
                SpectrogramZoomMath.formatFreqAxisHz(norm.maxFreqHz)
            }"
        }
    }

    fun summaryLabel(sampleRateHz: Int): String {
        val pre = if (preRecordSeconds > 0) "${preRecordSeconds}s pre" else "No pre-rec"
        return "${bandSummaryLabel(sampleRateHz)} • ≥${amplitudeThresholdDb.roundToInt()} dBFS • $pre • ${autoStopSilenceMinutes}m stop"
    }

    companion object {
        const val MIN_BAND_WIDTH_HZ = 100
        const val DEFAULT_MIN_FREQ_HZ = 500
        const val DEFAULT_MAX_FREQ_HZ = 8000

        const val MIN_THRESHOLD_DB = -60f
        const val MAX_THRESHOLD_DB = -3f
        const val DEFAULT_THRESHOLD_DB = -32f

        val PRE_RECORD_PRESETS = listOf(0, 2, 5, 10)
        const val DEFAULT_PRE_RECORD_SECONDS = 5
        const val MAX_PRE_RECORD_SECONDS = 10

        const val MIN_AUTO_STOP_SILENCE_MINUTES = 1
        const val MAX_AUTO_STOP_SILENCE_MINUTES = 30
        const val DEFAULT_AUTO_STOP_SILENCE_MINUTES = 5
        val AUTO_STOP_PRESETS_MINUTES = listOf(1, 3, 5, 10, 15, 30)
    }
}

data class TriggerEvaluationResult(
    val bandLevelDb: Float,
    val isTriggered: Boolean,
    val peakFreqInBandHz: Int
)

object AcousticTriggerEvaluator {
    fun evaluate(
        pcmBytes: ByteArray,
        lengthBytes: Int,
        sampleRateHz: Int,
        config: AcousticTriggerConfig
    ): TriggerEvaluationResult {
        val norm = config.normalizedForSampleRate(sampleRateHz)
        val measurement = SpectrogramAnalyzer.analyzeBandPeakDb(
            pcmBytes = pcmBytes,
            lengthBytes = lengthBytes,
            sampleRateHz = sampleRateHz,
            minFreqHz = norm.minFreqHz,
            maxFreqHz = norm.maxFreqHz,
            entireBand = norm.entireFrequencyRange
        )
        return TriggerEvaluationResult(
            bandLevelDb = measurement.levelDb,
            isTriggered = measurement.levelDb >= norm.amplitudeThresholdDb,
            peakFreqInBandHz = measurement.peakFreqHz
        )
    }
}

/**
 * Sample-aligned circular RAM buffer holding the pre-recording lookback PCM tail while
 * armed in background trigger standby.
 */
class PcmRingBuffer(
    capacityBytes: Int,
    frameAlignmentBytes: Int = 2
) {
    private val frameAlign = frameAlignmentBytes.coerceAtLeast(2)
    val capacityBytes: Int = (capacityBytes.coerceAtLeast(0) / frameAlign) * frameAlign

    private val storage: ByteArray = if (this.capacityBytes > 0) {
        ByteArray(this.capacityBytes)
    } else {
        ByteArray(0)
    }

    private var writePos = 0
    private var validBytes = 0

    val bufferedBytes: Int
        get() = validBytes

    fun bufferedDurationMs(bytesPerSecond: Long): Long {
        if (bytesPerSecond <= 0L || validBytes <= 0) return 0L
        return (validBytes.toLong() * 1000L) / bytesPerSecond
    }

    fun write(data: ByteArray, length: Int) {
        if (capacityBytes <= 0 || length <= 0) return
        val alignedLen = (minOf(length, data.size) / frameAlign) * frameAlign
        if (alignedLen <= 0) return

        if (alignedLen >= capacityBytes) {
            // Input chunk exceeds buffer capacity: keep only the newest [capacityBytes] tail
            val srcOffset = alignedLen - capacityBytes
            System.arraycopy(data, srcOffset, storage, 0, capacityBytes)
            writePos = 0
            validBytes = capacityBytes
            return
        }

        val firstCopy = minOf(alignedLen, capacityBytes - writePos)
        System.arraycopy(data, 0, storage, writePos, firstCopy)
        val remaining = alignedLen - firstCopy
        if (remaining > 0) {
            System.arraycopy(data, firstCopy, storage, 0, remaining)
        }
        writePos = (writePos + alignedLen) % capacityBytes
        validBytes = minOf(capacityBytes, validBytes + alignedLen)
    }

    fun drainChronological(): ByteArray {
        if (validBytes <= 0 || capacityBytes <= 0) {
            clear()
            return ByteArray(0)
        }
        val out = ByteArray(validBytes)
        if (validBytes < capacityBytes) {
            System.arraycopy(storage, 0, out, 0, validBytes)
        } else {
            val tailLen = capacityBytes - writePos
            System.arraycopy(storage, writePos, out, 0, tailLen)
            if (writePos > 0) {
                System.arraycopy(storage, 0, out, tailLen, writePos)
            }
        }
        clear()
        return out
    }

    fun clear() {
        writePos = 0
        validBytes = 0
    }
}
