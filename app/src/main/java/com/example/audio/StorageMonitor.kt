package com.example.audio

import android.os.StatFs
import com.example.data.RecordingRepository
import com.example.data.RecordingSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StorageHealth {
    HEALTHY,
    LOW,
    CRITICAL
}

data class StorageSnapshot(
    val availableBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val recordingsUsedBytes: Long = 0L,
    val recordingsCount: Int = 0
) {
    val usedByDeviceBytes: Long
        get() = (totalBytes - availableBytes).coerceAtLeast(0L)

    val otherUsedBytes: Long
        get() = (usedByDeviceBytes - recordingsUsedBytes).coerceAtLeast(0L)

    val totalUsedFraction: Float
        get() = if (totalBytes > 0L) {
            (usedByDeviceBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val recordingsFractionOfTotal: Float
        get() = if (totalBytes > 0L) {
            (recordingsUsedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val otherUsedFractionOfTotal: Float
        get() = if (totalBytes > 0L) {
            (otherUsedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
}

data class StorageUsageEstimate(
    val snapshot: StorageSnapshot,
    val audioMinutesAvailable: Long,
    val deploymentMinutesUntilFull: Long,
    val bytesPerHourActive: Long,
    val health: StorageHealth
)

object StorageEstimator {

    const val LOW_SPACE_BYTES = 1024L * 1024L * 1024L       // 1 GB
    const val CRITICAL_SPACE_BYTES = 200L * 1024L * 1024L   // 200 MB

    fun estimate(
        snapshot: StorageSnapshot,
        bytesPerSecond: Long,
        recordingDurationSeconds: Int,
        intervalSeconds: Int,
        continuousNoGap: Boolean,
        schedule: RecordingSchedule
    ): StorageUsageEstimate {
        val safeBps = bytesPerSecond.coerceAtLeast(16000L)
        val available = snapshot.availableBytes.coerceAtLeast(0L)

        val audioSecondsAvailable = available / safeBps
        val audioMinutesAvailable = (audioSecondsAvailable / 60L).coerceAtLeast(0L)

        val captureFraction = if (continuousNoGap) {
            1.0
        } else {
            val rec = recordingDurationSeconds.coerceAtLeast(1).toDouble()
            val gap = intervalSeconds.coerceAtLeast(0).toDouble()
            (rec / (rec + gap)).coerceIn(0.0, 1.0)
        }
        val scheduleFraction = BatteryPowerEstimator.scheduleActiveFraction(schedule)
        val effectiveActiveFraction = (captureFraction * scheduleFraction).coerceIn(0.005, 1.0)

        val bytesPerHourActive = safeBps * 3600L
        val avgBytesPerMinute = (safeBps.toDouble() * 60.0) * effectiveActiveFraction
        val deploymentMinutesUntilFull = if (avgBytesPerMinute > 0.0) {
            (available.toDouble() / avgBytesPerMinute).toLong().coerceAtLeast(0L)
        } else {
            0L
        }

        val health = when {
            available < CRITICAL_SPACE_BYTES || audioMinutesAvailable < 10L -> StorageHealth.CRITICAL
            available < LOW_SPACE_BYTES || audioMinutesAvailable < 60L -> StorageHealth.LOW
            else -> StorageHealth.HEALTHY
        }

        return StorageUsageEstimate(
            snapshot = snapshot,
            audioMinutesAvailable = audioMinutesAvailable,
            deploymentMinutesUntilFull = deploymentMinutesUntilFull,
            bytesPerHourActive = bytesPerHourActive,
            health = health
        )
    }
}

class StorageMonitor(
    private val recordingRepository: RecordingRepository
) {
    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot: StateFlow<StorageSnapshot> = _snapshot.asStateFlow()

    fun refresh(): StorageSnapshot {
        val fresh = readSnapshot()
        _snapshot.value = fresh
        return fresh
    }

    private fun readSnapshot(): StorageSnapshot {
        return try {
            val dir = recordingRepository.recordingsDirectory()
            val statFs = StatFs(dir.absolutePath)
            val available = statFs.availableBytes.coerceAtLeast(0L)
            val total = statFs.totalBytes.coerceAtLeast(available)
            val audioFiles = dir.listFiles { file ->
                file.isFile && file.extension.lowercase() in RecordingFileFormat.SUPPORTED_EXTENSIONS
            } ?: emptyArray()
            val recordingsBytes = audioFiles.sumOf { it.length().coerceAtLeast(0L) }
            StorageSnapshot(
                availableBytes = available,
                totalBytes = total,
                recordingsUsedBytes = recordingsBytes,
                recordingsCount = audioFiles.size
            )
        } catch (_: Exception) {
            StorageSnapshot()
        }
    }
}
