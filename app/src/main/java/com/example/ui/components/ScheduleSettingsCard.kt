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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.RecordingSchedule
import com.example.data.ScheduleMode
import kotlinx.coroutines.delay
import java.util.Calendar

private enum class ScheduleTimeTarget(val dialogTitle: String) {
    CUSTOM_START("Specific Window Start"),
    CUSTOM_END("Specific Window End"),
    DAY_START("Daytime Starts"),
    DAY_END("Daytime Ends"),
    SUNRISE_START("Sunrise Window Start"),
    SUNRISE_END("Sunrise Window End"),
    SUNSET_START("Sunset Window Start"),
    SUNSET_END("Sunset Window End")
}

private data class CustomWindowPreset(
    val label: String,
    val startMinute: Int,
    val endMinute: Int
)

private val CUSTOM_WINDOW_PRESETS = listOf(
    CustomWindowPreset("12 PM – 2 PM", 12 * 60, 14 * 60),
    CustomWindowPreset("9 AM – 5 PM", 9 * 60, 17 * 60),
    CustomWindowPreset("6 PM – 10 PM", 18 * 60, 22 * 60),
    CustomWindowPreset("10 PM – 6 AM", 22 * 60, 6 * 60)
)

@Composable
fun ScheduleSettingsCard(
    schedule: RecordingSchedule,
    onScheduleChanged: (RecordingSchedule) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingTarget by remember { mutableStateOf<ScheduleTimeTarget?>(null) }
    var clockTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(schedule.mode) {
        while (true) {
            clockTickMs = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    val calendar = remember(clockTickMs, schedule) {
        Calendar.getInstance().apply { timeInMillis = clockTickMs }
    }
    val isActiveNow = schedule.isActiveAt(calendar)
    val nextStartMin = schedule.nextWindowStartMinute(calendar)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = iconForScheduleMode(schedule.mode),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recording Schedule",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = schedule.summaryLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActiveNow) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isActiveNow) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                        }
                    )
                ) {
                    Text(
                        text = if (isActiveNow) {
                            "Active Now"
                        } else {
                            "Next ${nextStartMin?.let { RecordingSchedule.formatTimeOfDay(it) } ?: ""}"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isActiveNow) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // 2x2 Mode Selector Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScheduleModeTile(
                        mode = ScheduleMode.ALWAYS,
                        selected = schedule.mode == ScheduleMode.ALWAYS,
                        onClick = { onScheduleChanged(schedule.copy(mode = ScheduleMode.ALWAYS)) },
                        modifier = Modifier.weight(1f)
                    )
                    ScheduleModeTile(
                        mode = ScheduleMode.DAY_ONLY,
                        selected = schedule.mode == ScheduleMode.DAY_ONLY,
                        onClick = { onScheduleChanged(schedule.copy(mode = ScheduleMode.DAY_ONLY)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScheduleModeTile(
                        mode = ScheduleMode.SUNRISE_SUNSET,
                        selected = schedule.mode == ScheduleMode.SUNRISE_SUNSET,
                        onClick = { onScheduleChanged(schedule.copy(mode = ScheduleMode.SUNRISE_SUNSET)) },
                        modifier = Modifier.weight(1f)
                    )
                    ScheduleModeTile(
                        mode = ScheduleMode.CUSTOM_WINDOW,
                        selected = schedule.mode == ScheduleMode.CUSTOM_WINDOW,
                        onClick = { onScheduleChanged(schedule.copy(mode = ScheduleMode.CUSTOM_WINDOW)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Mode-specific time window pickers
            when (schedule.mode) {
                ScheduleMode.ALWAYS -> {
                    Text(
                        text = "Records continuously or by interval at any hour of the day or night.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ScheduleMode.DAY_ONLY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TimeWindowField(
                            label = "DAY STARTS",
                            minuteOfDay = schedule.dayStartMinuteOfDay,
                            onClick = { editingTarget = ScheduleTimeTarget.DAY_START },
                            testTag = "schedule_day_start_button",
                            modifier = Modifier.weight(1f)
                        )
                        TimeWindowField(
                            label = "DAY ENDS",
                            minuteOfDay = schedule.dayEndMinuteOfDay,
                            onClick = { editingTarget = ScheduleTimeTarget.DAY_END },
                            testTag = "schedule_day_end_button",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ScheduleMode.SUNRISE_SUNSET -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TimeWindowField(
                                label = "SUNRISE FROM",
                                minuteOfDay = schedule.sunriseStartMinuteOfDay,
                                onClick = { editingTarget = ScheduleTimeTarget.SUNRISE_START },
                                testTag = "schedule_sunrise_start_button",
                                modifier = Modifier.weight(1f)
                            )
                            TimeWindowField(
                                label = "SUNRISE TO",
                                minuteOfDay = schedule.sunriseEndMinuteOfDay,
                                onClick = { editingTarget = ScheduleTimeTarget.SUNRISE_END },
                                testTag = "schedule_sunrise_end_button",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TimeWindowField(
                                label = "SUNSET FROM",
                                minuteOfDay = schedule.sunsetStartMinuteOfDay,
                                onClick = { editingTarget = ScheduleTimeTarget.SUNSET_START },
                                testTag = "schedule_sunset_start_button",
                                modifier = Modifier.weight(1f)
                            )
                            TimeWindowField(
                                label = "SUNSET TO",
                                minuteOfDay = schedule.sunsetEndMinuteOfDay,
                                onClick = { editingTarget = ScheduleTimeTarget.SUNSET_END },
                                testTag = "schedule_sunset_end_button",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                ScheduleMode.CUSTOM_WINDOW -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TimeWindowField(
                                label = "FROM",
                                minuteOfDay = schedule.customStartMinuteOfDay,
                                onClick = { editingTarget = ScheduleTimeTarget.CUSTOM_START },
                                testTag = "schedule_custom_start_button",
                                modifier = Modifier.weight(1f)
                            )
                            TimeWindowField(
                                label = "TO",
                                minuteOfDay = schedule.customEndMinuteOfDay,
                                onClick = { editingTarget = ScheduleTimeTarget.CUSTOM_END },
                                testTag = "schedule_custom_end_button",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CUSTOM_WINDOW_PRESETS.forEach { preset ->
                                val isSelected = schedule.customStartMinuteOfDay == preset.startMinute &&
                                    schedule.customEndMinuteOfDay == preset.endMinute
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onScheduleChanged(
                                                schedule.copy(
                                                    mode = ScheduleMode.CUSTOM_WINDOW,
                                                    customStartMinuteOfDay = preset.startMinute,
                                                    customEndMinuteOfDay = preset.endMinute
                                                )
                                            )
                                        }
                                        .testTag("schedule_preset_${preset.startMinute}_${preset.endMinute}")
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onTertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingTarget?.let { target ->
        val initialMinuteOfDay = when (target) {
            ScheduleTimeTarget.CUSTOM_START -> schedule.customStartMinuteOfDay
            ScheduleTimeTarget.CUSTOM_END -> schedule.customEndMinuteOfDay
            ScheduleTimeTarget.DAY_START -> schedule.dayStartMinuteOfDay
            ScheduleTimeTarget.DAY_END -> schedule.dayEndMinuteOfDay
            ScheduleTimeTarget.SUNRISE_START -> schedule.sunriseStartMinuteOfDay
            ScheduleTimeTarget.SUNRISE_END -> schedule.sunriseEndMinuteOfDay
            ScheduleTimeTarget.SUNSET_START -> schedule.sunsetStartMinuteOfDay
            ScheduleTimeTarget.SUNSET_END -> schedule.sunsetEndMinuteOfDay
        }

        ScheduleTimePickerDialog(
            title = target.dialogTitle,
            initialMinuteOfDay = initialMinuteOfDay,
            onDismiss = { editingTarget = null },
            onConfirm = { newMinuteOfDay ->
                val updated = when (target) {
                    ScheduleTimeTarget.CUSTOM_START -> schedule.copy(customStartMinuteOfDay = newMinuteOfDay)
                    ScheduleTimeTarget.CUSTOM_END -> schedule.copy(customEndMinuteOfDay = newMinuteOfDay)
                    ScheduleTimeTarget.DAY_START -> schedule.copy(dayStartMinuteOfDay = newMinuteOfDay)
                    ScheduleTimeTarget.DAY_END -> schedule.copy(dayEndMinuteOfDay = newMinuteOfDay)
                    ScheduleTimeTarget.SUNRISE_START -> schedule.copy(sunriseStartMinuteOfDay = newMinuteOfDay)
                    ScheduleTimeTarget.SUNRISE_END -> schedule.copy(sunriseEndMinuteOfDay = newMinuteOfDay)
                    ScheduleTimeTarget.SUNSET_START -> schedule.copy(sunsetStartMinuteOfDay = newMinuteOfDay)
                    ScheduleTimeTarget.SUNSET_END -> schedule.copy(sunsetEndMinuteOfDay = newMinuteOfDay)
                }
                onScheduleChanged(updated)
                editingTarget = null
            }
        )
    }
}

@Composable
private fun ScheduleModeTile(
    mode: ScheduleMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("schedule_mode_${mode.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconForScheduleMode(mode),
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TimeWindowField(
    label: String,
    minuteOfDay: Int,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = RecordingSchedule.formatTimeOfDayFull(minuteOfDay),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Rounded.AccessTime,
                contentDescription = "Change $label time",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimePickerDialog(
    title: String,
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val clamped = initialMinuteOfDay.mod(RecordingSchedule.MINUTES_PER_DAY)
    val state = rememberTimePickerState(
        initialHour = clamped / 60,
        initialMinute = clamped % 60,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TimePicker(state = state)
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(state.hour * 60 + state.minute) },
                modifier = Modifier.testTag("schedule_time_picker_apply")
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

private fun iconForScheduleMode(mode: ScheduleMode): ImageVector {
    return when (mode) {
        ScheduleMode.ALWAYS -> Icons.Rounded.AllInclusive
        ScheduleMode.DAY_ONLY -> Icons.Rounded.WbSunny
        ScheduleMode.SUNRISE_SUNSET -> Icons.Rounded.WbTwilight
        ScheduleMode.CUSTOM_WINDOW -> Icons.Rounded.NightsStay
    }
}
