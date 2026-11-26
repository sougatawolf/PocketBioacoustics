package com.example.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.sqrt

data class RecordingVisualizationData(
    val filePath: String,
    val sampleRateHz: Int,
    val durationMs: Long,
    val waveformAmplitudes: List<Float>,
    val spectrogramColumns: List<List<Float>>,
    val peakFrequencyHz: Int,
    val startFraction: Float = 0f,
    val endFraction: Float = 1f
)

/**
 * Extracts a time-aligned amplitude waveform envelope and 512-point FFT spectrogram grid
 * across an entire recorded lossless file (WAV, AIFF, FLAC, ALAC) or a zoomed time sub-slice
 * `[startFraction .. endFraction]` without blocking the UI thread.
 */
object RecordingVisualizationExtractor {

    const val DEFAULT_WAVEFORM_BARS = 72
    const val DEFAULT_SPECTROGRAM_COLUMNS = SpectrogramAnalyzer.SPECTROGRAM_COLUMN_COUNT
    private const val MAX_IN_MEMORY_DECODE_BYTES = 64L * 1024L * 1024L

    private val pcmCacheLock = Any()
    private var cachedPcmKey: String? = null
    private var cachedPcmAudio: DecodedPcmAudio? = null

    fun extract(
        file: File,
        waveformBarCount: Int = DEFAULT_WAVEFORM_BARS,
        spectrogramColumnCount: Int = DEFAULT_SPECTROGRAM_COLUMNS,
        startFraction: Float = 0f,
        endFraction: Float = 1f
    ): RecordingVisualizationData? {
        if (!file.exists() || !file.isFile) return null
        val meta = AudioMetadataParser.parse(file) ?: return null
        if (meta.sampleRateHz <= 0 || meta.channels <= 0) return null

        val clampedStart = startFraction.coerceIn(0f, 1f - TimeZoomMath.MIN_TIME_SPAN)
        val clampedEnd = endFraction.coerceIn(clampedStart + TimeZoomMath.MIN_TIME_SPAN, 1f)

        return try {
            when (meta.format) {
                RecordingFileFormat.WAV -> extractFromWav(
                    file = file,
                    meta = meta,
                    waveformBarCount = waveformBarCount,
                    spectrogramColumnCount = spectrogramColumnCount,
                    startFraction = clampedStart,
                    endFraction = clampedEnd
                )
                RecordingFileFormat.AIFF -> extractFromAiff(
                    file = file,
                    meta = meta,
                    waveformBarCount = waveformBarCount,
                    spectrogramColumnCount = spectrogramColumnCount,
                    startFraction = clampedStart,
                    endFraction = clampedEnd
                )
                RecordingFileFormat.FLAC -> extractFromInMemoryDecoder(
                    file = file,
                    meta = meta,
                    waveformBarCount = waveformBarCount,
                    spectrogramColumnCount = spectrogramColumnCount,
                    startFraction = clampedStart,
                    endFraction = clampedEnd
                ) { FlacFormat.decodeToLittleEndianPcm(it) }
                RecordingFileFormat.ALAC -> extractFromInMemoryDecoder(
                    file = file,
                    meta = meta,
                    waveformBarCount = waveformBarCount,
                    spectrogramColumnCount = spectrogramColumnCount,
                    startFraction = clampedStart,
                    endFraction = clampedEnd
                ) { AlacFormat.decodeToLittleEndianPcm(it) }
            }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFromWav(
        file: File,
        meta: ParsedAudioMetadata,
        waveformBarCount: Int,
        spectrogramColumnCount: Int,
        startFraction: Float,
        endFraction: Float
    ): RecordingVisualizationData? {
        if (meta.bitsPerSample != 16) return null
        RandomAccessFile(file, "r").use { raf ->
            val riffHeader = ByteArray(12)
            if (raf.read(riffHeader) != 12) return null
            if (String(riffHeader, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                String(riffHeader, 8, 4, Charsets.US_ASCII) != "WAVE"
            ) {
                return null
            }

            var dataOffset = -1L
            var dataBytes = 0L
            val chunkHeader = ByteArray(8)
            while (raf.filePointer + 8 <= raf.length()) {
                if (raf.read(chunkHeader) != 8) break
                val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
                    .toLong() and 0xFFFFFFFFL
                if (chunkId == "data") {
                    dataOffset = raf.filePointer
                    val available = (raf.length() - dataOffset).coerceAtLeast(0L)
                    dataBytes = if (chunkSize > 0L) minOf(chunkSize, available) else available
                    break
                } else {
                    val nextPos = raf.filePointer + chunkSize + (chunkSize and 1L)
                    if (nextPos <= raf.filePointer || nextPos > raf.length()) break
                    raf.seek(nextPos)
                }
            }

            if (dataOffset < 0L || dataBytes < 4L) return null
            return extractFromRandomAccessPcm(
                file = file,
                raf = raf,
                dataOffset = dataOffset,
                dataBytes = dataBytes,
                sampleRateHz = meta.sampleRateHz,
                channels = meta.channels,
                durationMs = meta.durationMs,
                bigEndian = false,
                waveformBarCount = waveformBarCount,
                spectrogramColumnCount = spectrogramColumnCount,
                startFraction = startFraction,
                endFraction = endFraction
            )
        }
    }

    private fun extractFromAiff(
        file: File,
        meta: ParsedAudioMetadata,
        waveformBarCount: Int,
        spectrogramColumnCount: Int,
        startFraction: Float,
        endFraction: Float
    ): RecordingVisualizationData? {
        if (meta.bitsPerSample != 16) return null
        RandomAccessFile(file, "r").use { raf ->
            val formHeader = ByteArray(12)
            if (raf.read(formHeader) != 12) return null
            if (String(formHeader, 0, 4, Charsets.US_ASCII) != "FORM" ||
                String(formHeader, 8, 4, Charsets.US_ASCII) != "AIFF"
            ) {
                return null
            }

            var ssndOffset = -1L
            var ssndBytes = 0L
            val chunkHeader = ByteArray(8)
            while (raf.filePointer + 8 <= raf.length()) {
                if (raf.read(chunkHeader) != 8) break
                val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                val chunkSize = (((chunkHeader[4].toLong() and 0xFFL) shl 24) or
                    ((chunkHeader[5].toLong() and 0xFFL) shl 16) or
                    ((chunkHeader[6].toLong() and 0xFFL) shl 8) or
                    (chunkHeader[7].toLong() and 0xFFL))
                val chunkStart = raf.filePointer
                if (chunkId == "SSND" && chunkSize >= 8) {
                    val ssndSub = ByteArray(8)
                    if (raf.read(ssndSub) != 8) return null
                    val offsetVal = (((ssndSub[0].toLong() and 0xFFL) shl 24) or
                        ((ssndSub[1].toLong() and 0xFFL) shl 16) or
                        ((ssndSub[2].toLong() and 0xFFL) shl 8) or
                        (ssndSub[3].toLong() and 0xFFL))
                    ssndOffset = chunkStart + 8L + offsetVal
                    val available = (raf.length() - ssndOffset).coerceAtLeast(0L)
                    ssndBytes = minOf((chunkSize - 8L - offsetVal).coerceAtLeast(0L), available)
                    break
                }
                val nextPos = chunkStart + chunkSize + (chunkSize and 1L)
                if (nextPos <= chunkStart || nextPos > raf.length()) break
                raf.seek(nextPos)
            }

            if (ssndOffset < 0L || ssndBytes < 4L) return null
            return extractFromRandomAccessPcm(
                file = file,
                raf = raf,
                dataOffset = ssndOffset,
                dataBytes = ssndBytes,
                sampleRateHz = meta.sampleRateHz,
                channels = meta.channels,
                durationMs = meta.durationMs,
                bigEndian = true,
                waveformBarCount = waveformBarCount,
                spectrogramColumnCount = spectrogramColumnCount,
                startFraction = startFraction,
                endFraction = endFraction
            )
        }
    }

    private fun extractFromRandomAccessPcm(
        file: File,
        raf: RandomAccessFile,
        dataOffset: Long,
        dataBytes: Long,
        sampleRateHz: Int,
        channels: Int,
        durationMs: Long,
        bigEndian: Boolean,
        waveformBarCount: Int,
        spectrogramColumnCount: Int,
        startFraction: Float,
        endFraction: Float
    ): RecordingVisualizationData {
        val frameBytes = (channels * 2).coerceAtLeast(2)
        val totalFrames = (dataBytes / frameBytes).coerceAtLeast(1L)
        val sliceStartFrame = floor(startFraction * totalFrames).toLong()
            .coerceIn(0L, (totalFrames - 1L).coerceAtLeast(0L))
        val sliceEndFrame = ceil(endFraction * totalFrames).toLong()
            .coerceIn(sliceStartFrame + 1L, totalFrames)
        val sliceFrames = (sliceEndFrame - sliceStartFrame).coerceAtLeast(1L)

        // 1. Waveform envelope across [sliceStartFrame .. sliceEndFrame]
        val framesPerBucket = (sliceFrames / waveformBarCount.toLong()).toInt().coerceIn(32, 256)
        val windowBuf = ByteArray(framesPerBucket * frameBytes)
        val waveform = ArrayList<Float>(waveformBarCount)
        for (barIdx in 0 until waveformBarCount) {
            val bucketCenterFrame = sliceStartFrame +
                (((barIdx + 0.5) / waveformBarCount.toDouble()) * sliceFrames).toLong()
            val startFrame = (bucketCenterFrame - framesPerBucket / 2L)
                .coerceIn(0L, (totalFrames - 1L).coerceAtLeast(0L))
            val framesToRead = minOf(framesPerBucket.toLong(), totalFrames - startFrame).toInt()
            val bytesToRead = framesToRead * frameBytes
            raf.seek(dataOffset + startFrame * frameBytes)
            val read = readFullyUpTo(raf, windowBuf, bytesToRead)
            waveform.add(computeNormalizedDbAmplitude(windowBuf, read, channels, bigEndian))
        }

        // 2. Spectrogram columns (512-point FFT per column across [sliceStartFrame .. sliceEndFrame])
        val fftFrames = SpectrogramAnalyzer.FFT_SIZE
        val fftBuf = ByteArray(fftFrames * frameBytes)
        val monoBuf = ByteArray(fftFrames * 2)
        val columns = ArrayList<List<Float>>(spectrogramColumnCount)
        var strongestPeakHz = 0
        var strongestBinEnergy = 0f

        for (colIdx in 0 until spectrogramColumnCount) {
            val centerFrame = sliceStartFrame +
                (((colIdx + 0.5) / spectrogramColumnCount.toDouble()) * sliceFrames).toLong()
            val startFrame = (centerFrame - fftFrames / 2L)
                .coerceIn(0L, (totalFrames - fftFrames).coerceAtLeast(0L))
            val framesToRead = minOf(fftFrames.toLong(), totalFrames - startFrame).toInt()
            val bytesToRead = framesToRead * frameBytes
            raf.seek(dataOffset + startFrame * frameBytes)
            val read = readFullyUpTo(raf, fftBuf, bytesToRead)
            val monoBytesLen = convertToMonoLittleEndian(fftBuf, read, channels, bigEndian, monoBuf)
            val result = SpectrogramAnalyzer.analyzePcmChunk(
                pcmBytes = monoBuf,
                lengthBytes = monoBytesLen,
                sampleRateHz = sampleRateHz
            )
            columns.add(result.bins)
            val maxBin = result.bins.maxOrNull() ?: 0f
            if (maxBin > strongestBinEnergy && result.peakFrequencyHz > 0) {
                strongestBinEnergy = maxBin
                strongestPeakHz = result.peakFrequencyHz
            }
        }

        return RecordingVisualizationData(
            filePath = file.absolutePath,
            sampleRateHz = sampleRateHz,
            durationMs = durationMs,
            waveformAmplitudes = waveform,
            spectrogramColumns = columns,
            peakFrequencyHz = strongestPeakHz,
            startFraction = startFraction,
            endFraction = endFraction
        )
    }

    private inline fun extractFromInMemoryDecoder(
        file: File,
        meta: ParsedAudioMetadata,
        waveformBarCount: Int,
        spectrogramColumnCount: Int,
        startFraction: Float,
        endFraction: Float,
        decode: (File) -> DecodedPcmAudio?
    ): RecordingVisualizationData? {
        if (file.length() > MAX_IN_MEMORY_DECODE_BYTES) return null
        val cacheKey = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        val decoded = synchronized(pcmCacheLock) {
            if (cachedPcmKey == cacheKey) cachedPcmAudio else null
        } ?: decode(file)?.also { fresh ->
            synchronized(pcmCacheLock) {
                cachedPcmKey = cacheKey
                cachedPcmAudio = fresh
            }
        } ?: return null

        val pcm = decoded.pcmLittleEndian
        if (pcm.size < 4) return null

        val channels = decoded.channels.coerceAtLeast(1)
        val frameBytes = (channels * 2).coerceAtLeast(2)
        val totalFrames = (pcm.size / frameBytes).coerceAtLeast(1)
        val sliceStartFrame = floor(startFraction * totalFrames).toInt()
            .coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
        val sliceEndFrame = ceil(endFraction * totalFrames).toInt()
            .coerceIn(sliceStartFrame + 1, totalFrames)
        val sliceFrames = (sliceEndFrame - sliceStartFrame).coerceAtLeast(1)

        val waveform = ArrayList<Float>(waveformBarCount)
        val bucketFrames = maxOf(1, sliceFrames / waveformBarCount)
        for (barIdx in 0 until waveformBarCount) {
            val startFrame = sliceStartFrame + ((barIdx.toLong() * sliceFrames) / waveformBarCount).toInt()
            val endFrame = minOf(totalFrames, startFrame + maxOf(bucketFrames, 48))
            val startByte = startFrame * frameBytes
            val lenBytes = minOf(512 * frameBytes, (endFrame - startFrame) * frameBytes)
            waveform.add(
                computeNormalizedDbAmplitudeFromSlice(
                    pcm = pcm,
                    offsetBytes = startByte,
                    lengthBytes = lenBytes,
                    channels = channels
                )
            )
        }

        val fftFrames = SpectrogramAnalyzer.FFT_SIZE
        val monoBuf = ByteArray(fftFrames * 2)
        val columns = ArrayList<List<Float>>(spectrogramColumnCount)
        var strongestPeakHz = 0
        var strongestBinEnergy = 0f

        for (colIdx in 0 until spectrogramColumnCount) {
            val centerFrame = sliceStartFrame +
                (((colIdx + 0.5) / spectrogramColumnCount.toDouble()) * sliceFrames).toInt()
            val startFrame = (centerFrame - fftFrames / 2)
                .coerceIn(0, (totalFrames - fftFrames).coerceAtLeast(0))
            val framesToRead = minOf(fftFrames, totalFrames - startFrame)
            val startByte = startFrame * frameBytes
            val bytesToRead = framesToRead * frameBytes
            val monoBytesLen = convertSliceToMonoLittleEndian(
                pcm = pcm,
                offsetBytes = startByte,
                lengthBytes = bytesToRead,
                channels = channels,
                outMono = monoBuf
            )
            val result = SpectrogramAnalyzer.analyzePcmChunk(
                pcmBytes = monoBuf,
                lengthBytes = monoBytesLen,
                sampleRateHz = decoded.sampleRateHz
            )
            columns.add(result.bins)
            val maxBin = result.bins.maxOrNull() ?: 0f
            if (maxBin > strongestBinEnergy && result.peakFrequencyHz > 0) {
                strongestBinEnergy = maxBin
                strongestPeakHz = result.peakFrequencyHz
            }
        }

        return RecordingVisualizationData(
            filePath = file.absolutePath,
            sampleRateHz = meta.sampleRateHz,
            durationMs = meta.durationMs,
            waveformAmplitudes = waveform,
            spectrogramColumns = columns,
            peakFrequencyHz = strongestPeakHz,
            startFraction = startFraction,
            endFraction = endFraction
        )
    }

    private fun readFullyUpTo(raf: RandomAccessFile, buf: ByteArray, length: Int): Int {
        var total = 0
        val target = minOf(length, buf.size)
        while (total < target) {
            val n = raf.read(buf, total, target - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    private fun convertToMonoLittleEndian(
        src: ByteArray,
        lengthBytes: Int,
        channels: Int,
        bigEndian: Boolean,
        outMono: ByteArray
    ): Int {
        val frameBytes = (channels * 2).coerceAtLeast(2)
        val frames = minOf(lengthBytes / frameBytes, outMono.size / 2)
        var outPos = 0
        for (f in 0 until frames) {
            var sum = 0
            val base = f * frameBytes
            for (c in 0 until channels) {
                sum += readSample16(src, base + c * 2, bigEndian)
            }
            val avg = (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outMono[outPos++] = (avg and 0xFF).toByte()
            outMono[outPos++] = ((avg ushr 8) and 0xFF).toByte()
        }
        return outPos
    }

    private fun convertSliceToMonoLittleEndian(
        pcm: ByteArray,
        offsetBytes: Int,
        lengthBytes: Int,
        channels: Int,
        outMono: ByteArray
    ): Int {
        if (channels == 1) {
            val copyLen = minOf(lengthBytes, outMono.size, (pcm.size - offsetBytes).coerceAtLeast(0))
            System.arraycopy(pcm, offsetBytes, outMono, 0, copyLen)
            return copyLen
        }
        val frameBytes = channels * 2
        val available = (pcm.size - offsetBytes).coerceAtLeast(0)
        val frames = minOf(minOf(lengthBytes, available) / frameBytes, outMono.size / 2)
        var outPos = 0
        for (f in 0 until frames) {
            var sum = 0
            val base = offsetBytes + f * frameBytes
            for (c in 0 until channels) {
                sum += readSample16(pcm, base + c * 2, bigEndian = false)
            }
            val avg = (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outMono[outPos++] = (avg and 0xFF).toByte()
            outMono[outPos++] = ((avg ushr 8) and 0xFF).toByte()
        }
        return outPos
    }

    private fun computeNormalizedDbAmplitude(
        buf: ByteArray,
        lengthBytes: Int,
        channels: Int,
        bigEndian: Boolean
    ): Float {
        val frameBytes = (channels * 2).coerceAtLeast(2)
        val frames = lengthBytes / frameBytes
        if (frames <= 0) return 0.03f
        var sumSq = 0.0
        for (f in 0 until frames) {
            var sampleSum = 0.0
            val base = f * frameBytes
            for (c in 0 until channels) {
                sampleSum += readSample16(buf, base + c * 2, bigEndian)
            }
            val mono = sampleSum / channels
            sumSq += mono * mono
        }
        val rms = sqrt(sumSq / frames) / 32768.0
        val db = if (rms > 1e-6) (20.0 * log10(rms)).toFloat().coerceIn(RecordingState.MIN_DB, 0f)
        else RecordingState.MIN_DB
        return ((db - RecordingState.MIN_DB) / (0f - RecordingState.MIN_DB)).coerceIn(0.03f, 1f)
    }

    private fun computeNormalizedDbAmplitudeFromSlice(
        pcm: ByteArray,
        offsetBytes: Int,
        lengthBytes: Int,
        channels: Int
    ): Float {
        val frameBytes = (channels * 2).coerceAtLeast(2)
        val available = minOf(lengthBytes, (pcm.size - offsetBytes).coerceAtLeast(0))
        val frames = available / frameBytes
        if (frames <= 0) return 0.03f
        var sumSq = 0.0
        for (f in 0 until frames) {
            var sampleSum = 0.0
            val base = offsetBytes + f * frameBytes
            for (c in 0 until channels) {
                sampleSum += readSample16(pcm, base + c * 2, bigEndian = false)
            }
            val mono = sampleSum / channels
            sumSq += mono * mono
        }
        val rms = sqrt(sumSq / frames) / 32768.0
        val db = if (rms > 1e-6) (20.0 * log10(rms)).toFloat().coerceIn(RecordingState.MIN_DB, 0f)
        else RecordingState.MIN_DB
        return ((db - RecordingState.MIN_DB) / (0f - RecordingState.MIN_DB)).coerceIn(0.03f, 1f)
    }

    private fun readSample16(buf: ByteArray, offset: Int, bigEndian: Boolean): Int {
        if (offset + 1 >= buf.size) return 0
        return if (bigEndian) {
            val hi = buf[offset].toInt()
            val lo = buf[offset + 1].toInt() and 0xFF
            ((hi shl 8) or lo).toShort().toInt()
        } else {
            val lo = buf[offset].toInt() and 0xFF
            val hi = buf[offset + 1].toInt()
            ((hi shl 8) or lo).toShort().toInt()
        }
    }
}
