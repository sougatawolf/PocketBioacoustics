package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.example.data.RecorderSettings
import com.example.data.RecordingSchedule
import com.example.data.ScheduleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class BatterySnapshot(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false,
    val chargeCounterMah: Int? = null,
    val instantaneousCurrentMa: Int? = null,
    val temperatureCelsius: Float? = null,
    val sessionStartLevelPercent: Int? = null
) {
    val sessionUsedPercent: Int
        get() {
            val start = sessionStartLevelPercent ?: return 0
            return (start - levelPercent).coerceAtLeast(0)
        }
}

data class BatteryUsageEstimate(
    val levelPercent: Int,
    val thresholdPercent: Int,
    val usablePercent: Int,
    val isCharging: Boolean,
    val isBlockedByThreshold: Boolean,
    val estimatedMinutesRemaining: Long,
    val estimatedDrainPercentPerHour: Double,
    val estimatedAverageCurrentMa: Int,
    val estimatedCapacityMah: Int,
    val dutyCyclePercent: Int,
    val sessionUsedPercent: Int,
    val visualizerMode: VisualizerMode = VisualizerMode.WAVEFORM,
    val visualizerExtraMa: Int = 0,
    val visualizerExtraPercent: Int = 0,
    val baselineMinutesWithoutVisualizer: Long = estimatedMinutesRemaining,
    val gpsTagMode: GpsTagMode = GpsTagMode.OFF
)

object BatteryPowerEstimator {

    private const val DEFAULT_BATTERY_CAPACITY_MAH = 4500

    fun isBelowThreshold(
        snapshot: BatterySnapshot,
        protectionEnabled: Boolean,
        thresholdPercent: Int
    ): Boolean {
        if (!protectionEnabled) return false
        if (snapshot.isCharging) return false
        return snapshot.levelPercent < thresholdPercent
    }

    fun activeCurrentMaForSampleRate(sampleRateHz: Int): Double {
        return when {
            sampleRateHz <= 8000 -> 125.0   // Baseline
            sampleRateHz <= 12000 -> 127.0  // Negligible (+0-2%)
            sampleRateHz <= 16000 -> 129.0  // Negligible (+1-4%)
            sampleRateHz <= 24000 -> 130.0  // Very Low (+2-5%)
            sampleRateHz <= 44100 -> 131.0  // Very Low (+2-6%)
            sampleRateHz <= 48000 -> 129.0  // Very Low (+1-5%, Native OS rate)
            sampleRateHz <= 96000 -> 144.0  // Moderate (+10-20%)
            sampleRateHz <= 192000 -> 163.0 // High (+20-40%)
            else -> 180.0                   // Very High (+30-50%+, 320 kHz)
        }
    }

    fun spectrogramActiveOverheadMa(sampleRateHz: Int): Double {
        // Real-time 512-pt FFT + 2D GPU heatmap canvas redraws + sustained UI frame cost
        return when {
            sampleRateHz <= 48000 -> 34.0
            sampleRateHz <= 96000 -> 44.0
            else -> 58.0
        }
    }

    fun gpsAverageCurrentMa(
        gpsTagMode: GpsTagMode,
        recordingDurationSeconds: Int,
        intervalSeconds: Int,
        continuousNoGap: Boolean,
        scheduleFraction: Double
    ): Double {
        return when (gpsTagMode) {
            GpsTagMode.OFF -> 0.0
            // One ~6s fix on Record start amortized over long deployment (~negligible)
            GpsTagMode.ONE_TIME -> 0.05
            // One ~6s single fix (~135mA radio burst) per segment file start
            GpsTagMode.EACH_FILE -> {
                val cycleSec = if (continuousNoGap) {
                    recordingDurationSeconds
                } else {
                    recordingDurationSeconds + intervalSeconds
                }.coerceAtLeast(1).toDouble()
                ((6.0 * 135.0) / cycleSec) * scheduleFraction
            }
        }
    }

    fun scheduleActiveFraction(schedule: RecordingSchedule): Double {
        return when (schedule.mode) {
            ScheduleMode.ALWAYS -> 1.0
            ScheduleMode.DAY_ONLY,
            ScheduleMode.SUNRISE_SUNSET,
            ScheduleMode.CUSTOM_WINDOW -> {
                var activeMinutes = 0
                for (minute in 0 until RecordingSchedule.MINUTES_PER_DAY) {
                    if (schedule.isActiveAtMinute(minute)) {
                        activeMinutes++
                    }
                }
                (activeMinutes.toDouble() / RecordingSchedule.MINUTES_PER_DAY.toDouble())
                    .coerceIn(0.01, 1.0)
            }
        }
    }

