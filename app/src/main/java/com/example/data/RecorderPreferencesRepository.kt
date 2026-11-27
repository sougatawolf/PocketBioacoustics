package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import com.example.audio.AcousticTriggerConfig
import com.example.audio.AudioQuality
import com.example.audio.GpsTagMode
import com.example.audio.LowCutFilter
import com.example.audio.RecordingFileFormat
import com.example.audio.VisualizerMode
import com.example.update.UpdateCheckFrequency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recorderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "waveloop_prefs"
)

data class RecorderSettings(
    val intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
    val recordingDurationSeconds: Int = DEFAULT_RECORDING_DURATION_SECONDS,
    val continuousNoGap: Boolean = false,
    val schedule: RecordingSchedule = RecordingSchedule(),
    val quality: AudioQuality = AudioQuality.CD_44K,
    val fileFormat: RecordingFileFormat = RecordingFileFormat.WAV,
    val visualizerMode: VisualizerMode = VisualizerMode.WAVEFORM,
    val gpsTagMode: GpsTagMode = GpsTagMode.OFF,
    val lowCutFilterHz: Int = DEFAULT_LOW_CUT_FILTER_HZ,
    val acousticTrigger: AcousticTriggerConfig = AcousticTriggerConfig(),
    val batteryProtectionEnabled: Boolean = true,
    val minBatteryThresholdPercent: Int = DEFAULT_BATTERY_THRESHOLD_PERCENT,
    val githubUpdateRepo: String = "",
    val updateCheckFrequency: UpdateCheckFrequency = UpdateCheckFrequency.WEEKLY,
    val skippedUpdateTag: String = "",
    val lastUpdateCheckMs: Long = 0L
) {
    val totalCycleSeconds: Int
        get() = if (continuousNoGap) {
            recordingDurationSeconds
        } else {
            recordingDurationSeconds + intervalSeconds
        }

    companion object {
        // Recording Interval (wait between recordings): 5 min to 24 hours
        const val MIN_INTERVAL_SECONDS = 300
        const val MAX_INTERVAL_SECONDS = 86400
        const val DEFAULT_INTERVAL_SECONDS = 1800 // 30 min

        // Recording Duration (length of each WAV recording): 30 sec to 60 min (max 60 min even in continuous)
        const val MIN_RECORDING_DURATION_SECONDS = 30
        const val MAX_RECORDING_DURATION_SECONDS = 3600
        const val DEFAULT_RECORDING_DURATION_SECONDS = 300 // 5 min

        // Battery guard threshold: 5% to 80%
        const val MIN_BATTERY_THRESHOLD_PERCENT = 5
        const val MAX_BATTERY_THRESHOLD_PERCENT = 80
        const val DEFAULT_BATTERY_THRESHOLD_PERCENT = 15

        // Low-cut (high-pass) filter: 0 (Off) or 20 Hz .. 200 Hz
        const val DEFAULT_LOW_CUT_FILTER_HZ = LowCutFilter.OFF_HZ
    }
}

class RecorderPreferencesRepository(private val context: Context) {

    private object Keys {
        val INTERVAL_SECONDS = intPreferencesKey("interval_seconds_v3")
        val RECORDING_DURATION_SECONDS = intPreferencesKey("recording_duration_seconds")
        val CONTINUOUS_NO_GAP = booleanPreferencesKey("continuous_no_gap")
        val SAMPLE_RATE_HZ = intPreferencesKey("sample_rate_hz")
        val FILE_FORMAT = stringPreferencesKey("recording_file_format")
        val VISUALIZER_MODE = stringPreferencesKey("visualizer_mode")
        val GPS_TAG_MODE = stringPreferencesKey("gps_tag_mode")
        val LOW_CUT_FILTER_HZ = intPreferencesKey("low_cut_filter_hz")

        val SCHEDULE_MODE = stringPreferencesKey("schedule_mode")
        val CUSTOM_START_MIN = intPreferencesKey("schedule_custom_start_min")
        val CUSTOM_END_MIN = intPreferencesKey("schedule_custom_end_min")
        val DAY_START_MIN = intPreferencesKey("schedule_day_start_min")
        val DAY_END_MIN = intPreferencesKey("schedule_day_end_min")
        val SUNRISE_START_MIN = intPreferencesKey("schedule_sunrise_start_min")
        val SUNRISE_END_MIN = intPreferencesKey("schedule_sunrise_end_min")
        val SUNSET_START_MIN = intPreferencesKey("schedule_sunset_start_min")
        val SUNSET_END_MIN = intPreferencesKey("schedule_sunset_end_min")

