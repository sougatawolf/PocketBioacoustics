package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import com.example.data.RecorderSettings
import com.example.data.RecordingRepository
import com.example.data.RecordingSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import kotlin.math.log10
import kotlin.math.sqrt

enum class RecordingStatus {
    IDLE,
    ARMED_TRIGGER,
    RECORDING,
    PAUSED,
    PAUSED_LOW_BATTERY,
    WAITING_INTERVAL,
    WAITING_SCHEDULE
}

data class RecordingState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val continuousNoGap: Boolean = false,
    val schedule: RecordingSchedule = RecordingSchedule(),
    val nextScheduleWindowStartMinute: Int? = null,
    val batteryProtectionEnabled: Boolean = true,
    val minBatteryThresholdPercent: Int = RecorderSettings.DEFAULT_BATTERY_THRESHOLD_PERCENT,
    val segmentNumber: Int = 1,
    val completedSegmentsInSession: Int = 0,
    val currentSegmentElapsedMs: Long = 0L,
    val totalSessionElapsedMs: Long = 0L,
    val nextCycleCountdownMs: Long = 0L,
    val targetRecordingDurationSeconds: Int = RecorderSettings.DEFAULT_RECORDING_DURATION_SECONDS,
    val targetIntervalSeconds: Int = RecorderSettings.DEFAULT_INTERVAL_SECONDS,
    val quality: AudioQuality = AudioQuality.CD_44K,
    val fileFormat: RecordingFileFormat = RecordingFileFormat.WAV,
    val currentFileName: String? = null,
    val currentFileBytes: Long = 0L,
    val lastCompletedSegmentName: String? = null,
    val lastCompletedSegmentDurationMs: Long = 0L,
    val amplitudes: List<Float> = List(WAVEFORM_BAR_COUNT) { 0f },
    val visualizerMode: VisualizerMode = VisualizerMode.WAVEFORM,
    val spectrogramColumns: List<List<Float>> = emptyList(),
    val peakFrequencyHz: Int = 0,
    val currentLevelDb: Float = MIN_DB,
    val peakLevelDb: Float = MIN_DB,
    val gpsTagMode: GpsTagMode = GpsTagMode.OFF,
    val currentGpsCoordinate: GpsCoordinate? = null,
    val isAcquiringGps: Boolean = false,
    val lowCutFilterHz: Int = RecorderSettings.DEFAULT_LOW_CUT_FILTER_HZ,
    val triggerConfig: AcousticTriggerConfig = AcousticTriggerConfig(),
    val triggerBandLevelDb: Float = MIN_DB,
    val triggerSilenceElapsedMs: Long = 0L,
    val triggerPreRecordBufferedMs: Long = 0L,
    val errorMessage: String? = null
) {
    val triggerAutoStopRemainingMs: Long
        get() = (triggerConfig.autoStopSilenceMinutes.toLong() * 60_000L - triggerSilenceElapsedMs)
            .coerceAtLeast(0L)
    val segmentProgress: Float
        get() {
            val targetMs = targetRecordingDurationSeconds.toLong() * 1000L
            return if (targetMs > 0L) {
                (currentSegmentElapsedMs.toFloat() / targetMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

    val remainingSegmentMs: Long
        get() {
            val targetMs = targetRecordingDurationSeconds.toLong() * 1000L
            return (targetMs - currentSegmentElapsedMs).coerceAtLeast(0L)
        }

    val intervalWaitProgress: Float
        get() {
            val targetMs = targetIntervalSeconds.toLong() * 1000L
            return if (targetMs > 0L) {
                (1f - (nextCycleCountdownMs.toFloat() / targetMs.toFloat())).coerceIn(0f, 1f)
            } else {
                1f
            }
        }

    companion object {
        const val WAVEFORM_BAR_COUNT = 72
        const val MIN_DB = -60f
    }
}

class RecordingEngine(
    private val recordingRepository: RecordingRepository,
    private val batteryMonitor: BatteryMonitor,
    private val gpsTagManager: GpsTagManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    @Volatile
    private var captureRunning = false

    @Volatile
    private var paused = false

    @Volatile
    private var manualSplitRequested = false

    @Volatile
    private var triggerNextNowRequested = false

    @Volatile
    private var liveIntervalSeconds = RecorderSettings.DEFAULT_INTERVAL_SECONDS

    @Volatile
    private var liveRecordingDurationSeconds = RecorderSettings.DEFAULT_RECORDING_DURATION_SECONDS

    @Volatile
    private var liveContinuousNoGap = false

    @Volatile
    private var liveSchedule = RecordingSchedule()

    @Volatile
    private var liveBatteryProtectionEnabled = true

    @Volatile
    private var liveMinBatteryThresholdPercent = RecorderSettings.DEFAULT_BATTERY_THRESHOLD_PERCENT

    @Volatile
    private var activeQuality = AudioQuality.CD_44K

    @Volatile
    private var activeFileFormat = RecordingFileFormat.WAV

    @Volatile
    private var liveVisualizerMode = VisualizerMode.WAVEFORM

    @Volatile
    private var liveGpsTagMode = GpsTagMode.OFF

    @Volatile
    private var liveLowCutFilterHz = RecorderSettings.DEFAULT_LOW_CUT_FILTER_HZ

    @Volatile
    private var liveTriggerConfig = AcousticTriggerConfig()

    @Volatile
    private var sessionGpsCoordinate: GpsCoordinate? = null

    private val sessionGpsLock = Any()
    private val pendingOneTimeTaggedFiles = mutableListOf<File>()

    @Volatile
    private var hasLiveOverrides = false

    private var captureThread: Thread? = null
    private val stateLock = Any()

    fun syncIdleSettings(settings: RecorderSettings) {
        liveIntervalSeconds = settings.intervalSeconds
        liveRecordingDurationSeconds = settings.recordingDurationSeconds
        liveContinuousNoGap = settings.continuousNoGap
        liveSchedule = settings.schedule
        liveBatteryProtectionEnabled = settings.batteryProtectionEnabled
        liveMinBatteryThresholdPercent = settings.minBatteryThresholdPercent
        activeQuality = settings.quality
        activeFileFormat = settings.fileFormat
        liveVisualizerMode = settings.visualizerMode
        liveGpsTagMode = settings.gpsTagMode
        liveLowCutFilterHz = settings.lowCutFilterHz
        liveTriggerConfig = settings.acousticTrigger.normalizedForSampleRate(settings.quality.sampleRateHz)
        hasLiveOverrides = true
        if (_state.value.status == RecordingStatus.IDLE) {
            _state.value = _state.value.copy(
                continuousNoGap = settings.continuousNoGap,
                schedule = settings.schedule,
                batteryProtectionEnabled = settings.batteryProtectionEnabled,
                minBatteryThresholdPercent = settings.minBatteryThresholdPercent,
                targetRecordingDurationSeconds = settings.recordingDurationSeconds,
                targetIntervalSeconds = settings.intervalSeconds,
                quality = settings.quality,
                fileFormat = settings.fileFormat,
                visualizerMode = settings.visualizerMode,
                gpsTagMode = settings.gpsTagMode,
                lowCutFilterHz = settings.lowCutFilterHz,
                triggerConfig = liveTriggerConfig
            )
        } else {
            _state.value = _state.value.copy(
                continuousNoGap = settings.continuousNoGap,
                schedule = settings.schedule,
                batteryProtectionEnabled = settings.batteryProtectionEnabled,
                minBatteryThresholdPercent = settings.minBatteryThresholdPercent,
                targetRecordingDurationSeconds = settings.recordingDurationSeconds,
                targetIntervalSeconds = settings.intervalSeconds,
                visualizerMode = settings.visualizerMode,
                gpsTagMode = settings.gpsTagMode,
                lowCutFilterHz = settings.lowCutFilterHz,
                triggerConfig = liveTriggerConfig
            )
        }
    }

    fun updateGpsTagModeLive(mode: GpsTagMode) {
        liveGpsTagMode = mode
        hasLiveOverrides = true
        _state.value = _state.value.copy(gpsTagMode = mode)
    }

    fun updateLowCutFilterLive(hz: Int) {
        val normalized = LowCutFilter.normalizeCutoffHz(hz)
        liveLowCutFilterHz = normalized
        hasLiveOverrides = true
        _state.value = _state.value.copy(lowCutFilterHz = normalized)
    }

    fun updateAcousticTriggerLive(config: AcousticTriggerConfig) {
        val normalized = config.normalizedForSampleRate(activeQuality.sampleRateHz)
        liveTriggerConfig = normalized
        hasLiveOverrides = true
        if (normalized.enabled && !liveContinuousNoGap) {
            liveContinuousNoGap = true
        }
        _state.value = _state.value.copy(
            triggerConfig = normalized,
            continuousNoGap = liveContinuousNoGap
        )
    }

    fun updateVisualizerModeLive(mode: VisualizerMode) {
        liveVisualizerMode = mode
        hasLiveOverrides = true
        _state.value = if (mode == VisualizerMode.WAVEFORM) {
            _state.value.copy(
                visualizerMode = mode,
                spectrogramColumns = emptyList(),
                peakFrequencyHz = 0
            )
        } else {
            _state.value.copy(visualizerMode = mode)
        }
    }

    fun updateIntervalLive(seconds: Int) {
        val clamped = seconds.coerceIn(
            RecorderSettings.MIN_INTERVAL_SECONDS,
            RecorderSettings.MAX_INTERVAL_SECONDS
        )
        liveIntervalSeconds = clamped
        hasLiveOverrides = true
        _state.value = _state.value.copy(targetIntervalSeconds = clamped)
    }

    fun updateRecordingDurationLive(seconds: Int) {
        val clamped = seconds.coerceIn(
            RecorderSettings.MIN_RECORDING_DURATION_SECONDS,
            RecorderSettings.MAX_RECORDING_DURATION_SECONDS
        )
        liveRecordingDurationSeconds = clamped
        hasLiveOverrides = true
        _state.value = _state.value.copy(targetRecordingDurationSeconds = clamped)
    }

    fun updateContinuousNoGapLive(enabled: Boolean) {
        if (!enabled && liveTriggerConfig.enabled) {
            return
        }
        liveContinuousNoGap = enabled
        hasLiveOverrides = true
        _state.value = _state.value.copy(continuousNoGap = enabled)
    }

    fun updateScheduleLive(schedule: RecordingSchedule) {
        liveSchedule = schedule
        hasLiveOverrides = true
        _state.value = _state.value.copy(schedule = schedule)
    }

    fun updateBatteryProtectionLive(enabled: Boolean) {
        liveBatteryProtectionEnabled = enabled
        hasLiveOverrides = true
        _state.value = _state.value.copy(batteryProtectionEnabled = enabled)
    }

    fun updateMinBatteryThresholdLive(percent: Int) {
        val clamped = percent.coerceIn(
            RecorderSettings.MIN_BATTERY_THRESHOLD_PERCENT,
            RecorderSettings.MAX_BATTERY_THRESHOLD_PERCENT
        )
        liveMinBatteryThresholdPercent = clamped
        hasLiveOverrides = true
        _state.value = _state.value.copy(minBatteryThresholdPercent = clamped)
    }

    fun startSession(settings: RecorderSettings): Boolean {
        synchronized(stateLock) {
            if (captureRunning) return true
            if (!hasLiveOverrides) {
                liveIntervalSeconds = settings.intervalSeconds
                liveRecordingDurationSeconds = settings.recordingDurationSeconds
                liveContinuousNoGap = settings.continuousNoGap
                liveSchedule = settings.schedule
                liveBatteryProtectionEnabled = settings.batteryProtectionEnabled
                liveMinBatteryThresholdPercent = settings.minBatteryThresholdPercent
                activeQuality = settings.quality
                activeFileFormat = settings.fileFormat
                liveVisualizerMode = settings.visualizerMode
                liveGpsTagMode = settings.gpsTagMode
                liveLowCutFilterHz = settings.lowCutFilterHz
                liveTriggerConfig = settings.acousticTrigger.normalizedForSampleRate(settings.quality.sampleRateHz)
                hasLiveOverrides = true
            }

            if (batteryMonitor.isBelowThreshold(
                    liveBatteryProtectionEnabled,
                    liveMinBatteryThresholdPercent
                )
            ) {
                val snap = batteryMonitor.snapshot.value
                _state.value = _state.value.copy(
                    errorMessage = "Battery at ${snap.levelPercent}% (below ${liveMinBatteryThresholdPercent}% guard). Charge device or lower threshold."
                )
                return false
            }

            val sessionQuality = activeQuality
            val sessionFormat = activeFileFormat
            val sessionGpsMode = liveGpsTagMode
            val sessionTriggerCfg = liveTriggerConfig.normalizedForSampleRate(sessionQuality.sampleRateHz)
            liveTriggerConfig = sessionTriggerCfg
            if (sessionTriggerCfg.enabled) {
                liveContinuousNoGap = true
            }
            val scheduleActiveNow = liveSchedule.isActiveAt(Calendar.getInstance())
            synchronized(sessionGpsLock) {
                sessionGpsCoordinate = null
                pendingOneTimeTaggedFiles.clear()
            }
            batteryMonitor.markSessionStart()
            paused = false
            manualSplitRequested = false
            triggerNextNowRequested = false
            captureRunning = true

            _state.value = RecordingState(
                status = when {
                    !scheduleActiveNow -> RecordingStatus.WAITING_SCHEDULE
                    sessionTriggerCfg.enabled -> RecordingStatus.ARMED_TRIGGER
                    else -> RecordingStatus.RECORDING
                },
                continuousNoGap = liveContinuousNoGap,
                schedule = liveSchedule,
                batteryProtectionEnabled = liveBatteryProtectionEnabled,
                minBatteryThresholdPercent = liveMinBatteryThresholdPercent,
                nextScheduleWindowStartMinute = if (scheduleActiveNow) {
                    null
                } else {
                    liveSchedule.nextWindowStartMinute(Calendar.getInstance())
                },
                nextCycleCountdownMs = if (scheduleActiveNow) {
                    0L
                } else {
                    liveSchedule.millisUntilNextWindow(Calendar.getInstance())
                },
                segmentNumber = 1,
                completedSegmentsInSession = 0,
                targetRecordingDurationSeconds = liveRecordingDurationSeconds,
                targetIntervalSeconds = liveIntervalSeconds,
                quality = sessionQuality,
                fileFormat = sessionFormat,
                visualizerMode = liveVisualizerMode,
                gpsTagMode = sessionGpsMode,
                isAcquiringGps = sessionGpsMode == GpsTagMode.ONE_TIME && !sessionTriggerCfg.enabled,
                lowCutFilterHz = liveLowCutFilterHz,
                triggerConfig = sessionTriggerCfg
            )

            if (sessionGpsMode == GpsTagMode.ONE_TIME) {
                triggerOneTimeSessionGpsFix()
            }

            val thread = Thread({
                runCaptureLoop(sessionQuality, sessionFormat)
            }, "WaveLoop-AudioCapture")
            captureThread = thread
            thread.start()
            return true
        }
    }

    private fun triggerOneTimeSessionGpsFix() {
        scope.launch {
            _state.value = _state.value.copy(isAcquiringGps = true)
            val fix = gpsTagManager.acquireSingleFix()
            val filesToTag: List<File>
            synchronized(sessionGpsLock) {
                sessionGpsCoordinate = fix
                filesToTag = pendingOneTimeTaggedFiles.toList()
                pendingOneTimeTaggedFiles.clear()
            }
            if (fix != null) {
                filesToTag.forEach { file ->
                    recordingRepository.saveGpsTagForFile(file, fix)
                }
            }
            _state.value = _state.value.copy(
                currentGpsCoordinate = fix,
                isAcquiringGps = false
            )
        }
    }

    private fun createSegmentAndTagGps(fileFormat: RecordingFileFormat): File {
        val file = recordingRepository.createSegmentFile(fileFormat)
        when (liveGpsTagMode) {
            GpsTagMode.OFF -> Unit
            GpsTagMode.ONE_TIME -> {
                val existingFix = synchronized(sessionGpsLock) {
                    val fix = sessionGpsCoordinate
                    if (fix == null) {
                        pendingOneTimeTaggedFiles.add(file)
                    }
                    fix
                }
                if (existingFix != null) {
                    recordingRepository.saveGpsTagForFile(file, existingFix)
                }
            }
            GpsTagMode.EACH_FILE -> {
                scope.launch {
                    _state.value = _state.value.copy(isAcquiringGps = true)
                    val fix = gpsTagManager.acquireSingleFix()
                    if (fix != null) {
                        recordingRepository.saveGpsTagForFile(file, fix)
                    }
                    _state.value = _state.value.copy(
                        currentGpsCoordinate = fix ?: _state.value.currentGpsCoordinate,
                        isAcquiringGps = false
                    )
                }
            }
        }
        return file
    }

    fun pauseSession() {
        if (!captureRunning) return
        paused = true
        if (_state.value.status != RecordingStatus.PAUSED_LOW_BATTERY) {
            _state.value = _state.value.copy(
                status = RecordingStatus.PAUSED,
                currentLevelDb = RecordingState.MIN_DB
            )
        }
    }

    fun resumeSession() {
        if (!captureRunning) return
        if (batteryMonitor.isBelowThreshold(
                liveBatteryProtectionEnabled,
                liveMinBatteryThresholdPercent
            )
        ) {
            val snap = batteryMonitor.snapshot.value
            _state.value = _state.value.copy(
                errorMessage = "Battery at ${snap.levelPercent}% is still below ${liveMinBatteryThresholdPercent}% limit."
            )
            return
        }
        paused = false
        val current = _state.value
        val currentStatus = when {
            current.currentFileName == null && current.nextScheduleWindowStartMinute != null ->
                RecordingStatus.WAITING_SCHEDULE
            current.currentFileName == null && current.nextCycleCountdownMs > 0L ->
                RecordingStatus.WAITING_INTERVAL
            current.currentFileName == null && liveTriggerConfig.enabled ->
                RecordingStatus.ARMED_TRIGGER
            else -> RecordingStatus.RECORDING
        }
        _state.value = current.copy(status = currentStatus)
    }

    fun requestManualSplit() {
        if (!captureRunning) return
        manualSplitRequested = true
    }

    fun triggerNextCycleNow() {
        if (!captureRunning) return
        triggerNextNowRequested = true
    }

    fun stopSession() {
        val threadToJoin: Thread?
        synchronized(stateLock) {
            if (!captureRunning) return
            captureRunning = false
            paused = false
            threadToJoin = captureThread
        }
        try {
            threadToJoin?.join(2000L)
        } catch (_: InterruptedException) {
        }
        captureThread = null
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    @SuppressLint("MissingPermission")
    private fun runCaptureLoop(quality: AudioQuality, fileFormat: RecordingFileFormat) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        val minBuf = AudioRecord.getMinBufferSize(
            quality.sampleRateHz,
            quality.channelConfig,
            quality.audioEncoding
        )
        if (minBuf <= 0) {
            captureRunning = false
            _state.value = _state.value.copy(
                status = RecordingStatus.IDLE,
                errorMessage = "Audio input format (${quality.shortLabel}) is not supported on this device."
            )
            return
        }

        val frameAlign = quality.bytesPerSampleFrame.coerceAtLeast(2)
        val chunkBytes = ((quality.bytesPerSecond / 20L).toInt() / frameAlign) * frameAlign
        val readBufferSize = maxOf(chunkBytes, frameAlign * 256)
        val audioRecordBufferSize = maxOf(minBuf * 2, readBufferSize * 4)

        var audioRecord: AudioRecord? = null
        var writer: AudioSegmentWriter? = null

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                quality.sampleRateHz,
                quality.channelConfig,
                quality.audioEncoding,
                audioRecordBufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                captureRunning = false
                _state.value = _state.value.copy(
                    status = RecordingStatus.IDLE,
                    errorMessage = "Could not initialize microphone hardware."
                )
                return
            }

            var segmentNumber = 1
            var completedSegments = 0
            var totalSessionPcmBytes = 0L
            val buffer = ByteArray(readBufferSize)
            val waveform = FloatArray(RecordingState.WAVEFORM_BAR_COUNT)
            val spectrogramHistory = ArrayDeque<List<Float>>(SpectrogramAnalyzer.SPECTROGRAM_COLUMN_COUNT)
            val lowCutFilter = LowCutFilter()
            var peakFreqHz = 0
            var peakDb = RecordingState.MIN_DB
            var lastUiEmitTimeMs = 0L
            var isHardwareRecording = false

            fun stopHardwareIfRecording() {
                if (isHardwareRecording) {
                    try {
                        audioRecord.stop()
                    } catch (_: Exception) {
                    }
                    isHardwareRecording = false
                    lowCutFilter.resetState()
                }
            }

            fun finalizeCurrentWriter(): Boolean {
                val activeWriter = writer ?: return false
                writer = null
                val finishedFile = activeWriter.file
                val finishedBytes = activeWriter.pcmBytesWritten
                activeWriter.close()
                return if (finishedBytes > 0L) {
                    completedSegments++
                    val finishedDurationMs = (finishedBytes * 1000L) / quality.bytesPerSecond
                    onSegmentCompleted(finishedFile, finishedDurationMs)
                    true
                } else {
                    try {
                        finishedFile.delete()
                    } catch (_: Exception) {
                    }
                    false
                }
            }

            var lastBatteryRefreshMs = 0L

            fun checkLowBattery(): Boolean {
                val nowElapsed = SystemClock.elapsedRealtime()
                val snap = if (nowElapsed - lastBatteryRefreshMs >= 1500L) {
                    lastBatteryRefreshMs = nowElapsed
                    batteryMonitor.refreshSnapshot()
                } else {
                    batteryMonitor.snapshot.value
                }
                return BatteryPowerEstimator.isBelowThreshold(
                    snapshot = snap,
                    protectionEnabled = liveBatteryProtectionEnabled,
                    thresholdPercent = liveMinBatteryThresholdPercent
                )
            }

            fun waitWhileLowBattery() {
                while (captureRunning) {
                    val lowBattery = checkLowBattery()
                    if (!lowBattery && !paused) {
                        return
                    }
                    stopHardwareIfRecording()
                    waveform.fill(0f)
                    _state.value = _state.value.copy(
                        status = if (lowBattery) {
                            RecordingStatus.PAUSED_LOW_BATTERY
                        } else {
                            RecordingStatus.PAUSED
                        },
                        continuousNoGap = liveContinuousNoGap,
                        schedule = liveSchedule,
                        batteryProtectionEnabled = liveBatteryProtectionEnabled,
                        minBatteryThresholdPercent = liveMinBatteryThresholdPercent,
                        nextScheduleWindowStartMinute = null,
                        segmentNumber = segmentNumber,
                        completedSegmentsInSession = completedSegments,
                        currentSegmentElapsedMs = 0L,
                        totalSessionElapsedMs = (totalSessionPcmBytes * 1000L) / quality.bytesPerSecond,
                        nextCycleCountdownMs = 0L,
                        targetRecordingDurationSeconds = liveRecordingDurationSeconds,
                        targetIntervalSeconds = liveIntervalSeconds,
                        quality = quality,
                        currentFileName = null,
                        currentFileBytes = 0L,
                        amplitudes = waveform.toList(),
                        currentLevelDb = RecordingState.MIN_DB
                    )
                    Thread.sleep(400L)
                }
            }

            fun waitUntilScheduleActive() {
                while (captureRunning) {
                    if (checkLowBattery()) {
                        waitWhileLowBattery()
                        if (!captureRunning) return
                    }
                    val cal = Calendar.getInstance()
                    val schedule = liveSchedule
                    val activeNow = schedule.isActiveAt(cal)
                    if (activeNow && !paused) {
                        return
                    }
                    stopHardwareIfRecording()
                    waveform.fill(0f)
                    val waitMs = if (activeNow) 0L else schedule.millisUntilNextWindow(cal)
                    val nextStartMin = if (activeNow) null else schedule.nextWindowStartMinute(cal)
                    _state.value = _state.value.copy(
                        status = if (paused) {
                            RecordingStatus.PAUSED
                        } else {
                            RecordingStatus.WAITING_SCHEDULE
                        },
                        continuousNoGap = liveContinuousNoGap,
                        schedule = schedule,
                        batteryProtectionEnabled = liveBatteryProtectionEnabled,
                        minBatteryThresholdPercent = liveMinBatteryThresholdPercent,
                        nextScheduleWindowStartMinute = nextStartMin,
                        segmentNumber = segmentNumber,
                        completedSegmentsInSession = completedSegments,
                        currentSegmentElapsedMs = 0L,
                        totalSessionElapsedMs = (totalSessionPcmBytes * 1000L) / quality.bytesPerSecond,
                        nextCycleCountdownMs = waitMs,
                        targetRecordingDurationSeconds = liveRecordingDurationSeconds,
                        targetIntervalSeconds = liveIntervalSeconds,
                        quality = quality,
                        currentFileName = null,
                        currentFileBytes = 0L,
                        amplitudes = waveform.toList(),
                        currentLevelDb = RecordingState.MIN_DB
                    )
                    Thread.sleep(200L)
                }
            }

            // Wait if session started outside the selected schedule window or low battery
            waitUntilScheduleActive()
            if (!captureRunning) return

            var currentFile: File? = null
            var ringBufferPreSec = liveTriggerConfig.preRecordSeconds.coerceIn(
                0,
                AcousticTriggerConfig.MAX_PRE_RECORD_SECONDS
            )
            var ringBuffer = PcmRingBuffer(
                capacityBytes = (ringBufferPreSec.toLong() * quality.bytesPerSecond).toInt(),
                frameAlignmentBytes = frameAlign
            )
            var silenceElapsedMs = 0L

            fun openNextWriter(): Boolean {
                val file = createSegmentAndTagGps(fileFormat)
                currentFile = file
                writer = AudioSegmentWriterFactory.create(file, quality, fileFormat)
                return true
            }

            fun writePcmWithSegmentPolicy(
                pcmBytes: ByteArray,
                byteCount: Int,
                triggerModeActive: Boolean
            ): Boolean {
                val clampedDurationSec = liveRecordingDurationSeconds.coerceIn(
                    RecorderSettings.MIN_RECORDING_DURATION_SECONDS,
                    RecorderSettings.MAX_RECORDING_DURATION_SECONDS
                )
                val targetSegmentBytes = clampedDurationSec.toLong() * quality.bytesPerSecond
                var offset = 0
                var remaining = byteCount

                while (remaining > 0 && captureRunning) {
                    val activeWriter = writer ?: break
                    val bytesWrittenSoFar = activeWriter.pcmBytesWritten
                    val roomInSegment = (targetSegmentBytes - bytesWrittenSoFar).coerceAtLeast(0L)

                    val durationReached = roomInSegment < frameAlign
                    val triggeredManualSplit = manualSplitRequested

                    if (triggeredManualSplit || durationReached) {
                        manualSplitRequested = false
                        finalizeCurrentWriter()
                        currentFile = null

                        // In Trigger Mode, long active events roll over seamlessly without waiting an interval
                        val shouldWaitInterval = !triggerModeActive && !triggeredManualSplit && !liveContinuousNoGap
                        if (shouldWaitInterval) {
                            stopHardwareIfRecording()
                            waveform.fill(0f)
                            triggerNextNowRequested = false

                            var waitElapsedMs = 0L
                            var lastTickMs = SystemClock.elapsedRealtime()

                            while (captureRunning && !triggerNextNowRequested && !liveContinuousNoGap) {
                                if (checkLowBattery()) {
                                    waitWhileLowBattery()
                                    lastTickMs = SystemClock.elapsedRealtime()
                                    if (!captureRunning) break
                                }

                                val nowTickMs = SystemClock.elapsedRealtime()
                                val deltaMs = (nowTickMs - lastTickMs).coerceAtLeast(0L)
                                lastTickMs = nowTickMs

                                if (!paused) {
                                    waitElapsedMs += deltaMs
                                }

                                val intervalTargetMs = liveIntervalSeconds.toLong() * 1000L
                                val waitRemaining = (intervalTargetMs - waitElapsedMs).coerceAtLeast(0L)

                                _state.value = _state.value.copy(
                                    status = if (paused) RecordingStatus.PAUSED else RecordingStatus.WAITING_INTERVAL,
                                    continuousNoGap = liveContinuousNoGap,
                                    schedule = liveSchedule,
                                    batteryProtectionEnabled = liveBatteryProtectionEnabled,
                                    minBatteryThresholdPercent = liveMinBatteryThresholdPercent,
                                    nextScheduleWindowStartMinute = null,
                                    segmentNumber = segmentNumber,
                                    completedSegmentsInSession = completedSegments,
                                    currentSegmentElapsedMs = 0L,
                                    totalSessionElapsedMs = (totalSessionPcmBytes * 1000L) / quality.bytesPerSecond,
                                    nextCycleCountdownMs = waitRemaining,
                                    targetRecordingDurationSeconds = liveRecordingDurationSeconds,
                                    targetIntervalSeconds = liveIntervalSeconds,
                                    currentFileName = null,
                                    currentFileBytes = 0L,
                                    amplitudes = waveform.toList(),
                                    currentLevelDb = RecordingState.MIN_DB,
                                    triggerConfig = liveTriggerConfig.normalizedForSampleRate(quality.sampleRateHz),
                                    triggerBandLevelDb = RecordingState.MIN_DB,
                                    triggerSilenceElapsedMs = 0L,
                                    triggerPreRecordBufferedMs = 0L
                                )

                                if (waitRemaining == 0L) break
                                Thread.sleep(minOf(150L, waitRemaining))
                            }

                            if (!captureRunning) break
                            triggerNextNowRequested = false
                        }

                        if (checkLowBattery()) {
                            stopHardwareIfRecording()
                            ringBuffer.clear()
                            silenceElapsedMs = 0L
                            waitWhileLowBattery()
                            if (!captureRunning) break
                        }

                        if (!liveSchedule.isActiveAt(Calendar.getInstance())) {
                            stopHardwareIfRecording()
                            ringBuffer.clear()
                            silenceElapsedMs = 0L
                            waitUntilScheduleActive()
                            if (!captureRunning) break
                        }

                        if (!isHardwareRecording) {
                            audioRecord.startRecording()
                            isHardwareRecording = true
                        }

                        segmentNumber++
                        openNextWriter()
                        if (shouldWaitInterval) {
                            return false
                        }
                        continue
                    }

                    val toWrite = minOf(remaining.toLong(), roomInSegment).toInt()
                    val alignedToWrite = (toWrite / frameAlign) * frameAlign
                    if (alignedToWrite > 0) {
                        if (offset == 0 && alignedToWrite == pcmBytes.size) {
                            activeWriter.writePcm(pcmBytes, alignedToWrite)
                        } else if (offset == 0) {
                            activeWriter.writePcm(pcmBytes, alignedToWrite)
                        } else {
                            activeWriter.writePcm(
                                pcmBytes.copyOfRange(offset, offset + alignedToWrite),
                                alignedToWrite
                            )
                        }
                        totalSessionPcmBytes += alignedToWrite
                        offset += alignedToWrite
                        remaining -= alignedToWrite
                    }
                }
                return true
            }

            if (!liveTriggerConfig.enabled) {
                openNextWriter()
            }

            _state.value = _state.value.copy(
                status = if (liveTriggerConfig.enabled) {
                    RecordingStatus.ARMED_TRIGGER
                } else {
                    RecordingStatus.RECORDING
                },
                continuousNoGap = liveContinuousNoGap,
                schedule = liveSchedule,
                batteryProtectionEnabled = liveBatteryProtectionEnabled,
                minBatteryThresholdPercent = liveMinBatteryThresholdPercent,
                nextScheduleWindowStartMinute = null,
                nextCycleCountdownMs = 0L,
                segmentNumber = segmentNumber,
                fileFormat = fileFormat,
                currentFileName = currentFile?.name,
                currentFileBytes = writer?.estimatedFileBytes ?: 0L,
                triggerConfig = liveTriggerConfig.normalizedForSampleRate(quality.sampleRateHz),
                triggerSilenceElapsedMs = 0L,
                triggerPreRecordBufferedMs = 0L
            )

            audioRecord.startRecording()
            isHardwareRecording = true

            while (captureRunning) {
                if (checkLowBattery()) {
                    stopHardwareIfRecording()
                    val savedSomething = finalizeCurrentWriter()
                    currentFile = null
                    ringBuffer.clear()
                    silenceElapsedMs = 0L
                    waitWhileLowBattery()
                    if (!captureRunning) break
                    waitUntilScheduleActive()
                    if (!captureRunning) break
                    if (savedSomething) {
                        segmentNumber++
                    }
                    if (!liveTriggerConfig.enabled) {
                        openNextWriter()
                    }
                    audioRecord.startRecording()
                    isHardwareRecording = true
                    _state.value = _state.value.copy(
                        status = if (liveTriggerConfig.enabled) {
                            RecordingStatus.ARMED_TRIGGER
                        } else {
                            RecordingStatus.RECORDING
                        },
                        continuousNoGap = liveContinuousNoGap,
                        schedule = liveSchedule,
                        batteryProtectionEnabled = liveBatteryProtectionEnabled,
                        minBatteryThresholdPercent = liveMinBatteryThresholdPercent,
                        nextScheduleWindowStartMinute = null,
                        nextCycleCountdownMs = 0L,
                        segmentNumber = segmentNumber,
                        fileFormat = fileFormat,
                        currentFileName = currentFile?.name,
                        currentFileBytes = writer?.estimatedFileBytes ?: 0L,
                        triggerConfig = liveTriggerConfig.normalizedForSampleRate(quality.sampleRateHz),
                        triggerSilenceElapsedMs = 0L,
                        triggerPreRecordBufferedMs = 0L
                    )
                    continue
                }

                if (paused) {
                    stopHardwareIfRecording()
                    Thread.sleep(60L)
                    continue
                }

                if (!liveSchedule.isActiveAt(Calendar.getInstance())) {
                    stopHardwareIfRecording()
                    val savedSomething = finalizeCurrentWriter()
                    currentFile = null
                    ringBuffer.clear()
                    silenceElapsedMs = 0L
                    waitUntilScheduleActive()
                    if (!captureRunning) break
                    if (savedSomething) {
                        segmentNumber++
                    }
                    if (!liveTriggerConfig.enabled) {
                        openNextWriter()
                    }
                    audioRecord.startRecording()
                    isHardwareRecording = true
                    _state.value = _state.value.copy(
                        status = if (liveTriggerConfig.enabled) {
                            RecordingStatus.ARMED_TRIGGER
                        } else {
                            RecordingStatus.RECORDING
                        },
                        continuousNoGap = liveContinuousNoGap,
                        schedule = liveSchedule,
                        batteryProtectionEnabled = liveBatteryProtectionEnabled,
                        minBatteryThresholdPercent = liveMinBatteryThresholdPercent,
                        nextScheduleWindowStartMinute = null,
                        nextCycleCountdownMs = 0L,
                        segmentNumber = segmentNumber,
                        fileFormat = fileFormat,
                        currentFileName = currentFile?.name,
                        currentFileBytes = writer?.estimatedFileBytes ?: 0L,
                        triggerConfig = liveTriggerConfig.normalizedForSampleRate(quality.sampleRateHz),
                        triggerSilenceElapsedMs = 0L,
                        triggerPreRecordBufferedMs = 0L
                    )
                    continue
                }

                if (!isHardwareRecording) {
                    audioRecord.startRecording()
                    isHardwareRecording = true
                }

                val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                if (bytesRead <= 0) {
                    if (bytesRead < 0) Thread.sleep(20L)
                    continue
                }

                val alignedBytesRead = (bytesRead / frameAlign) * frameAlign
                if (alignedBytesRead <= 0) continue

                val activeLowCutHz = liveLowCutFilterHz
                lowCutFilter.configure(activeLowCutHz, quality.sampleRateHz)
                lowCutFilter.processPcm16LeInPlace(buffer, alignedBytesRead)

                val triggerCfgNow = liveTriggerConfig.normalizedForSampleRate(quality.sampleRateHz)
                if (triggerCfgNow.preRecordSeconds != ringBufferPreSec) {
                    ringBufferPreSec = triggerCfgNow.preRecordSeconds
                    ringBuffer = PcmRingBuffer(
                        capacityBytes = (ringBufferPreSec.toLong() * quality.bytesPerSecond).toInt(),
                        frameAlignmentBytes = frameAlign
                    )
                }

                val db = computeRmsDb(buffer, alignedBytesRead)
                val triggerEval = if (triggerCfgNow.enabled) {
                    AcousticTriggerEvaluator.evaluate(
                        pcmBytes = buffer,
                        lengthBytes = alignedBytesRead,
                        sampleRateHz = quality.sampleRateHz,
                        config = triggerCfgNow
                    )
                } else {
                    null
                }

                if (writer == null) {
                    // Armed background trigger standby
                    if (!triggerCfgNow.enabled) {
                        triggerNextNowRequested = false
                        silenceElapsedMs = 0L
                        ringBuffer.clear()
                        openNextWriter()
                        writePcmWithSegmentPolicy(buffer, alignedBytesRead, triggerModeActive = false)
                    } else if (triggerEval?.isTriggered == true || triggerNextNowRequested) {
                        triggerNextNowRequested = false
                        silenceElapsedMs = 0L
                        openNextWriter()
                        val preRecordPcm = ringBuffer.drainChronological()
                        if (preRecordPcm.isNotEmpty()) {
                            writePcmWithSegmentPolicy(preRecordPcm, preRecordPcm.size, triggerModeActive = true)
                        }
                        if (captureRunning && writer != null) {
                            writePcmWithSegmentPolicy(buffer, alignedBytesRead, triggerModeActive = true)
                        }
                    } else {
                        ringBuffer.write(buffer, alignedBytesRead)
                    }
                } else {
                    // Actively writing triggered or continuous/interval segment
                    val wroteCleanly = writePcmWithSegmentPolicy(
                        pcmBytes = buffer,
                        byteCount = alignedBytesRead,
                        triggerModeActive = triggerCfgNow.enabled
                    )
                    if (wroteCleanly && triggerCfgNow.enabled && writer != null) {
                        val chunkDurationMs = (alignedBytesRead.toLong() * 1000L) /
                            quality.bytesPerSecond.coerceAtLeast(1L)
                        if (triggerEval?.isTriggered == true) {
                            silenceElapsedMs = 0L
                        } else {
                            silenceElapsedMs += chunkDurationMs
                        }
                        val autoStopTimeoutMs = triggerCfgNow.autoStopSilenceMinutes.toLong() * 60_000L
                        if (silenceElapsedMs >= autoStopTimeoutMs) {
                            val savedSomething = finalizeCurrentWriter()
                            currentFile = null
                            if (savedSomething) {
                                segmentNumber++
                            }
                            silenceElapsedMs = 0L
                            ringBuffer.clear()
                        }
                    } else if (!triggerCfgNow.enabled) {
                        silenceElapsedMs = 0L
                    }
                }

                if (!captureRunning) break

                if (isHardwareRecording) {
                    if (db > peakDb) peakDb = db
                    val normalized = ((db - RecordingState.MIN_DB) / (0f - RecordingState.MIN_DB))
                        .coerceIn(0.02f, 1f)

                    System.arraycopy(waveform, 1, waveform, 0, waveform.size - 1)
                    waveform[waveform.size - 1] = normalized

                    val modeNow = liveVisualizerMode
                    if (modeNow == VisualizerMode.SPECTROGRAM) {
                        val specCol = SpectrogramAnalyzer.analyzePcmChunk(
                            pcmBytes = buffer,
                            lengthBytes = alignedBytesRead,
                            sampleRateHz = quality.sampleRateHz
                        )
                        if (spectrogramHistory.size >= SpectrogramAnalyzer.SPECTROGRAM_COLUMN_COUNT) {
                            spectrogramHistory.removeFirst()
                        }
                        spectrogramHistory.addLast(specCol.bins)
                        peakFreqHz = specCol.peakFrequencyHz
                    } else {
                        if (spectrogramHistory.isNotEmpty()) {
                            spectrogramHistory.clear()
                        }
                        peakFreqHz = 0
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastUiEmitTimeMs >= 50L) {
                        lastUiEmitTimeMs = now
                        val segPcmBytes = writer?.pcmBytesWritten ?: 0L
                        val segFileBytes = writer?.estimatedFileBytes ?: 0L
                        val segElapsedMs = (segPcmBytes * 1000L) / quality.bytesPerSecond
                        val totalElapsedMs = (totalSessionPcmBytes * 1000L) / quality.bytesPerSecond
                        val activeStatus = when {
                            paused -> RecordingStatus.PAUSED
                            writer == null && triggerCfgNow.enabled -> RecordingStatus.ARMED_TRIGGER
                            else -> RecordingStatus.RECORDING
                        }
                        _state.value = _state.value.copy(
                            status = activeStatus,
                            continuousNoGap = liveContinuousNoGap,
                            schedule = liveSchedule,
                            batteryProtectionEnabled = liveBatteryProtectionEnabled,
                            minBatteryThresholdPercent = liveMinBatteryThresholdPercent,
                            nextScheduleWindowStartMinute = null,
                            segmentNumber = segmentNumber,
                            completedSegmentsInSession = completedSegments,
                            currentSegmentElapsedMs = segElapsedMs,
                            totalSessionElapsedMs = totalElapsedMs,
                            nextCycleCountdownMs = 0L,
                            targetRecordingDurationSeconds = liveRecordingDurationSeconds,
                            targetIntervalSeconds = liveIntervalSeconds,
                            quality = quality,
                            fileFormat = fileFormat,
                            visualizerMode = modeNow,
                            currentFileName = currentFile?.name,
                            currentFileBytes = segFileBytes,
                            amplitudes = waveform.toList(),
                            spectrogramColumns = if (modeNow == VisualizerMode.SPECTROGRAM) {
                                spectrogramHistory.toList()
                            } else {
                                emptyList()
                            },
                            peakFrequencyHz = peakFreqHz,
                            currentLevelDb = db,
                            peakLevelDb = peakDb,
                            lowCutFilterHz = activeLowCutHz,
                            triggerConfig = triggerCfgNow,
                            triggerBandLevelDb = triggerEval?.bandLevelDb ?: db,
                            triggerSilenceElapsedMs = if (writer != null && triggerCfgNow.enabled) {
                                silenceElapsedMs
                            } else {
                                0L
                            },
                            triggerPreRecordBufferedMs = if (writer == null && triggerCfgNow.enabled) {
                                ringBuffer.bufferedDurationMs(quality.bytesPerSecond)
                            } else {
                                0L
                            }
                        )
                    }
                }
            }

            finalizeCurrentWriter()
            currentFile = null
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                errorMessage = e.localizedMessage ?: "Recording stopped unexpectedly."
            )
        } finally {
            captureRunning = false
            paused = false
            manualSplitRequested = false
            triggerNextNowRequested = false
            synchronized(sessionGpsLock) {
                sessionGpsCoordinate = null
                pendingOneTimeTaggedFiles.clear()
            }
            batteryMonitor.clearSessionStart()

            try {
                writer?.close()
            } catch (_: Exception) {
            }

            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            } catch (_: Exception) {
            }
            try {
                audioRecord?.release()
            } catch (_: Exception) {
            }

            scope.launch {
                recordingRepository.refresh()
            }

            val lastCompletedName = _state.value.lastCompletedSegmentName
            val lastCompletedDur = _state.value.lastCompletedSegmentDurationMs
            _state.value = RecordingState(
                status = RecordingStatus.IDLE,
                continuousNoGap = liveContinuousNoGap,
                schedule = liveSchedule,
                batteryProtectionEnabled = liveBatteryProtectionEnabled,
                minBatteryThresholdPercent = liveMinBatteryThresholdPercent,
                targetRecordingDurationSeconds = liveRecordingDurationSeconds,
                targetIntervalSeconds = liveIntervalSeconds,
                quality = activeQuality,
                fileFormat = activeFileFormat,
                visualizerMode = liveVisualizerMode,
                gpsTagMode = liveGpsTagMode,
                lowCutFilterHz = liveLowCutFilterHz,
                triggerConfig = liveTriggerConfig.normalizedForSampleRate(activeQuality.sampleRateHz),
                lastCompletedSegmentName = lastCompletedName,
                lastCompletedSegmentDurationMs = lastCompletedDur
            )
        }
    }

    private fun onSegmentCompleted(file: File, durationMs: Long) {
        _state.value = _state.value.copy(
            lastCompletedSegmentName = file.name,
            lastCompletedSegmentDurationMs = durationMs
        )
        scope.launch {
            recordingRepository.refresh(activeRecordingPath = null)
        }
    }

    private fun computeRmsDb(buffer: ByteArray, length: Int): Float {
        if (length < 2) return RecordingState.MIN_DB
        var sumSquares = 0.0
        var sampleCount = 0
        var i = 0
        while (i + 1 < length) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toDouble()
            val norm = sample / 32768.0
            sumSquares += norm * norm
            sampleCount++
            i += 2
        }
        if (sampleCount == 0) return RecordingState.MIN_DB
        val rms = sqrt(sumSquares / sampleCount)
        if (rms <= 1e-6) return RecordingState.MIN_DB
        val db = (20.0 * log10(rms)).toFloat()
        return db.coerceIn(RecordingState.MIN_DB, 0f)
    }
}
