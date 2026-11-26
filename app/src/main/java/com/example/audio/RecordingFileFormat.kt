package com.example.audio

import java.io.Closeable
import java.io.File

enum class RecordingFileFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val estimatedSizeRatioToPcm: Double
) {
    FLAC("FLAC", "flac", "audio/flac", 0.58),
    ALAC("ALAC", "m4a", "audio/mp4", 1.0),
    WAV("WAV", "wav", "audio/wav", 1.0),
    AIFF("AIFF", "aiff", "audio/aiff", 1.0);

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("flac", "m4a", "wav", "aiff", "aif")

        fun fromFile(file: File): RecordingFileFormat? {
            return when (file.extension.lowercase()) {
                "flac" -> FLAC
                "m4a" -> ALAC
                "wav" -> WAV
                "aiff", "aif" -> AIFF
                else -> null
            }
        }
    }
}

interface AudioSegmentWriter : Closeable {
    val file: File
    val quality: AudioQuality
    val format: RecordingFileFormat
    val pcmBytesWritten: Long
    val estimatedFileBytes: Long

    fun writePcm(buffer: ByteArray, length: Int)
}

data class ParsedAudioMetadata(
    val format: RecordingFileFormat,
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val durationMs: Long
)

object AudioSegmentWriterFactory {
    fun create(
        file: File,
        quality: AudioQuality,
        format: RecordingFileFormat
    ): AudioSegmentWriter {
        return when (format) {
            RecordingFileFormat.FLAC -> FlacFileWriter(file, quality)
            RecordingFileFormat.ALAC -> AlacFileWriter(file, quality)
            RecordingFileFormat.WAV -> WavFileWriter(file, quality)
            RecordingFileFormat.AIFF -> AiffFileWriter(file, quality)
        }
    }
}

object AudioMetadataParser {
    fun parse(file: File): ParsedAudioMetadata? {
        if (!file.exists() || !file.isFile) return null
        return when (RecordingFileFormat.fromFile(file)) {
            RecordingFileFormat.WAV -> WavHeader.parse(file)?.let {
                ParsedAudioMetadata(
                    format = RecordingFileFormat.WAV,
                    sampleRateHz = it.sampleRateHz,
                    channels = it.channels,
                    bitsPerSample = it.bitsPerSample,
                    durationMs = it.durationMs
                )
            }
            RecordingFileFormat.AIFF -> AiffFormat.parse(file)
            RecordingFileFormat.FLAC -> FlacFormat.parse(file)
            RecordingFileFormat.ALAC -> AlacFormat.parse(file)
            null -> null
        }
    }
}
