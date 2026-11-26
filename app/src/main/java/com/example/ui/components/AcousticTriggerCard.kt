package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.AcousticTriggerConfig
import com.example.audio.RecordingState
import com.example.audio.RecordingStatus
import com.example.audio.SpectrogramZoomMath
import com.example.util.Formatters
import java.util.Locale
import kotlin.math.roundToInt

private data class BandPreset(
    val label: String,
    val minHz: Int,
    val maxHz: Int
)

@Composable
fun AcousticTriggerCard(
    config: AcousticTriggerConfig,
    sampleRateHz: Int,
    status: RecordingStatus,
    liveBandLevelDb: Float,
    preRecordBufferedMs: Long,
    autoStopRemainingMs: Long,
    targetRecordingDurationSeconds: Int,
    onConfigChanged: (AcousticTriggerConfig) -> Unit,
    modifier: Modifier = Modifier,
    lowCutFilterHz: Int = 0
) {
    val nyquistHz = (sampleRateHz / 2).coerceAtLeast(1000)
    val norm = config.normalizedForSampleRate(sampleRateHz)
    val isArmed = status == RecordingStatus.ARMED_TRIGGER
    val isTriggeredRec = status == RecordingStatus.RECORDING && norm.enabled

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (norm.enabled) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row + Master Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onConfigChanged(norm.copy(enabled = !norm.enabled))
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Sensors,
                            contentDescription = null,
                            tint = if (norm.enabled) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Acoustic Trigger & Pre-Rec",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (norm.enabled) {
                            norm.summaryLabel(sampleRateHz)
                        } else {
                            "Monitors mic in background & records when target band crosses threshold"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (norm.enabled) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Switch(
                    checked = norm.enabled,
                    onCheckedChange = { checked ->
                        onConfigChanged(norm.copy(enabled = checked))
                    },
                    modifier = Modifier.testTag("trigger_enable_switch")
                )
            }

            AnimatedVisibility(visible = norm.enabled) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Live Band Energy vs Threshold Meter + Status Strip
                    val bandLabel = norm.bandSummaryLabel(sampleRateHz)
                    val isAboveThreshold = liveBandLevelDb >= norm.amplitudeThresholdDb
                    val meterActive = isArmed || isTriggeredRec

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (isArmed) {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                            } else if (isTriggeredRec) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trigger_live_meter_panel")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusLine = when {
                                    isArmed -> {
                                        if (norm.preRecordSeconds > 0) {
                                            String.format(
                                                Locale.US,
                                                "ARMED • Pre-rec %.1f / %ds buffered",
                                                preRecordBufferedMs / 1000.0,
                                                norm.preRecordSeconds
                                            )
                                        } else {
                                            "ARMED • Listening ($bandLabel)"
                                        }
                                    }
                                    isTriggeredRec ->
                                        "ACTIVE • Auto-stop in ${Formatters.formatDurationHms(autoStopRemainingMs)} if quiet"
                                    else ->
                                        "READY • Press Record to arm background listener"
                                }
                                Text(
                                    text = statusLine,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = when {
                                        isTriggeredRec -> MaterialTheme.colorScheme.primary
                                        isArmed -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (meterActive) {
                                        String.format(Locale.US, "%.1f dB", liveBandLevelDb)
                                    } else {
                                        "≥ ${norm.amplitudeThresholdDb.roundToInt()} dB"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (meterActive && isAboveThreshold) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            val trackCol = MaterialTheme.colorScheme.surfaceContainerHighest
                            val fillCol = when {
                                meterActive && isAboveThreshold -> MaterialTheme.colorScheme.primary
                                meterActive -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            }
                            val threshLineCol = MaterialTheme.colorScheme.secondary

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                            ) {
                                val w = size.width
                                val h = size.height
                                val corner = CornerRadius(h / 2f, h / 2f)
                                drawRoundRect(
                                    color = trackCol,
                                    topLeft = Offset.Zero,
                                    size = Size(w, h),
                                    cornerRadius = corner
                                )

                                val levelFrac = if (meterActive) {
                                    ((liveBandLevelDb - RecordingState.MIN_DB) /
                                        (0f - RecordingState.MIN_DB)).coerceIn(0.02f, 1f)
                                } else {
                                    0.02f
                                }
                                drawRoundRect(
                                    color = fillCol,
                                    topLeft = Offset.Zero,
                                    size = Size(w * levelFrac, h),
                                    cornerRadius = corner
                                )

                                val threshFrac = ((norm.amplitudeThresholdDb - RecordingState.MIN_DB) /
                                    (0f - RecordingState.MIN_DB)).coerceIn(0f, 1f)
                                val threshX = (w * threshFrac).coerceIn(2.dp.toPx(), w - 2.dp.toPx())
                                drawLine(
                                    color = threshLineCol,
                                    start = Offset(threshX, 0f),
                                    end = Offset(threshX, h),
                                    strokeWidth = 2.5.dp.toPx()
                                )
                            }
                        }
                    }

                    // 1. Frequency Range Mode & Band Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Frequency Range",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = bandLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TriggerModeChip(
                                label = "Entire Spectrum",
                                selected = norm.entireFrequencyRange,
                                onClick = {
                                    onConfigChanged(norm.copy(entireFrequencyRange = true))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("trigger_band_entire")
                            )
                            TriggerModeChip(
                                label = "Specific Band",
                                selected = !norm.entireFrequencyRange,
                                onClick = {
                                    onConfigChanged(norm.copy(entireFrequencyRange = false))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("trigger_band_custom")
                            )
                        }

                        AnimatedVisibility(visible = !norm.entireFrequencyRange) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                RangeSlider(
                                    value = norm.minFreqHz.toFloat()..norm.maxFreqHz.toFloat(),
                                    onValueChange = { raw ->
                                        val low = raw.start.roundToInt()
                                            .coerceIn(0, nyquistHz - AcousticTriggerConfig.MIN_BAND_WIDTH_HZ)
                                        val high = raw.endInclusive.roundToInt()
                                            .coerceIn(low + AcousticTriggerConfig.MIN_BAND_WIDTH_HZ, nyquistHz)
                                        onConfigChanged(
                                            norm.copy(
                                                entireFrequencyRange = false,
                                                minFreqHz = low,
                                                maxFreqHz = high
                                            )
                                        )
                                    },
                                    valueRange = 0f..nyquistHz.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.tertiary,
                                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("trigger_freq_range_slider")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "0 Hz",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${SpectrogramZoomMath.formatFreqAxisHz(norm.minFreqHz)} – ${
                                            SpectrogramZoomMath.formatFreqAxisHz(norm.maxFreqHz)
                                        }",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = SpectrogramZoomMath.formatFreqAxisHz(nyquistHz),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val presets = listOf(
                                    BandPreset("Voice", 300, minOf(4000, nyquistHz)),
                                    BandPreset("Frogs", 200, minOf(3500, nyquistHz)),
                                    BandPreset("Birdsong", minOf(1500, nyquistHz - 500), minOf(10000, nyquistHz)),
                                    BandPreset("High/Insects", minOf(6000, nyquistHz - 500), nyquistHz)
                                ).distinctBy { it.minHz to it.maxHz }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    presets.forEach { preset ->
                                        val isPresetSelected = norm.minFreqHz == preset.minHz &&
                                            norm.maxFreqHz == preset.maxHz
                                        TriggerSmallChip(
                                            label = preset.label,
                                            selected = isPresetSelected,
                                            onClick = {
                                                onConfigChanged(
                                                    norm.copy(
                                                        entireFrequencyRange = false,
                                                        minFreqHz = preset.minHz,
                                                        maxFreqHz = preset.maxHz
                                                    ).normalizedForSampleRate(sampleRateHz)
                                                )
                                            },
                                            modifier = Modifier.testTag(
                                                "trigger_band_preset_${preset.label.lowercase().replace("/", "_")}"
                                            )
                                        )
                                    }
                                }

                                if (lowCutFilterHz > 0 && norm.minFreqHz < lowCutFilterHz) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("trigger_low_cut_overlap_warning")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PriorityHigh,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Low-Cut (${lowCutFilterHz} Hz) attenuates audio below ${lowCutFilterHz} Hz before trigger detection.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Amplitude Threshold (-60 to -3 dBFS)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Amplitude Threshold",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${norm.amplitudeThresholdDb.roundToInt()} dBFS",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    val nextDb = (norm.amplitudeThresholdDb - 2f)
                                        .coerceAtLeast(AcousticTriggerConfig.MIN_THRESHOLD_DB)
                                    onConfigChanged(norm.copy(amplitudeThresholdDb = nextDb))
                                },
                                enabled = norm.amplitudeThresholdDb > AcousticTriggerConfig.MIN_THRESHOLD_DB,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("trigger_threshold_decrease")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Lower amplitude threshold"
                                )
                            }

                            Slider(
                                value = norm.amplitudeThresholdDb,
                                onValueChange = { raw ->
                                    onConfigChanged(
                                        norm.copy(amplitudeThresholdDb = raw.roundToInt().toFloat())
                                    )
                                },
                                valueRange = AcousticTriggerConfig.MIN_THRESHOLD_DB..AcousticTriggerConfig.MAX_THRESHOLD_DB,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.secondary,
                                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .testTag("trigger_threshold_slider")
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    val nextDb = (norm.amplitudeThresholdDb + 2f)
                                        .coerceAtMost(AcousticTriggerConfig.MAX_THRESHOLD_DB)
                                    onConfigChanged(norm.copy(amplitudeThresholdDb = nextDb))
                                },
                                enabled = norm.amplitudeThresholdDb < AcousticTriggerConfig.MAX_THRESHOLD_DB,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("trigger_threshold_increase")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Raise amplitude threshold"
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(-48, -36, -24, -12).forEach { dbPreset ->
                                TriggerSmallChip(
                                    label = "$dbPreset dB",
                                    selected = norm.amplitudeThresholdDb.roundToInt() == dbPreset,
                                    onClick = {
                                        onConfigChanged(norm.copy(amplitudeThresholdDb = dbPreset.toFloat()))
                                    },
                                    modifier = Modifier.testTag("trigger_thresh_${-dbPreset}")
                                )
                            }
                        }
                    }

                    // 3. Pre-Recording Lookback Buffer (RAM Ring Buffer)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val ramKb = (norm.preRecordSeconds.toLong() * sampleRateHz.toLong() * 2L) / 1024L
                        val ramLabel = if (norm.preRecordSeconds == 0) {
                            "0 KB RAM"
                        } else if (ramKb >= 1024L) {
                            String.format(Locale.US, "%.1f MB RAM", ramKb / 1024.0)
                        } else {
                            "$ramKb KB RAM"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pre-Recording Buffer",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = ramLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AcousticTriggerConfig.PRE_RECORD_PRESETS.forEach { sec ->
                                TriggerModeChip(
                                    label = if (sec == 0) "Off" else "${sec}s",
                                    selected = norm.preRecordSeconds == sec,
                                    onClick = {
                                        onConfigChanged(norm.copy(preRecordSeconds = sec))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("trigger_prerec_${sec}s")
                                )
                            }
                        }
                    }

                    // 4. Auto-Stop Silence Timeout (1 min - 30 min)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto-Stop Silence Timeout",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${norm.autoStopSilenceMinutes} min",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Text(
                            text = "Saves & re-arms after ${norm.autoStopSilenceMinutes} min below threshold. Events > ${
                                Formatters.formatDurationShort(targetRecordingDurationSeconds)
                            } split cleanly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    val nextMin = (norm.autoStopSilenceMinutes - 1)
                                        .coerceAtLeast(AcousticTriggerConfig.MIN_AUTO_STOP_SILENCE_MINUTES)
                                    onConfigChanged(norm.copy(autoStopSilenceMinutes = nextMin))
                                },
                                enabled = norm.autoStopSilenceMinutes > AcousticTriggerConfig.MIN_AUTO_STOP_SILENCE_MINUTES,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("trigger_autostop_decrease")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Decrease silence auto-stop duration"
                                )
                            }

                            Slider(
                                value = norm.autoStopSilenceMinutes.toFloat(),
                                onValueChange = { raw ->
                                    onConfigChanged(
                                        norm.copy(
                                            autoStopSilenceMinutes = raw.roundToInt().coerceIn(
                                                AcousticTriggerConfig.MIN_AUTO_STOP_SILENCE_MINUTES,
                                                AcousticTriggerConfig.MAX_AUTO_STOP_SILENCE_MINUTES
                                            )
                                        )
                                    )
                                },
                                valueRange = AcousticTriggerConfig.MIN_AUTO_STOP_SILENCE_MINUTES.toFloat()..
                                    AcousticTriggerConfig.MAX_AUTO_STOP_SILENCE_MINUTES.toFloat(),
                                steps = AcousticTriggerConfig.MAX_AUTO_STOP_SILENCE_MINUTES -
                                    AcousticTriggerConfig.MIN_AUTO_STOP_SILENCE_MINUTES - 1,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.tertiary,
                                    activeTrackColor = MaterialTheme.colorScheme.tertiary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .testTag("trigger_autostop_slider")
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    val nextMin = (norm.autoStopSilenceMinutes + 1)
                                        .coerceAtMost(AcousticTriggerConfig.MAX_AUTO_STOP_SILENCE_MINUTES)
                                    onConfigChanged(norm.copy(autoStopSilenceMinutes = nextMin))
                                },
                                enabled = norm.autoStopSilenceMinutes < AcousticTriggerConfig.MAX_AUTO_STOP_SILENCE_MINUTES,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("trigger_autostop_increase")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Increase silence auto-stop duration"
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AcousticTriggerConfig.AUTO_STOP_PRESETS_MINUTES.forEach { mins ->
                                TriggerSmallChip(
                                    label = "${mins}m",
                                    selected = norm.autoStopSilenceMinutes == mins,
                                    onClick = {
                                        onConfigChanged(norm.copy(autoStopSilenceMinutes = mins))
                                    },
                                    modifier = Modifier.testTag("trigger_autostop_${mins}m")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TriggerModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TriggerSmallChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
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
