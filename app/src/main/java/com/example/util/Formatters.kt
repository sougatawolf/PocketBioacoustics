package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Formatters {

    fun formatDurationHms(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** Formats 30s..3600s recording duration concisely (e.g. "30s", "5m", "1h"). */
    fun formatDurationShort(seconds: Int): String {
        val clamped = seconds.coerceIn(30, 86400)
        val hours = clamped / 3600
        val minutes = (clamped % 3600) / 60
        val secs = clamped % 60
        return when {
            hours > 0 && minutes == 0 && secs == 0 -> "${hours}h"
            hours > 0 && secs == 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m ${secs}s"
            minutes == 0 -> "${secs}s"
            secs == 0 -> "${minutes}m"
            else -> "${minutes}m ${secs}s"
        }
    }

    /** Formats 30s..3600s recording duration verbosely (e.g. "30 sec", "5 min", "60 min"). */
    fun formatRecordingDurationVerbose(seconds: Int): String {
        val clamped = seconds.coerceIn(30, 3600)
        val minutes = clamped / 60
        val secs = clamped % 60
        return when {
            clamped == 3600 -> "60 min"
            minutes == 0 -> "$secs sec"
            secs == 0 -> "$minutes min"
            else -> "$minutes min $secs sec"
        }
    }

    /** Formats 5m..24h interval verbosely (e.g. "5 min", "1 hr 30 min", "24 hr"). */
    fun formatIntervalVerbose(seconds: Int): String {
        val clamped = seconds.coerceIn(300, 86400)
        val hours = clamped / 3600
        val minutes = (clamped % 3600) / 60
        return when {
            hours == 0 -> "$minutes min"
            minutes == 0 -> "$hours hr"
            else -> "$hours hr $minutes min"
        }
    }

    fun formatEstimatedRuntime(totalMinutes: Long): String {
        val clamped = totalMinutes.coerceAtLeast(0L)
        if (clamped == 0L) return "0m"
        val days = clamped / (24L * 60L)
        val hours = (clamped % (24L * 60L)) / 60L
        val mins = clamped % 60L
        return when {
            days > 0L && hours > 0L -> "${days}d ${hours}h"
            days > 0L -> "${days}d"
            hours > 0L && mins > 0L -> "${hours}h ${mins}m"
            hours > 0L -> "${hours}h"
            else -> "${mins}m"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatDateTime(timestampMs: Long): String {
        val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }

    fun formatClockTimeHms(timestampMs: Long): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }

    fun formatDateFull(timestampMs: Long): String {
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }

    fun formatDateTimeWithSeconds(timestampMs: Long): String {
        val formatter = SimpleDateFormat("MMM d, yyyy • HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }

    /**
     * Resolves the wall-clock start time of a recorded file. Prefers parsing the
     * `yyyyMMdd_HHmmss` prefix from the generated filename (exact start time); falls
     * back to `createdAtMs - durationMs` for user-renamed files.
     */
    fun resolveRecordingStartMs(
        displayName: String,
        createdAtMs: Long,
        durationMs: Long
    ): Long {
        val match = Regex("^(\\d{8}_\\d{6})").find(displayName.trim())
        if (match != null) {
            try {
                val parser = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
                    isLenient = false
                }
                val parsed = parser.parse(match.groupValues[1])
                if (parsed != null) {
                    return parsed.time
                }
            } catch (_: Exception) {
            }
        }
        return (createdAtMs - durationMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    /**
     * Piecewise slider mapping [0f..1f] <-> [30..3600] seconds for WAV Recording Duration (30s..60m).
     */
    fun durationSliderFractionToSeconds(fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val raw = when {
            f <= 0.25f -> {
                val t = f / 0.25f
                30f + t * 90f
            }
            f <= 0.55f -> {
                val t = (f - 0.25f) / 0.30f
                120f + t * 480f
            }
            else -> {
                val t = (f - 0.55f) / 0.45f
                600f + t * 3000f
            }
        }
        val step = when {
            raw < 120f -> 15
            raw < 600f -> 30
            else -> 60
        }
        return ((raw / step).roundToInt() * step).coerceIn(30, 3600)
    }

    fun durationSecondsToSliderFraction(seconds: Int): Float {
        val s = seconds.coerceIn(30, 3600).toFloat()
        return when {
            s <= 120f -> ((s - 30f) / 90f) * 0.25f
            s <= 600f -> 0.25f + ((s - 120f) / 480f) * 0.30f
            else -> 0.55f + ((s - 600f) / 3000f) * 0.45f
        }.coerceIn(0f, 1f)
    }

    /**
     * Piecewise slider mapping [0f..1f] <-> [300..86400] seconds for Interval (5m..24h):
     *  - 0.00..0.30 -> 5m..60m (300s..3600s, 5m steps)
     *  - 0.30..0.65 -> 1h..6h (3600s..21600s, 15m steps)
     *  - 0.65..1.00 -> 6h..24h (21600s..86400s, 30m steps)
     */
    fun intervalSliderFractionToSeconds(fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val raw = when {
            f <= 0.30f -> {
                val t = f / 0.30f
                300f + t * 3300f
            }
            f <= 0.65f -> {
                val t = (f - 0.30f) / 0.35f
                3600f + t * 18000f
            }
            else -> {
                val t = (f - 0.65f) / 0.35f
                21600f + t * 64800f
            }
        }
        val step = when {
            raw < 3600f -> 300
            raw < 21600f -> 900
            else -> 1800
        }
        return ((raw / step).roundToInt() * step).coerceIn(300, 86400)
    }

    fun intervalSecondsToSliderFraction(seconds: Int): Float {
        val s = seconds.coerceIn(300, 86400).toFloat()
        return when {
            s <= 3600f -> ((s - 300f) / 3300f) * 0.30f
            s <= 21600f -> 0.30f + ((s - 3600f) / 18000f) * 0.35f
            else -> 0.65f + ((s - 21600f) / 64800f) * 0.35f
        }.coerceIn(0f, 1f)
    }
}
