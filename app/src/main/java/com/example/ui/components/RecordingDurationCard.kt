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
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.audio.AudioQuality
import com.example.audio.RecordingFileFormat
import com.example.data.RecorderSettings
import com.example.util.Formatters

private val DURATION_PRESETS = listOf(
    30 to "30s",
    60 to "1m",
    300 to "5m",
    900 to "15m",
    1800 to "30m",
    3600 to "60m"
)

@Composable
fun RecordingDurationCard(
    recordingDurationSeconds: Int,
    quality: AudioQuality,
    fileFormat: RecordingFileFormat = RecordingFileFormat.WAV,
    isRecordingActive: Boolean,
    onDurationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showExactPicker by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

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
                            imageVector = Icons.Rounded.AudioFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recording Duration",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val estimatedSize = Formatters.formatFileSize(
                        quality.estimateSegmentBytes(recordingDurationSeconds, fileFormat)
                    )
                    Text(
                        text = if (isRecordingActive) {
                            "${fileFormat.label} file length • ~$estimatedSize / file"
                        } else {
                            "30s to 60m per ${fileFormat.label} • ~$estimatedSize / file"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showExactPicker = true }
                        .testTag("recording_duration_exact_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Formatters.formatRecordingDurationVerbose(recordingDurationSeconds),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Enter custom recording duration",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DURATION_PRESETS.forEach { (seconds, label) ->
                    val selected = recordingDurationSeconds == seconds
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDurationSelected(seconds) }
                            .testTag("duration_preset_$label")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondary
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val step = durationStepDeltaFor(recordingDurationSeconds, decrement = true)
                        onDurationSelected(
                            (recordingDurationSeconds - step).coerceAtLeast(
                                RecorderSettings.MIN_RECORDING_DURATION_SECONDS
                            )
                        )
                    },
                    enabled = recordingDurationSeconds > RecorderSettings.MIN_RECORDING_DURATION_SECONDS,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("duration_decrease_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Decrease recording duration"
                    )
                }

                Slider(
                    value = dragFraction ?: Formatters.durationSecondsToSliderFraction(recordingDurationSeconds),
                    onValueChange = { fraction ->
                        dragFraction = fraction
                        onDurationSelected(Formatters.durationSliderFractionToSeconds(fraction))
                    },
                    onValueChangeFinished = {
                        dragFraction = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("duration_slider")
                )

                FilledTonalIconButton(
                    onClick = {
                        val step = durationStepDeltaFor(recordingDurationSeconds, decrement = false)
                        onDurationSelected(
                            (recordingDurationSeconds + step).coerceAtMost(
                                RecorderSettings.MAX_RECORDING_DURATION_SECONDS
                            )
                        )
                    },
                    enabled = recordingDurationSeconds < RecorderSettings.MAX_RECORDING_DURATION_SECONDS,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("duration_increase_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Increase recording duration"
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "30 sec",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "5 min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "60 min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showExactPicker) {
        ExactRecordingDurationDialog(
            initialSeconds = recordingDurationSeconds,
            onDismiss = { showExactPicker = false },
            onConfirm = { newSeconds ->
                onDurationSelected(newSeconds)
                showExactPicker = false
            }
        )
    }
}

private fun durationStepDeltaFor(currentSeconds: Int, decrement: Boolean): Int {
    return when {
        decrement && currentSeconds <= 120 -> 15
        !decrement && currentSeconds < 120 -> 15
        decrement && currentSeconds <= 600 -> 60
        !decrement && currentSeconds < 600 -> 60
        else -> 300
    }
}

@Composable
private fun ExactRecordingDurationDialog(
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var minutesText by remember { mutableStateOf((initialSeconds / 60).toString()) }
    var secondsText by remember { mutableStateOf((initialSeconds % 60).toString()) }

    val parsedMinutes = minutesText.toIntOrNull()?.coerceIn(0, 60) ?: 0
    val parsedSeconds = secondsText.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val totalSeconds = (parsedMinutes * 60 + parsedSeconds)
    val isValid = totalSeconds in RecorderSettings.MIN_RECORDING_DURATION_SECONDS..
        RecorderSettings.MAX_RECORDING_DURATION_SECONDS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Recording Duration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Set each audio file recording length between 30 seconds and 60 minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { input ->
                            if (input.length <= 2 && input.all { it.isDigit() }) {
                                minutesText = input
                            }
                        },
                        label = { Text("Minutes (0-60)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_duration_minutes_input")
                    )
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { input ->
                            if (input.length <= 2 && input.all { it.isDigit() }) {
                                secondsText = input
                            }
                        },
                        label = { Text("Seconds (0-59)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_duration_seconds_input")
                    )
                }
                if (!isValid) {
                    Text(
                        text = "Duration must be between 0m 30s and 60m 00s.",
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
                            RecorderSettings.MIN_RECORDING_DURATION_SECONDS,
                            RecorderSettings.MAX_RECORDING_DURATION_SECONDS
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
