package com.example.audio

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.abs

object FlacFormat {
    const val STREAMINFO_OFFSET = 4
    const val HEADER_BYTES = 42 // "fLaC" (4) + block header (4) + STREAMINFO (34)
    const val BLOCK_SIZE = 4096

    fun parse(file: File): ParsedAudioMetadata? {
        if (!file.exists() || file.length() < HEADER_BYTES) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(HEADER_BYTES)
                if (raf.read(header) != HEADER_BYTES) return null
                if (header[0] != 'f'.code.toByte() ||
                    header[1] != 'L'.code.toByte() ||
                    header[2] != 'a'.code.toByte() ||
                    header[3] != 'C'.code.toByte()
                ) {
                    return null
                }
                val blockType = header[4].toInt() and 0x7F
                if (blockType != 0) return null

                // STREAMINFO starts at byte 8; packed 64-bit rate/ch/bps/samples starts at byte 18
                var packed = 0L
                for (i in 0 until 8) {
                    packed = (packed shl 8) or (header[18 + i].toLong() and 0xFFL)
                }
                val sampleRateHz = ((packed ushr 44) and 0xFFFFFL).toInt()
                val channels = (((packed ushr 41) and 0x7L).toInt()) + 1
                val bitsPerSample = (((packed ushr 36) and 0x1FL).toInt()) + 1
                val totalSamples = packed and 0xFFFFFFFFFL

                if (sampleRateHz <= 0 || channels <= 0 || bitsPerSample <= 0) return null
                val durationMs = (totalSamples * 1000L) / sampleRateHz.toLong()
                ParsedAudioMetadata(
                    format = RecordingFileFormat.FLAC,
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

    /**
     * Pure-Kotlin decoder for FLAC files written by FlacFileWriter (or any mono 16-bit
     * CONSTANT / VERBATIM / FIXED FLAC stream). Used as universal playback fallback & verification.
     */
    fun decodeToLittleEndianPcm(file: File): DecodedPcmAudio? {
        if (!file.exists() || file.length() < HEADER_BYTES) return null
        return try {
            val bytes = file.readBytes()
            if (bytes.size < HEADER_BYTES) return null
            if (bytes[0] != 'f'.code.toByte() ||
                bytes[1] != 'L'.code.toByte() ||
                bytes[2] != 'a'.code.toByte() ||
                bytes[3] != 'C'.code.toByte()
            ) {
                return null
            }

            var pos = 4
            var sampleRateHz = 0
            var channels = 0
            var bitsPerSample = 0
            var totalSamples = 0L

            while (pos + 4 <= bytes.size) {
                val blockHeader = bytes[pos].toInt() and 0xFF
                val isLast = (blockHeader and 0x80) != 0
                val blockType = blockHeader and 0x7F
                val blockLen = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 3].toInt() and 0xFF)
                pos += 4
                if (pos + blockLen > bytes.size) return null

                if (blockType == 0 && blockLen >= 34) {
                    var packed = 0L
                    for (i in 0 until 8) {
                        packed = (packed shl 8) or (bytes[pos + 10 + i].toLong() and 0xFFL)
                    }
                    sampleRateHz = ((packed ushr 44) and 0xFFFFFL).toInt()
                    channels = (((packed ushr 41) and 0x7L).toInt()) + 1
                    bitsPerSample = (((packed ushr 36) and 0x1FL).toInt()) + 1
                    totalSamples = packed and 0xFFFFFFFFFL
                }
                pos += blockLen
                if (isLast) break
            }

            if (sampleRateHz <= 0 || channels != 1 || bitsPerSample != 16) return null

            val pcmOut = ByteArrayOutputStream((totalSamples * 2L).toInt().coerceAtLeast(1024))
            val reader = BitReader(bytes, pos)

            while (reader.hasMoreBytes()) {
                val sync = reader.readBits(14)
                if (sync != 0x3FFE) break
                val reserved0 = reader.readBits(1)
                val blockingStrategy = reader.readBits(1)
                if (reserved0 != 0) break

                val blockSizeCode = reader.readBits(4)
                val sampleRateCode = reader.readBits(4)
                val channelAssign = reader.readBits(4)
                val sampleSizeCode = reader.readBits(3)
                val reserved1 = reader.readBits(1)
                if (reserved1 != 0 || channelAssign != 0) break

                // Frame/sample number in UTF-8
                reader.readUtf8Uint()
                if (blockingStrategy == 1) {
                    // variable blocksize not used by us
                }

                val blockSize = when (blockSizeCode) {
                    1 -> 192
                    in 2..5 -> 576 shl (blockSizeCode - 2)
                    6 -> reader.readBits(8) + 1
                    7 -> reader.readBits(16) + 1
                    in 8..15 -> 256 shl (blockSizeCode - 8)
                    else -> return null
                }

                when (sampleRateCode) {
                    12 -> reader.readBits(8)
                    13, 14 -> reader.readBits(16)
                }
                // CRC-8
                reader.readBits(8)

                // Subframe
                val pad = reader.readBits(1)
                if (pad != 0) return null
                val subframeType = reader.readBits(6)
                val wastedFlag = reader.readBits(1)
                if (wastedFlag != 0) return null

                val samples = IntArray(blockSize)
                when {
                    subframeType == 0 -> {
                        val c = reader.readSignedBits(16)
                        samples.fill(c)
                    }
                    subframeType == 1 -> {
                        for (i in 0 until blockSize) {
                            samples[i] = reader.readSignedBits(16)
                        }
                    }
                    subframeType in 8..12 -> {
                        val order = subframeType - 8
                        for (i in 0 until order) {
                            samples[i] = reader.readSignedBits(16)
                        }
                        val residualMethod = reader.readBits(2)
                        val paramBits = if (residualMethod == 0) 4 else 5
                        val escapeParam = if (residualMethod == 0) 15 else 31
                        val partitionOrder = reader.readBits(4)
                        val numPartitions = 1 shl partitionOrder
                        var sampleIdx = order

                        for (p in 0 until numPartitions) {
                            val partitionSamples = (blockSize ushr partitionOrder) - if (p == 0) order else 0
                            val riceParam = reader.readBits(paramBits)
                            if (riceParam == escapeParam) {
                                val rawBits = reader.readBits(5)
                                for (j in 0 until partitionSamples) {
                                    val res = reader.readSignedBits(rawBits)
                                    samples[sampleIdx] = reconstructFixed(samples, sampleIdx, order, res)
                                    sampleIdx++
                                }
                            } else {
                                for (j in 0 until partitionSamples) {
                                    var q = 0
                                    while (reader.readBits(1) == 0) {
                                        q++
                                    }
                                    val r = if (riceParam > 0) reader.readBits(riceParam) else 0
                                    val unsignedVal = (q shl riceParam) or r
                                    val res = if ((unsignedVal and 1) == 0) {
                                        unsignedVal ushr 1
                                    } else {
                                        -((unsignedVal ushr 1) + 1)
                                    }
                                    samples[sampleIdx] = reconstructFixed(samples, sampleIdx, order, res)
                                    sampleIdx++
                                }
                            }
                        }
                    }
                    else -> return null
                }

                reader.alignToByte()
                // CRC-16
                reader.readBits(16)

                for (s in samples) {
                    pcmOut.write(s and 0xFF)
                    pcmOut.write((s ushr 8) and 0xFF)
                }
            }

            DecodedPcmAudio(
                sampleRateHz = sampleRateHz,
                channels = channels,
                bitsPerSample = bitsPerSample,
                pcmLittleEndian = pcmOut.toByteArray()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun reconstructFixed(samples: IntArray, idx: Int, order: Int, residual: Int): Int {
        val pred = when (order) {
            0 -> 0
            1 -> samples[idx - 1]
            2 -> 2 * samples[idx - 1] - samples[idx - 2]
            3 -> 3 * samples[idx - 1] - 3 * samples[idx - 2] + samples[idx - 3]
            4 -> 4 * samples[idx - 1] - 6 * samples[idx - 2] + 4 * samples[idx - 3] - samples[idx - 4]
            else -> 0
        }
        return pred + residual
    }

    private class BitReader(private val data: ByteArray, startByte: Int) {
        private var bytePos = startByte
        private var bitOffset = 0

        fun hasMoreBytes(): Boolean = bytePos < data.size

        fun alignToByte() {
            if (bitOffset != 0) {
                bitOffset = 0
                bytePos++
            }
        }

        fun readBits(count: Int): Int {
            var result = 0
            for (i in 0 until count) {
                if (bytePos >= data.size) throw IllegalStateException("Unexpected EOF")
                val bit = ((data[bytePos].toInt() and 0xFF) ushr (7 - bitOffset)) and 1
                result = (result shl 1) or bit
                bitOffset++
                if (bitOffset == 8) {
                    bitOffset = 0
                    bytePos++
                }
            }
            return result
        }

        fun readSignedBits(count: Int): Int {
            val raw = readBits(count)
            val signBit = 1 shl (count - 1)
            return if ((raw and signBit) != 0) raw - (1 shl count) else raw
        }

        fun readUtf8Uint(): Long {
            val first = readBits(8)
            if ((first and 0x80) == 0) return first.toLong()
            var mask = 0x40
            var extraBytes = 0
            while ((first and mask) != 0 && extraBytes < 6) {
                extraBytes++
                mask = mask ushr 1
            }
            var value = (first and (mask - 1)).toLong()
            for (i in 0 until extraBytes) {
                val cont = readBits(8)
                value = (value shl 6) or (cont and 0x3F).toLong()
            }
            return value
        }
    }

    internal fun sampleRateCodeFor(sampleRateHz: Int): Int {
        return when (sampleRateHz) {
            8000 -> 4
            16000 -> 5
            22050 -> 6
            24000 -> 7
            32000 -> 8
            44100 -> 9
            48000 -> 10
            96000 -> 11
            else -> 0 // Defined in STREAMINFO
        }
    }
}

class FlacFileWriter(
    override val file: File,
    override val quality: AudioQuality
) : AudioSegmentWriter {

    override val format: RecordingFileFormat = RecordingFileFormat.FLAC

    private var outputStream: BufferedOutputStream? = null
    override var pcmBytesWritten: Long = 0L
        private set
    private var bytesWrittenToFile: Long = 0L
    override val estimatedFileBytes: Long
        get() = bytesWrittenToFile

    private val md5 = MessageDigest.getInstance("MD5")
    private val sampleBuffer = IntArray(FlacFormat.BLOCK_SIZE)
    private var bufferedSamples = 0
    private var totalSamplesWritten = 0L
    private var frameNumber = 0L
    private var closed = false

    private val frameBuffer = ByteArrayOutputStream(FlacFormat.BLOCK_SIZE * 2 + 64)
    private val residualBuffer = IntArray(FlacFormat.BLOCK_SIZE)

    init {
        file.parentFile?.mkdirs()
        val fos = FileOutputStream(file)
        val bos = BufferedOutputStream(fos, 65536)

        // Write "fLaC" + STREAMINFO metadata block placeholder (patched on close)
        val header = ByteArray(FlacFormat.HEADER_BYTES)
        header[0] = 'f'.code.toByte()
        header[1] = 'L'.code.toByte()
        header[2] = 'a'.code.toByte()
        header[3] = 'C'.code.toByte()
        header[4] = 0x80.toByte() // last metadata block = true, type = 0 (STREAMINFO)
        header[5] = 0x00
        header[6] = 0x00
        header[7] = 34 // STREAMINFO payload length = 34 bytes
        // minBlockSize = 4096, maxBlockSize = 4096
        header[8] = ((FlacFormat.BLOCK_SIZE ushr 8) and 0xFF).toByte()
        header[9] = (FlacFormat.BLOCK_SIZE and 0xFF).toByte()
        header[10] = ((FlacFormat.BLOCK_SIZE ushr 8) and 0xFF).toByte()
        header[11] = (FlacFormat.BLOCK_SIZE and 0xFF).toByte()
        writeStreamInfoPacked(header, totalSamples = 0L)

        bos.write(header)
        bytesWrittenToFile = FlacFormat.HEADER_BYTES.toLong()
        outputStream = bos
    }

    private fun writeStreamInfoPacked(header: ByteArray, totalSamples: Long) {
        val packed = ((quality.sampleRateHz.toLong() and 0xFFFFFL) shl 44) or
            (((quality.channels - 1).toLong() and 0x7L) shl 41) or
            (((quality.bitsPerSample - 1).toLong() and 0x1FL) shl 36) or
            (totalSamples and 0xFFFFFFFFFL)
        for (i in 0 until 8) {
            header[18 + i] = ((packed ushr ((7 - i) * 8)) and 0xFFL).toByte()
        }
    }

    override fun writePcm(buffer: ByteArray, length: Int) {
        if (closed || length <= 1) return
        val alignedLen = length and 1.inv()
        if (alignedLen <= 0) return

        md5.update(buffer, 0, alignedLen)
        pcmBytesWritten += alignedLen.toLong()

        var i = 0
        while (i < alignedLen) {
            val lo = buffer[i].toInt() and 0xFF
            val hi = buffer[i + 1].toInt()
            sampleBuffer[bufferedSamples++] = (hi shl 8) or lo
            if (bufferedSamples == FlacFormat.BLOCK_SIZE) {
                encodeAndWriteFrame(bufferedSamples)
                bufferedSamples = 0
            }
            i += 2
        }
    }

    private fun encodeAndWriteFrame(blockSamples: Int) {
        val out = outputStream ?: return
        frameBuffer.reset()

        // Frame header
        frameBuffer.write(0xFF)
        frameBuffer.write(0xF8) // Fixed blocksize sync

        val blockSizeCode = if (blockSamples == FlacFormat.BLOCK_SIZE) 12 else 7
        val sampleRateCode = FlacFormat.sampleRateCodeFor(quality.sampleRateHz)
        frameBuffer.write((blockSizeCode shl 4) or sampleRateCode)
        frameBuffer.write(0x08) // Mono (0) + 16-bit (4 shl 1) + reserved 0

        writeUtf8Uint(frameBuffer, frameNumber)
        if (blockSizeCode == 7) {
            val bsMinusOne = blockSamples - 1
            frameBuffer.write((bsMinusOne ushr 8) and 0xFF)
            frameBuffer.write(bsMinusOne and 0xFF)
        }

        val headerBytes = frameBuffer.toByteArray()
        val crc8 = computeCrc8(headerBytes, headerBytes.size)
        frameBuffer.write(crc8)

        // Subframe selection: Constant -> Fixed(order 1) Rice -> Verbatim
        var allEqual = true
        val firstSample = sampleBuffer[0]
        for (i in 1 until blockSamples) {
            if (sampleBuffer[i] != firstSample) {
                allEqual = false
                break
            }
        }

        if (allEqual) {
            // SUBFRAME_CONSTANT
            frameBuffer.write(0x00)
            frameBuffer.write((firstSample ushr 8) and 0xFF)
            frameBuffer.write(firstSample and 0xFF)
        } else {
            val bestRiceK = evaluateFixedOrder1Rice(blockSamples)
            if (bestRiceK >= 0) {
                writeFixedOrder1Subframe(blockSamples, bestRiceK)
            } else {
                // SUBFRAME_VERBATIM
                frameBuffer.write(0x02)
                for (i in 0 until blockSamples) {
                    val s = sampleBuffer[i]
                    frameBuffer.write((s ushr 8) and 0xFF)
                    frameBuffer.write(s and 0xFF)
                }
            }
        }

        val frameBody = frameBuffer.toByteArray()
        val crc16 = computeCrc16(frameBody, frameBody.size)
        out.write(frameBody)
        out.write((crc16 ushr 8) and 0xFF)
        out.write(crc16 and 0xFF)

        bytesWrittenToFile += (frameBody.size + 2).toLong()
        totalSamplesWritten += blockSamples.toLong()
        frameNumber++
    }

    private fun evaluateFixedOrder1Rice(blockSamples: Int): Int {
        if (blockSamples < 4) return -1
        var sumAbs = 0L
        var maxAbs = 0
        for (i in 1 until blockSamples) {
            val res = sampleBuffer[i] - sampleBuffer[i - 1]
            residualBuffer[i] = res
            val a = abs(res)
            sumAbs += a.toLong()
            if (a > maxAbs) maxAbs = a
        }
        val meanAbs = (sumAbs.toDouble() / (blockSamples - 1).toDouble()).coerceAtLeast(1.0)
        var k = 0
        var thresh = 1.0
        while (k < 14 && thresh * 2.0 <= meanAbs) {
            thresh *= 2.0
            k++
        }
        // Check worst-case Rice quotient isn't excessive
        val maxUnsigned = maxAbs.toLong() * 2L
        if ((maxUnsigned ushr k) > 1024L) return -1

        var totalBits = 24L + 6L // subframe header + warm-up + rice header
        for (i in 1 until blockSamples) {
            val r = residualBuffer[i]
            val u = if (r >= 0) r shl 1 else ((-r) shl 1) - 1
            totalBits += (u ushr k).toLong() + 1L + k.toLong()
        }
        val verbatimBits = 8L + blockSamples.toLong() * 16L
        return if (totalBits < verbatimBits) k else -1
    }

    private fun writeFixedOrder1Subframe(blockSamples: Int, riceParam: Int) {
        // Subframe header: FIXED order 1 = (001001 << 1) = 0x12
        frameBuffer.write(0x12)
        // Warm-up sample 0 (16-bit BE)
        val s0 = sampleBuffer[0]
        frameBuffer.write((s0 ushr 8) and 0xFF)
        frameBuffer.write(s0 and 0xFF)

        var bitAcc = 0
        var bitCount = 0

        fun writeBits(value: Int, bits: Int) {
            for (b in bits - 1 downTo 0) {
                bitAcc = (bitAcc shl 1) or ((value ushr b) and 1)
                bitCount++
                if (bitCount == 8) {
                    frameBuffer.write(bitAcc)
                    bitAcc = 0
                    bitCount = 0
                }
            }
        }

        // Residual coding method 0 (4-bit Rice param), partition order 0 (1 partition)
        writeBits(0, 2)
        writeBits(0, 4)
        writeBits(riceParam, 4)

        val mask = (1 shl riceParam) - 1
        for (i in 1 until blockSamples) {
            val r = residualBuffer[i]
            val unsignedVal = if (r >= 0) r shl 1 else ((-r) shl 1) - 1
            val q = unsignedVal ushr riceParam
            for (z in 0 until q) {
                writeBits(0, 1)
            }
            writeBits(1, 1)
            if (riceParam > 0) {
                writeBits(unsignedVal and mask, riceParam)
            }
        }

        if (bitCount > 0) {
            bitAcc = bitAcc shl (8 - bitCount)
            frameBuffer.write(bitAcc)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            if (bufferedSamples > 0) {
                encodeAndWriteFrame(bufferedSamples)
                bufferedSamples = 0
            }
            outputStream?.flush()
            outputStream?.close()
        } finally {
            outputStream = null
        }

        if (pcmBytesWritten > 0L && file.exists()) {
            val digest = md5.digest()
            val headerPatch = ByteArray(FlacFormat.HEADER_BYTES)
            RandomAccessFile(file, "rw").use { raf ->
                if (raf.length() >= FlacFormat.HEADER_BYTES) {
                    raf.seek(0)
                    raf.readFully(headerPatch)
                    writeStreamInfoPacked(headerPatch, totalSamplesWritten)
                    System.arraycopy(digest, 0, headerPatch, 26, 16)
                    raf.seek(18)
                    raf.write(headerPatch, 18, 24)
                }
            }
        } else if (file.exists()) {
            file.delete()
        }
    }

    private fun writeUtf8Uint(out: ByteArrayOutputStream, value: Long) {
        when {
            value < 0x80L -> out.write(value.toInt())
            value < 0x800L -> {
                out.write((0xC0L or (value ushr 6)).toInt())
                out.write((0x80L or (value and 0x3FL)).toInt())
            }
            value < 0x10000L -> {
                out.write((0xE0L or (value ushr 12)).toInt())
                out.write((0x80L or ((value ushr 6) and 0x3FL)).toInt())
                out.write((0x80L or (value and 0x3FL)).toInt())
            }
            value < 0x200000L -> {
                out.write((0xF0L or (value ushr 18)).toInt())
                out.write((0x80L or ((value ushr 12) and 0x3FL)).toInt())
                out.write((0x80L or ((value ushr 6) and 0x3FL)).toInt())
                out.write((0x80L or (value and 0x3FL)).toInt())
            }
            else -> {
                out.write((0xF8L or (value ushr 24)).toInt())
                out.write((0x80L or ((value ushr 18) and 0x3FL)).toInt())
                out.write((0x80L or ((value ushr 12) and 0x3FL)).toInt())
                out.write((0x80L or ((value ushr 6) and 0x3FL)).toInt())
                out.write((0x80L or (value and 0x3FL)).toInt())
            }
        }
    }

    private fun computeCrc8(data: ByteArray, length: Int): Int {
        var crc = 0
        for (i in 0 until length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (b in 0 until 8) {
                crc = if ((crc and 0x80) != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
            }
        }
        return crc
    }

    private fun computeCrc16(data: ByteArray, length: Int): Int {
        var crc = 0
        for (i in 0 until length) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            for (b in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x8005) and 0xFFFF else (crc shl 1) and 0xFFFF
            }
        }
        return crc
    }
}
