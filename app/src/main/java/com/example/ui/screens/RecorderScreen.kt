package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.PaddingValues
import com.example.R
import com.example.audio.AcousticTriggerConfig
import com.example.audio.AudioCapabilities
import com.example.audio.AudioQuality
import com.example.audio.BatteryUsageEstimate
import com.example.audio.GpsTagMode
import com.example.audio.RecordingFileFormat
import com.example.audio.RecordingState
import com.example.audio.RecordingStatus
import com.example.audio.StorageUsageEstimate
import com.example.audio.VisualizerMode
import com.example.data.RecorderSettings
import com.example.data.RecordingSchedule
import com.example.ui.components.AcousticTriggerCard
import com.example.ui.components.AudioVisualizerPanel
import com.example.ui.components.BatteryIconGraphic
import com.example.ui.components.BatterySettingsCard
import com.example.ui.components.CompactTelemetryStorageStrip
import com.example.ui.components.IntervalSettingsCard
import com.example.ui.components.LowCutFilterButton
import com.example.ui.components.LowCutFilterCard
import com.example.ui.components.RecordingDurationCard
import com.example.ui.components.SampleRateSelectorCard
import com.example.ui.components.ScheduleSettingsCard
import com.example.util.Formatters
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RecorderScreen(
    recordingState: RecordingState,
    settings: RecorderSettings,
    audioCapabilities: AudioCapabilities,
    batteryUsageEstimate: BatteryUsageEstimate,
    storageUsageEstimate: StorageUsageEstimate,
    onProbeCapabilities: () -> Unit,
    onRefreshStorage: () -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onSplitSegmentNow: () -> Unit,
    onTriggerNextNow: () -> Unit,
    onStopRecording: () -> Unit,
    onRecordingDurationChanged: (Int) -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onContinuousNoGapChanged: (Boolean) -> Unit,
    onQualityChanged: (AudioQuality) -> Unit,
    onFileFormatChanged: (RecordingFileFormat) -> Unit,
    onVisualizerModeChanged: (VisualizerMode) -> Unit,
    onGpsTagModeChanged: (GpsTagMode) -> Unit,
    onLowCutFilterChanged: (Int) -> Unit,
    onAcousticTriggerChanged: (AcousticTriggerConfig) -> Unit,
    onShowMessage: (String) -> Unit,
    onScheduleChanged: (RecordingSchedule) -> Unit,
    onBatteryProtectionChanged: (Boolean) -> Unit,
    onBatteryThresholdChanged: (Int) -> Unit,
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun checkLocationGranted(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(checkLocationGranted())
    }
    var pendingGpsMode by remember { mutableStateOf<GpsTagMode?>(null) }
    var micPermissionRequestedOnce by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                hasMicPermission = granted
                hasLocationPermission = checkLocationGranted()
                if (granted) {
                    onProbeCapabilities()
                }
                onRefreshStorage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            checkLocationGranted()
        hasLocationPermission = granted
        val targetMode = pendingGpsMode
        pendingGpsMode = null
        if (granted && targetMode != null) {
            onGpsTagModeChanged(targetMode)
        } else if (!granted) {
            onShowMessage("Location permission is needed to geotag recordings.")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onStartRecording()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        micPermissionRequestedOnce = true
        if (granted) {
            onProbeCapabilities()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onStartRecording()
            }
        }
    }

    fun handleGpsModeSelected(mode: GpsTagMode) {
        if (mode == GpsTagMode.OFF || hasLocationPermission || checkLocationGranted()) {
            hasLocationPermission = checkLocationGranted()
            onGpsTagModeChanged(mode)
        } else {
            pendingGpsMode = mode
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun triggerStartFlow() {
        if (!hasMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onStartRecording()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isIdle = recordingState.status == RecordingStatus.IDLE
            val activeQuality = if (isIdle) settings.quality else recordingState.quality
            val activeFormat = if (isIdle) settings.fileFormat else recordingState.fileFormat
            val activeVisualizerMode = if (isIdle) settings.visualizerMode else recordingState.visualizerMode
            val activeGpsMode = if (isIdle) settings.gpsTagMode else recordingState.gpsTagMode
            val activeLowCutHz = if (isIdle) settings.lowCutFilterHz else recordingState.lowCutFilterHz
            val activeTriggerConfig = (if (isIdle) settings.acousticTrigger else recordingState.triggerConfig)
                .normalizedForSampleRate(activeQuality.sampleRateHz)

            // App Brand & Live Status Pill
            StudioBrandHeader(
                recordingState = recordingState,
                batteryUsageEstimate = batteryUsageEstimate
            )

            // Top Lossless Format Selector (FLAC, ALAC, WAV, AIFF) + GPS Selector beside it
            FormatAndGpsSelectorBar(
                selectedFormat = activeFormat,
                selectedGpsMode = activeGpsMode,
                isRecordingActive = !isIdle,
                onFormatSelected = onFileFormatChanged,
                onGpsModeSelected = ::handleGpsModeSelected
            )

            if (!hasMicPermission) {
                MicrophonePermissionCard(
                    showSettingsHint = micPermissionRequestedOnce,
                    onRequestPermission = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onOpenSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                )
            }

            // Hero Telemetry + Live Waveform / Spectrogram + Compact Disk Usage beneath
            TelemetryCard(
                recordingState = recordingState,
                fileFormat = activeFormat,
                sampleRateHz = activeQuality.sampleRateHz,
                visualizerMode = activeVisualizerMode,
                gpsTagMode = activeGpsMode,
                triggerConfig = activeTriggerConfig,
                batteryUsageEstimate = batteryUsageEstimate,
                storageUsageEstimate = storageUsageEstimate,
                onVisualizerModeChanged = onVisualizerModeChanged
            )

            var showBatteryGuardDialog by remember { mutableStateOf(false) }
            var showLowCutDialog by remember { mutableStateOf(false) }

            // Centered Record/Stop button flanked by Low-Cut Filter (left) & Battery Guard (right)
            TransportDeck(
                recordingState = recordingState,
                lowCutFilterHz = activeLowCutHz,
                batteryUsageEstimate = batteryUsageEstimate,
                onLowCutClick = { showLowCutDialog = true },
                onBatteryGuardClick = { showBatteryGuardDialog = true },
                onRecordOrStop = {
                    if (recordingState.status == RecordingStatus.IDLE) {
                        triggerStartFlow()
                    } else {
                        onStopRecording()
                    }
                },
                onPauseOrResume = {
                    if (recordingState.status == RecordingStatus.RECORDING) {
                        onPauseRecording()
                    } else if (recordingState.status == RecordingStatus.PAUSED) {
                        onResumeRecording()
                    }
                },
                onSplitNow = onSplitSegmentNow,
                onTriggerNextNow = onTriggerNextNow
            )

            if (showLowCutDialog) {
                Dialog(
                    onDismissRequest = { showLowCutDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp)
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        LowCutFilterCard(
                            cutoffHz = activeLowCutHz,
                            onCutoffChanged = onLowCutFilterChanged,
                            onDismiss = { showLowCutDialog = false }
                        )
                    }
                }
            }

            if (showBatteryGuardDialog) {
                Dialog(
                    onDismissRequest = { showBatteryGuardDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp)
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        BatterySettingsCard(
                            estimate = batteryUsageEstimate,
                            protectionEnabled = recordingState.batteryProtectionEnabled,
                            thresholdPercent = recordingState.minBatteryThresholdPercent,
                            onProtectionChanged = onBatteryProtectionChanged,
                            onThresholdChanged = onBatteryThresholdChanged,
                            onDismiss = { showBatteryGuardDialog = false }
                        )
                    }
                }
            }

            var showContinuousTurnedOnDialog by remember { mutableStateOf(false) }

            // Acoustic Trigger & Pre-Recording Buffer controls
            AcousticTriggerCard(
                config = activeTriggerConfig,
                sampleRateHz = activeQuality.sampleRateHz,
                status = recordingState.status,
                liveBandLevelDb = recordingState.triggerBandLevelDb,
                preRecordBufferedMs = recordingState.triggerPreRecordBufferedMs,
                autoStopRemainingMs = recordingState.triggerAutoStopRemainingMs,
                targetRecordingDurationSeconds = recordingState.targetRecordingDurationSeconds,
                lowCutFilterHz = activeLowCutHz,
                onConfigChanged = { newConfig ->
                    val justTurnedOn = !activeTriggerConfig.enabled && newConfig.enabled
                    onAcousticTriggerChanged(newConfig)
                    if (justTurnedOn) {
                        showContinuousTurnedOnDialog = true
                    }
                }
            )

            if (showContinuousTurnedOnDialog) {
                AlertDialog(
                    onDismissRequest = { showContinuousTurnedOnDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Sensors,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    },
                    title = { Text("Continuous Mode Turned On") },
                    text = {
                        Text(
                            text = "Recording Interval has been turned off automatically and Continuous (No Interval Wait) is now ON so Acoustic Trigger & Pre-Recording can listen continuously and capture events without waiting intervals.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { showContinuousTurnedOnDialog = false },
                            modifier = Modifier.testTag("trigger_continuous_popup_ok")
                        ) {
                            Text("OK")
                        }
                    }
                )
            }

            // Setting 1: Recording Duration per audio file (30s to 60m)
            RecordingDurationCard(
                recordingDurationSeconds = recordingState.targetRecordingDurationSeconds,
                quality = activeQuality,
                fileFormat = activeFormat,
                isRecordingActive = !isIdle,
                onDurationSelected = onRecordingDurationChanged
            )

            // Setting 2: Recording Interval (5m to 24h)
            IntervalSettingsCard(
                intervalSeconds = recordingState.targetIntervalSeconds,
                continuousNoGap = recordingState.continuousNoGap || activeTriggerConfig.enabled,
                recordingDurationSeconds = recordingState.targetRecordingDurationSeconds,
                onIntervalSelected = onIntervalChanged,
                onContinuousNoGapChanged = onContinuousNoGapChanged,
                isTriggerActive = activeTriggerConfig.enabled
            )

            // Setting 3: Recording Schedule (Always, Day Only, Sunrise & Sunset, Specific Time)
            ScheduleSettingsCard(
                schedule = recordingState.schedule,
                onScheduleChanged = onScheduleChanged
            )

            // Verified Sampling Rate Options (up to device max)
            SampleRateSelectorCard(
                capabilities = audioCapabilities,
                selectedQuality = activeQuality,
                fileFormat = activeFormat,
                intervalSeconds = recordingState.targetRecordingDurationSeconds,
                isRecordingActive = !isIdle,
                onQualitySelected = onQualityChanged
            )

            // Latest Completed Segment Toast Strip
            AnimatedVisibility(visible = recordingState.lastCompletedSegmentName != null) {
                recordingState.lastCompletedSegmentName?.let { fileName ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenArchive)
                            .testTag("last_saved_segment_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Saved $fileName",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Duration ${
                                            Formatters.formatDurationHms(
                                                recordingState.lastCompletedSegmentDurationMs
                                            )
                                        } • Tap to open archive",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StudioBrandHeader(
    recordingState: RecordingState,
    batteryUsageEstimate: BatteryUsageEstimate
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_pocket_bioacoustics_icon),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .testTag("app_brand_emblem")
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("app_brand_title")
                )
                Text(
                    text = stringResource(R.string.author_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("app_author_attribution")
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Live Session Status Pill
        val statusColor = when (recordingState.status) {
            RecordingStatus.ARMED_TRIGGER -> MaterialTheme.colorScheme.tertiary
            RecordingStatus.RECORDING -> MaterialTheme.colorScheme.primary
            RecordingStatus.PAUSED -> MaterialTheme.colorScheme.secondary
            RecordingStatus.PAUSED_LOW_BATTERY -> MaterialTheme.colorScheme.primary
            RecordingStatus.WAITING_INTERVAL -> MaterialTheme.colorScheme.tertiary
            RecordingStatus.WAITING_SCHEDULE -> MaterialTheme.colorScheme.secondary
            RecordingStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val statusBg = when (recordingState.status) {
            RecordingStatus.ARMED_TRIGGER -> MaterialTheme.colorScheme.tertiaryContainer
            RecordingStatus.RECORDING -> MaterialTheme.colorScheme.primaryContainer
            RecordingStatus.PAUSED -> MaterialTheme.colorScheme.secondaryContainer
            RecordingStatus.PAUSED_LOW_BATTERY -> MaterialTheme.colorScheme.primaryContainer
            RecordingStatus.WAITING_INTERVAL -> MaterialTheme.colorScheme.tertiaryContainer
            RecordingStatus.WAITING_SCHEDULE -> MaterialTheme.colorScheme.secondaryContainer
            RecordingStatus.IDLE -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
        val statusText = when (recordingState.status) {
            RecordingStatus.ARMED_TRIGGER -> "ARMED • TRIGGER"
            RecordingStatus.RECORDING -> "REC • #${recordingState.segmentNumber}"
            RecordingStatus.PAUSED -> "PAUSED • #${recordingState.segmentNumber}"
            RecordingStatus.PAUSED_LOW_BATTERY ->
                "LOW BATT • ${batteryUsageEstimate.levelPercent}%"
            RecordingStatus.WAITING_INTERVAL -> "WAIT • NEXT #${recordingState.segmentNumber + 1}"
            RecordingStatus.WAITING_SCHEDULE -> "SCHEDULE WAIT"
            RecordingStatus.IDLE -> stringResource(R.string.status_standby)
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = statusBg,
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (recordingState.status == RecordingStatus.RECORDING ||
                                recordingState.status == RecordingStatus.ARMED_TRIGGER
                            ) {
                                statusColor.copy(alpha = pulseAlpha)
                            } else {
                                statusColor
                            }
                        )
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (recordingState.status == RecordingStatus.IDLE) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        statusColor
                    },
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FormatAndGpsSelectorBar(
    selectedFormat: RecordingFileFormat,
    selectedGpsMode: GpsTagMode,
    isRecordingActive: Boolean,
    onFormatSelected: (RecordingFileFormat) -> Unit,
    onGpsModeSelected: (GpsTagMode) -> Unit
) {
    var gpsMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Top-left lossless format selector (FLAC, ALAC, WAV, AIFF only)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .weight(1f, fill = false)
                .alpha(if (isRecordingActive) 0.72f else 1f)
                .testTag("top_left_format_selector")
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RecordingFileFormat.entries.forEach { format ->
                    val selected = format == selectedFormat
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onFormatSelected(format) }
                            .testTag("format_button_${format.label}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = format.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onTertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Top-right 3-option GPS Tagging Button (Off, One-Time GPS, GPS Each File)
        val gpsEnabled = selectedGpsMode != GpsTagMode.OFF
        Box {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (gpsEnabled) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = BorderStroke(
                    1.dp,
                    if (gpsEnabled) {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { gpsMenuExpanded = true }
                    .testTag("gps_mode_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (gpsEnabled) {
                            Icons.Rounded.GpsFixed
                        } else {
                            Icons.Rounded.LocationOff
                        },
                        contentDescription = "GPS tagging mode: ${selectedGpsMode.label}",
                        tint = if (gpsEnabled) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "GPS ${selectedGpsMode.buttonBadge}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (gpsEnabled) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (gpsEnabled) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = if (gpsEnabled) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = gpsMenuExpanded,
                onDismissRequest = { gpsMenuExpanded = false }
            ) {
                GpsTagMode.entries.forEach { option ->
                    val isSelected = option == selectedGpsMode
                    val optionTag = when (option) {
                        GpsTagMode.OFF -> "gps_option_off"
                        GpsTagMode.ONE_TIME -> "gps_option_one_time"
                        GpsTagMode.EACH_FILE -> "gps_option_each_file"
                    }
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (option == GpsTagMode.OFF) {
                                    Icons.Rounded.LocationOff
                                } else {
                                    Icons.Rounded.GpsFixed
                                },
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        },
                        onClick = {
                            gpsMenuExpanded = false
                            onGpsModeSelected(option)
                        },
                        modifier = Modifier.testTag(optionTag)
                    )
                }
            }
        }
    }
}

@Composable
private fun MicrophonePermissionCard(
    showSettingsHint: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.permission_mic_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.permission_mic_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.testTag("grant_mic_button")
                ) {
                    Text(stringResource(R.string.permission_grant))
                }
                if (showSettingsHint) {
                    OutlinedButton(onClick = onOpenSettings) {
                        Text("Open Settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    recordingState: RecordingState,
    fileFormat: RecordingFileFormat,
    sampleRateHz: Int,
    visualizerMode: VisualizerMode,
    gpsTagMode: GpsTagMode,
    triggerConfig: AcousticTriggerConfig,
    batteryUsageEstimate: BatteryUsageEstimate,
    storageUsageEstimate: StorageUsageEstimate,
    onVisualizerModeChanged: (VisualizerMode) -> Unit
) {
    val isActive = recordingState.status != RecordingStatus.IDLE
    val isArmedTrigger = recordingState.status == RecordingStatus.ARMED_TRIGGER
    val isTriggeredRecording = recordingState.status == RecordingStatus.RECORDING && triggerConfig.enabled
    val isLowBatteryPaused = recordingState.status == RecordingStatus.PAUSED_LOW_BATTERY
    val isIntervalWaiting = recordingState.status == RecordingStatus.WAITING_INTERVAL
    val isScheduleWaiting = recordingState.status == RecordingStatus.WAITING_SCHEDULE
    val isWaiting = isIntervalWaiting || isScheduleWaiting
    val recDurationTargetMs = recordingState.targetRecordingDurationSeconds.toLong() * 1000L
    val elapsedText = when {
        isWaiting -> Formatters.formatDurationHms(recordingState.nextCycleCountdownMs)
        isArmedTrigger -> Formatters.formatDurationHms(recordingState.triggerPreRecordBufferedMs)
        else -> Formatters.formatDurationHms(recordingState.currentSegmentElapsedMs)
    }
    val targetText = Formatters.formatDurationHms(recDurationTargetMs)
    val remainingText = Formatters.formatDurationHms(recordingState.remainingSegmentMs)

    var isSpectrogramExpanded by remember { mutableStateOf(false) }
    var spectrogramFreqRange by remember { mutableStateOf(0f..1f) }
    if (visualizerMode != VisualizerMode.SPECTROGRAM && isSpectrogramExpanded) {
        isSpectrogramExpanded = false
    }
    val expandedSpectrogramTab = visualizerMode == VisualizerMode.SPECTROGRAM && isSpectrogramExpanded

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (expandedSpectrogramTab) {
                // Compact single-line live recording header so the Spectrogram fills the whole TelemetryCard tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            isLowBatteryPaused ->
                                "LOW BATT (${batteryUsageEstimate.levelPercent}%)"
                            isScheduleWaiting -> "SCHEDULE • $elapsedText"
                            isArmedTrigger -> "ARMED • ${triggerConfig.bandSummaryLabel(sampleRateHz)}"
                            isIntervalWaiting -> "NEXT IN $elapsedText"
                            isActive -> "${recordingState.currentFileName ?: "SEG #${recordingState.segmentNumber}"}"
                            else -> "STANDBY • ${fileFormat.label}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isArmedTrigger) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArmedTrigger) {
                            String.format(
                                Locale.US,
                                "PRE %.1f/%ds",
                                recordingState.triggerPreRecordBufferedMs / 1000.0,
                                triggerConfig.preRecordSeconds
                            )
                        } else {
                            "$elapsedText / $targetText"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            isArmedTrigger -> MaterialTheme.colorScheme.tertiary
                            isActive -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.testTag("expanded_tab_timer")
                    )
                }
            } else {
                // Main Timer & Target Readout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isLowBatteryPaused ->
                                    "BATTERY GUARD (${batteryUsageEstimate.levelPercent}% < ${recordingState.minBatteryThresholdPercent}%)"
                                isScheduleWaiting -> "SCHEDULE WINDOW IN"
                                isArmedTrigger -> "ARMED • PRE-REC BUFFER"
                                isIntervalWaiting -> "NEXT RECORDING IN"
                                isActive -> recordingState.currentFileName ?: "SEGMENT #${recordingState.segmentNumber}"
                                else -> "YYYYMMDD_HHMMSS.${fileFormat.extension}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isLowBatteryPaused -> MaterialTheme.colorScheme.primary
                                isArmedTrigger -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = elapsedText,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("segment_elapsed_timer")
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isArmedTrigger) {
                                "THRESH ≥ ${triggerConfig.amplitudeThresholdDb.roundToInt()} dB"
                            } else {
                                "FILE $targetText"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isArmedTrigger) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val intShort = Formatters.formatDurationShort(recordingState.targetIntervalSeconds)
                        val nextScheduleLabel = recordingState.nextScheduleWindowStartMinute?.let {
                            RecordingSchedule.formatTimeOfDay(it)
                        } ?: recordingState.schedule.summaryLabel()
                        Text(
                            text = when {
                                isLowBatteryPaused -> "Charge or lower limit"
                                isScheduleWaiting -> "Starts at $nextScheduleLabel"
                                isArmedTrigger -> if (triggerConfig.preRecordSeconds > 0) {
                                    "Pre-rec ${triggerConfig.preRecordSeconds}s • ${triggerConfig.bandSummaryLabel(sampleRateHz)}"
                                } else {
                                    "Listening ${triggerConfig.bandSummaryLabel(sampleRateHz)}"
                                }
                                isTriggeredRecording ->
                                    "Stop ${Formatters.formatDurationHms(recordingState.triggerAutoStopRemainingMs)} quiet"
                                isIntervalWaiting -> "Waiting $intShort interval"
                                isActive -> "Split in $remainingText"
                                triggerConfig.enabled ->
                                    "Trigger ≥${triggerConfig.amplitudeThresholdDb.roundToInt()}dB"
                                recordingState.continuousNoGap -> "Continuous loop"
                                else -> "Then wait $intShort"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Segment, Pre-Rec Buffer, or Interval Wait progress bar
                val preRecProgress = if (triggerConfig.preRecordSeconds > 0) {
                    (recordingState.triggerPreRecordBufferedMs.toFloat() /
                        (triggerConfig.preRecordSeconds.toFloat() * 1000f)).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = {
                        when {
                            isLowBatteryPaused || isScheduleWaiting -> 0f
                            isArmedTrigger -> preRecProgress
                            isIntervalWaiting -> recordingState.intervalWaitProgress
                            isActive -> recordingState.segmentProgress
                            else -> 0f
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        isLowBatteryPaused -> MaterialTheme.colorScheme.primary
                        isWaiting || isArmedTrigger -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            // Live Oscilloscope Waveform or Real-Time FFT Spectrogram + Zoom & Expand
            AudioVisualizerPanel(
                visualizerMode = visualizerMode,
                amplitudes = recordingState.amplitudes,
                spectrogramColumns = recordingState.spectrogramColumns,
                peakFrequencyHz = recordingState.peakFrequencyHz,
                sampleRateHz = sampleRateHz,
                fileFormat = fileFormat,
                status = recordingState.status,
                currentLevelDb = recordingState.currentLevelDb,
                peakLevelDb = recordingState.peakLevelDb,
                batteryUsageEstimate = batteryUsageEstimate,
                onVisualizerModeChanged = onVisualizerModeChanged,
                isSpectrogramExpanded = isSpectrogramExpanded,
                onToggleSpectrogramExpanded = {
                    isSpectrogramExpanded = !isSpectrogramExpanded
                },
                freqRange = spectrogramFreqRange,
                onFreqRangeChanged = { spectrogramFreqRange = it },
                triggerConfig = triggerConfig
            )

            if (!expandedSpectrogramTab) {
                // Bottom Telemetry Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val intervalLabel = if (recordingState.continuousNoGap) "MODE" else "INTERVAL"
                    val intervalValue = if (recordingState.continuousNoGap) {
                        "No Wait"
                    } else {
                        Formatters.formatDurationShort(recordingState.targetIntervalSeconds)
                    }

                    TelemetryChip(
                        label = intervalLabel,
                        value = intervalValue
                    )
                    TelemetryChip(
                        label = "SAVED",
                        value = "${recordingState.completedSegmentsInSession} ${fileFormat.label}"
                    )
                    TelemetryChip(
                        label = "RECORDED",
                        value = Formatters.formatDurationHms(recordingState.totalSessionElapsedMs)
                    )
                }

            if (gpsTagMode != GpsTagMode.OFF || recordingState.currentGpsCoordinate != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("telemetry_gps_status_strip")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GpsFixed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        val gpsSummary = when {
                            recordingState.isAcquiringGps ->
                                "GPS (${gpsTagMode.label}) • Acquiring single fix..."
                            recordingState.currentGpsCoordinate != null ->
                                "GPS (${gpsTagMode.label}) • ${recordingState.currentGpsCoordinate.formatDetailed()}"
                            gpsTagMode == GpsTagMode.ONE_TIME ->
                                "One-Time GPS • Locks once when Record is pressed"
                            else ->
                                "GPS Each File • Locks once per segment start"
                        }
                        Text(
                            text = gpsSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

                CompactTelemetryStorageStrip(
                    estimate = storageUsageEstimate,
                    fileFormat = fileFormat
                )
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TransportDeck(
    recordingState: RecordingState,
    lowCutFilterHz: Int,
    batteryUsageEstimate: BatteryUsageEstimate,
    onLowCutClick: () -> Unit,
    onBatteryGuardClick: () -> Unit,
    onRecordOrStop: () -> Unit,
    onPauseOrResume: () -> Unit,
    onSplitNow: () -> Unit,
    onTriggerNextNow: () -> Unit
) {
    val isActive = recordingState.status != RecordingStatus.IDLE
    val isArmedTrigger = recordingState.status == RecordingStatus.ARMED_TRIGGER
    val isPaused = recordingState.status == RecordingStatus.PAUSED
    val isLowBatteryPaused = recordingState.status == RecordingStatus.PAUSED_LOW_BATTERY
    val isIntervalWaiting = recordingState.status == RecordingStatus.WAITING_INTERVAL
    val isScheduleWaiting = recordingState.status == RecordingStatus.WAITING_SCHEDULE

    val buttonScale by animateFloatAsState(
        targetValue = if (recordingState.status == RecordingStatus.RECORDING) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "record_btn_scale"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "record_btn_color"
    )

    val ringColor = MaterialTheme.colorScheme.secondary
    val ringTrackColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary symmetrical row: [Low-Cut Filter] — [Centered Record/Stop] — [Battery Guard]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                LowCutFilterButton(
                    cutoffHz = lowCutFilterHz,
                    onClick = onLowCutClick
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main Record / Stop Button fixed at horizontal center
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .scale(buttonScale)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = ringTrackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke
                    )
                    if (isArmedTrigger) {
                        val preFrac = if (recordingState.triggerConfig.preRecordSeconds > 0) {
                            (recordingState.triggerPreRecordBufferedMs.toFloat() /
                                (recordingState.triggerConfig.preRecordSeconds.toFloat() * 1000f))
                                .coerceIn(0.08f, 1f)
                        } else {
                            1f
                        }
                        drawArc(
                            color = ringColor.copy(alpha = 0.65f),
                            startAngle = -90f,
                            sweepAngle = 360f * preFrac,
                            useCenter = false,
                            style = stroke
                        )
                    } else if (isActive && !isIntervalWaiting && !isScheduleWaiting && !isLowBatteryPaused) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * recordingState.segmentProgress,
                            useCenter = false,
                            style = stroke
                        )
                    }
                }

                Button(
                    onClick = onRecordOrStop,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    ),
                    border = if (isActive) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                    modifier = Modifier
                        .size(76.dp)
                        .testTag("record_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Rounded.Stop else Icons.Rounded.RadioButtonChecked,
                        contentDescription = if (isActive) {
                            stringResource(R.string.action_stop)
                        } else {
                            stringResource(R.string.action_record)
                        },
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                BatteryIconGraphic(
                    estimate = batteryUsageEstimate,
                    protectionEnabled = recordingState.batteryProtectionEnabled,
                    onClick = onBatteryGuardClick
                )
            }
        }

        // Active session secondary controls (Pause/Resume & Split/Now) placed cleanly underneath
        // so the Record/Stop button remains centered at all times.
        AnimatedVisibility(visible = isActive && !isLowBatteryPaused) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onPauseOrResume,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("pause_resume_button")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (isPaused) {
                            stringResource(R.string.action_resume)
                        } else {
                            stringResource(R.string.action_pause)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPaused) "Resume" else "Pause",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }

                val canForceRecordNow = isIntervalWaiting || isArmedTrigger
                if (!isScheduleWaiting) {
                    FilledTonalButton(
                        onClick = if (canForceRecordNow) onTriggerNextNow else onSplitNow,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("split_now_button")
                    ) {
                        Icon(
                            imageVector = if (canForceRecordNow) {
                                Icons.Rounded.PlayArrow
                            } else {
                                Icons.AutoMirrored.Rounded.CallSplit
                            },
                            contentDescription = if (canForceRecordNow) {
                                "Start recording immediately"
                            } else {
                                stringResource(R.string.action_split)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (canForceRecordNow) "Record Now" else "Split Segment",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
