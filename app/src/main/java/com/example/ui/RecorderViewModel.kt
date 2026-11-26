package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.WaveLoopApplication
import com.example.audio.AcousticTriggerConfig
import com.example.audio.AudioCapabilities
import com.example.audio.AudioCapabilityProber
import com.example.audio.AudioPlaybackManager
import com.example.audio.AudioQuality
import com.example.audio.BatteryMonitor
import com.example.audio.BatteryPowerEstimator
import com.example.audio.BatterySnapshot
import com.example.audio.BatteryUsageEstimate
import com.example.audio.GpsTagMode
import com.example.audio.PlaybackState
import com.example.audio.RecordingEngine
import com.example.audio.RecordingFileFormat
import com.example.audio.RecordingState
import com.example.audio.RecordingStatus
import com.example.audio.RecordingVisualizationData
import com.example.audio.RecordingVisualizationExtractor
import com.example.audio.StorageEstimator
import com.example.audio.StorageMonitor
import com.example.audio.StorageUsageEstimate
import com.example.audio.TimeZoomMath
import com.example.audio.VisualizerMode
import com.example.data.RecorderPreferencesRepository
import com.example.data.RecorderSettings
import com.example.data.RecordingFile
import com.example.data.RecordingRepository
import com.example.data.RecordingSchedule
import com.example.service.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ArchiveSortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    LONGEST("Longest"),
    LARGEST("Largest")
}

data class ArchiveUiState(
    val recordings: List<RecordingFile> = emptyList(),
    val totalFiles: Int = 0,
    val totalDurationMs: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val searchQuery: String = "",
    val sortOrder: ArchiveSortOrder = ArchiveSortOrder.NEWEST
)

data class PlaybackInspectorState(
    val inspectedRecording: RecordingFile? = null,
    val previewPositionMs: Long = 0L,
    val timeRange: ClosedFloatingPointRange<Float> = 0f..1f,
    val visualization: RecordingVisualizationData? = null,
    val isLoadingVisualization: Boolean = false,
    val isRefiningTimeSlice: Boolean = false
)

private data class VisualizationMerge(
    val overview: RecordingVisualizationData?,
    val detailed: RecordingVisualizationData?,
    val loadingPath: String?,
    val refiningPath: String?,
    val timeRange: ClosedFloatingPointRange<Float>
)

