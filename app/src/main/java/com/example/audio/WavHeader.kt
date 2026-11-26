package com.example.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WavMetadata(
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val dataBytes: Long,
    val durationMs: Long
)

object WavHeader {
    const val HEADER_SIZE = 44

    fun createHeader(
        sampleRateHz: Int,
        channels: Int,
        bitsPerSample: Int,
        dataBytes: Long
    ): ByteArray {
        val byteRate = sampleRateHz.toLong() * channels * bitsPerSample / 8L
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val clampedDataBytes = dataBytes.coerceIn(0L, 0xFFFFFFFFL - 36L)
        val chunkSize = 36L + clampedDataBytes

        val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(chunkSize.toInt())
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // fmt sub-chunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // PCM subchunk size
        buffer.putShort(1.toShort()) // AudioFormat = 1 (PCM)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRateHz)
        buffer.putInt(byteRate.toInt())
        buffer.putShort(blockAlign)
        buffer.putShort(bitsPerSample.toShort())

        // data sub-chunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(clampedDataBytes.toInt())

        return buffer.array()
    }

    fun updateHeaderSizes(file: File, dataBytes: Long) {
        val clampedDataBytes = dataBytes.coerceIn(0L, 0xFFFFFFFFL - 36L)
        val chunkSize = (36L + clampedDataBytes).toInt()
        val subChunk2Size = clampedDataBytes.toInt()

        RandomAccessFile(file, "rw").use { raf ->
            val intBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            raf.seek(4L)
            intBuf.putInt(chunkSize)
            raf.write(intBuf.array())

            intBuf.clear()
            raf.seek(40L)
            intBuf.putInt(subChunk2Size)
            raf.write(intBuf.array())
        }
    }

    fun parse(file: File): WavMetadata? {
        if (!file.exists() || file.length() < HEADER_SIZE) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val riffHeader = ByteArray(12)
                if (raf.read(riffHeader) != 12) return null
                val riffId = String(riffHeader, 0, 4, Charsets.US_ASCII)
                val waveId = String(riffHeader, 8, 4, Charsets.US_ASCII)
                if (riffId != "RIFF" || waveId != "WAVE") return null

                var sampleRate = 44100
                var channels = 1
                var bitsPerSample = 16
                var dataBytes = (file.length() - HEADER_SIZE).coerceAtLeast(0L)
                var foundFmt = false
                var foundData = false

                val chunkHeader = ByteArray(8)
                while (raf.filePointer + 8 <= raf.length()) {
                    if (raf.read(chunkHeader) != 8) break
                    val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                    val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                        .toLong() and 0xFFFFFFFFL

                    when (chunkId) {
                        "fmt " -> {
                            if (chunkSize >= 16 && raf.filePointer + 16 <= raf.length()) {
                                val fmtBytes = ByteArray(16)
                                raf.readFully(fmtBytes)
                                val bb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                                bb.short // audioFormat
                                channels = bb.short.toInt().coerceAtLeast(1)
                                sampleRate = bb.int.coerceAtLeast(8000)
                                bb.int // byteRate
                                bb.short // blockAlign
                                bitsPerSample = bb.short.toInt().coerceAtLeast(8)
                                foundFmt = true
                                val remaining = chunkSize - 16
                                if (remaining > 0) raf.seek(raf.filePointer + remaining)
                            } else {
                                break
                            }
                        }
                        "data" -> {
                            val available = (raf.length() - raf.filePointer).coerceAtLeast(0L)
                            dataBytes = if (chunkSize > 0L) minOf(chunkSize, available) else available
                            foundData = true
                            break
                        }
                        else -> {
                            val nextPos = raf.filePointer + chunkSize + (chunkSize and 1L)
                            if (nextPos > raf.length() || nextPos <= raf.filePointer) break
                            raf.seek(nextPos)
                        }
                    }
                }

                if (!foundFmt && !foundData) return null
                val bytesPerSec = sampleRate.toLong() * channels * (bitsPerSample / 8)
                val durationMs = if (bytesPerSec > 0L) {
                    (dataBytes * 1000L) / bytesPerSec
                } else {
                    0L
                }

                WavMetadata(
                    sampleRateHz = sampleRate,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                    dataBytes = dataBytes,
                    durationMs = durationMs
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
