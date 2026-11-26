package com.example.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs

object AiffFormat {
    const val HEADER_SIZE = 54

    fun createHeader(
        sampleRateHz: Int,
        channels: Int,
        bitsPerSample: Int,
        pcmDataBytes: Long
    ): ByteArray {
        val bytesPerFrame = (channels * (bitsPerSample / 8)).coerceAtLeast(1)
        val numSampleFrames = pcmDataBytes / bytesPerFrame
        val formSize = (46L + pcmDataBytes).coerceAtMost(0xFFFFFFFFL)
        val ssndSize = (8L + pcmDataBytes).coerceAtMost(0xFFFFFFFFL)

        val out = ByteArray(HEADER_SIZE)
        var pos = 0

        fun writeAscii(s: String) {
            for (c in s) out[pos++] = c.code.toByte()
        }

        fun writeU32BE(v: Long) {
            out[pos++] = ((v ushr 24) and 0xFF).toByte()
            out[pos++] = ((v ushr 16) and 0xFF).toByte()
            out[pos++] = ((v ushr 8) and 0xFF).toByte()
            out[pos++] = (v and 0xFF).toByte()
        }

        fun writeU16BE(v: Int) {
            out[pos++] = ((v ushr 8) and 0xFF).toByte()
            out[pos++] = (v and 0xFF).toByte()
        }

        writeAscii("FORM")
        writeU32BE(formSize)
        writeAscii("AIFF")

        writeAscii("COMM")
        writeU32BE(18L)
        writeU16BE(channels)
        writeU32BE(numSampleFrames)
        writeU16BE(bitsPerSample)
        val ext = encodeExtended80(sampleRateHz.toDouble())
        System.arraycopy(ext, 0, out, pos, 10)
        pos += 10

        writeAscii("SSND")
        writeU32BE(ssndSize)
        writeU32BE(0L) // offset
        writeU32BE(0L) // blockSize

        return out
    }

    fun updateHeaderSizes(file: File, pcmDataBytes: Long) {
        if (!file.exists()) return
        val bytesPerFrame = 2L // default fallback; updated below from COMM if possible
        val meta = parse(file)
        val actualFrameBytes = if (meta != null) {
            (meta.channels * (meta.bitsPerSample / 8)).coerceAtLeast(1).toLong()
        } else {
            bytesPerFrame
        }
        val numFrames = pcmDataBytes / actualFrameBytes
        val formSize = (46L + pcmDataBytes).coerceAtMost(0xFFFFFFFFL)
        val ssndSize = (8L + pcmDataBytes).coerceAtMost(0xFFFFFFFFL)

        RandomAccessFile(file, "rw").use { raf ->
            if (raf.length() < HEADER_SIZE) return
            raf.seek(4)
            raf.writeInt(formSize.toInt())
            raf.seek(22)
            raf.writeInt(numFrames.toInt())
            raf.seek(42)
            raf.writeInt(ssndSize.toInt())
        }
    }