        val BATTERY_PROTECTION_ENABLED = booleanPreferencesKey("battery_protection_enabled")
        val MIN_BATTERY_THRESHOLD_PERCENT = intPreferencesKey("min_battery_threshold_percent")

        val TRIGGER_ENABLED = booleanPreferencesKey("trigger_enabled")
        val TRIGGER_ENTIRE_FREQ_RANGE = booleanPreferencesKey("trigger_entire_freq_range")
        val TRIGGER_MIN_FREQ_HZ = intPreferencesKey("trigger_min_freq_hz")
        val TRIGGER_MAX_FREQ_HZ = intPreferencesKey("trigger_max_freq_hz")
        val TRIGGER_AMPLITUDE_DB = floatPreferencesKey("trigger_amplitude_db")
        val TRIGGER_PRE_RECORD_SECONDS = intPreferencesKey("trigger_pre_record_seconds")
        val TRIGGER_AUTO_STOP_SILENCE_MINUTES = intPreferencesKey("trigger_auto_stop_silence_minutes")

        val GITHUB_UPDATE_REPO = stringPreferencesKey("github_update_repo")
        val UPDATE_CHECK_FREQUENCY = stringPreferencesKey("update_check_frequency")
        val SKIPPED_UPDATE_TAG = stringPreferencesKey("skipped_update_tag")
        val LAST_UPDATE_CHECK_MS = longPreferencesKey("last_update_check_ms")
    }

