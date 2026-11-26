package com.example.ui.components

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioCapabilities
import com.example.audio.AudioQuality
import com.example.audio.RecordingFileFormat
import com.example.util.Formatters

@Composable
fun SampleRateSelectorCard(
    capabilities: AudioCapabilities,
    selectedQuality: AudioQuality,
    fileFormat: RecordingFileFormat = RecordingFileFormat.WAV,
    intervalSeconds: Int,
    isRecordingActive: Boolean,
    onQualitySelected: (AudioQuality) -> Unit,
    modifier: Modifier = Modifier
) {
    val coverage = AudioQuality.cumulativeCoverageFor(selectedQuality.sampleRateHz)
    val selectedSpec = coverage.selectedSpec

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
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
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sampling Rate",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val subtitle = buildString {
                        if (capabilities.isProbing) {
                            append("Verifying microphone sample rates...")
                        } else {
                            val devicePrefix = capabilities.inputDeviceLabel?.let { "$it • " } ?: ""
                            val mbPerMin = Formatters.formatFileSize(
                                selectedQuality.estimateSegmentBytes(60, fileFormat)
                            )
                            append("${devicePrefix}~$mbPerMin / min ${fileFormat.label}")
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Max Verified Sample Rate Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.testTag("max_sample_rate_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (capabilities.isProbing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PROBING",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = if (isRecordingActive) {
                                    Icons.Rounded.Lock
                                } else {
                                    Icons.Rounded.Verified
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (capabilities.isHardwareVerified) {
                                    "MAX ${capabilities.maxQuality.shortLabel}"
                                } else {
                                    "UP TO ${capabilities.maxQuality.shortLabel}"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Display strictly the 9 bioacoustic sampling rate classes; grey out unsupported hardware rates (e.g. 192 kHz / 320 kHz)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                capabilities.displayCandidateRates().forEach { quality ->
                    val isSupported = capabilities.isRateSupported(quality.sampleRateHz)
                    val selected = isSupported && quality.sampleRateHz == selectedQuality.sampleRateHz
                    val isMax = isSupported && quality.sampleRateHz == capabilities.maxSampleRateHz
                    val isOptionEnabled = isSupported && !isRecordingActive

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            !isSupported -> MaterialTheme.colorScheme.surfaceContainerLow
                            selected -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                !isSupported -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                selected -> MaterialTheme.colorScheme.tertiary
                                isMax -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .alpha(
                                when {
                                    !isSupported -> 0.38f
                                    isRecordingActive -> 0.65f
                                    else -> 1f
                                }
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = isOptionEnabled) {
                                onQualitySelected(quality)
                            }
                            .testTag("sample_rate_option_${quality.sampleRateHz}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = quality.chipLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        !isSupported -> MaterialTheme.colorScheme.onSurfaceVariant
                                        selected -> MaterialTheme.colorScheme.onTertiary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (isMax) {
                                    Text(
                                        text = "MAX",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.tertiary
                                        }
                                    )
                                }
                            }
                            Text(
                                text = if (isSupported) quality.tierLabel else "Unsupported",
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    !isSupported -> MaterialTheme.colorScheme.onSurfaceVariant
                                    selected -> MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.85f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bioacoustic Coverage + Subscript Specs for Selected Rate
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sample_rate_coverage_panel")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Pets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "What You Can Record (${selectedSpec.shortChipLabel})",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Default / Baseline (8 kHz) always captured
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                append("Default (${coverage.baselineSpec.shortChipLabel}): ")
                            }
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                append(coverage.baselineSpec.animalGroupsAdded)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Cumulative "+" classes unlocked up to selected sample rate
                    coverage.addedTiers.forEachIndexed { index, tierSpec ->
                        val isActiveTier = index == coverage.addedTiers.lastIndex
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActiveTier) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                ) {
                                    append("+")
                                }
                                withStyle(
                                    SpanStyle(
                                        fontWeight = if (isActiveTier) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isActiveTier) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                ) {
                                    append(tierSpec.animalGroupsAdded)
                                }
                                withStyle(
                                    SpanStyle(
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                                    )
                                ) {
                                    append("  (${tierSpec.shortChipLabel})")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("coverage_tier_${tierSpec.sampleRateHz}")
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    // Subscript metadata: usable frequency, WAV size, extra battery cost
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.testTag("sample_rate_subscript_info")
                    ) {
                        SubscriptSpecRow(
                            icon = Icons.Rounded.Waves,
                            label = "Usable Freq",
                            value = selectedSpec.usableFreqRange
                        )
                        val wavValueText = if (fileFormat == RecordingFileFormat.WAV) {
                            selectedSpec.wavSizeSummary
                        } else {
                            val activePerMin = Formatters.formatFileSize(
                                selectedQuality.estimateSegmentBytes(60, fileFormat)
                            )
                            "${selectedSpec.wavSizeSummary} • Est. ${fileFormat.label}: ~$activePerMin/min"
                        }
                        SubscriptSpecRow(
                            icon = Icons.Rounded.SdStorage,
                            label = "WAV Size (16-bit Mono)",
                            value = wavValueText
                        )
                        SubscriptSpecRow(
                            icon = Icons.Rounded.BatteryChargingFull,
                            label = "Extra Battery (vs 8 kHz)",
                            value = selectedSpec.extraBatteryCost
                        )

                        if (selectedSpec.rateFootnoteMark.isNotEmpty() || selectedSpec.ultrasonicHardwareNote) {
                            val notes = buildList {
                                if (selectedSpec.rateFootnoteMark.isNotEmpty()) {
                                    add("* Non-native OS or hardware/external-ADC dependent rate")
                                }
                                if (selectedSpec.ultrasonicHardwareNote) {
                                    add("** Effective ultrasonic bandwidth depends on mic capsule & anti-aliasing filter")
                                }
                            }.joinToString("  •  ")
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            if (isRecordingActive) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sampling rate locked during active capture (${
                        Formatters.formatFileSize(
                            selectedQuality.estimateSegmentBytes(intervalSeconds, fileFormat)
                        )
                    } / segment).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubscriptSpecRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                ) {
                    append("$label: ")
                }
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    append(value)
                }
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        )
    }
}