class RecorderViewModel(
    private val preferencesRepository: RecorderPreferencesRepository,
    private val recordingRepository: RecordingRepository,
    private val capabilityProber: AudioCapabilityProber,
    private val batteryMonitor: BatteryMonitor,
    private val storageMonitor: StorageMonitor,
    private val recordingEngine: RecordingEngine,
    private val playbackManager: AudioPlaybackManager
) : ViewModel() {

    val recordingState: StateFlow<RecordingState> = recordingEngine.state

    val audioCapabilities: StateFlow<AudioCapabilities> = capabilityProber.capabilities

    val batterySnapshot: StateFlow<BatterySnapshot> = batteryMonitor.snapshot

    val settings: StateFlow<RecorderSettings> = preferencesRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = RecorderSettings()
        )

    val batteryUsageEstimate: StateFlow<BatteryUsageEstimate> = combine(
        batteryMonitor.snapshot,
        recordingEngine.state,
        settings
    ) { snapshot, recState, storedSettings ->
        val activeQuality = if (recState.status == RecordingStatus.IDLE) {
            storedSettings.quality
        } else {
            recState.quality
        }
        val activeVisualizerMode = if (recState.status == RecordingStatus.IDLE) {
            storedSettings.visualizerMode
        } else {
            recState.visualizerMode
        }
        val activeGpsMode = if (recState.status == RecordingStatus.IDLE) {
            storedSettings.gpsTagMode
        } else {
            recState.gpsTagMode
        }
        BatteryPowerEstimator.estimate(
            snapshot = snapshot,
            protectionEnabled = recState.batteryProtectionEnabled,
            thresholdPercent = recState.minBatteryThresholdPercent,
            recordingDurationSeconds = recState.targetRecordingDurationSeconds,
            intervalSeconds = recState.targetIntervalSeconds,
            continuousNoGap = recState.continuousNoGap,
            sampleRateHz = activeQuality.sampleRateHz,
            schedule = recState.schedule,
            visualizerMode = activeVisualizerMode,
            gpsTagMode = activeGpsMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = BatteryPowerEstimator.estimate(
            snapshot = batteryMonitor.snapshot.value,
            protectionEnabled = true,
            thresholdPercent = RecorderSettings.DEFAULT_BATTERY_THRESHOLD_PERCENT,
            recordingDurationSeconds = RecorderSettings.DEFAULT_RECORDING_DURATION_SECONDS,
            intervalSeconds = RecorderSettings.DEFAULT_INTERVAL_SECONDS,
            continuousNoGap = false,
            sampleRateHz = AudioQuality.CD_44K.sampleRateHz,
            schedule = RecordingSchedule()
        )
    )

    val storageUsageEstimate: StateFlow<StorageUsageEstimate> = combine(
        storageMonitor.snapshot,
        recordingEngine.state,
        settings
    ) { snapshot, recState, storedSettings ->
        val isIdle = recState.status == RecordingStatus.IDLE
        val activeQuality = if (isIdle) storedSettings.quality else recState.quality
        val activeFormat = if (isIdle) storedSettings.fileFormat else recState.fileFormat
        StorageEstimator.estimate(
            snapshot = snapshot,
            bytesPerSecond = activeQuality.bytesPerSecondFor(activeFormat),
            recordingDurationSeconds = recState.targetRecordingDurationSeconds,
            intervalSeconds = recState.targetIntervalSeconds,
            continuousNoGap = recState.continuousNoGap,
            schedule = recState.schedule
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = StorageEstimator.estimate(
            snapshot = storageMonitor.snapshot.value,
            bytesPerSecond = AudioQuality.CD_44K.bytesPerSecondFor(RecordingFileFormat.WAV),
            recordingDurationSeconds = RecorderSettings.DEFAULT_RECORDING_DURATION_SECONDS,
            intervalSeconds = RecorderSettings.DEFAULT_INTERVAL_SECONDS,
            continuousNoGap = false,
            schedule = RecordingSchedule()
        )
    )

    val playbackState: StateFlow<PlaybackState> = playbackManager.state

    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(ArchiveSortOrder.NEWEST)
    private val _transientMessage = MutableStateFlow<String?>(null)
    val transientMessage: StateFlow<String?> = _transientMessage

    private val inspectedFilePath = MutableStateFlow<String?>(null)
    private val previewPositionMs = MutableStateFlow(0L)
    private val playbackTimeRange = MutableStateFlow(0f..1f)
    private val activeVisualization = MutableStateFlow<RecordingVisualizationData?>(null)
    private val detailedSliceVisualization = MutableStateFlow<RecordingVisualizationData?>(null)
    private val loadingVisualizationPath = MutableStateFlow<String?>(null)
    private val refiningSlicePath = MutableStateFlow<String?>(null)
    private val visualizationCache = object : LinkedHashMap<String, RecordingVisualizationData>(10, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RecordingVisualizationData>?): Boolean {
            return size > 8
        }
    }
    private var visualizationJob: Job? = null
    private var sliceExtractionJob: Job? = null

    val archiveUiState: StateFlow<ArchiveUiState> = combine(
        recordingRepository.recordings,
        searchQuery,
        sortOrder
    ) { allFiles, query, order ->
        val filtered = if (query.isBlank()) {
            allFiles
        } else {
            val q = query.trim().lowercase()
            allFiles.filter { it.displayName.lowercase().contains(q) }
        }
        val sorted = when (order) {
            ArchiveSortOrder.NEWEST -> filtered.sortedByDescending { it.createdAtMs }
            ArchiveSortOrder.OLDEST -> filtered.sortedBy { it.createdAtMs }
            ArchiveSortOrder.LONGEST -> filtered.sortedByDescending { it.durationMs }
            ArchiveSortOrder.LARGEST -> filtered.sortedByDescending { it.sizeBytes }
        }
        ArchiveUiState(
            recordings = sorted,
            totalFiles = allFiles.size,
            totalDurationMs = allFiles.sumOf { it.durationMs },
            totalSizeBytes = allFiles.sumOf { it.sizeBytes },
            searchQuery = query,
            sortOrder = order
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ArchiveUiState()
    )

    private val visualizationStateFlow = combine(
        activeVisualization,
        detailedSliceVisualization,
        loadingVisualizationPath,
        refiningSlicePath,
        playbackTimeRange
    ) { overview, detailed, loadingPath, refiningPath, timeRange ->
        VisualizationMerge(overview, detailed, loadingPath, refiningPath, timeRange)
    }

    val playbackInspectorState: StateFlow<PlaybackInspectorState> = combine(
        recordingRepository.recordings,
        playbackManager.state,
        inspectedFilePath,
        previewPositionMs,
        visualizationStateFlow
    ) { allFiles, playback, selectedPath, previewPos, vizMerge ->
        val activeRec = playback.activeFilePath?.let { p -> allFiles.firstOrNull { it.path == p } }
        val selectedRec = selectedPath?.let { p -> allFiles.firstOrNull { it.path == p } }
        val target = activeRec ?: selectedRec ?: allFiles.firstOrNull()
        val timeRange = vizMerge.timeRange
        val isZoomed = (timeRange.endInclusive - timeRange.start) < 0.995f
        val overview = vizMerge.overview?.takeIf { target != null && it.filePath == target.path }
        val detailed = vizMerge.detailed?.takeIf {
            target != null &&
                it.filePath == target.path &&
                isZoomed &&
                it.startFraction <= timeRange.start + 0.004f &&
                it.endFraction >= timeRange.endInclusive - 0.004f
        }
        val effectiveViz = detailed ?: overview
        PlaybackInspectorState(
            inspectedRecording = target,
            previewPositionMs = previewPos,
            timeRange = timeRange,
            visualization = effectiveViz,
            isLoadingVisualization = target != null && vizMerge.loadingPath == target.path && overview == null,
            isRefiningTimeSlice = target != null && vizMerge.refiningPath == target.path && isZoomed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = PlaybackInspectorState()
    )

    private var intervalSaveJob: Job? = null
    private var durationSaveJob: Job? = null
    private var scheduleSaveJob: Job? = null
    private var batteryThresholdSaveJob: Job? = null
    private var triggerSaveJob: Job? = null

    init {
        viewModelScope.launch {
            val initialSettings = preferencesRepository.settingsFlow.first()
            recordingEngine.syncIdleSettings(initialSettings)
        }
        viewModelScope.launch {
            recordingRepository.recordings.collect {
                storageMonitor.refresh()
            }
        }
        viewModelScope.launch {
            recordingEngine.state
                .map { it.status == RecordingStatus.RECORDING }
                .distinctUntilChanged()
                .collect { isRecording ->
                    storageMonitor.refresh()
                    while (isRecording && isActive) {
                        delay(5000L)
                        if (recordingEngine.state.value.status != RecordingStatus.RECORDING) {
                            break
                        }
                        storageMonitor.refresh()
                    }
                }
        }
        viewModelScope.launch {
            playbackInspectorState
                .map { it.inspectedRecording }
                .distinctUntilChanged { old, new ->
                    old?.path == new?.path && old?.sizeBytes == new?.sizeBytes
                }
                .collect { target ->
                    playbackTimeRange.value = 0f..1f
                    detailedSliceVisualization.value = null
                    sliceExtractionJob?.cancel()
                    refiningSlicePath.value = null
                    if (target != null) {
                        ensureVisualizationLoaded(target)
                    } else {
                        activeVisualization.value = null
                    }
                }
        }
        probeAudioCapabilities()
        refreshRecordings()
    }

    private fun ensureVisualizationLoaded(recording: RecordingFile) {
        val cacheKey = "${recording.path}|${recording.sizeBytes}|${recording.createdAtMs}"
        val cached = synchronized(visualizationCache) { visualizationCache[cacheKey] }
        if (cached != null) {
            activeVisualization.value = cached
            loadingVisualizationPath.value = null
            return
        }
        visualizationJob?.cancel()
        loadingVisualizationPath.value = recording.path
        visualizationJob = viewModelScope.launch {
            val extracted = withContext(Dispatchers.IO) {
                RecordingVisualizationExtractor.extract(recording.file)
            }
            if (extracted != null) {
                synchronized(visualizationCache) {
                    visualizationCache[cacheKey] = extracted
                }
                if (inspectedFilePath.value == null ||
                    inspectedFilePath.value == recording.path ||
                    playbackManager.state.value.activeFilePath == recording.path ||
                    recordingRepository.recordings.value.firstOrNull()?.path == recording.path
                ) {
                    activeVisualization.value = extracted
                }
            }
            if (loadingVisualizationPath.value == recording.path) {
                loadingVisualizationPath.value = null
            }
        }
    }

    fun probeAudioCapabilities() {
        viewModelScope.launch {
            val isActive = recordingEngine.state.value.status != RecordingStatus.IDLE
            val caps = capabilityProber.probe(isRecordingActive = isActive)
            val currentSettings = preferencesRepository.settingsFlow.first()
            val supportedMatch = caps.supportedRates.any {
                it.sampleRateHz == currentSettings.quality.sampleRateHz
            }
            if (!supportedMatch && recordingEngine.state.value.status == RecordingStatus.IDLE) {
                val fallback = caps.closestSupported(currentSettings.quality.sampleRateHz)
                preferencesRepository.setAudioQuality(fallback)
            }
        }
    }

    fun refreshRecordings() {
        viewModelScope.launch {
            recordingRepository.refresh()
            storageMonitor.refresh()
        }
    }

    fun refreshStorage() {
        storageMonitor.refresh()
    }

    fun startRecording(context: Context) {
        val recState = recordingEngine.state.value
        if (batteryMonitor.isBelowThreshold(
                recState.batteryProtectionEnabled,
                recState.minBatteryThresholdPercent
            )
        ) {
            val level = batteryMonitor.snapshot.value.levelPercent
            _transientMessage.value =
                "Recording blocked: Battery at $level% is below ${recState.minBatteryThresholdPercent}% limit."
            return
        }
        playbackManager.stop()
        RecordingService.start(context)
    }

    fun pauseRecording(context: Context) {
        RecordingService.sendAction(context, RecordingService.ACTION_PAUSE)
    }

    fun resumeRecording(context: Context) {
        RecordingService.sendAction(context, RecordingService.ACTION_RESUME)
    }

    fun splitSegmentNow(context: Context) {
        RecordingService.sendAction(context, RecordingService.ACTION_SPLIT)
    }

    fun triggerNextCycleNow(context: Context) {
        RecordingService.sendAction(context, RecordingService.ACTION_TRIGGER_NOW)
    }

    fun stopRecording(context: Context) {
        RecordingService.sendAction(context, RecordingService.ACTION_STOP)
    }

    fun setIntervalSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(
            RecorderSettings.MIN_INTERVAL_SECONDS,
            RecorderSettings.MAX_INTERVAL_SECONDS
        )
        recordingEngine.updateIntervalLive(clamped)
        intervalSaveJob?.cancel()
        intervalSaveJob = viewModelScope.launch {
            preferencesRepository.setIntervalSeconds(clamped)
        }
    }

    fun setRecordingDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(
            RecorderSettings.MIN_RECORDING_DURATION_SECONDS,
            RecorderSettings.MAX_RECORDING_DURATION_SECONDS
        )
        recordingEngine.updateRecordingDurationLive(clamped)
        durationSaveJob?.cancel()
        durationSaveJob = viewModelScope.launch {
            preferencesRepository.setRecordingDurationSeconds(clamped)
        }
    }

    fun setContinuousNoGap(enabled: Boolean) {
        val triggerActive = recordingEngine.state.value.triggerConfig.enabled ||
            settings.value.acousticTrigger.enabled
        if (!enabled && triggerActive) {
            _transientMessage.value =
                "Continuous (No Interval Wait) stays ON while Acoustic Trigger is enabled."
            return
        }
        recordingEngine.updateContinuousNoGapLive(enabled)
        viewModelScope.launch {
            preferencesRepository.setContinuousNoGap(enabled)
        }
    }

    fun setAudioQuality(quality: AudioQuality) {
        if (recordingEngine.state.value.status != RecordingStatus.IDLE) {
            _transientMessage.value = "Stop recording to change sample rate."
            return
        }
        viewModelScope.launch {
            preferencesRepository.setAudioQuality(quality)
            recordingEngine.syncIdleSettings(preferencesRepository.settingsFlow.first())
        }
    }

    fun setFileFormat(format: RecordingFileFormat) {
        if (recordingEngine.state.value.status != RecordingStatus.IDLE) {
            _transientMessage.value = "Stop recording to change audio format."
            return
        }
        viewModelScope.launch {
            preferencesRepository.setFileFormat(format)
            recordingEngine.syncIdleSettings(preferencesRepository.settingsFlow.first())
        }
    }

    fun setVisualizerMode(mode: VisualizerMode) {
        recordingEngine.updateVisualizerModeLive(mode)
        viewModelScope.launch {
            preferencesRepository.setVisualizerMode(mode)
        }
    }

    fun setGpsTagMode(mode: GpsTagMode) {
        recordingEngine.updateGpsTagModeLive(mode)
        viewModelScope.launch {
            preferencesRepository.setGpsTagMode(mode)
        }
    }

    fun setLowCutFilterHz(hz: Int) {
        recordingEngine.updateLowCutFilterLive(hz)
        viewModelScope.launch {
            preferencesRepository.setLowCutFilterHz(hz)
        }
    }

    fun setAcousticTriggerConfig(config: AcousticTriggerConfig) {
        recordingEngine.updateAcousticTriggerLive(config)
        triggerSaveJob?.cancel()
        triggerSaveJob = viewModelScope.launch {
            preferencesRepository.setAcousticTriggerConfig(config)
        }
    }

    fun showMessage(message: String) {
        _transientMessage.value = message
    }

    fun setSchedule(schedule: RecordingSchedule) {
        recordingEngine.updateScheduleLive(schedule)
        scheduleSaveJob?.cancel()
        scheduleSaveJob = viewModelScope.launch {
            preferencesRepository.setSchedule(schedule)
        }
    }

    fun setBatteryProtectionEnabled(enabled: Boolean) {
        recordingEngine.updateBatteryProtectionLive(enabled)
        viewModelScope.launch {
            preferencesRepository.setBatteryProtectionEnabled(enabled)
        }
    }

    fun setMinBatteryThresholdPercent(percent: Int) {
        val clamped = percent.coerceIn(
            RecorderSettings.MIN_BATTERY_THRESHOLD_PERCENT,
            RecorderSettings.MAX_BATTERY_THRESHOLD_PERCENT
        )
        recordingEngine.updateMinBatteryThresholdLive(clamped)
        batteryThresholdSaveJob?.cancel()
        batteryThresholdSaveJob = viewModelScope.launch {
            preferencesRepository.setMinBatteryThresholdPercent(clamped)
        }
    }

    fun inspectRecording(recording: RecordingFile) {
        val previousPath = inspectedFilePath.value
        inspectedFilePath.value = recording.path
        if (previousPath != recording.path) {
            playbackTimeRange.value = 0f..1f
            detailedSliceVisualization.value = null
            sliceExtractionJob?.cancel()
            refiningSlicePath.value = null
            if (playbackManager.state.value.activeFilePath != recording.path) {
                previewPositionMs.value = 0L
            }
        }
        ensureVisualizationLoaded(recording)
    }

    fun updatePlaybackTimeRange(range: ClosedFloatingPointRange<Float>) {
        val target = playbackInspectorState.value.inspectedRecording
        val durationMs = target?.durationMs ?: 0L
        val minSpan = TimeZoomMath.minSpanForDuration(durationMs)
        val clampedStart = range.start.coerceIn(0f, (1f - minSpan).coerceAtLeast(0f))
        val clampedEnd = range.endInclusive.coerceIn((clampedStart + minSpan).coerceAtMost(1f), 1f)
        val isFullRange = clampedStart <= 0.002f && clampedEnd >= 0.998f
        val normalized = if (isFullRange) 0f..1f else clampedStart..clampedEnd

        playbackTimeRange.value = normalized
        if (isFullRange || target == null) {
            sliceExtractionJob?.cancel()
            detailedSliceVisualization.value = null
            refiningSlicePath.value = null
            return
        }

        val currentSlice = detailedSliceVisualization.value
        val alreadyExact = currentSlice != null &&
            currentSlice.filePath == target.path &&
            kotlin.math.abs(currentSlice.startFraction - normalized.start) < 0.005f &&
            kotlin.math.abs(currentSlice.endFraction - normalized.endInclusive) < 0.005f
        if (alreadyExact) return

        sliceExtractionJob?.cancel()
        refiningSlicePath.value = target.path
        sliceExtractionJob = viewModelScope.launch {
            delay(110L)
            val extracted = withContext(Dispatchers.IO) {
                RecordingVisualizationExtractor.extract(
                    file = target.file,
                    startFraction = normalized.start,
                    endFraction = normalized.endInclusive
                )
            }
            if (extracted != null && playbackInspectorState.value.inspectedRecording?.path == target.path) {
                detailedSliceVisualization.value = extracted
            }
            if (refiningSlicePath.value == target.path) {
                refiningSlicePath.value = null
            }
        }
    }

    fun togglePlayback(recording: RecordingFile) {
        if (recordingEngine.state.value.status == RecordingStatus.RECORDING) {
            _transientMessage.value = "Pause or stop recording before playing an audio file."
            return
        }
        val previousPath = inspectedFilePath.value
        val startPos = if (previousPath == recording.path) {
            previewPositionMs.value.coerceIn(0L, recording.durationMs.coerceAtLeast(0L))
        } else {
            0L
        }
        if (previousPath != recording.path) {
            playbackTimeRange.value = 0f..1f
            detailedSliceVisualization.value = null
            sliceExtractionJob?.cancel()
            refiningSlicePath.value = null
        }
        inspectedFilePath.value = recording.path
        ensureVisualizationLoaded(recording)
        playbackManager.togglePlay(recording.file, startPositionMs = startPos)
    }

    fun seekPlayback(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        previewPositionMs.value = clamped
        if (playbackManager.state.value.activeFilePath != null) {
            playbackManager.seekTo(clamped)
        }
    }

    fun stopPlayback() {
        previewPositionMs.value = 0L
        playbackManager.stop()
    }

    fun renameRecording(recording: RecordingFile, newName: String) {
        viewModelScope.launch {
            if (playbackManager.state.value.activeFilePath == recording.path) {
                playbackManager.stop()
            }
            val ext = recording.file.extension.ifEmpty { recording.format.extension }
            val ok = recordingRepository.renameRecording(recording, newName)
            _transientMessage.value = if (ok) "Renamed to $newName.$ext" else "Could not rename file."
        }
    }

    fun deleteRecording(recording: RecordingFile) {
        viewModelScope.launch {
            if (playbackManager.state.value.activeFilePath == recording.path) {
                playbackManager.stop()
            }
            val ext = recording.file.extension.ifEmpty { recording.format.extension }
            val ok = recordingRepository.deleteRecording(recording)
            storageMonitor.refresh()
            _transientMessage.value = if (ok) "Deleted ${recording.displayName}.$ext" else "Could not delete file."
        }
    }

    fun exportRecording(recording: RecordingFile, destinationUri: Uri) {
        viewModelScope.launch {
            val ext = recording.file.extension.ifEmpty { recording.format.extension }
            val ok = recordingRepository.copyToUri(recording, destinationUri)
            _transientMessage.value = if (ok) {
                "Saved ${recording.displayName}.$ext"
            } else {
                "Failed to save copy."
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateSortOrder(order: ArchiveSortOrder) {
        sortOrder.value = order
    }

    fun clearTransientMessage() {
        _transientMessage.value = null
        recordingEngine.clearError()
        playbackManager.clearError()
    }

    class Factory(private val app: WaveLoopApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecorderViewModel(
                preferencesRepository = app.preferencesRepository,
                recordingRepository = app.recordingRepository,
                capabilityProber = app.capabilityProber,
                batteryMonitor = app.batteryMonitor,
                storageMonitor = app.storageMonitor,
                recordingEngine = app.recordingEngine,
                playbackManager = app.playbackManager
            ) as T
        }
    }
}
