package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

enum class VisualizerMode(val label: String) {
    WAVEFORM("Waveform"),
    SPECTROGRAM("Spectrogram")
}

data class SpectrumColumnResult(
    val bins: List<Float>,
    val peakFrequencyHz: Int
)

/**
 * Pure-Kotlin 512-point Hann-windowed Cooley-Tukey Radix-2 FFT analyzer
 * producing normalized (0f..1f) dBFS spectral bins for real-time bioacoustic spectrograms.
 */
object SpectrogramAnalyzer {

    const val FFT_SIZE = 512
    const val BIN_COUNT = 256
    const val SPECTROGRAM_COLUMN_COUNT = 56
    private const val MIN_SPEC_DB = -72f
    private const val MAX_SPEC_DB = -6f

    private val hannWindow = FloatArray(FFT_SIZE) { i ->
        (0.5 * (1.0 - cos((2.0 * PI * i) / (FFT_SIZE - 1)))).toFloat()
    }

    private val bitReversedIndices = IntArray(FFT_SIZE).apply {
        val bits = 9 // log2(512)
        for (i in 0 until FFT_SIZE) {
            var x = i
            var rev = 0
            for (b in 0 until bits) {
                rev = (rev shl 1) or (x and 1)
                x = x ushr 1
            }
            this[i] = rev
        }
    }

    fun analyzePcmChunk(
        pcmBytes: ByteArray,
        lengthBytes: Int,
        sampleRateHz: Int
    ): SpectrumColumnResult {
        if (lengthBytes < 4 || sampleRateHz <= 0) {
            return SpectrumColumnResult(
                bins = List(BIN_COUNT) { 0f },
                peakFrequencyHz = 0
            )
        }

        val totalSamples = lengthBytes / 2
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)