    val settingsFlow: Flow<RecorderSettings> = context.recorderDataStore.data.map { prefs ->
        val rawInterval = prefs[Keys.INTERVAL_SECONDS] ?: RecorderSettings.DEFAULT_INTERVAL_SECONDS
        val clampedInterval = rawInterval.coerceIn(
            RecorderSettings.MIN_INTERVAL_SECONDS,
            RecorderSettings.MAX_INTERVAL_SECONDS
        )

        val rawDuration = prefs[Keys.RECORDING_DURATION_SECONDS]
            ?: RecorderSettings.DEFAULT_RECORDING_DURATION_SECONDS
        val clampedDuration = rawDuration.coerceIn(
            RecorderSettings.MIN_RECORDING_DURATION_SECONDS,
            RecorderSettings.MAX_RECORDING_DURATION_SECONDS
        )

        val continuousNoGap = prefs[Keys.CONTINUOUS_NO_GAP] ?: false
        val sampleRate = prefs[Keys.SAMPLE_RATE_HZ] ?: AudioQuality.CD_44K.sampleRateHz
        val fileFormat = prefs[Keys.FILE_FORMAT]?.let { name ->
            RecordingFileFormat.entries.firstOrNull { it.name == name }
        } ?: RecordingFileFormat.WAV
        val visualizerMode = prefs[Keys.VISUALIZER_MODE]?.let { name ->
            VisualizerMode.entries.firstOrNull { it.name == name }
        } ?: VisualizerMode.WAVEFORM
        val gpsTagMode = prefs[Keys.GPS_TAG_MODE]?.let { name ->
            GpsTagMode.entries.firstOrNull { it.name == name }
        } ?: GpsTagMode.OFF

        val scheduleMode = prefs[Keys.SCHEDULE_MODE]?.let { name ->
            ScheduleMode.entries.firstOrNull { it.name == name }
        } ?: ScheduleMode.ALWAYS

        val defaults = RecordingSchedule()
        val schedule = RecordingSchedule(
            mode = scheduleMode,
            customStartMinuteOfDay = (prefs[Keys.CUSTOM_START_MIN] ?: defaults.customStartMinuteOfDay)
                .mod(RecordingSchedule.MINUTES_PER_DAY),
            customEndMinuteOfDay = (prefs[Keys.CUSTOM_END_MIN] ?: defaults.customEndMinuteOfDay)
                .mod(RecordingSchedule.MINUTES_PER_DAY),
            dayStartMinuteOfDay = (prefs[Keys.DAY_START_MIN] ?: defaults.dayStartMinuteOfDay)
                .mod(RecordingSchedule.MINUTES_PER_DAY),
            dayEndMinuteOfDay = (prefs[Keys.DAY_END_MIN] ?: defaults.dayEndMinuteOfDay)
                .mod(RecordingSchedule.MINUTES_PER_DAY),
            sunriseStartMinuteOfDay = (prefs[Keys.SUNRISE_START_MIN] ?: defaults.sunriseStartMinuteOfDay)
                .mod(RecordingSchedule.MINUTES_PER_DAY),
            sunriseEndMinuteOfDay = (prefs[Keys.SUNRISE_END_MIN] ?: defaults.sunriseEndMinuteOfDay)
                .mod(RecordingSchedule.MINUTES_PER_DAY),
            sunsetStartMinuteOfDay = (prefs[Keys.SUNSET_START_MIN] ?: defaults.sunsetStartMinuteOfDay)
                .mod(RecordingSchedule.MINUTES_PER_DAY),
            sunsetEndMinuteOfDay = (prefs[Keys.SUNSET_END_MIN] ?: defaults.sunsetEndMinuteOfDay)
                .mod(RecordingSchedule.MINUTES_PER_DAY)
        )

        val batteryProtectionEnabled = prefs[Keys.BATTERY_PROTECTION_ENABLED] ?: true
        val minBatteryThreshold = (
            prefs[Keys.MIN_BATTERY_THRESHOLD_PERCENT]
                ?: RecorderSettings.DEFAULT_BATTERY_THRESHOLD_PERCENT
            ).coerceIn(
            RecorderSettings.MIN_BATTERY_THRESHOLD_PERCENT,
            RecorderSettings.MAX_BATTERY_THRESHOLD_PERCENT
        )

        val lowCutFilterHz = LowCutFilter.normalizeCutoffHz(
            prefs[Keys.LOW_CUT_FILTER_HZ] ?: RecorderSettings.DEFAULT_LOW_CUT_FILTER_HZ
        )
        val resolvedQuality = AudioQuality.fromSampleRate(sampleRate)
        val triggerConfig = AcousticTriggerConfig(
            enabled = prefs[Keys.TRIGGER_ENABLED] ?: false,
            entireFrequencyRange = prefs[Keys.TRIGGER_ENTIRE_FREQ_RANGE] ?: true,
            minFreqHz = prefs[Keys.TRIGGER_MIN_FREQ_HZ] ?: AcousticTriggerConfig.DEFAULT_MIN_FREQ_HZ,
            maxFreqHz = prefs[Keys.TRIGGER_MAX_FREQ_HZ] ?: AcousticTriggerConfig.DEFAULT_MAX_FREQ_HZ,
            amplitudeThresholdDb = prefs[Keys.TRIGGER_AMPLITUDE_DB]
                ?: AcousticTriggerConfig.DEFAULT_THRESHOLD_DB,
            preRecordSeconds = prefs[Keys.TRIGGER_PRE_RECORD_SECONDS]
                ?: AcousticTriggerConfig.DEFAULT_PRE_RECORD_SECONDS,
            autoStopSilenceMinutes = prefs[Keys.TRIGGER_AUTO_STOP_SILENCE_MINUTES]
                ?: AcousticTriggerConfig.DEFAULT_AUTO_STOP_SILENCE_MINUTES
        ).normalizedForSampleRate(resolvedQuality.sampleRateHz)

        val effectiveContinuousNoGap = continuousNoGap || triggerConfig.enabled

        val defaultGithubRepo = context.getString(R.string.default_github_update_repo).trim()
        val githubUpdateRepo = prefs[Keys.GITHUB_UPDATE_REPO] ?: defaultGithubRepo
        val updateCheckFrequency = prefs[Keys.UPDATE_CHECK_FREQUENCY]?.let { name ->
            UpdateCheckFrequency.entries.firstOrNull { it.name == name }
        } ?: UpdateCheckFrequency.WEEKLY
        val skippedUpdateTag = prefs[Keys.SKIPPED_UPDATE_TAG] ?: ""
        val lastUpdateCheckMs = prefs[Keys.LAST_UPDATE_CHECK_MS] ?: 0L

        RecorderSettings(
            intervalSeconds = clampedInterval,
            recordingDurationSeconds = clampedDuration,
            continuousNoGap = effectiveContinuousNoGap,
            schedule = schedule,
            quality = resolvedQuality,
            fileFormat = fileFormat,
            visualizerMode = visualizerMode,
            gpsTagMode = gpsTagMode,
            lowCutFilterHz = lowCutFilterHz,
            acousticTrigger = triggerConfig,
            batteryProtectionEnabled = batteryProtectionEnabled,
            minBatteryThresholdPercent = minBatteryThreshold,
            githubUpdateRepo = githubUpdateRepo,
            updateCheckFrequency = updateCheckFrequency,
            skippedUpdateTag = skippedUpdateTag,
            lastUpdateCheckMs = lastUpdateCheckMs
        )
    }