    fun estimate(
        snapshot: BatterySnapshot,
        protectionEnabled: Boolean,
        thresholdPercent: Int,
        recordingDurationSeconds: Int,
        intervalSeconds: Int,
        continuousNoGap: Boolean,
        sampleRateHz: Int,
        schedule: RecordingSchedule,
        visualizerMode: VisualizerMode = VisualizerMode.WAVEFORM,
        gpsTagMode: GpsTagMode = GpsTagMode.OFF
    ): BatteryUsageEstimate {
        val safeLevel = snapshot.levelPercent.coerceIn(0, 100)
        val effectiveThreshold = if (protectionEnabled) thresholdPercent.coerceIn(0, 95) else 0
        val usablePercent = (safeLevel - effectiveThreshold).coerceAtLeast(0)

        val estimatedCapacityMah = inferFullCapacityMah(snapshot)

        val captureFractionInCycle = if (continuousNoGap) {
            1.0
        } else {
            val rec = recordingDurationSeconds.coerceAtLeast(1).toDouble()
            val gap = intervalSeconds.coerceAtLeast(0).toDouble()
            (rec / (rec + gap)).coerceIn(0.0, 1.0)
        }

        val scheduleFraction = scheduleActiveFraction(schedule)
        val effectiveActiveFraction = (captureFractionInCycle * scheduleFraction).coerceIn(0.0, 1.0)

        val activeMa = activeCurrentMaForSampleRate(sampleRateHz)
        val standbyMa = 26.0
        val gpsAvgMa = gpsAverageCurrentMa(
            gpsTagMode = gpsTagMode,
            recordingDurationSeconds = recordingDurationSeconds,
            intervalSeconds = intervalSeconds,
            continuousNoGap = continuousNoGap,
            scheduleFraction = scheduleFraction
        )
        val baselineAvgCurrentMa =
            effectiveActiveFraction * activeMa + (1.0 - effectiveActiveFraction) * standbyMa + gpsAvgMa

        val isSpectrogram = visualizerMode == VisualizerMode.SPECTROGRAM
        val extraActiveMa = if (isSpectrogram) spectrogramActiveOverheadMa(sampleRateHz) else 0.0
        val extraStandbyMa = if (isSpectrogram) 14.0 else 0.0 // Kept-open live spectrogram UI overhead
        val modeledAvgCurrentMa =
            effectiveActiveFraction * (activeMa + extraActiveMa) +
                (1.0 - effectiveActiveFraction) * (standbyMa + extraStandbyMa) + gpsAvgMa

        val baselineDrainPerHour =
            ((baselineAvgCurrentMa / estimatedCapacityMah.toDouble()) * 100.0).coerceAtLeast(0.1)
        val drainPercentPerHour =
            ((modeledAvgCurrentMa / estimatedCapacityMah.toDouble()) * 100.0).coerceAtLeast(0.1)

        val baselineMinutes = if (usablePercent <= 0) {
            0L
        } else {
            ((usablePercent.toDouble() / baselineDrainPerHour) * 60.0).roundToLong().coerceAtLeast(0L)
        }
        val minutesRemaining = if (usablePercent <= 0) {
            0L
        } else {
            ((usablePercent.toDouble() / drainPercentPerHour) * 60.0).roundToLong().coerceAtLeast(0L)
        }

        val extraMa = (modeledAvgCurrentMa - baselineAvgCurrentMa).roundToInt().coerceAtLeast(0)
        val extraPercent = if (isSpectrogram && baselineAvgCurrentMa > 0.0) {
            (((modeledAvgCurrentMa - baselineAvgCurrentMa) / baselineAvgCurrentMa) * 100.0)
                .roundToInt()
                .coerceAtLeast(1)
        } else {
            0
        }

        return BatteryUsageEstimate(
            levelPercent = safeLevel,
            thresholdPercent = thresholdPercent,
            usablePercent = usablePercent,
            isCharging = snapshot.isCharging,
            isBlockedByThreshold = isBelowThreshold(snapshot, protectionEnabled, thresholdPercent),
            estimatedMinutesRemaining = minutesRemaining,
            estimatedDrainPercentPerHour = drainPercentPerHour,
            estimatedAverageCurrentMa = modeledAvgCurrentMa.roundToInt(),
            estimatedCapacityMah = estimatedCapacityMah,
            dutyCyclePercent = (effectiveActiveFraction * 100.0).roundToInt(),
            sessionUsedPercent = snapshot.sessionUsedPercent,
            visualizerMode = visualizerMode,
            visualizerExtraMa = extraMa,
            visualizerExtraPercent = extraPercent,
            baselineMinutesWithoutVisualizer = baselineMinutes,
            gpsTagMode = gpsTagMode
        )
    }

