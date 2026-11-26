package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.audio.PlaybackState
import com.example.audio.RecordingFileFormat
import com.example.audio.StorageHealth
import com.example.audio.StorageUsageEstimate
import com.example.data.RecordingFile
import com.example.ui.ArchiveSortOrder
import com.example.ui.ArchiveUiState
import com.example.ui.PlaybackInspectorState
import com.example.ui.components.PlaybackSpectrogramCanvas
import com.example.ui.components.PlaybackWaveformCanvas
import com.example.ui.components.SegmentedDiskUsageBar
import com.example.ui.components.SpectrogramZoomToolbar
import com.example.ui.components.TimeWindowZoomToolbar
import com.example.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun ArchiveScreen(
    archiveState: ArchiveUiState,
    playbackState: PlaybackState,
    playbackInspectorState: PlaybackInspectorState,
    storageUsageEstimate: StorageUsageEstimate,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderChange: (ArchiveSortOrder) -> Unit,
    onInspectRecording: (RecordingFile) -> Unit,
    onTogglePlay: (RecordingFile) -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onTimeRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    onStopPlayback: () -> Unit,
    onRenameRecording: (RecordingFile, String) -> Unit,
    onDeleteRecording: (RecordingFile) -> Unit,
    onExportRecording: (RecordingFile, android.net.Uri) -> Unit,
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var pendingExportRecording by remember { mutableStateOf<RecordingFile?>(null) }
    var renameTarget by remember { mutableStateOf<RecordingFile?>(null) }
    var deleteTarget by remember { mutableStateOf<RecordingFile?>(null) }

    var isSpectrogramExpanded by remember { mutableStateOf(false) }
    var spectrogramFreqRange by remember { mutableStateOf(0f..1f) }

    LaunchedEffect(playbackInspectorState.inspectedRecording?.path) {
        spectrogramFreqRange = 0f..1f
    }

    val showFloatingMiniBar by remember {
        derivedStateOf {
            playbackState.activeFilePath != null && listState.firstVisibleItemIndex > 1
        }
    }

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/*")
    ) { uri ->
        val recording = pendingExportRecording
        pendingExportRecording = null
        if (uri != null && recording != null) {
            onExportRecording(recording, uri)
        }
    }

    fun shareRecording(recording: RecordingFile) {
        val ext = recording.file.extension.ifEmpty { recording.format.extension }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            recording.file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = recording.format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${recording.displayName}.$ext")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${recording.format.label} Recording"))
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "archive_summary") {
                    ArchiveSummaryCard(
                        totalFiles = archiveState.totalFiles,
                        totalDurationMs = archiveState.totalDurationMs,
                        totalSizeBytes = archiveState.totalSizeBytes,
                        storageUsageEstimate = storageUsageEstimate,
                        onRefresh = onRefresh
                    )
                }

                item(key = "playback_telemetry_window") {
                    PlaybackTelemetryCard(
                        inspectorState = playbackInspectorState,
                        playbackState = playbackState,
                        isSpectrogramExpanded = isSpectrogramExpanded,
                        freqRange = spectrogramFreqRange,
                        onFreqRangeChanged = { spectrogramFreqRange = it },
                        onTimeRangeChanged = onTimeRangeChanged,
                        onToggleSpectrogramExpanded = {
                            isSpectrogramExpanded = !isSpectrogramExpanded
                        },
                        onTogglePlay = { rec -> onTogglePlay(rec) },
                        onSeek = onSeekPlayback,
                        onStop = onStopPlayback
                    )
                }

                if (archiveState.totalFiles > 0) {
                    item(key = "archive_search_and_sort") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = archiveState.searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Search recorded segments...") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (archiveState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("archive_search_input")
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ArchiveSortOrder.entries.forEach { order ->
                                    FilterChip(
                                        selected = archiveState.sortOrder == order,
                                        onClick = { onSortOrderChange(order) },
                                        label = { Text(order.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (archiveState.recordings.isEmpty()) {
                    item(key = "empty_archive_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyArchiveState(
                                isSearchFiltered = archiveState.searchQuery.isNotBlank(),
                                onClearSearch = { onSearchQueryChange("") },
                                onGoToStudio = onGoToStudio
                            )
                        }
                    }
                } else {
                    items(
                        items = archiveState.recordings,
                        key = { it.path }
                    ) { recording ->
                        val isLoaded = playbackState.activeFilePath == recording.path
                        val isInspected = playbackInspectorState.inspectedRecording?.path == recording.path
                        val isPlaying = isLoaded && playbackState.isPlaying
                        RecordingFileCard(
                            recording = recording,
                            isLoaded = isLoaded,
                            isInspected = isInspected,
                            isPlaying = isPlaying,
                            onSelectInspect = {
                                onInspectRecording(recording)
                                coroutineScope.launch {
                                    listState.animateScrollToItem(1)
                                }
                            },
                            onTogglePlay = { onTogglePlay(recording) },
                            onShare = { shareRecording(recording) },
                            onSaveCopy = {
                                val ext = recording.file.extension.ifEmpty { recording.format.extension }
                                pendingExportRecording = recording
                                saveCopyLauncher.launch("${recording.displayName}.$ext")
                            },
                            onRename = { renameTarget = recording },
                            onDelete = { deleteTarget = recording }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // Docked Mini Audio Player (only shown when user scrolls down past the Playback Telemetry Window)
            AnimatedVisibility(visible = showFloatingMiniBar) {
                MiniAudioPlayerBar(
                    playbackState = playbackState,
                    onJumpToWindow = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(1)
                        }
                    },
                    onTogglePlayPause = {
                        val path = playbackState.activeFilePath ?: return@MiniAudioPlayerBar
                        val match = archiveState.recordings.firstOrNull { it.path == path }
                        if (match != null) {
                            onTogglePlay(match)
                        }
                    },
                    onSeek = onSeekPlayback,
                    onClose = onStopPlayback
                )
            }
        }
    }

    renameTarget?.let { target ->
        val ext = target.file.extension.ifEmpty { target.format.extension }
        RenameRecordingDialog(
            currentName = target.displayName,
            extension = ext,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onRenameRecording(target, newName)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        val ext = target.file.extension.ifEmpty { target.format.extension }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Recording?") },
            text = {
                Text("Remove ${target.displayName}.$ext (${Formatters.formatFileSize(target.sizeBytes)}) permanently from device storage?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecording(target)
                        deleteTarget = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlaybackTelemetryCard(
    inspectorState: PlaybackInspectorState,
    playbackState: PlaybackState,
    isSpectrogramExpanded: Boolean,
    freqRange: ClosedFloatingPointRange<Float>,
    onFreqRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    onTimeRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    onToggleSpectrogramExpanded: () -> Unit,
    onTogglePlay: (RecordingFile) -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit
) {
    val recording = inspectorState.inspectedRecording
    val isLoaded = recording != null && playbackState.activeFilePath == recording.path
    val isPlaying = isLoaded && playbackState.isPlaying

    val durationMs = when {
        isLoaded && playbackState.durationMs > 0L -> playbackState.durationMs
        recording != null -> recording.durationMs.coerceAtLeast(1L)
        else -> 1L
    }
    val positionMs = when {
        isLoaded -> playbackState.positionMs.coerceIn(0L, durationMs)
        recording != null -> inspectorState.previewPositionMs.coerceIn(0L, durationMs)
        else -> 0L
    }
    val playheadFraction = if (recording != null) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val startEpochMs = if (recording != null) {
        Formatters.resolveRecordingStartMs(
            displayName = recording.displayName,
            createdAtMs = recording.createdAtMs,
            durationMs = durationMs
        )
    } else {
        0L
    }
    val playingEpochMs = startEpochMs + positionMs
    val endEpochMs = startEpochMs + durationMs

    val viz = inspectorState.visualization
    val sampleRateHz = viz?.sampleRateHz ?: recording?.sampleRateHz ?: 44100
    val fileFormat = recording?.format ?: RecordingFileFormat.WAV

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("playback_telemetry_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isSpectrogramExpanded && recording != null) {
                // Compact single-line header when Spectrogram is expanded
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val ext = recording.file.extension.ifEmpty { recording.format.extension }
                        Text(
                            text = "${recording.displayName}.$ext • ${Formatters.formatDateFull(playingEpochMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${Formatters.formatClockTimeHms(playingEpochMs)}  (${
                                Formatters.formatDurationHms(positionMs)
                            } / ${Formatters.formatDurationHms(durationMs)})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isPlaying) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.testTag("playback_playing_clock")
                        )
                    }

                    FilledIconButton(
                        onClick = { onTogglePlay(recording) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isPlaying) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                            contentColor = if (isPlaying) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onTertiary
                            }
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("playback_window_play_toggle")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause playback" else "Play recording"
                        )
                    }
                }
            } else {
                // Main Header: Playing Date & Time + Elapsed / Total + Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val ext = recording?.file?.extension?.ifEmpty { recording.format.extension }
                        Text(
                            text = if (recording != null) {
                                "${recording.displayName}.$ext • ${recording.format.label} ${sampleRateHz / 1000f}k"
                            } else {
                                "STANDBY • NO RECORDING SELECTED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (recording != null) {
                                Formatters.formatClockTimeHms(playingEpochMs)
                            } else {
                                "--:--:--"
                            },
                            style = MaterialTheme.typography.displayMedium,
                            color = if (isPlaying) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.testTag("playback_playing_clock")
                        )
                        Text(
                            text = if (recording != null) {
                                Formatters.formatDateFull(playingEpochMs)
                            } else {
                                "Record in Studio to inspect & play"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.testTag("playback_playing_date")
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isLoaded) {
                                FilledTonalIconButton(
                                    onClick = onStop,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("playback_window_stop")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = "Stop playback"
                                    )
                                }
                            }
                            FilledIconButton(
                                onClick = {
                                    if (recording != null) {
                                        onTogglePlay(recording)
                                    }
                                },
                                enabled = recording != null,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isPlaying) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.tertiary
                                    },
                                    contentColor = if (isPlaying) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onTertiary
                                    }
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("playback_window_play_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause playback" else "Play recording"
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (recording != null) {
                                "POS ${Formatters.formatDurationHms(positionMs)} / ${
                                    Formatters.formatDurationHms(durationMs)
                                }"
                            } else {
                                "POS 00:00 / 00:00"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.testTag("playback_pos_total")
                        )
                        if (recording != null) {
                            Text(
                                text = "${Formatters.formatClockTimeHms(startEpochMs)} – ${
                                    Formatters.formatClockTimeHms(endEpochMs)
                                }",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Scrubbable Time Position Slider
            Slider(
                value = playheadFraction,
                onValueChange = { fraction ->
                    if (recording != null) {
                        onSeek((fraction * durationMs).toLong())
                    }
                },
                enabled = recording != null,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.tertiary,
                    activeTrackColor = MaterialTheme.colorScheme.tertiary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playback_seek_slider")
            )

            // UP: Waveform Envelope Window (with synced playhead, time zoom & tap/drag seek)
            if (!isSpectrogramExpanded) {
                PlaybackWaveformCanvas(
                    amplitudes = viz?.waveformAmplitudes ?: emptyList(),
                    playheadFraction = playheadFraction,
                    fileFormat = fileFormat,
                    isPlaying = isPlaying,
                    isLoading = inspectorState.isLoadingVisualization,
                    onSeekFraction = { frac ->
                        if (recording != null) {
                            onSeek((frac * durationMs).toLong())
                        }
                    },
                    timeRange = inspectorState.timeRange,
                    dataStartFraction = viz?.startFraction ?: 0f,
                    dataEndFraction = viz?.endFraction ?: 1f,
                    durationMs = if (recording != null) durationMs else 0L,
                    isRefining = inspectorState.isRefiningTimeSlice,
                    onTimeRangeChanged = onTimeRangeChanged
                )
            }

            // Shared Horizontal Time Zoom & Pan Controls (synced across Waveform + Spectrogram)
            if (recording != null) {
                TimeWindowZoomToolbar(
                    durationMs = durationMs,
                    timeRange = inspectorState.timeRange,
                    onTimeRangeChanged = onTimeRangeChanged,
                    isRefining = inspectorState.isRefiningTimeSlice
                )
            }

            // DOWN: Spectrogram Window (with synced time zoom, playhead, expand & spectral range)
            PlaybackSpectrogramCanvas(
                columns = viz?.spectrogramColumns ?: emptyList(),
                playheadFraction = playheadFraction,
                peakFrequencyHz = viz?.peakFrequencyHz ?: 0,
                sampleRateHz = sampleRateHz,
                isPlaying = isPlaying,
                isLoading = inspectorState.isLoadingVisualization,
                isExpanded = isSpectrogramExpanded,
                freqRange = freqRange,
                onFreqRangeChanged = onFreqRangeChanged,
                onToggleExpand = onToggleSpectrogramExpanded,
                timeRange = inspectorState.timeRange,
                dataStartFraction = viz?.startFraction ?: 0f,
                dataEndFraction = viz?.endFraction ?: 1f,
                durationMs = if (recording != null) durationMs else 0L,
                isRefining = inspectorState.isRefiningTimeSlice,
                onTimeRangeChanged = onTimeRangeChanged
            )

            // Spectral Range Shifting & Frequency Band Controls
            SpectrogramZoomToolbar(
                sampleRateHz = sampleRateHz,
                freqRange = freqRange,
                isExpanded = isSpectrogramExpanded,
                onFreqRangeChanged = onFreqRangeChanged,
                onToggleExpand = onToggleSpectrogramExpanded,
                alwaysShowRangeSlider = recording != null
            )

            // Bottom Date/Time & Total Duration Chips (when not in expanded spectrogram mode)
            if (!isSpectrogramExpanded && recording != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PlaybackMetricChip(
                        label = "START TIME",
                        value = Formatters.formatClockTimeHms(startEpochMs)
                    )
                    PlaybackMetricChip(
                        label = "PLAYING",
                        value = "${Formatters.formatDurationHms(positionMs)} / ${
                            Formatters.formatDurationHms(durationMs)
                        }"
                    )
                    PlaybackMetricChip(
                        label = "END TIME",
                        value = Formatters.formatClockTimeHms(endEpochMs)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackMetricChip(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ArchiveSummaryCard(
    totalFiles: Int,
    totalDurationMs: Long,
    totalSizeBytes: Long,
    storageUsageEstimate: StorageUsageEstimate,
    onRefresh: () -> Unit
) {
    val snap = storageUsageEstimate.snapshot
    val wavColor = when (storageUsageEstimate.health) {
        StorageHealth.CRITICAL -> MaterialTheme.colorScheme.primary
        StorageHealth.LOW -> MaterialTheme.colorScheme.secondary
        StorageHealth.HEALTHY -> MaterialTheme.colorScheme.tertiary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Field Archive",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$totalFiles files • ${Formatters.formatDurationHms(totalDurationMs)} • ${
                            Formatters.formatFileSize(totalSizeBytes)
                        }",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("archive_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh recordings"
                    )
                }
            }

            SegmentedDiskUsageBar(
                otherUsedFraction = snap.otherUsedFractionOfTotal,
                wavUsedFraction = snap.recordingsFractionOfTotal,
                wavColor = wavColor,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${Formatters.formatFileSize(snap.availableBytes)} free of ${
                        Formatters.formatFileSize(snap.totalBytes)
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "~${Formatters.formatEstimatedRuntime(storageUsageEstimate.audioMinutesAvailable)} audio left",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = wavColor
                )
            }
        }
    }
}

@Composable
private fun EmptyArchiveState(
    isSearchFiltered: Boolean,
    onClearSearch: () -> Unit,
    onGoToStudio: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Text(
            text = if (isSearchFiltered) "No Matching Files" else stringResource(R.string.empty_archive_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = if (isSearchFiltered) {
                "Try a different search term or clear your filter."
            } else {
                stringResource(R.string.empty_archive_body)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = if (isSearchFiltered) onClearSearch else onGoToStudio,
            modifier = Modifier.testTag("empty_archive_cta")
        ) {
            Text(if (isSearchFiltered) "Clear Search" else "Open Studio")
        }
    }
}

@Composable
private fun RecordingFileCard(
    recording: RecordingFile,
    isLoaded: Boolean,
    isInspected: Boolean,
    isPlaying: Boolean,
    onSelectInspect: () -> Unit,
    onTogglePlay: () -> Unit,
    onShare: () -> Unit,
    onSaveCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val highlighted = isLoaded || isInspected

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            1.dp,
            when {
                isLoaded -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                isInspected -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onSelectInspect() }
            .testTag("recording_card_${recording.displayName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onTogglePlay,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isPlaying) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (isPlaying) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("play_button_${recording.displayName}")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause ${recording.displayName}" else "Play ${recording.displayName}"
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            val ext = recording.file.extension.ifEmpty { recording.format.extension }
            val startEpochMs = Formatters.resolveRecordingStartMs(
                displayName = recording.displayName,
                createdAtMs = recording.createdAtMs,
                durationMs = recording.durationMs
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${recording.displayName}.$ext",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = recording.format.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = Formatters.formatDateTime(startEpochMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${recording.sampleRateHz / 1000f}k • ${
                            Formatters.formatFileSize(recording.sizeBytes)
                        }",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                recording.gpsCoordinate?.let { coord ->
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = "GPS coordinates",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = coord.formatShort(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = Formatters.formatDurationHms(recording.durationMs),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (highlighted) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("more_button_${recording.displayName}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More actions for ${recording.displayName}"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Share ${recording.format.label}") },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Save Copy") },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onSaveCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniAudioPlayerBar(
    playbackState: PlaybackState,
    onJumpToWindow: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onJumpToWindow() }
            .testTag("mini_audio_player")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AudioFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val playerExt = playbackState.activeFileExtension?.ifEmpty { "audio" } ?: "audio"
                Text(
                    text = "${playbackState.activeFileName ?: "Playing"}.$playerExt",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (playbackState.isPlaying) "Pause playback" else "Resume playback"
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close player"
                    )
                }
            }

            val maxDuration = playbackState.durationMs.coerceAtLeast(1L).toFloat()
            Slider(
                value = (playbackState.positionMs.toFloat() / maxDuration).coerceIn(0f, 1f),
                onValueChange = { fraction ->
                    onSeek((fraction * maxDuration).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.tertiary,
                    activeTrackColor = MaterialTheme.colorScheme.tertiary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Formatters.formatDurationHms(playbackState.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatters.formatDurationHms(playbackState.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RenameRecordingDialog(
    currentName: String,
    extension: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Recording") },
        text = {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Filename") },
                suffix = { Text(".$extension") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nameInput) },
                enabled = nameInput.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
