package com.example.audio

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

object AlacFormat {
    const val FRAME_SAMPLES = 4096

    fun parse(file: File): ParsedAudioMetadata? {
        if (!file.exists() || file.length() < 64) return null
        return try {
            val bytes = file.readBytes()
            val moovRange = findTopLevelBoxRange(bytes, "moov") ?: (0 to bytes.size)
            val moovBytes = bytes.copyOfRange(moovRange.first, moovRange.second)
            val alacInfo = findAlacConfig(moovBytes) ?: return null
            val totalSamples = findMdhdDurationSamples(moovBytes) ?: return null
            if (alacInfo.sampleRateHz <= 0 || alacInfo.channels <= 0 || alacInfo.bitsPerSample <= 0) {
                return null
            }
            val durationMs = (totalSamples * 1000L) / alacInfo.sampleRateHz.toLong()
            ParsedAudioMetadata(
                format = RecordingFileFormat.ALAC,
                sampleRateHz = alacInfo.sampleRateHz,
                channels = alacInfo.channels,
                bitsPerSample = alacInfo.bitsPerSample,
                durationMs = durationMs
            )
        } catch (_: Exception) {
            null
        }
    }

    fun decodeToLittleEndianPcm(file: File): DecodedPcmAudio? {
        if (!file.exists() || file.length() < 64) return null
        return try {
            val bytes = file.readBytes()
            val moovRange = findTopLevelBoxRange(bytes, "moov") ?: (0 to bytes.size)
            val moovBytes = bytes.copyOfRange(moovRange.first, moovRange.second)
            val config = findAlacConfig(moovBytes) ?: return null
            val stszSizes = findStszSizes(moovBytes) ?: return null
            val stcoOffsets = findStcoOffsets(moovBytes) ?: return null
            if (stszSizes.size != stcoOffsets.size || stszSizes.isEmpty()) return null
            if (config.channels != 1 || config.bitsPerSample != 16) return null

            val totalSamplesEstimate = stszSizes.size * config.frameLength
            val out = ByteArrayOutputStream(totalSamplesEstimate * 2)

            for (idx in stszSizes.indices) {
                val offset = stcoOffsets[idx].toInt()
                val size = stszSizes[idx]
                if (offset < 0 || offset + size > bytes.size) return null

                val reader = BitReader(bytes, offset, offset + size)
                val elemTag = reader.readBits(3)
                if (elemTag != 0) return null // Expect ID_SCE
                reader.readBits(4) // instance tag
                reader.readBits(12) // unused
                val hasSize = reader.readBits(1)
                val bytesShifted = reader.readBits(2)
                val isUncompressed = reader.readBits(1)
                if (isUncompressed != 1 || bytesShifted != 0) return null

                val sampleCount = if (hasSize == 1) {
                    reader.readBits(32)
                } else {
                    config.frameLength
                }
                for (s in 0 until sampleCount) {
                    val sample = reader.readBits(16)
                    // Little-endian signed 16-bit PCM
                    out.write(sample and 0xFF)
                    out.write((sample ushr 8) and 0xFF)
                }
            }

            DecodedPcmAudio(
                sampleRateHz = config.sampleRateHz,
                channels = config.channels,
                bitsPerSample = config.bitsPerSample,
                pcmLittleEndian = out.toByteArray()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun findTopLevelBoxRange(bytes: ByteArray, type: String): Pair<Int, Int>? {
        var pos = 0
        while (pos + 8 <= bytes.size) {
            val size = readU32BE(bytes, pos).toInt()
            if (size < 8 || pos + size > bytes.size) break
            val boxType = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            if (boxType == type) {
                return pos to (pos + size)
            }
            pos += size
        }
        return null
    }

    private data class AlacConfig(
        val frameLength: Int,
        val bitsPerSample: Int,
        val channels: Int,
        val sampleRateHz: Int
    )

    private fun findAlacConfig(bytes: ByteArray): AlacConfig? {
        // Find magic cookie inside stsd -> alac -> alac (36-byte specific config)
        var i = 0
        while (i + 36 <= bytes.size) {
            if (bytes[i + 4] == 'a'.code.toByte() &&
                bytes[i + 5] == 'l'.code.toByte() &&
                bytes[i + 6] == 'a'.code.toByte() &&
                bytes[i + 7] == 'c'.code.toByte()
            ) {
                val boxSize = readU32BE(bytes, i).toInt()
                if (boxSize == 36 && i + 36 <= bytes.size) {
                    val frameLength = readU32BE(bytes, i + 12).toInt()
                    val bitDepth = bytes[i + 17].toInt() and 0xFF
                    val numChannels = bytes[i + 21].toInt() and 0xFF
                    val sampleRate = readU32BE(bytes, i + 32).toInt()
                    if (frameLength > 0 && sampleRate > 0) {
                        return AlacConfig(frameLength, bitDepth, numChannels, sampleRate)
                    }
                }
            }
            i++
        }
        return null
    }

    private fun findMdhdDurationSamples(bytes: ByteArray): Long? {
        val idx = findBoxPayload(bytes, "mdhd") ?: return null
        if (idx + 20 > bytes.size) return null
        val version = bytes[idx].toInt() and 0xFF
        return if (version == 0) {
            readU32BE(bytes, idx + 16)
        } else {
            null
        }
    }

    private fun findStszSizes(bytes: ByteArray): IntArray? {
        val idx = findBoxPayload(bytes, "stsz") ?: return null
        if (idx + 12 > bytes.size) return null
        val sampleSize = readU32BE(bytes, idx + 4).toInt()
        val count = readU32BE(bytes, idx + 8).toInt()
        if (count < 0 || idx + 12 + count * 4 > bytes.size) return null
        val sizes = IntArray(count)
        if (sampleSize != 0) {
            sizes.fill(sampleSize)
        } else {
            for (k in 0 until count) {
                sizes[k] = readU32BE(bytes, idx + 12 + k * 4).toInt()
            }
        }
        return sizes
    }

    private fun findStcoOffsets(bytes: ByteArray): LongArray? {
        val idx = findBoxPayload(bytes, "stco") ?: return null
        if (idx + 8 > bytes.size) return null
        val count = readU32BE(bytes, idx + 4).toInt()
        if (count < 0 || idx + 8 + count * 4 > bytes.size) return null
        val offsets = LongArray(count)
        for (k in 0 until count) {
            offsets[k] = readU32BE(bytes, idx + 8 + k * 4)
        }
        return offsets
    }

    private fun findBoxPayload(bytes: ByteArray, type: String): Int? {
        val target0 = type[0].code.toByte()
        val target1 = type[1].code.toByte()
        val target2 = type[2].code.toByte()
        val target3 = type[3].code.toByte()
        var i = 0
        while (i + 8 <= bytes.size) {
            if (bytes[i + 4] == target0 &&
                bytes[i + 5] == target1 &&
                bytes[i + 6] == target2 &&
                bytes[i + 7] == target3
            ) {
                val size = readU32BE(bytes, i)
                if (size >= 8 && i + size <= bytes.size) {
                    return i + 8
                }
            }
            i++
        }
        return null
    }

    private fun readU32BE(b: ByteArray, offset: Int): Long {
        return ((b[offset].toLong() and 0xFFL) shl 24) or
            ((b[offset + 1].toLong() and 0xFFL) shl 16) or
            ((b[offset + 2].toLong() and 0xFFL) shl 8) or
            (b[offset + 3].toLong() and 0xFFL)
    }

    private class BitReader(
        private val data: ByteArray,
        startByte: Int,
        private val endByte: Int
    ) {
        private var bytePos = startByte
        private var bitOffset = 0

        fun readBits(count: Int): Int {
            var result = 0
            for (i in 0 until count) {
                if (bytePos >= endByte) throw IllegalStateException("Unexpected packet end")
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
    }
}

class AlacFileWriter(
    override val file: File,
    override val quality: AudioQuality
) : AudioSegmentWriter {

    override val format: RecordingFileFormat = RecordingFileFormat.ALAC

    private var outputStream: BufferedOutputStream? = null
    override var pcmBytesWritten: Long = 0L
        private set
    private var fileBytesWritten: Long = 0L
    override val estimatedFileBytes: Long
        get() = fileBytesWritten

    private var mdatOffset = 0L
    private val sampleBuffer = ShortArray(AlacFormat.FRAME_SAMPLES)
    private var bufferedSamples = 0
    private var totalSamplesWritten = 0L
    private var maxPacketBytes = 0

    private val packetOffsets = ArrayList<Int>(1024)
    private val packetSizes = ArrayList<Int>(1024)
    private val packetSampleCounts = ArrayList<Int>(1024)
    private val packetScratch = ByteArrayOutputStream(AlacFormat.FRAME_SAMPLES * 2 + 16)
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        val fos = FileOutputStream(file)
        val bos = BufferedOutputStream(fos, 65536)

        // ftyp box (28 bytes)
        val ftyp = ByteArrayOutputStream(28)
        writeU32BE(ftyp, 28)
        writeAscii(ftyp, "ftyp")
        writeAscii(ftyp, "M4A ")
        writeU32BE(ftyp, 0)
        writeAscii(ftyp, "M4A ")
        writeAscii(ftyp, "mp42")
        writeAscii(ftyp, "isom")
        val ftypBytes = ftyp.toByteArray()
        bos.write(ftypBytes)

        mdatOffset = ftypBytes.size.toLong()
        // mdat header placeholder (8 bytes)
        val mdatHeader = ByteArrayOutputStream(8)
        writeU32BE(mdatHeader, 8)
        writeAscii(mdatHeader, "mdat")
        bos.write(mdatHeader.toByteArray())

        fileBytesWritten = mdatOffset + 8L
        outputStream = bos
    }

    override fun writePcm(buffer: ByteArray, length: Int) {
        if (closed || length <= 1) return
        val alignedLen = length and 1.inv()
        if (alignedLen <= 0) return

        pcmBytesWritten += alignedLen.toLong()
        var i = 0
        while (i < alignedLen) {
            val lo = buffer[i].toInt() and 0xFF
            val hi = buffer[i + 1].toInt()
            sampleBuffer[bufferedSamples++] = ((hi shl 8) or lo).toShort()
            if (bufferedSamples == AlacFormat.FRAME_SAMPLES) {
                encodeAndWritePacket(bufferedSamples)
                bufferedSamples = 0
            }
            i += 2
        }
    }

    private fun encodeAndWritePacket(sampleCount: Int) {
        val out = outputStream ?: return
        packetScratch.reset()

        var bitAcc = 0
        var bitCount = 0

        fun writeBits(value: Int, bits: Int) {
            for (b in bits - 1 downTo 0) {
                bitAcc = (bitAcc shl 1) or ((value ushr b) and 1)
                bitCount++
                if (bitCount == 8) {
                    packetScratch.write(bitAcc)
                    bitAcc = 0
                    bitCount = 0
                }
            }
        }

        // Apple Lossless uncompressed frame payload (bit-exact lossless)
        writeBits(0, 3) // ID_SCE (mono)
        writeBits(0, 4) // elementInstanceTag
        writeBits(0, 12) // unused
        val hasSize = if (sampleCount == AlacFormat.FRAME_SAMPLES) 0 else 1
        writeBits(hasSize, 1)
        writeBits(0, 2) // bytesShifted
        writeBits(1, 1) // isUncompressed
        if (hasSize == 1) {
            writeBits(sampleCount, 32)
        }
        for (i in 0 until sampleCount) {
            writeBits(sampleBuffer[i].toInt() and 0xFFFF, 16)
        }
        writeBits(7, 3) // ID_END
        if (bitCount > 0) {
            bitAcc = bitAcc shl (8 - bitCount)
            packetScratch.write(bitAcc)
        }

        val packet = packetScratch.toByteArray()
        packetOffsets.add(fileBytesWritten.toInt())
        packetSizes.add(packet.size)
        packetSampleCounts.add(sampleCount)
        if (packet.size > maxPacketBytes) maxPacketBytes = packet.size

        out.write(packet)
        fileBytesWritten += packet.size.toLong()
        totalSamplesWritten += sampleCount.toLong()
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            if (bufferedSamples > 0) {
                encodeAndWritePacket(bufferedSamples)
                bufferedSamples = 0
            }
            if (pcmBytesWritten > 0L) {
                val moov = buildMoovBox()
                outputStream?.write(moov)
            }
            outputStream?.flush()
            outputStream?.close()
        } finally {
            outputStream = null
        }

        if (pcmBytesWritten > 0L && file.exists()) {
            val mdatSize = (fileBytesWritten - mdatOffset).toInt()
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(mdatOffset)
                raf.writeInt(mdatSize)
            }
        } else if (file.exists()) {
            file.delete()
        }
    }

    private fun buildMoovBox(): ByteArray {
        val mvhd = box("mvhd") { out ->
            writeU32BE(out, 0) // version + flags
            writeU32BE(out, 0) // creation
            writeU32BE(out, 0) // modification
            writeU32BE(out, quality.sampleRateHz) // timescale
            writeU32BE(out, totalSamplesWritten.toInt()) // duration
            writeU32BE(out, 0x00010000) // rate 1.0
            writeU16BE(out, 0x0100) // volume 1.0
            out.write(ByteArray(10)) // reserved
            writeUnityMatrix(out)
            out.write(ByteArray(24)) // pre_defined
            writeU32BE(out, 2) // next_track_ID
        }

        val tkhd = box("tkhd") { out ->
            writeU32BE(out, 0x00000007) // enabled | in_movie | in_preview
            writeU32BE(out, 0)
            writeU32BE(out, 0)
            writeU32BE(out, 1) // track ID
            writeU32BE(out, 0)
            writeU32BE(out, totalSamplesWritten.toInt())
            out.write(ByteArray(8))
            writeU16BE(out, 0)
            writeU16BE(out, 0)
            writeU16BE(out, 0x0100) // volume 1.0
            writeU16BE(out, 0)
            writeUnityMatrix(out)
            writeU32BE(out, 0)
            writeU32BE(out, 0)
        }

        val mdhd = box("mdhd") { out ->
            writeU32BE(out, 0)
            writeU32BE(out, 0)
            writeU32BE(out, 0)
            writeU32BE(out, quality.sampleRateHz)
            writeU32BE(out, totalSamplesWritten.toInt())
            writeU16BE(out, 0x55C4) // "und"
            writeU16BE(out, 0)
        }

        val hdlr = box("hdlr") { out ->
            writeU32BE(out, 0)
            writeU32BE(out, 0)
            writeAscii(out, "soun")
            out.write(ByteArray(12))
            writeAscii(out, "SoundHandler")
            out.write(0)
        }

        val smhd = box("smhd") { out ->
            writeU32BE(out, 0)
            writeU16BE(out, 0)
            writeU16BE(out, 0)
        }

        val url = box("url ") { out ->
            writeU32BE(out, 1) // self-contained flag
        }

        val dref = box("dref") { out ->
            writeU32BE(out, 0)
            writeU32BE(out, 1)
            out.write(url)
        }

        val dinf = box("dinf") { out ->
            out.write(dref)
        }

        val alacMagicBox = box("alac") { out ->
            writeU32BE(out, 0) // version + flags
            writeU32BE(out, AlacFormat.FRAME_SAMPLES)
            out.write(0) // compatibleVersion
            out.write(quality.bitsPerSample)
            out.write(40) // pb
            out.write(10) // mb
            out.write(14) // kb
            out.write(quality.channels)
            writeU16BE(out, 255) // maxRun
            writeU32BE(out, maxPacketBytes)
            writeU32BE(out, quality.sampleRateHz * quality.bitsPerSample)
            writeU32BE(out, quality.sampleRateHz)
        }

        val alacEntry = box("alac") { out ->
            out.write(ByteArray(6)) // reserved
            writeU16BE(out, 1) // data_reference_index
            writeU16BE(out, 0) // version
            writeU16BE(out, 0) // revision
            writeU32BE(out, 0) // vendor
            writeU16BE(out, quality.channels)
            writeU16BE(out, quality.bitsPerSample)
            writeU16BE(out, 0) // compression_id
            writeU16BE(out, 0) // packet_size
            val legacyRate = if (quality.sampleRateHz <= 65535) quality.sampleRateHz else 44100
            writeU32BE(out, legacyRate shl 16)
            out.write(alacMagicBox)
        }

        val stsd = box("stsd") { out ->
            writeU32BE(out, 0)
            writeU32BE(out, 1)
            out.write(alacEntry)
        }

        // Run-length encode sample durations into stts entries
        val sttsEntries = ArrayList<Pair<Int, Int>>(2)
        for (sc in packetSampleCounts) {
            val last = sttsEntries.lastOrNull()
            if (last != null && last.second == sc) {
                sttsEntries[sttsEntries.lastIndex] = (last.first + 1) to sc
            } else {
                sttsEntries.add(1 to sc)
            }
        }
        val stts = box("stts") { out ->
            writeU32BE(out, 0)
            writeU32BE(out, sttsEntries.size)
            for ((count, delta) in sttsEntries) {
                writeU32BE(out, count)
                writeU32BE(out, delta)
            }
        }

        val stsc = box("stsc") { out ->
            writeU32BE(out, 0)
            writeU32BE(out, 1)
            writeU32BE(out, 1) // first_chunk
            writeU32BE(out, 1) // samples_per_chunk
            writeU32BE(out, 1) // sample_description_index
        }

        val stsz = box("stsz") { out ->
            writeU32BE(out, 0)
            writeU32BE(out, 0) // variable sizes
            writeU32BE(out, packetSizes.size)
            for (s in packetSizes) {
                writeU32BE(out, s)
            }
        }

        val stco = box("stco") { out ->
            writeU32BE(out, 0)
            writeU32BE(out, packetOffsets.size)
            for (off in packetOffsets) {
                writeU32BE(out, off)
            }
        }

        val stbl = box("stbl") { out ->
            out.write(stsd)
            out.write(stts)
            out.write(stsc)
            out.write(stsz)
            out.write(stco)
        }

        val minf = box("minf") { out ->
            out.write(smhd)
            out.write(dinf)
            out.write(stbl)
        }

        val mdia = box("mdia") { out ->
            out.write(mdhd)
            out.write(hdlr)
            out.write(minf)
        }

        val trak = box("trak") { out ->
            out.write(tkhd)
            out.write(mdia)
        }

        return box("moov") { out ->
            out.write(mvhd)
            out.write(trak)
        }
    }

    private fun box(type: String, block: (ByteArrayOutputStream) -> Unit): ByteArray {
        val payload = ByteArrayOutputStream()
        block(payload)
        val payloadBytes = payload.toByteArray()
        val totalSize = 8 + payloadBytes.size
        val result = ByteArrayOutputStream(totalSize)
        writeU32BE(result, totalSize)
        writeAscii(result, type)
        result.write(payloadBytes)
        return result.toByteArray()
    }

    private fun writeUnityMatrix(out: ByteArrayOutputStream) {
        writeU32BE(out, 0x00010000); writeU32BE(out, 0); writeU32BE(out, 0)
        writeU32BE(out, 0); writeU32BE(out, 0x00010000); writeU32BE(out, 0)
        writeU32BE(out, 0); writeU32BE(out, 0); writeU32BE(out, 0x40000000)
    }

    private fun writeU32BE(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeU16BE(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeAscii(out: ByteArrayOutputStream, s: String) {
        for (c in s) out.write(c.code and 0xFF)
    }
}