    suspend fun setIntervalSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(
            RecorderSettings.MIN_INTERVAL_SECONDS,
            RecorderSettings.MAX_INTERVAL_SECONDS
        )
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.INTERVAL_SECONDS] = clamped
        }
    }

    suspend fun setRecordingDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(
            RecorderSettings.MIN_RECORDING_DURATION_SECONDS,
            RecorderSettings.MAX_RECORDING_DURATION_SECONDS
        )
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.RECORDING_DURATION_SECONDS] = clamped
        }
    }

    suspend fun setContinuousNoGap(enabled: Boolean) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.CONTINUOUS_NO_GAP] = enabled
        }
    }

    suspend fun setSchedule(schedule: RecordingSchedule) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.SCHEDULE_MODE] = schedule.mode.name
            prefs[Keys.CUSTOM_START_MIN] = schedule.customStartMinuteOfDay
            prefs[Keys.CUSTOM_END_MIN] = schedule.customEndMinuteOfDay
            prefs[Keys.DAY_START_MIN] = schedule.dayStartMinuteOfDay
            prefs[Keys.DAY_END_MIN] = schedule.dayEndMinuteOfDay
            prefs[Keys.SUNRISE_START_MIN] = schedule.sunriseStartMinuteOfDay
            prefs[Keys.SUNRISE_END_MIN] = schedule.sunriseEndMinuteOfDay
            prefs[Keys.SUNSET_START_MIN] = schedule.sunsetStartMinuteOfDay
            prefs[Keys.SUNSET_END_MIN] = schedule.sunsetEndMinuteOfDay
        }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.SAMPLE_RATE_HZ] = quality.sampleRateHz
        }
    }

    suspend fun setFileFormat(format: RecordingFileFormat) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.FILE_FORMAT] = format.name
        }
    }

    suspend fun setVisualizerMode(mode: VisualizerMode) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.VISUALIZER_MODE] = mode.name
        }
    }

    suspend fun setGpsTagMode(mode: GpsTagMode) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.GPS_TAG_MODE] = mode.name
        }
    }

    suspend fun setLowCutFilterHz(hz: Int) {
        val normalized = LowCutFilter.normalizeCutoffHz(hz)
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.LOW_CUT_FILTER_HZ] = normalized
        }
    }

    suspend fun setBatteryProtectionEnabled(enabled: Boolean) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.BATTERY_PROTECTION_ENABLED] = enabled
        }
    }

    suspend fun setMinBatteryThresholdPercent(percent: Int) {
        val clamped = percent.coerceIn(
            RecorderSettings.MIN_BATTERY_THRESHOLD_PERCENT,
            RecorderSettings.MAX_BATTERY_THRESHOLD_PERCENT
        )
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.MIN_BATTERY_THRESHOLD_PERCENT] = clamped
        }
    }

    suspend fun setAcousticTriggerConfig(config: AcousticTriggerConfig) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.TRIGGER_ENABLED] = config.enabled
            prefs[Keys.TRIGGER_ENTIRE_FREQ_RANGE] = config.entireFrequencyRange
            prefs[Keys.TRIGGER_MIN_FREQ_HZ] = config.minFreqHz
            prefs[Keys.TRIGGER_MAX_FREQ_HZ] = config.maxFreqHz
            prefs[Keys.TRIGGER_AMPLITUDE_DB] = config.amplitudeThresholdDb
            prefs[Keys.TRIGGER_PRE_RECORD_SECONDS] = config.preRecordSeconds
            prefs[Keys.TRIGGER_AUTO_STOP_SILENCE_MINUTES] = config.autoStopSilenceMinutes
            if (config.enabled) {
                prefs[Keys.CONTINUOUS_NO_GAP] = true
            }
        }
    }

    suspend fun setGithubUpdateRepo(repoSlug: String) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.GITHUB_UPDATE_REPO] = repoSlug.trim()
        }
    }

    suspend fun setUpdateCheckFrequency(frequency: UpdateCheckFrequency) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.UPDATE_CHECK_FREQUENCY] = frequency.name
        }
    }

    suspend fun setSkippedUpdateTag(tag: String) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.SKIPPED_UPDATE_TAG] = tag.trim()
        }
    }

    suspend fun setLastUpdateCheckMs(timestampMs: Long) {
        context.recorderDataStore.edit { prefs ->
            prefs[Keys.LAST_UPDATE_CHECK_MS] = timestampMs
        }
    }
}