        // Read the most recent up-to-FFT_SIZE samples so spectral display stays low-latency
        val startSample = (totalSamples - FFT_SIZE).coerceAtLeast(0)
        val copyCount = minOf(totalSamples, FFT_SIZE)
        for (i in 0 until copyCount) {
            val byteIdx = (startSample + i) * 2
            val lo = pcmBytes[byteIdx].toInt() and 0xFF
            val hi = pcmBytes[byteIdx + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toDouble() / 32768.0
            real[i] = sample * hannWindow[i]
        }

        // In-place bit-reversal permutation
        for (i in 0 until FFT_SIZE) {
            val j = bitReversedIndices[i]
            if (i < j) {
                val tmpR = real[i]
                real[i] = real[j]
                real[j] = tmpR
            }
        }

        // Iterative Cooley-Tukey Radix-2 FFT
        var len = 2
        while (len <= FFT_SIZE) {
            val halfLen = len ushr 1
            val angleStep = -2.0 * PI / len.toDouble()
            val wStepR = cos(angleStep)
            val wStepI = sin(angleStep)
            var i = 0
            while (i < FFT_SIZE) {
                var wR = 1.0
                var wI = 0.0
                for (j in 0 until halfLen) {
                    val uR = real[i + j]
                    val uI = imag[i + j]
                    val idx2 = i + j + halfLen
                    val vR = real[idx2] * wR - imag[idx2] * wI
                    val vI = real[idx2] * wI + imag[idx2] * wR
                    real[i + j] = uR + vR
                    imag[i + j] = uI + vI
                    real[idx2] = uR - vR
                    imag[idx2] = uI - vI
                    val nextWR = wR * wStepR - wI * wStepI
                    wI = wR * wStepI + wI * wStepR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }

        val halfSpectrum = FFT_SIZE / 2 // 256 bins covering 0 .. Nyquist
        val magnitudes = FloatArray(halfSpectrum)
        var maxFftIdx = 1
        var maxMag = 0.0
        val scale = 2.0 / (FFT_SIZE * 0.5) // compensate Hann window coherent gain

        for (k in 1 until halfSpectrum) {
            val mag = sqrt(real[k] * real[k] + imag[k] * imag[k]) * scale
            if (mag > maxMag) {
                maxMag = mag
                maxFftIdx = k
            }
            val db = if (mag > 1e-6) (20.0 * log10(mag)).toFloat() else MIN_SPEC_DB
            magnitudes[k] = ((db - MIN_SPEC_DB) / (MAX_SPEC_DB - MIN_SPEC_DB)).coerceIn(0f, 1f)
        }
        magnitudes[0] = magnitudes[1] * 0.5f

        // Keep full 256 FFT bins (0 .. Nyquist) so frequency zooming reveals sharp spectral detail
        val outBins = magnitudes.toList()

        val peakFreqHz = if (maxMag > 1e-4) {
            ((maxFftIdx.toDouble() * sampleRateHz.toDouble()) / FFT_SIZE.toDouble()).toInt()
        } else {
            0
        }

        return SpectrumColumnResult(
            bins = outBins,
            peakFrequencyHz = peakFreqHz
        )
    }

    data class BandPeakMeasurement(
        val levelDb: Float,
        val peakFreqHz: Int
    )

    /**
     * Scans up to 16 overlapping 512-point Hann-windowed FFT windows across [pcmBytes]
     * and returns the sine-RMS-calibrated peak dBFS inside [minFreqHz..maxFreqHz]
     * (or full-band max of RMS & peak bin dBFS when [entireBand] is true).
     */
    fun analyzeBandPeakDb(
        pcmBytes: ByteArray,
        lengthBytes: Int,
        sampleRateHz: Int,
        minFreqHz: Int,
        maxFreqHz: Int,
        entireBand: Boolean
    ): BandPeakMeasurement {
        val totalSamples = lengthBytes / 2
        if (totalSamples < 4 || sampleRateHz <= 0) {
            return BandPeakMeasurement(levelDb = -60f, peakFreqHz = 0)
        }

        var sumSq = 0.0
        for (i in 0 until totalSamples) {
            val idx = i * 2
            val lo = pcmBytes[idx].toInt() and 0xFF
            val hi = pcmBytes[idx + 1].toInt()
            val norm = ((hi shl 8) or lo).toShort().toDouble() / 32768.0
            sumSq += norm * norm
        }
        val rms = sqrt(sumSq / totalSamples.toDouble())
        val fullRmsDb = if (rms > 1e-6) (20.0 * log10(rms)).toFloat().coerceIn(-60f, 0f) else -60f

        val startBin: Int
        val endBin: Int
        if (entireBand) {
            startBin = 1
            endBin = BIN_COUNT - 1
        } else {
            val loHz = minOf(minFreqHz, maxFreqHz).coerceAtLeast(0)
            val hiHz = maxOf(minFreqHz, maxFreqHz).coerceAtLeast(loHz)
            startBin = kotlin.math.floor(loHz.toDouble() * FFT_SIZE.toDouble() / sampleRateHz.toDouble())
                .toInt().coerceIn(1, BIN_COUNT - 1)
            endBin = kotlin.math.ceil(hiHz.toDouble() * FFT_SIZE.toDouble() / sampleRateHz.toDouble())
                .toInt().coerceIn(startBin, BIN_COUNT - 1)
        }

        val windowOffsets = if (totalSamples <= FFT_SIZE) {
            listOf(0)
        } else {
            val maxStart = totalSamples - FFT_SIZE
            val step = maxOf(FFT_SIZE / 2, maxStart / 15)
            buildList {
                var pos = 0
                while (pos <= maxStart) {
                    add(pos)
                    pos += step
                }
                if (lastOrNull() != maxStart) {
                    add(maxStart)
                }
            }
        }

        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        val coherentScale = 2.0 / (FFT_SIZE * 0.5)
        val rmsScale = coherentScale / sqrt(2.0)

        var bestMagRms = 0.0
        var bestBin = startBin

        for (startSample in windowOffsets) {
            java.util.Arrays.fill(real, 0.0)
            java.util.Arrays.fill(imag, 0.0)
            val copyCount = minOf(totalSamples - startSample, FFT_SIZE)
            for (i in 0 until copyCount) {
                val byteIdx = (startSample + i) * 2
                val lo = pcmBytes[byteIdx].toInt() and 0xFF
                val hi = pcmBytes[byteIdx + 1].toInt()
                val sample = ((hi shl 8) or lo).toShort().toDouble() / 32768.0
                real[i] = sample * hannWindow[i]
            }

            for (i in 0 until FFT_SIZE) {
                val j = bitReversedIndices[i]
                if (i < j) {
                    val tmp = real[i]
                    real[i] = real[j]
                    real[j] = tmp
                }
            }

            var len = 2
            while (len <= FFT_SIZE) {
                val halfLen = len ushr 1
                val angleStep = -2.0 * PI / len.toDouble()
                val wStepR = cos(angleStep)
                val wStepI = sin(angleStep)
                var i = 0
                while (i < FFT_SIZE) {
                    var wR = 1.0
                    var wI = 0.0
                    for (j in 0 until halfLen) {
                        val uR = real[i + j]
                        val uI = imag[i + j]
                        val idx2 = i + j + halfLen
                        val vR = real[idx2] * wR - imag[idx2] * wI
                        val vI = real[idx2] * wI + imag[idx2] * wR
                        real[i + j] = uR + vR
                        imag[i + j] = uI + vI
                        real[idx2] = uR - vR
                        imag[idx2] = uI - vI
                        val nextWR = wR * wStepR - wI * wStepI
                        wI = wR * wStepI + wI * wStepR
                        wR = nextWR
                    }
                    i += len
                }
                len = len shl 1
            }

            for (k in startBin..endBin) {
                val magRms = sqrt(real[k] * real[k] + imag[k] * imag[k]) * rmsScale
                if (magRms > bestMagRms) {
                    bestMagRms = magRms
                    bestBin = k
                }
            }
        }

        val bandDb = if (bestMagRms > 1e-6) {
            (20.0 * log10(bestMagRms)).toFloat().coerceIn(-60f, 0f)
        } else {
            -60f
        }
        val effectiveDb = if (entireBand) maxOf(fullRmsDb, bandDb) else bandDb
        val peakFreqHz = if (bestMagRms > 1e-4) {
            ((bestBin.toDouble() * sampleRateHz.toDouble()) / FFT_SIZE.toDouble()).toInt()
        } else {
            0
        }

        return BandPeakMeasurement(
            levelDb = effectiveDb,
            peakFreqHz = peakFreqHz
        )
    }
}

object SpectrogramZoomMath {
    const val MIN_ZOOM_SPAN = 0.08f

    fun zoomRange(
        startFrac: Float,
        endFrac: Float,
        zoomFactor: Float,
        anchorNormalizedInView: Float = 0.5f
    ): Pair<Float, Float> {
        val curStart = startFrac.coerceIn(0f, 1f - MIN_ZOOM_SPAN)
        val curEnd = endFrac.coerceIn(curStart + MIN_ZOOM_SPAN, 1f)
        val curSpan = (curEnd - curStart).coerceIn(MIN_ZOOM_SPAN, 1f)
        val factor = zoomFactor.coerceIn(0.1f, 12.5f)
        val newSpan = (curSpan / factor).coerceIn(MIN_ZOOM_SPAN, 1f)
        val anchor = anchorNormalizedInView.coerceIn(0f, 1f)
        val anchorFreqFrac = curStart + anchor * curSpan
        var newStart = anchorFreqFrac - anchor * newSpan
        if (newStart < 0f) newStart = 0f
        if (newStart + newSpan > 1f) newStart = (1f - newSpan).coerceAtLeast(0f)
        val newEnd = (newStart + newSpan).coerceAtMost(1f)
        return newStart to newEnd
    }

    fun panRange(
        startFrac: Float,
        endFrac: Float,
        deltaFrac: Float
    ): Pair<Float, Float> {
        val curStart = startFrac.coerceIn(0f, 1f - MIN_ZOOM_SPAN)
        val curEnd = endFrac.coerceIn(curStart + MIN_ZOOM_SPAN, 1f)
        val span = (curEnd - curStart).coerceIn(MIN_ZOOM_SPAN, 1f)
        if (span >= 0.999f) return 0f to 1f
        val newStart = (curStart + deltaFrac).coerceIn(0f, 1f - span)
        val newEnd = (newStart + span).coerceAtMost(1f)
        return newStart to newEnd
    }

    fun zoomLevel(startFrac: Float, endFrac: Float): Float {
        val span = (endFrac - startFrac).coerceIn(MIN_ZOOM_SPAN, 1f)
        return (1f / span).coerceIn(1f, 12.5f)
    }

    fun formatFreqAxisHz(hz: Int): String {
        return when {
            hz <= 0 -> "0 Hz"
            hz < 1000 -> "$hz Hz"
            hz < 10000 -> String.format(java.util.Locale.US, "%.2fk", hz / 1000.0)
            else -> String.format(java.util.Locale.US, "%.1fk", hz / 1000.0)
        }
    }
}

object TimeZoomMath {
    const val MIN_TIME_SPAN = 0.01f

    fun minSpanForDuration(durationMs: Long): Float {
        if (durationMs <= 0L) return MIN_TIME_SPAN
        // Allow zooming down to ~250 ms or 1% of the recording (whichever is larger, capped at 1f)
        return maxOf(MIN_TIME_SPAN, 250f / durationMs.toFloat()).coerceIn(MIN_TIME_SPAN, 1f)
    }

    fun zoomRange(
        startFrac: Float,
        endFrac: Float,
        zoomFactor: Float,
        anchorNormalizedInView: Float = 0.5f,
        minSpan: Float = MIN_TIME_SPAN
    ): Pair<Float, Float> {
        val clampedMin = minSpan.coerceIn(0.005f, 1f)
        val curStart = startFrac.coerceIn(0f, (1f - clampedMin).coerceAtLeast(0f))
        val curEnd = endFrac.coerceIn((curStart + clampedMin).coerceAtMost(1f), 1f)
        val curSpan = (curEnd - curStart).coerceIn(clampedMin, 1f)
        val factor = zoomFactor.coerceIn(0.1f, 16f)
        val newSpan = (curSpan / factor).coerceIn(clampedMin, 1f)
        val anchor = anchorNormalizedInView.coerceIn(0f, 1f)
        val anchorTimeFrac = curStart + anchor * curSpan
        var newStart = anchorTimeFrac - anchor * newSpan
        if (newStart < 0f) newStart = 0f
        if (newStart + newSpan > 1f) newStart = (1f - newSpan).coerceAtLeast(0f)
        val newEnd = (newStart + newSpan).coerceAtMost(1f)
        return newStart to newEnd
    }

    fun panRange(
        startFrac: Float,
        endFrac: Float,
        deltaFrac: Float,
        minSpan: Float = MIN_TIME_SPAN
    ): Pair<Float, Float> {
        val clampedMin = minSpan.coerceIn(0.005f, 1f)
        val curStart = startFrac.coerceIn(0f, (1f - clampedMin).coerceAtLeast(0f))
        val curEnd = endFrac.coerceIn((curStart + clampedMin).coerceAtMost(1f), 1f)
        val span = (curEnd - curStart).coerceIn(clampedMin, 1f)
        if (span >= 0.999f) return 0f to 1f
        val newStart = (curStart + deltaFrac).coerceIn(0f, 1f - span)
        val newEnd = (newStart + span).coerceAtMost(1f)
        return newStart to newEnd
    }

    fun formatTimeMs(ms: Long, showTenths: Boolean = false): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalSeconds = clamped / 1000L
        val tenths = ((clamped % 1000L) / 100L).toInt()
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val base = if (hours > 0L) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
        return if (showTenths) "$base.$tenths" else base
    }
}

