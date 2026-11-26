package com.example.ui.components

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
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.BatteryUsageEstimate
import com.example.data.RecorderSettings
import com.example.util.Formatters
import java.util.Locale
import kotlin.math.roundToInt

private val BATTERY_THRESHOLD_PRESETS = listOf(5, 10, 15, 20, 30, 50)

/**
 * Interactive Canvas-drawn battery guard & runtime widget sitting directly beside the Record/Stop button.
 * Tapping opens the Battery Guard & Runtime configuration dialog.
 */
@Composable
fun BatteryIconGraphic(
    estimate: BatteryUsageEstimate,
    protectionEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeColor = MaterialTheme.colorScheme.tertiary
    val cautionColor = MaterialTheme.colorScheme.secondary
    val dangerColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceBg = MaterialTheme.colorScheme.surfaceContainerHigh

    val fillColor = when {
        estimate.isBlockedByThreshold -> dangerColor
        estimate.isCharging -> safeColor
        protectionEnabled && estimate.levelPercent <= estimate.thresholdPercent + 10 -> cautionColor
        estimate.levelPercent <= 15 -> dangerColor
        else -> safeColor
    }

    val containerBg = if (estimate.isBlockedByThreshold) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    } else {
        surfaceBg
    }

    val borderStroke = when {
        estimate.isBlockedByThreshold -> BorderStroke(1.5.dp, dangerColor)
        protectionEnabled -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    val runtimeShort = when {
        estimate.isBlockedByThreshold -> "LOW"
        else -> Formatters.formatEstimatedRuntime(estimate.estimatedMinutesRemaining)
            .substringBefore(" ")
    }
    val guardLabel = if (protectionEnabled) "Min ${estimate.thresholdPercent}%" else "Off"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerBg,
        border = borderStroke,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("transport_battery_graphic")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(width = 24.dp, height = 28.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val capWidth = size.width * 0.42f
                    val capHeight = 3.dp.toPx()
                    val capLeft = (size.width - capWidth) / 2f

                    // Battery top terminal cap
                    drawRoundRect(
                        color = outlineColor,
                        topLeft = Offset(capLeft, 0f),
                        size = Size(capWidth, capHeight),
                        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                    )

                    val bodyTop = capHeight + 1.5.dp.toPx()
                    val bodyHeight = (size.height - bodyTop).coerceAtLeast(8f)
                    val strokePx = 1.6.dp.toPx()
                    val innerPad = strokePx + 1.5.dp.toPx()

                    // Battery body outline
                    drawRoundRect(
                        color = if (estimate.isBlockedByThreshold) dangerColor else outlineColor,
                        topLeft = Offset(0f, bodyTop),
                        size = Size(size.width, bodyHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = strokePx)
                    )

                    // Charge fill bar
                    val innerWidth = (size.width - innerPad * 2f).coerceAtLeast(2f)
                    val innerHeight = (bodyHeight - innerPad * 2f).coerceAtLeast(2f)
                    val levelFrac = (estimate.levelPercent / 100f).coerceIn(0f, 1f)
                    val fillHeight = (innerHeight * levelFrac).coerceAtLeast(
                        if (estimate.levelPercent > 0) 2.dp.toPx() else 0f
                    )

                    if (fillHeight > 0f) {
                        drawRoundRect(
                            color = fillColor,
                            topLeft = Offset(innerPad, bodyTop + innerPad + (innerHeight - fillHeight)),
                            size = Size(innerWidth, fillHeight),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }

                    // Threshold cutoff tick line across battery body
                    if (protectionEnabled) {
                        val threshFrac = (estimate.thresholdPercent / 100f).coerceIn(0.05f, 0.95f)
                        val threshY = bodyTop + innerPad + innerHeight * (1f - threshFrac)
                        drawLine(
                            color = cautionColor,
                            start = Offset(1.dp.toPx(), threshY),
                            end = Offset(size.width - 1.dp.toPx(), threshY),
                            strokeWidth = 1.6.dp.toPx()
                        )
                    }
                }

                if (estimate.isCharging) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Charging",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp)
                    )
                } else if (estimate.isBlockedByThreshold) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = "Low battery guard active",
                        tint = dangerColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Text(
                text = "${estimate.levelPercent}% • $runtimeShort",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (estimate.isBlockedByThreshold) {
                    dangerColor
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1
            )
            Text(
                text = if (estimate.isCharging) "CHG • $guardLabel" else "Guard $guardLabel",
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    estimate.isBlockedByThreshold -> dangerColor
                    protectionEnabled -> cautionColor
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

@Composable
fun BatterySettingsCard(
    estimate: BatteryUsageEstimate,
    protectionEnabled: Boolean,
    thresholdPercent: Int,
    onProtectionChanged: (Boolean) -> Unit,
    onThresholdChanged: (Int) -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var dragPercent by remember { mutableStateOf<Float?>(null) }

    val activeBorder = if (estimate.isBlockedByThreshold) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = activeBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                estimate.isBlockedByThreshold -> Icons.Rounded.BatteryAlert
                                estimate.isCharging -> Icons.Rounded.BatteryChargingFull
                                else -> Icons.Rounded.BatteryStd
                            },
                            contentDescription = null,
                            tint = when {
                                estimate.isBlockedByThreshold -> MaterialTheme.colorScheme.primary
                                estimate.isCharging -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.secondary
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Battery Guard & Runtime",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val statusSubtitle = when {
                        estimate.isBlockedByThreshold ->
                            "Blocked: ${estimate.levelPercent}% < $thresholdPercent% safety limit"
                        estimate.isCharging && protectionEnabled ->
                            "Charging (${estimate.levelPercent}%) • Cutoff armed at $thresholdPercent%"
                        protectionEnabled ->
                            "Auto-pauses below $thresholdPercent% to protect field device"
                        else ->
                            "Battery cutoff disabled (monitoring usage only)"
                    }
                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (estimate.isBlockedByThreshold) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (protectionEnabled) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = BorderStroke(
                        1.dp,
                        if (protectionEnabled) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier.testTag("battery_threshold_badge")
                ) {
                    Text(
                        text = if (protectionEnabled) "Min $thresholdPercent%" else "Off",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (protectionEnabled) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Runtime & Battery Usage Telemetry Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BatteryStatBox(
                    label = if (protectionEnabled) "RUNS TO $thresholdPercent%" else "EST. RUNTIME",
                    value = if (estimate.isBlockedByThreshold) {
                        "0m"
                    } else {
                        Formatters.formatEstimatedRuntime(estimate.estimatedMinutesRemaining)
                    },
                    highlight = !estimate.isBlockedByThreshold,
                    modifier = Modifier.weight(1f)
                )
                BatteryStatBox(
                    label = "EST. DRAIN",
                    value = String.format(Locale.US, "%.1f%%/hr", estimate.estimatedDrainPercentPerHour),
                    highlight = false,
                    modifier = Modifier.weight(1f)
                )
                BatteryStatBox(
                    label = if (estimate.sessionUsedPercent > 0) "USED NOW" else "AVG DRAW",
                    value = if (estimate.sessionUsedPercent > 0) {
                        "${estimate.sessionUsedPercent}%"
                    } else {
                        "${estimate.estimatedAverageCurrentMa} mA"
                    },
                    highlight = false,
                    modifier = Modifier.weight(1f)
                )
            }

            // Protection Toggle Row
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProtectionChanged(!protectionEnabled) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Low-Battery Auto-Pause",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Block start & pause recording below $thresholdPercent%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = protectionEnabled,
                        onCheckedChange = onProtectionChanged,
                        modifier = Modifier.testTag("battery_protection_switch")
                    )
                }
            }

            // Threshold Preset Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .alpha(if (protectionEnabled) 1f else 0.45f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BATTERY_THRESHOLD_PRESETS.forEach { preset ->
                    val selected = protectionEnabled && thresholdPercent == preset
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
                            .clickable(enabled = protectionEnabled) { onThresholdChanged(preset) }
                            .testTag("battery_preset_$preset")
                    ) {
                        Text(
                            text = "$preset%",
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

            // Threshold Slider + Step Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (protectionEnabled) 1f else 0.45f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        onThresholdChanged(
                            (thresholdPercent - 5).coerceAtLeast(
                                RecorderSettings.MIN_BATTERY_THRESHOLD_PERCENT
                            )
                        )
                    },
                    enabled = protectionEnabled &&
                        thresholdPercent > RecorderSettings.MIN_BATTERY_THRESHOLD_PERCENT,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("battery_threshold_decrease")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Decrease battery threshold"
                    )
                }

                Slider(
                    value = dragPercent ?: thresholdPercent.toFloat(),
                    onValueChange = { raw ->
                        dragPercent = raw
                        onThresholdChanged(raw.roundToInt())
                    },
                    onValueChangeFinished = { dragPercent = null },
                    valueRange = RecorderSettings.MIN_BATTERY_THRESHOLD_PERCENT.toFloat()..
                        RecorderSettings.MAX_BATTERY_THRESHOLD_PERCENT.toFloat(),
                    enabled = protectionEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("battery_threshold_slider")
                )

                FilledTonalIconButton(
                    onClick = {
                        onThresholdChanged(
                            (thresholdPercent + 5).coerceAtMost(
                                RecorderSettings.MAX_BATTERY_THRESHOLD_PERCENT
                            )
                        )
                    },
                    enabled = protectionEnabled &&
                        thresholdPercent < RecorderSettings.MAX_BATTERY_THRESHOLD_PERCENT,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("battery_threshold_increase")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Increase battery threshold"
                    )
                }
            }

            if (onDismiss != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("battery_guard_done_button")
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryStatBox(
    label: String,
    value: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (highlight) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
