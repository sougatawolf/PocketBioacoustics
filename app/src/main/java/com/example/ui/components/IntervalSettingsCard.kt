package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.RecorderSettings
import com.example.util.Formatters

private val INTERVAL_PRESETS = listOf(
    300 to "5m",
    900 to "15m",
    1800 to "30m",
    3600 to "1h",
    14400 to "4h",
    43200 to "12h",
    86400 to "24h"
)

@Composable
fun IntervalSettingsCard(
    intervalSeconds: Int,
    continuousNoGap: Boolean,
    recordingDurationSeconds: Int,
    onIntervalSelected: (Int) -> Unit,
    onContinuousNoGapChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isTriggerActive: Boolean = false
) {
    var showExactPicker by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val effectiveContinuous = continuousNoGap || isTriggerActive
    val recText = Formatters.formatDurationShort(recordingDurationSeconds)
    val intText = Formatters.formatDurationShort(intervalSeconds)
    val cycleText = Formatters.formatDurationShort(recordingDurationSeconds + intervalSeconds)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recording Interval",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            isTriggerActive -> "Off automatically • Continuous ON for Acoustic Trigger"
                            effectiveContinuous -> "Back-to-back WAV files (no wait)"
                            else -> "Records $recText, waits $intText ($cycleText cycle)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isTriggerActive) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .alpha(if (effectiveContinuous) 0.45f else 1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !effectiveContinuous) { showExactPicker = true }
                        .testTag("interval_exact_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (effectiveContinuous) "0s gap" else Formatters.formatIntervalVerbose(intervalSeconds),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (!effectiveContinuous) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Enter custom recording interval",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interval Presets (5m to 24h)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .alpha(if (effectiveContinuous) 0.45f else 1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                INTERVAL_PRESETS.forEach { (seconds, label) ->
                    val selected = !effectiveContinuous && intervalSeconds == seconds
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !effectiveContinuous) { onIntervalSelected(seconds) }
                            .testTag("interval_preset_$label")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (effectiveContinuous) 0.45f else 1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val step = intervalStepDeltaFor(intervalSeconds, decrement = true)
                        onIntervalSelected(
                            (intervalSeconds - step).coerceAtLeast(
                                RecorderSettings.MIN_INTERVAL_SECONDS
                            )
                        )
                    },
                    enabled = !effectiveContinuous && intervalSeconds > RecorderSettings.MIN_INTERVAL_SECONDS,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("interval_decrease_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Decrease recording interval"
                    )
                }

                Slider(
                    value = dragFraction ?: Formatters.intervalSecondsToSliderFraction(intervalSeconds),
                    onValueChange = { fraction ->
                        dragFraction = fraction
                        onIntervalSelected(Formatters.intervalSliderFractionToSeconds(fraction))
                    },
                    onValueChangeFinished = {
                        dragFraction = null
                    },
                    enabled = !effectiveContinuous,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("interval_slider")
                )

                FilledTonalIconButton(
                    onClick = {
                        val step = intervalStepDeltaFor(intervalSeconds, decrement = false)
                        onIntervalSelected(
                            (intervalSeconds + step).coerceAtMost(
                                RecorderSettings.MAX_INTERVAL_SECONDS
                            )
                        )
                    },
                    enabled = !effectiveContinuous && intervalSeconds < RecorderSettings.MAX_INTERVAL_SECONDS,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("interval_increase_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Increase recording interval"
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .alpha(if (effectiveContinuous) 0.45f else 1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "5 min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "6 hr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "24 hr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Optional zero-gap continuous toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    if (isTriggerActive) {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onContinuousNoGapChanged(!effectiveContinuous) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Continuous (No Interval Wait)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isTriggerActive) {
                                "Auto-enabled while Acoustic Trigger is ON (Interval Wait off)"
                            } else {
                                "Start next WAV immediately instead of waiting $intText"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTriggerActive) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Switch(
                        checked = effectiveContinuous,
                        onCheckedChange = onContinuousNoGapChanged,
                        modifier = Modifier.testTag("continuous_no_gap_switch")
                    )
                }
            }
        }
    }

    if (showExactPicker) {
        ExactIntervalDialog(
            initialSeconds = intervalSeconds,
            onDismiss = { showExactPicker = false },
            onConfirm = { newSeconds ->
                onIntervalSelected(newSeconds)
                showExactPicker = false
            }
        )
    }
}

private fun intervalStepDeltaFor(currentSeconds: Int, decrement: Boolean): Int {
    return when {
        decrement && currentSeconds <= 3600 -> 300
        !decrement && currentSeconds < 3600 -> 300
        decrement && currentSeconds <= 21600 -> 900
        !decrement && currentSeconds < 21600 -> 900
        else -> 1800
    }
}

@Composable
fun ExactIntervalDialog(
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hoursText by remember { mutableStateOf((initialSeconds / 3600).toString()) }
    var minutesText by remember { mutableStateOf(((initialSeconds % 3600) / 60).toString()) }

    val parsedHours = if (hoursText.isEmpty()) 0 else hoursText.toIntOrNull()
    val parsedMinutes = if (minutesText.isEmpty()) 0 else minutesText.toIntOrNull()
    val fieldsValid = parsedHours != null && parsedMinutes != null &&
        parsedHours in 0..24 && parsedMinutes in 0..59
    val totalSeconds = if (fieldsValid) {
        (parsedHours!! * 3600 + parsedMinutes!! * 60)
    } else {
        -1
    }
    val isValid = fieldsValid &&
        totalSeconds in RecorderSettings.MIN_INTERVAL_SECONDS..RecorderSettings.MAX_INTERVAL_SECONDS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Recording Interval") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Set the wait interval between recordings (5 minutes to 24 hours).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { input ->
                            if (input.length <= 2 && input.all { it.isDigit() }) {
                                hoursText = input
                            }
                        },
                        label = { Text("Hours (0-24)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_interval_hours_input")
                    )
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { input ->
                            if (input.length <= 2 && input.all { it.isDigit() }) {
                                minutesText = input
                            }
                        },
                        label = { Text("Minutes (0-59)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_interval_minutes_input")
                    )
                }
                if (!isValid) {
                    Text(
                        text = "Enter between 0h 05m and 24h 00m (minutes 0–59).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        totalSeconds.coerceIn(
                            RecorderSettings.MIN_INTERVAL_SECONDS,
                            RecorderSettings.MAX_INTERVAL_SECONDS
                        )
                    )
                },
                enabled = isValid
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