    fun parse(file: File): ParsedAudioMetadata? {
        if (!file.exists() || file.length() < HEADER_SIZE) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val formHeader = ByteArray(12)
                if (raf.read(formHeader) != 12) return null
                if (String(formHeader, 0, 4, Charsets.US_ASCII) != "FORM") return null
                if (String(formHeader, 8, 4, Charsets.US_ASCII) != "AIFF") return null

                var channels = 0
                var numFrames = 0L
                var bitsPerSample = 0
                var sampleRateHz = 0

                val chunkHeader = ByteArray(8)
                while (raf.filePointer + 8 <= raf.length()) {
                    if (raf.read(chunkHeader) != 8) break
                    val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                    val chunkSize = readU32BE(chunkHeader, 4)
                    val chunkStart = raf.filePointer

                    if (chunkId == "COMM" && chunkSize >= 18) {
                        val comm = ByteArray(18)
                        if (raf.read(comm) != 18) return null
                        channels = readU16BE(comm, 0)
                        numFrames = readU32BE(comm, 2)
                        bitsPerSample = readU16BE(comm, 6)
                        sampleRateHz = decodeExtended80(comm, 8).toInt()
                    }
                    val paddedSize = chunkSize + (chunkSize and 1L)
                    val nextPos = chunkStart + paddedSize
                    if (nextPos <= chunkStart || nextPos > raf.length()) break
                    raf.seek(nextPos)
                }

                if (channels <= 0 || bitsPerSample <= 0 || sampleRateHz <= 0) return null
                val durationMs = (numFrames * 1000L) / sampleRateHz.toLong()
                ParsedAudioMetadata(
                    format = RecordingFileFormat.AIFF,
                    sampleRateHz = sampleRateHz,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                    durationMs = durationMs
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Reads AIFF SSND audio data and converts big-endian 16-bit PCM to little-endian PCM bytes. */
    fun decodeToLittleEndianPcm(file: File): DecodedPcmAudio? {
        if (!file.exists()) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val formHeader = ByteArray(12)
                if (raf.read(formHeader) != 12) return null
                if (String(formHeader, 0, 4, Charsets.US_ASCII) != "FORM") return null
                if (String(formHeader, 8, 4, Charsets.US_ASCII) != "AIFF") return null

                var channels = 0
                var bitsPerSample = 0
                var sampleRateHz = 0
                var ssndDataOffset = -1L
                var ssndDataBytes = 0L

                val chunkHeader = ByteArray(8)
                while (raf.filePointer + 8 <= raf.length()) {
                    if (raf.read(chunkHeader) != 8) break
                    val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                    val chunkSize = readU32BE(chunkHeader, 4)
                    val chunkStart = raf.filePointer

                    if (chunkId == "COMM" && chunkSize >= 18) {
                        val comm = ByteArray(18)
                        if (raf.read(comm) != 18) return null
                        channels = readU16BE(comm, 0)
                        bitsPerSample = readU16BE(comm, 6)
                        sampleRateHz = decodeExtended80(comm, 8).toInt()
                    } else if (chunkId == "SSND" && chunkSize >= 8) {
                        val ssndSub = ByteArray(8)
                        if (raf.read(ssndSub) != 8) return null
                        val dataOffset = readU32BE(ssndSub, 0)
                        ssndDataOffset = chunkStart + 8L + dataOffset
                        ssndDataBytes = (chunkSize - 8L - dataOffset).coerceAtLeast(0L)
                    }

                    val paddedSize = chunkSize + (chunkSize and 1L)
                    val nextPos = chunkStart + paddedSize
                    if (nextPos <= chunkStart || nextPos > raf.length()) break
                    raf.seek(nextPos)
                }

                if (channels <= 0 || bitsPerSample != 16 || sampleRateHz <= 0 || ssndDataOffset < 0) {
                    return null
                }
                val totalLen = minOf(ssndDataBytes, (raf.length() - ssndDataOffset).coerceAtLeast(0L)).toInt()
                val pcm = ByteArray(totalLen)
                raf.seek(ssndDataOffset)
                var readTotal = 0
                while (readTotal < totalLen) {
                    val n = raf.read(pcm, readTotal, totalLen - readTotal)
                    if (n <= 0) break
                    readTotal += n
                }
                // Swap big-endian 16-bit PCM to little-endian 16-bit PCM
                var i = 0
                while (i + 1 < readTotal) {
                    val hi = pcm[i]
                    pcm[i] = pcm[i + 1]
                    pcm[i + 1] = hi
                    i += 2
                }
                DecodedPcmAudio(
                    sampleRateHz = sampleRateHz,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                    pcmLittleEndian = if (readTotal == pcm.size) pcm else pcm.copyOf(readTotal)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readU16BE(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0xFF) shl 8) or (b[offset + 1].toInt() and 0xFF)
    }

    private fun readU32BE(b: ByteArray, offset: Int): Long {
        return ((b[offset].toLong() and 0xFFL) shl 24) or
            ((b[offset + 1].toLong() and 0xFFL) shl 16) or
            ((b[offset + 2].toLong() and 0xFFL) shl 8) or
            (b[offset + 3].toLong() and 0xFFL)
    }

    private fun encodeExtended80(value: Double): ByteArray {
        val out = ByteArray(10)
        if (value == 0.0) return out
        val absVal = abs(value)
        val signBit = if (value < 0.0) 0x8000 else 0
        var exp = 0
        var normalized = absVal
        while (normalized >= 2.0) {
            normalized /= 2.0
            exp++
        }
        while (normalized < 1.0) {
            normalized *= 2.0
            exp--
        }
        val biasedExp = (exp + 16383) and 0x7FFF
        val hiExp = signBit or biasedExp
        out[0] = ((hiExp ushr 8) and 0xFF).toByte()
        out[1] = (hiExp and 0xFF).toByte()

        // 64-bit mantissa with explicit integer bit at bit 63
        var frac = normalized
        var mantissa = 0L
        for (bit in 63 downTo 0) {
            if (frac >= 1.0) {
                mantissa = mantissa or (1L shl bit)
                frac -= 1.0
            }
            frac *= 2.0
        }
        for (i in 0 until 8) {
            out[2 + i] = ((mantissa ushr ((7 - i) * 8)) and 0xFFL).toByte()
        }
        return out
    }

    private fun decodeExtended80(b: ByteArray, offset: Int): Double {
        val expField = ((b[offset].toInt() and 0x7F) shl 8) or (b[offset + 1].toInt() and 0xFF)
        if (expField == 0) return 0.0
        val exp = expField - 16383
        var mantissa = 0.0
        var factor = 1.0
        for (byteIdx in 0 until 8) {
            val v = b[offset + 2 + byteIdx].toInt() and 0xFF
            for (bit in 7 downTo 0) {
                if (((v ushr bit) and 1) == 1) {
                    mantissa += factor
                }
                factor *= 0.5
            }
        }
        val result = mantissa * Math.pow(2.0, exp.toDouble())
        return if ((b[offset].toInt() and 0x80) != 0) -result else result
    }
}

data class DecodedPcmAudio(
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val pcmLittleEndian: ByteArray
)

class AiffFileWriter(
    override val file: File,
    override val quality: AudioQuality
) : AudioSegmentWriter {

    override val format: RecordingFileFormat = RecordingFileFormat.AIFF

    private var outputStream: BufferedOutputStream? = null
    override var pcmBytesWritten: Long = 0L
        private set
    override val estimatedFileBytes: Long
        get() = AiffFormat.HEADER_SIZE.toLong() + pcmBytesWritten

    private var swapBuffer = ByteArray(8192)
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        val fos = FileOutputStream(file)
        val bos = BufferedOutputStream(fos, 65536)
        bos.write(
            AiffFormat.createHeader(
                sampleRateHz = quality.sampleRateHz,
                channels = quality.channels,
                bitsPerSample = quality.bitsPerSample,
                pcmDataBytes = 0L
            )
        )
        outputStream = bos
    }

    override fun writePcm(buffer: ByteArray, length: Int) {
        if (closed || length <= 1) return
        val alignedLen = length and 1.inv()
        if (alignedLen <= 0) return
        if (swapBuffer.size < alignedLen) {
            swapBuffer = ByteArray(alignedLen)
        }
        var i = 0
        while (i < alignedLen) {
            // Convert little-endian 16-bit PCM to big-endian 16-bit PCM
            swapBuffer[i] = buffer[i + 1]
            swapBuffer[i + 1] = buffer[i]
            i += 2
        }
        outputStream?.write(swapBuffer, 0, alignedLen)
        pcmBytesWritten += alignedLen.toLong()
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            if ((pcmBytesWritten and 1L) != 0L) {
                outputStream?.write(0)
            }
            outputStream?.flush()
            outputStream?.close()
        } finally {
            outputStream = null
        }

        if (pcmBytesWritten > 0L && file.exists()) {
            AiffFormat.updateHeaderSizes(file, pcmBytesWritten)
        } else if (file.exists()) {
            file.delete()
        }
    }
}
