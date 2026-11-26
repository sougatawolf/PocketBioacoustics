package com.example.data

import com.example.audio.GpsCoordinate
import com.example.audio.RecordingFileFormat
import java.io.File

data class RecordingFile(
    val path: String,
    val file: File,
    val displayName: String,
    val createdAtMs: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val format: RecordingFileFormat = RecordingFileFormat.WAV,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyMeters: Float? = null
) {
    val gpsCoordinate: GpsCoordinate?
        get() = if (latitude != null && longitude != null) {
            GpsCoordinate(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = gpsAccuracyMeters
            )
        } else {
            null
        }
}
