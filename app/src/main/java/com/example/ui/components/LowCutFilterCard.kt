package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.LowCutFilter
import kotlin.math.roundToInt

/**
 * Interactive Low-Cut (High-Pass) Filter button placed beside the centered Record button.
 * Tapping opens [LowCutFilterCard] where users can cut low-frequency noise up to 200 Hz.
 */
@Composable
fun LowCutFilterButton(
    cutoffHz: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = cutoffHz > 0
    val isAggressive = cutoffHz >= 120
    val curveColor = when {
        isAggressive -> MaterialTheme.colorScheme.error
        isActive -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val containerBg = when {
        isAggressive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        isActive -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderStroke = when {
        isAggressive -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.75f))
        isActive -> BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerBg,
        border = borderStroke,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("transport_low_cut_button")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(width = 30.dp, height = 28.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val padX = 2.dp.toPx()
                    val topY = 6.dp.toPx()
                    val bottomY = h - 4.dp.toPx()
                    val passY = topY + 2.dp.toPx()

                    // Baseline axis
                    drawLine(
                        color = gridColor,
                        start = Offset(padX, bottomY),
                        end = Offset(w - padX, bottomY),
                        strokeWidth = 1.dp.toPx()
                    )

                    if (!isActive) {
                        // Flat bypass curve
                        drawLine(
                            color = curveColor,
                            start = Offset(padX, passY + 3.dp.toPx()),
                            end = Offset(w - padX, passY + 3.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    } else {
                        val frac = ((cutoffHz - LowCutFilter.MIN_ACTIVE_CUTOFF_HZ).toFloat() /
                            (LowCutFilter.MAX_CUTOFF_HZ - LowCutFilter.MIN_ACTIVE_CUTOFF_HZ).toFloat())
                            .coerceIn(0f, 1f)
                        val kneeX = padX + (w - padX * 2f) * (0.28f + frac * 0.40f)
                        val slopeStartX = (kneeX - w * 0.24f).coerceAtLeast(padX)

                        // Cutoff marker line
                        drawLine(
                            color = curveColor.copy(alpha = 0.5f),
                            start = Offset(kneeX, passY),
                            end = Offset(kneeX, bottomY),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                        )

                        // High-pass roll-off curve
                        val path = Path().apply {
                            moveTo(slopeStartX, bottomY - 1.dp.toPx())
                            cubicTo(
                                slopeStartX + (kneeX - slopeStartX) * 0.45f,
                                bottomY - 1.dp.toPx(),
                                kneeX - (kneeX - slopeStartX) * 0.25f,
                                passY,
                                kneeX + 2.dp.toPx(),
                                passY
                            )
                            lineTo(w - padX, passY)
                        }
                        drawPath(
                            path = path,
                            color = curveColor,
                            style = Stroke(
                                width = 2.2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                if (isActive) {
                    // Subtle exclamation indicator in corner for call-cut caution
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PriorityHigh,
                            contentDescription = "Low-cut caution",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(8.dp)
                        )
                    }
                }
            }

            Text(
                text = if (isActive) "$cutoffHz Hz" else "Off",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = when {
                    isAggressive -> MaterialTheme.colorScheme.error
                    isActive -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
            Text(
                text = "Low Cut",
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isAggressive -> MaterialTheme.colorScheme.error
                    isActive -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

@Composable
fun LowCutFilterCard(
    cutoffHz: Int,
    onCutoffChanged: (Int) -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isActive = cutoffHz > 0
    var lastActiveHz by remember {
        mutableIntStateOf(
            if (cutoffHz > 0) cutoffHz else LowCutFilter.DEFAULT_ACTIVE_CUTOFF_HZ
        )
    }
    if (cutoffHz > 0) {
        lastActiveHz = cutoffHz
    }
    var dragHz by remember { mutableStateOf<Float?>(null) }

    val displayHz = if (isActive) {
        dragHz?.roundToInt()?.coerceIn(LowCutFilter.MIN_ACTIVE_CUTOFF_HZ, LowCutFilter.MAX_CUTOFF_HZ)
            ?: cutoffHz
    } else {
        0
    }

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
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = if (isActive) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Low-Cut Filter",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (displayHz > 0) {
                            "Cuts wind, traffic & handling rumble below $displayHz Hz (up to 200 Hz)"
                        } else {
                            "Bypassed • Captures full low-frequency spectrum (e.g. cut 90–200 Hz)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (displayHz > 0) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = BorderStroke(
                        1.dp,
                        if (displayHz > 0) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier.testTag("low_cut_badge")
                ) {
                    Text(
                        text = if (displayHz > 0) "$displayHz Hz" else "Off",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (displayHz > 0) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Red warning banner with exclamation mark
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("low_cut_warning_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PriorityHigh,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "You may miss important low-frequency calls!",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = if (displayHz >= 120) {
                                "Strong low cut at $displayHz Hz attenuates owls, bitterns, doves, large frogs & toads, and low mammal calls."
                            } else {
                                "Enabling low-cut (e.g. 90 Hz up to 200 Hz) removes wind rumble but can weaken or cut owls, bitterns, large frogs, and low mammal vocalizations."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Frequency response curve preview (0 .. 300 Hz)
            LowCutResponseGraph(
                cutoffHz = displayHz,
                modifier = Modifier.fillMaxWidth()
            )

            // Enable / Bypass Toggle Row
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isActive) {
                                onCutoffChanged(LowCutFilter.OFF_HZ)
                            } else {
                                onCutoffChanged(lastActiveHz)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Real-Time High-Pass Filter",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isActive) {
                                "Active at $displayHz Hz on live recording & preview"
                            } else {
                                "Tap to enable (defaults to $lastActiveHz Hz)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onCutoffChanged(lastActiveHz)
                            } else {
                                onCutoffChanged(LowCutFilter.OFF_HZ)
                            }
                        },
                        modifier = Modifier.testTag("low_cut_switch")
                    )
                }
            }

            // Preset chips: Off + 40, 60, 80, 90, 120, 150, 200 Hz
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val offSelected = displayHz == 0
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (offSelected) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = BorderStroke(
                        1.dp,
                        if (offSelected) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCutoffChanged(LowCutFilter.OFF_HZ) }
                        .testTag("low_cut_preset_off")
                ) {
                    Text(
                        text = "Off",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (offSelected) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)
                    )
                }

                LowCutFilter.PRESET_CUTOFFS_HZ.forEach { presetHz ->
                    val selected = displayHz == presetHz
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.tertiary
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
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onCutoffChanged(presetHz) }
                            .testTag("low_cut_preset_$presetHz")
                    ) {
                        Text(
                            text = "${presetHz}Hz",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onTertiary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Cutoff Frequency Slider (20 Hz .. 200 Hz) + Step Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isActive) 1f else 0.5f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val effectiveSliderHz = if (isActive) displayHz else lastActiveHz
                FilledTonalIconButton(
                    onClick = {
                        val next = (effectiveSliderHz - 10).coerceAtLeast(LowCutFilter.MIN_ACTIVE_CUTOFF_HZ)
                        onCutoffChanged(next)
                    },
                    enabled = effectiveSliderHz > LowCutFilter.MIN_ACTIVE_CUTOFF_HZ || !isActive,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("low_cut_decrease")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Decrease low-cut frequency"
                    )
                }

                Slider(
                    value = dragHz ?: effectiveSliderHz.toFloat(),
                    onValueChange = { raw ->
                        dragHz = raw
                        onCutoffChanged(raw.roundToInt())
                    },
                    onValueChangeFinished = { dragHz = null },
                    valueRange = LowCutFilter.MIN_ACTIVE_CUTOFF_HZ.toFloat()..
                        LowCutFilter.MAX_CUTOFF_HZ.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.tertiary,
                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("low_cut_slider")
                )

                FilledTonalIconButton(
                    onClick = {
                        val next = (effectiveSliderHz + 10).coerceAtMost(LowCutFilter.MAX_CUTOFF_HZ)
                        onCutoffChanged(next)
                    },
                    enabled = effectiveSliderHz < LowCutFilter.MAX_CUTOFF_HZ || !isActive,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("low_cut_increase")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Increase low-cut frequency"
                    )
                }
            }

            if (onDismiss != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Range: 20 Hz – 200 Hz • 2nd-order Butterworth",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("low_cut_done_button")
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun LowCutResponseGraph(
    cutoffHz: Int,
    modifier: Modifier = Modifier
) {
    val surfaceBg = MaterialTheme.colorScheme.surfaceContainerLow
    val borderCol = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val gridCol = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val curveCol = if (cutoffHz > 0) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    val cutZoneCol = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
    val markerCol = MaterialTheme.colorScheme.error

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = surfaceBg,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                val w = size.width
                val h = size.height
                val maxGraphHz = 300.0
                val topPad = 8.dp.toPx()
                val bottomPad = 6.dp.toPx()
                val plotHeight = (h - topPad - bottomPad).coerceAtLeast(10f)

                // Horizontal reference lines (0 dB and -12 dB)
                drawLine(
                    color = gridCol,
                    start = Offset(0f, topPad),
                    end = Offset(w, topPad),
                    strokeWidth = 1.dp.toPx()
                )
                val midY = topPad + plotHeight * 0.5f
                drawLine(
                    color = gridCol.copy(alpha = 0.5f),
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )

                if (cutoffHz > 0) {
                    val cutoffX = ((cutoffHz.toDouble() / maxGraphHz) * w).toFloat().coerceIn(0f, w)
                    // Shaded attenuated band below cutoffHz
                    drawRect(
                        color = cutZoneCol,
                        topLeft = Offset(0f, topPad),
                        size = androidx.compose.ui.geometry.Size(cutoffX, plotHeight)
                    )
                    // Cutoff vertical dashed line
                    drawLine(
                        color = markerCol.copy(alpha = 0.75f),
                        start = Offset(cutoffX, topPad),
                        end = Offset(cutoffX, topPad + plotHeight),
                        strokeWidth = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                    )
                }

                // Plot Butterworth response from 5 Hz .. 300 Hz (-24 dB .. 0 dB mapped vertically)
                val steps = 60
                val curvePath = Path()
                for (i in 0..steps) {
                    val frac = i.toFloat() / steps.toFloat()
                    val freq = (frac * maxGraphHz).coerceAtLeast(5.0)
                    val db = LowCutFilter.magnitudeResponseDb(freq, cutoffHz).coerceIn(-24f, 0f)
                    val normY = (-db / 24f).coerceIn(0f, 1f)
                    val x = frac * w
                    val y = topPad + normY * plotHeight
                    if (i == 0) {
                        curvePath.moveTo(x, y)
                    } else {
                        curvePath.lineTo(x, y)
                    }
                }
                drawPath(
                    path = curvePath,
                    color = curveCol,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                if (cutoffHz > 0) {
                    val cutoffX = ((cutoffHz.toDouble() / maxGraphHz) * w).toFloat().coerceIn(0f, w)
                    val dbAtCutoff = LowCutFilter.magnitudeResponseDb(cutoffHz.toDouble(), cutoffHz)
                    val normY = (-dbAtCutoff / 24f).coerceIn(0f, 1f)
                    val dotY = topPad + normY * plotHeight
                    drawCircle(
                        color = markerCol,
                        radius = 4.dp.toPx(),
                        center = Offset(cutoffX, dotY)
                    )
                }
            }

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
                    text = if (cutoffHz > 0) "Cut < $cutoffHz Hz (-3 dB)" else "Flat (0 Hz)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (cutoffHz > 0) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = "200 Hz max",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