    private fun inferFullCapacityMah(snapshot: BatterySnapshot): Int {
        val remainingMah = snapshot.chargeCounterMah
        val level = snapshot.levelPercent
        if (remainingMah != null && remainingMah > 100 && level in 5..100) {
            val inferred = ((remainingMah.toDouble() / level.toDouble()) * 100.0).roundToInt()
            if (inferred in 1500..15000) {
                return inferred
            }
        }
        return DEFAULT_BATTERY_CAPACITY_MAH
    }
}

class BatteryMonitor(private val context: Context) {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private val _snapshot = MutableStateFlow(readInitialSnapshot())
    val snapshot: StateFlow<BatterySnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var sessionStartLevel: Int? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent != null) {
                _snapshot.value = parseIntent(intent)
            }
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {
        }
    }

    fun refreshSnapshot(): BatterySnapshot {
        val sticky = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        val current = if (sticky != null) parseIntent(sticky) else readManagerFallback()
        _snapshot.value = current
        return current
    }

    fun markSessionStart() {
        val current = refreshSnapshot()
        sessionStartLevel = current.levelPercent
        _snapshot.value = current.copy(sessionStartLevelPercent = sessionStartLevel)
    }

    fun clearSessionStart() {
        sessionStartLevel = null
        _snapshot.value = _snapshot.value.copy(sessionStartLevelPercent = null)
    }

    fun isBelowThreshold(
        protectionEnabled: Boolean,
        thresholdPercent: Int
    ): Boolean {
        val current = refreshSnapshot()
        return BatteryPowerEstimator.isBelowThreshold(
            snapshot = current,
            protectionEnabled = protectionEnabled,
            thresholdPercent = thresholdPercent
        )
    }

    private fun readInitialSnapshot(): BatterySnapshot {
        val sticky = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        return if (sticky != null) parseIntent(sticky) else readManagerFallback()
    }

    private fun readManagerFallback(): BatterySnapshot {
        val capacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 } ?: 100
        val charging = batteryManager?.isCharging ?: false
        return BatterySnapshot(
            levelPercent = capacity,
            isCharging = charging,
            chargeCounterMah = readChargeCounterMah(),
            instantaneousCurrentMa = readInstantaneousCurrentMa(),
            sessionStartLevelPercent = sessionStartLevel
        )
    }

    private fun parseIntent(intent: Intent): BatterySnapshot {
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val rawScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val levelPercent = if (rawLevel >= 0) {
            ((rawLevel * 100f) / rawScale.toFloat()).roundToInt().coerceIn(0, 100)
        } else {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 } ?: 100
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val isCharging = plugged != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val tempC = if (rawTemp != Int.MIN_VALUE && rawTemp > 0) rawTemp / 10f else null

        return BatterySnapshot(
            levelPercent = levelPercent,
            isCharging = isCharging,
            chargeCounterMah = readChargeCounterMah(),
            instantaneousCurrentMa = readInstantaneousCurrentMa(),
            temperatureCelsius = tempC,
            sessionStartLevelPercent = sessionStartLevel
        )
    }

    private fun readChargeCounterMah(): Int? {
        val uAh = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?: return null
        if (uAh == Int.MIN_VALUE || uAh <= 0) return null
        return uAh / 1000
    }

    private fun readInstantaneousCurrentMa(): Int? {
        val avgUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val nowUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val validUa = listOfNotNull(avgUa, nowUa).firstOrNull {
            it != Int.MIN_VALUE && it != 0 && abs(it) in 5_000..10_000_000
        } ?: return null
        return abs(validUa) / 1000
    }
}
