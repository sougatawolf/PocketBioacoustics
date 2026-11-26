package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.AudioQuality
import com.example.audio.RecordingFileFormat
import com.example.audio.StorageHealth
import com.example.audio.StorageUsageEstimate
import com.example.util.Formatters

@Composable
fun StorageIndicatorCard(
    estimate: StorageUsageEstimate,
    quality: AudioQuality,
    fileFormat: RecordingFileFormat = RecordingFileFormat.WAV,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snap = estimate.snapshot
    val freeColor = when (estimate.health) {
        StorageHealth.CRITICAL -> MaterialTheme.colorScheme.primary
        StorageHealth.LOW -> MaterialTheme.colorScheme.secondary
        StorageHealth.HEALTHY -> MaterialTheme.colorScheme.tertiary
    }
    val badgeBg = when (estimate.health) {
        StorageHealth.CRITICAL -> MaterialTheme.colorScheme.primaryContainer
        StorageHealth.LOW -> MaterialTheme.colorScheme.secondaryContainer
        StorageHealth.HEALTHY -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val badgeFg = when (estimate.health) {
        StorageHealth.CRITICAL -> MaterialTheme.colorScheme.onPrimaryContainer
        StorageHealth.LOW -> MaterialTheme.colorScheme.onSecondaryContainer
        StorageHealth.HEALTHY -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    val freeText = Formatters.formatFileSize(snap.availableBytes)
    val totalText = Formatters.formatFileSize(snap.totalBytes)
    val wavUsedText = Formatters.formatFileSize(snap.recordingsUsedBytes)
    val audioCapacityText = Formatters.formatEstimatedRuntime(estimate.audioMinutesAvailable)
    val deploySpanText = Formatters.formatEstimatedRuntime(estimate.deploymentMinutesUntilFull)
    val usedPct = (snap.totalUsedFraction * 100f).toInt().coerceIn(0, 100)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("storage_indicator_card"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (estimate.health == StorageHealth.CRITICAL) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (estimate.health == StorageHealth.CRITICAL) {
                                Icons.Rounded.WarningAmber
                            } else {
                                Icons.Rounded.SdStorage
                            },
                            contentDescription = null,
                            tint = freeColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Disk Usage & Storage",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$freeText remaining of $totalText ($usedPct% used)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeBg,
                        border = BorderStroke(1.dp, freeColor.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("storage_free_badge")
                    ) {
                        Text(
                            text = "$freeText free",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("storage_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh storage usage",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Segmented Disk Usage Bar (Other Used + PocketBioacoustics WAVs + Free)
            SegmentedDiskUsageBar(
                otherUsedFraction = snap.otherUsedFractionOfTotal,
                wavUsedFraction = snap.recordingsFractionOfTotal,
                wavColor = freeColor,
                modifier = Modifier.fillMaxWidth()
            )

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DiskLegendItem(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    label = "Device ${Formatters.formatFileSize(snap.otherUsedBytes)}"
                )
                DiskLegendItem(
                    color = freeColor,
                    label = "Audio $wavUsedText"
                )
                DiskLegendItem(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "Free $freeText"
                )
            }

            // Field Capacity Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StorageStatBox(
                    label = "${fileFormat.label} FITS",
                    value = "~$audioCapacityText",
                    highlight = true,
                    accentColor = freeColor,
                    modifier = Modifier.weight(1f)
                )
                StorageStatBox(
                    label = "FIELD SPAN",
                    value = "~$deploySpanText",
                    highlight = false,
                    accentColor = freeColor,
                    modifier = Modifier.weight(1f)
                )
                StorageStatBox(
                    label = "RATE (${quality.shortLabel})",
                    value = "${Formatters.formatFileSize(estimate.bytesPerHourActive)}/hr",
                    highlight = false,
                    accentColor = freeColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SegmentedDiskUsageBar(
    otherUsedFraction: Float,
    wavUsedFraction: Float,
    wavColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val safeOther = otherUsedFraction.coerceIn(0f, 1f)
    val safeWav = wavUsedFraction.coerceIn(0f, (1f - safeOther).coerceAtLeast(0f))
    val visibleWav = if (wavUsedFraction > 0f) maxOf(safeWav, 0.02f) else 0f
    val clampedOther = safeOther.coerceAtMost(1f - visibleWav)

    Row(
        modifier = modifier
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (clampedOther > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(clampedOther)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            )
        }
        if (visibleWav > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(visibleWav)
                    .background(wavColor)
            )
        }
        val freeWeight = (1f - clampedOther - visibleWav).coerceAtLeast(0f)
        if (freeWeight > 0f) {
            Spacer(modifier = Modifier.weight(freeWeight))
        }
    }
}

@Composable
private fun DiskLegendItem(
    color: androidx.compose.ui.graphics.Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StorageStatBox(
    label: String,
    value: String,
    highlight: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
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
                color = if (highlight) accentColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CompactTelemetryStorageStrip(
    estimate: StorageUsageEstimate,
    fileFormat: RecordingFileFormat = RecordingFileFormat.WAV,
    modifier: Modifier = Modifier
) {
    val snap = estimate.snapshot
    val barColor = when (estimate.health) {
        StorageHealth.CRITICAL -> MaterialTheme.colorScheme.primary
        StorageHealth.LOW -> MaterialTheme.colorScheme.secondary
        StorageHealth.HEALTHY -> MaterialTheme.colorScheme.tertiary
    }
    val freeText = Formatters.formatFileSize(snap.availableBytes)
    val capacityText = Formatters.formatEstimatedRuntime(estimate.audioMinutesAvailable)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("telemetry_storage_strip")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = barColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DISK FREE: $freeText",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "~$capacityText ${fileFormat.label} left",
                    style = MaterialTheme.typography.labelSmall,
                    color = barColor
                )
            }
            SegmentedDiskUsageBar(
                otherUsedFraction = snap.otherUsedFractionOfTotal,
                wavUsedFraction = snap.recordingsFractionOfTotal,
                wavColor = barColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
