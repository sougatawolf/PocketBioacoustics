package com.example.audio

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

class WavFileWriter(
    override val file: File,
    override val quality: AudioQuality
) : AudioSegmentWriter {

    override val format: RecordingFileFormat = RecordingFileFormat.WAV

    private var outputStream: BufferedOutputStream? = null
    var dataBytesWritten: Long = 0L
        private set
    override val pcmBytesWritten: Long
        get() = dataBytesWritten
    override val estimatedFileBytes: Long
        get() = WavHeader.HEADER_SIZE.toLong() + dataBytesWritten
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        val fos = FileOutputStream(file)
        val bos = BufferedOutputStream(fos, 65536)
        bos.write(
            WavHeader.createHeader(
                sampleRateHz = quality.sampleRateHz,
                channels = quality.channels,
                bitsPerSample = quality.bitsPerSample,
                dataBytes = 0L
            )
        )
        outputStream = bos
    }

    override fun writePcm(buffer: ByteArray, length: Int) {
        if (closed || length <= 0) return
        outputStream?.write(buffer, 0, length)
        dataBytesWritten += length.toLong()
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            outputStream?.flush()
            outputStream?.close()
        } finally {
            outputStream = null
        }

        if (dataBytesWritten > 0L && file.exists()) {
            WavHeader.updateHeaderSizes(file, dataBytesWritten)
        } else if (file.exists()) {
            file.delete()
        }
    }
}
