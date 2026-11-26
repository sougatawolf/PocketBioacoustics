package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AcousticTriggerConfig
import com.example.audio.BatteryUsageEstimate
import com.example.audio.RecordingFileFormat
import com.example.audio.RecordingState
import com.example.audio.RecordingStatus
import com.example.audio.SpectrogramAnalyzer
import com.example.audio.SpectrogramZoomMath
import com.example.audio.TimeZoomMath
import com.example.audio.VisualizerMode
import com.example.util.Formatters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun AudioVisualizerPanel(
    visualizerMode: VisualizerMode,
    amplitudes: List<Float>,
    spectrogramColumns: List<List<Float>>,
    peakFrequencyHz: Int,
    sampleRateHz: Int,
    fileFormat: RecordingFileFormat,
    status: RecordingStatus,
    currentLevelDb: Float,
    peakLevelDb: Float,
    batteryUsageEstimate: BatteryUsageEstimate,
    onVisualizerModeChanged: (VisualizerMode) -> Unit,
    modifier: Modifier = Modifier,
    isSpectrogramExpanded: Boolean = false,
    onToggleSpectrogramExpanded: () -> Unit = {},
    freqRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onFreqRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit = {},
    triggerConfig: AcousticTriggerConfig = AcousticTriggerConfig()
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Visualizer Mode Switcher: Waveform vs Live FFT Spectrogram
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (visualizerMode == VisualizerMode.SPECTROGRAM) {
                    if (isSpectrogramExpanded) "EXPANDED SPECTROGRAM" else "LIVE FFT SPECTROGRAM"
                } else {
                    "LIVE WAVEFORM"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VisualizerMode.entries.forEach { mode ->
                        val selected = mode == visualizerMode
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (selected) {
                                if (mode == VisualizerMode.SPECTROGRAM) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                }
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { onVisualizerModeChanged(mode) }
                                .testTag("visualizer_mode_${mode.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (mode == VisualizerMode.SPECTROGRAM) {
                                        Icons.Rounded.Waves
                                    } else {
                                        Icons.Rounded.GraphicEq
                                    },
                                    contentDescription = null,
                                    tint = if (selected) {
                                        if (mode == VisualizerMode.SPECTROGRAM) {
                                            MaterialTheme.colorScheme.onSecondary
                                        } else {
                                            MaterialTheme.colorScheme.onTertiary
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) {
                                        if (mode == VisualizerMode.SPECTROGRAM) {
                                            MaterialTheme.colorScheme.onSecondary
                                        } else {
                                            MaterialTheme.colorScheme.onTertiary
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (visualizerMode == VisualizerMode.SPECTROGRAM) {
            SpectrogramCanvas(
                columns = spectrogramColumns,
                peakFrequencyHz = peakFrequencyHz,
                sampleRateHz = sampleRateHz,
                status = status,
                currentLevelDb = currentLevelDb,
                isExpanded = isSpectrogramExpanded,
                freqRange = freqRange,
                onFreqRangeChanged = onFreqRangeChanged,
                onToggleExpand = onToggleSpectrogramExpanded,
                triggerConfig = triggerConfig
            )

            SpectrogramZoomToolbar(
                sampleRateHz = sampleRateHz,
                freqRange = freqRange,
                isExpanded = isSpectrogramExpanded,
                onFreqRangeChanged = onFreqRangeChanged,
                onToggleExpand = onToggleSpectrogramExpanded
            )
        } else {
            WaveformCanvas(
                amplitudes = amplitudes,
                fileFormat = fileFormat,
                status = status,
                currentLevelDb = currentLevelDb,
                peakLevelDb = peakLevelDb,
                triggerConfig = triggerConfig
            )
        }

        // Battery Impact Alert when Spectrogram mode is selected (shown in compact view to keep expanded tab maximal)
        AnimatedVisibility(visible = visualizerMode == VisualizerMode.SPECTROGRAM && !isSpectrogramExpanded) {
            val baseRuntime = Formatters.formatEstimatedRuntime(
                batteryUsageEstimate.baselineMinutesWithoutVisualizer
            )
            val specRuntime = Formatters.formatEstimatedRuntime(
                batteryUsageEstimate.estimatedMinutesRemaining
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("spectrogram_battery_alert")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryAlert,
                            contentDescription = "Spectrogram battery alert",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "Spectrogram Battery Alert: +${batteryUsageEstimate.visualizerExtraPercent}% drain (+${batteryUsageEstimate.visualizerExtraMa} mA avg)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Runtime drops $baseRuntime → $specRuntime if kept on. Use Waveform for unattended deployment.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.88f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onVisualizerModeChanged(VisualizerMode.WAVEFORM) }
                            .testTag("switch_to_waveform_alert_btn")
                    ) {
                        Text(
                            text = "Waveform",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpectrogramCanvas(
    columns: List<List<Float>>,
    peakFrequencyHz: Int,
    sampleRateHz: Int,
    status: RecordingStatus,
    currentLevelDb: Float,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    freqRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onFreqRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit = {},
    onToggleExpand: () -> Unit = {},
    triggerConfig: AcousticTriggerConfig = AcousticTriggerConfig()
) {
    val gridColor = Color(0xFF335C6B)
    val bgColor = Color(0xFF071318) // Abyssal pine backdrop for deeper spectral contrast
    val labelLight = Color(0xFFFAF3EC)
    val labelMuted = Color(0xFFA9C3C9)
    val accentPeach = Color(0xFFF9D5B4)
    val accentCoral = Color(0xFFF78473)
    val borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)

    val nyquistHz = (sampleRateHz / 2).coerceAtLeast(1000)
    val startFrac = freqRange.start.coerceIn(0f, 1f - SpectrogramZoomMath.MIN_ZOOM_SPAN)
    val endFrac = freqRange.endInclusive.coerceIn(
        startFrac + SpectrogramZoomMath.MIN_ZOOM_SPAN,
        1f
    )
    val span = (endFrac - startFrac).coerceIn(SpectrogramZoomMath.MIN_ZOOM_SPAN, 1f)
    val visibleMinHz = (startFrac * nyquistHz).roundToInt()
    val visibleMaxHz = (endFrac * nyquistHz).roundToInt()

    val animatedHeight by animateDpAsState(
        targetValue = if (isExpanded) 310.dp else 136.dp,
        label = "spectrogram_height"
    )

    val currentRangeState by rememberUpdatedState(startFrac..endFrac)
    val currentOnRangeChange by rememberUpdatedState(onFreqRangeChanged)
    val currentOnToggleExpand by rememberUpdatedState(onToggleExpand)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .testTag("live_spectrogram_canvas")
            .semantics {
                contentDescription = when (status) {
                    RecordingStatus.ARMED_TRIGGER -> "Acoustic trigger spectrogram monitoring. Tap to expand or collapse."
                    RecordingStatus.RECORDING -> "Live audio spectrogram active. Tap to expand or collapse."
                    RecordingStatus.PAUSED -> "Audio spectrogram paused. Tap to expand or collapse."
                    else -> "Audio spectrogram standby. Tap to expand or collapse."
                }
            }
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var movedBeyondSlop = false
                    var maxPointers = 1
                    var totalPanDistance = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount > maxPointers) {
                            maxPointers = pressedCount
                        }
                        if (pressedCount == 0) {
                            if (!movedBeyondSlop && maxPointers == 1) {
                                currentOnToggleExpand()
                            }
                            break
                        }

                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val rangeNow = currentRangeState
                        val spanNow = rangeNow.endInclusive - rangeNow.start
                        val isZoomed = spanNow < 0.995f

                        if (pressedCount >= 2 && abs(zoom - 1f) > 0.005f) {
                            movedBeyondSlop = true
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val anchorYNorm = (1f - (centroid.y / size.height.toFloat().coerceAtLeast(1f)))
                                .coerceIn(0f, 1f)
                            val (nextStart, nextEnd) = SpectrogramZoomMath.zoomRange(
                                startFrac = rangeNow.start,
                                endFrac = rangeNow.endInclusive,
                                zoomFactor = zoom,
                                anchorNormalizedInView = anchorYNorm
                            )
                            currentOnRangeChange(nextStart..nextEnd)
                            event.changes.forEach { it.consume() }
                        } else if (pressedCount == 1) {
                            totalPanDistance += abs(pan.x) + abs(pan.y)
                            if (totalPanDistance > touchSlop) {
                                movedBeyondSlop = true
                            }
                            // Dragging vertically while zoomed pans frequency window smoothly
                            if (isZoomed && movedBeyondSlop && abs(pan.y) > 0.5f) {
                                val deltaFrac = (pan.y / size.height.toFloat().coerceAtLeast(1f)) * spanNow
                                val (nextStart, nextEnd) = SpectrogramZoomMath.panRange(
                                    startFrac = rangeNow.start,
                                    endFrac = rangeNow.endInclusive,
                                    deltaFrac = deltaFrac
                                )
                                currentOnRangeChange(nextStart..nextEnd)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 52.dp, top = 28.dp, bottom = 10.dp)
                .clipToBounds()
        ) {
            val width = size.width
            val height = size.height
            val colCount = SpectrogramAnalyzer.SPECTROGRAM_COLUMN_COUNT
            val cellWidth = width / colCount.toFloat()

            // Draw scrolling time-frequency heatmap sliced to zoomed [startFrac .. endFrac]
            val padCols = (colCount - columns.size).coerceAtLeast(0)
            for (cIdx in columns.indices) {
                val col = columns[cIdx]
                val binCount = col.size.coerceAtLeast(1)
                val startBin = floor(startFrac * binCount).toInt().coerceIn(0, binCount - 1)
                val endBin = ceil(endFrac * binCount).toInt().coerceIn(startBin + 1, binCount)
                val x = (padCols + cIdx) * cellWidth

                for (bIdx in startBin until endBin) {
                    val intensity = col[bIdx].coerceIn(0f, 1f)
                    if (intensity <= 0.03f) continue

                    val binLowNorm = ((bIdx.toFloat() / binCount.toFloat()) - startFrac) / span
                    val binHighNorm = (((bIdx + 1).toFloat() / binCount.toFloat()) - startFrac) / span
                    val yTop = height * (1f - binHighNorm.coerceIn(0f, 1f))
                    val yBottom = height * (1f - binLowNorm.coerceIn(0f, 1f))
                    val rectH = (yBottom - yTop).coerceAtLeast(1f)

                    drawRect(
                        color = spectrogramHeatColor(intensity),
                        topLeft = Offset(x, yTop),
                        size = Size(cellWidth + 0.6f, rectH + 0.5f)
                    )
                }
            }

            // Horizontal frequency reference lines across current zoomed window
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f), 0f)
            val gridSteps = if (isExpanded) listOf(0.25f, 0.5f, 0.75f) else listOf(0.5f)
            gridSteps.forEach { frac ->
                val y = height * frac
                drawLine(
                    color = gridColor.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 0.9.dp.toPx(),
                    pathEffect = dashEffect
                )
            }

            // Target trigger frequency band highlight & boundaries
            if (triggerConfig.enabled && !triggerConfig.entireFrequencyRange) {
                val normTrig = triggerConfig.normalizedForSampleRate(sampleRateHz)
                val trigLowFrac = (normTrig.minFreqHz.toFloat() / nyquistHz.toFloat()).coerceIn(0f, 1f)
                val trigHighFrac = (normTrig.maxFreqHz.toFloat() / nyquistHz.toFloat()).coerceIn(trigLowFrac, 1f)
                val trigDash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                val trigBorderCol = Color(0xFF4FD1C5)

                if (trigHighFrac < endFrac) {
                    val highView = ((trigHighFrac - startFrac) / span).coerceIn(0f, 1f)
                    val topOutY = height * (1f - highView)
                    if (topOutY > 0f) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.36f),
                            topLeft = Offset.Zero,
                            size = Size(width, topOutY)
                        )
                    }
                    if (trigHighFrac > startFrac) {
                        drawLine(
                            color = trigBorderCol.copy(alpha = 0.85f),
                            start = Offset(0f, topOutY),
                            end = Offset(width, topOutY),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = trigDash
                        )
                    }
                }
                if (trigLowFrac > startFrac) {
                    val lowView = ((trigLowFrac - startFrac) / span).coerceIn(0f, 1f)
                    val bottomOutY = height * (1f - lowView)
                    if (bottomOutY < height) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.36f),
                            topLeft = Offset(0f, bottomOutY),
                            size = Size(width, height - bottomOutY)
                        )
                    }
                    if (trigLowFrac < endFrac) {
                        drawLine(
                            color = trigBorderCol.copy(alpha = 0.85f),
                            start = Offset(0f, bottomOutY),
                            end = Offset(width, bottomOutY),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = trigDash
                        )
                    }
                }
            }

            // Peak frequency indicator tick when active peak falls in visible zoomed range
            if ((status == RecordingStatus.RECORDING || status == RecordingStatus.ARMED_TRIGGER) &&
                peakFrequencyHz > 0
            ) {
                val peakNormInFull = peakFrequencyHz.toFloat() / nyquistHz.toFloat()
                if (peakNormInFull in startFrac..endFrac) {
                    val peakInView = (peakNormInFull - startFrac) / span
                    val peakY = height * (1f - peakInView.coerceIn(0f, 1f))
                    drawLine(
                        color = accentCoral.copy(alpha = 0.85f),
                        start = Offset(width - 12.dp.toPx(), peakY),
                        end = Offset(width, peakY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }

        // Right-edge Frequency Axis labels dynamically scaled to zoomed range
        val tickCount = if (isExpanded) 5 else 3
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 8.dp, top = 26.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            for (t in 0 until tickCount) {
                val fracFromTop = t.toFloat() / (tickCount - 1).toFloat()
                val tickHz = (visibleMaxHz - fracFromTop * (visibleMaxHz - visibleMinHz)).roundToInt()
                Text(
                    text = SpectrogramZoomMath.formatFreqAxisHz(tickHz),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (t == 0) labelLight else labelMuted
                )
            }
        }

        // Top Telemetry Overlay: Level, Peak Frequency & Expand/Collapse indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusLabel = when (status) {
                RecordingStatus.ARMED_TRIGGER -> String.format(Locale.US, "ARMED • %.1f dBFS", currentLevelDb)
                RecordingStatus.RECORDING -> String.format(Locale.US, "FFT 512 • %.1f dBFS", currentLevelDb)
                RecordingStatus.PAUSED -> "SPECTROGRAM PAUSED"
                RecordingStatus.PAUSED_LOW_BATTERY -> "LOW BATTERY PAUSE"
                RecordingStatus.WAITING_INTERVAL -> "NEXT CYCLE STANDBY"
                RecordingStatus.WAITING_SCHEDULE -> "SCHEDULE STANDBY"
                RecordingStatus.IDLE -> "FFT 512 • ${SpectrogramZoomMath.formatFreqAxisHz(nyquistHz)} MAX"
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = when (status) {
                    RecordingStatus.RECORDING -> accentPeach
                    RecordingStatus.ARMED_TRIGGER -> Color(0xFF64E1D5)
                    else -> labelMuted
                }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val liveSpectrum = status == RecordingStatus.RECORDING || status == RecordingStatus.ARMED_TRIGGER
                val peakFreqText = if (liveSpectrum && peakFrequencyHz > 0) {
                    "PEAK ${SpectrogramZoomMath.formatFreqAxisHz(peakFrequencyHz)}"
                } else if (isExpanded) {
                    "PINCH / DRAG ZOOM"
                } else {
                    "TAP TO EXPAND"
                }
                Text(
                    text = peakFreqText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (liveSpectrum && peakFrequencyHz > 0) {
                        accentCoral
                    } else {
                        labelMuted
                    }
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                    contentDescription = if (isExpanded) "Collapse spectrogram" else "Expand spectrogram",
                    tint = accentPeach,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun SpectrogramZoomToolbar(
    sampleRateHz: Int,
    freqRange: ClosedFloatingPointRange<Float>,
    isExpanded: Boolean,
    onFreqRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    onToggleExpand: () -> Unit,
    alwaysShowRangeSlider: Boolean = false
) {
    val nyquistHz = (sampleRateHz / 2).coerceAtLeast(1000)
    val startFrac = freqRange.start.coerceIn(0f, 1f - SpectrogramZoomMath.MIN_ZOOM_SPAN)
    val endFrac = freqRange.endInclusive.coerceIn(
        startFrac + SpectrogramZoomMath.MIN_ZOOM_SPAN,
        1f
    )
    val minHz = (startFrac * nyquistHz).roundToInt()
    val maxHz = (endFrac * nyquistHz).roundToInt()
    val zoomX = SpectrogramZoomMath.zoomLevel(startFrac, endFrac)
    val isZoomed = zoomX > 1.05f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("spectrogram_zoom_controls"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Zoom Out (-) / Range Readout / Zoom In (+)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val (s, e) = SpectrogramZoomMath.zoomRange(startFrac, endFrac, zoomFactor = 0.65f)
                        onFreqRangeChanged(s..e)
                    },
                    enabled = isZoomed,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("spectrogram_zoom_out")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Zoom out frequency range",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isZoomed) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isZoomed) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier.testTag("spectrogram_freq_range_badge")
                ) {
                    Text(
                        text = "${SpectrogramZoomMath.formatFreqAxisHz(minHz)}–${
                            SpectrogramZoomMath.formatFreqAxisHz(maxHz)
                        }",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isZoomed) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        val (s, e) = SpectrogramZoomMath.zoomRange(startFrac, endFrac, zoomFactor = 1.6f)
                        onFreqRangeChanged(s..e)
                    },
                    enabled = (endFrac - startFrac) > SpectrogramZoomMath.MIN_ZOOM_SPAN + 0.01f,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("spectrogram_zoom_in")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Zoom in frequency range",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isZoomed) {
                    FilledTonalIconButton(
                        onClick = { onFreqRangeChanged(0f..1f) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("spectrogram_zoom_reset")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset frequency zoom",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onToggleExpand,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isExpanded) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("spectrogram_expand_toggle")
                ) {
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Rounded.CloseFullscreen
                        } else {
                            Icons.Rounded.OpenInFull
                        },
                        contentDescription = if (isExpanded) {
                            "Collapse spectrogram"
                        } else {
                            "Expand spectrogram"
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Frequency Range Slider & Spectral Shift controls
        AnimatedVisibility(visible = alwaysShowRangeSlider || isExpanded || isZoomed) {
            val span = (endFrac - startFrac).coerceIn(SpectrogramZoomMath.MIN_ZOOM_SPAN, 1f)
            val canShiftDown = isZoomed && startFrac > 0.002f
            val canShiftUp = isZoomed && endFrac < 0.998f
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RangeSlider(
                    value = startFrac..endFrac,
                    onValueChange = { raw ->
                        val clampedStart = raw.start.coerceIn(0f, 1f - SpectrogramZoomMath.MIN_ZOOM_SPAN)
                        val clampedEnd = raw.endInclusive.coerceIn(
                            clampedStart + SpectrogramZoomMath.MIN_ZOOM_SPAN,
                            1f
                        )
                        onFreqRangeChanged(clampedStart..clampedEnd)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spectrogram_freq_range_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Band: ${SpectrogramZoomMath.formatFreqAxisHz(minHz)} → ${
                                SpectrogramZoomMath.formatFreqAxisHz(maxHz)
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Span 0 – ${SpectrogramZoomMath.formatFreqAxisHz(nyquistHz)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                val step = -(span * 0.25f).coerceAtLeast(0.02f)
                                val (s, e) = SpectrogramZoomMath.panRange(startFrac, endFrac, step)
                                onFreqRangeChanged(s..e)
                            },
                            enabled = canShiftDown,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("spectrogram_shift_down")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Shift frequency range down",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                val step = (span * 0.25f).coerceAtLeast(0.02f)
                                val (s, e) = SpectrogramZoomMath.panRange(startFrac, endFrac, step)
                                onFreqRangeChanged(s..e)
                            },
                            enabled = canShiftUp,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("spectrogram_shift_up")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Shift frequency range up",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun spectrogramHeatColor(value: Float): Color {
    val v = value.coerceIn(0f, 1f)
    val c0 = Color(0xFF071318) // abyssal pine noise floor
    val c1 = Color(0xFF0D344A) // deep midnight pine
    val c2 = Color(0xFF175973) // rich botanical ocean teal
    val c3 = Color(0xFF8B3D54) // deep dusk plum-rose bridge
    val c4 = Color(0xFFD64D3E) // deep coral terracotta
    val c5 = Color(0xFFEE9445) // glowing warm amber
    val c6 = Color(0xFFFDE6CB) // crisp warm ivory-peach peak
    return when {
        v < 0.20f -> lerp(c0, c1, v / 0.20f)
        v < 0.40f -> lerp(c1, c2, (v - 0.20f) / 0.20f)
        v < 0.58f -> lerp(c2, c3, (v - 0.40f) / 0.18f)
        v < 0.76f -> lerp(c3, c4, (v - 0.58f) / 0.18f)
        v < 0.90f -> lerp(c4, c5, (v - 0.76f) / 0.14f)
        else -> lerp(c5, c6, (v - 0.90f) / 0.10f)
    }
}

@Composable
fun WaveformCanvas(
    amplitudes: List<Float>,
    status: RecordingStatus,
    currentLevelDb: Float,
    peakLevelDb: Float,
    modifier: Modifier = Modifier,
    fileFormat: RecordingFileFormat = RecordingFileFormat.WAV,
    triggerConfig: AcousticTriggerConfig = AcousticTriggerConfig()
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val bgColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val idleBarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgColor,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .testTag("live_waveform_canvas")
            .semantics {
                contentDescription = when (status) {
                    RecordingStatus.ARMED_TRIGGER -> "Acoustic trigger waveform monitoring"
                    RecordingStatus.RECORDING -> "Live audio waveform active"
                    RecordingStatus.PAUSED -> "Audio waveform paused"
                    RecordingStatus.PAUSED_LOW_BATTERY -> "Recording paused due to low battery"
                    RecordingStatus.WAITING_INTERVAL -> "Waiting for next scheduled interval"
                    RecordingStatus.WAITING_SCHEDULE -> "Waiting for scheduled recording time window"
                    RecordingStatus.IDLE -> "Audio waveform standby"
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            val width = size.width
            val height = size.height
            val midY = height / 2f

            // Subtle technical oscilloscope gridlines
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
            drawLine(
                color = gridColor.copy(alpha = 0.65f),
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = gridColor.copy(alpha = 0.35f),
                start = Offset(0f, height * 0.2f),
                end = Offset(width, height * 0.2f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
            drawLine(
                color = gridColor.copy(alpha = 0.35f),
                start = Offset(0f, height * 0.8f),
                end = Offset(width, height * 0.8f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )

            val threshAmp = if (triggerConfig.enabled) {
                ((triggerConfig.amplitudeThresholdDb - RecordingState.MIN_DB) /
                    (0f - RecordingState.MIN_DB)).coerceIn(0.03f, 1f)
            } else {
                2f
            }

            if (triggerConfig.enabled) {
                val threshHalfOffset = (threshAmp * height * 0.9f) / 2f
                val threshDash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                drawLine(
                    color = secondaryColor.copy(alpha = 0.72f),
                    start = Offset(0f, midY - threshHalfOffset),
                    end = Offset(width, midY - threshHalfOffset),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = threshDash
                )
                drawLine(
                    color = secondaryColor.copy(alpha = 0.72f),
                    start = Offset(0f, midY + threshHalfOffset),
                    end = Offset(width, midY + threshHalfOffset),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = threshDash
                )
            }

            val count = amplitudes.size.coerceAtLeast(1)
            val slotWidth = width / count.toFloat()
            val barWidth = (slotWidth * 0.58f).coerceIn(2.dp.toPx(), 6.dp.toPx())
            val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

            for (i in 0 until count) {
                val amp = if (
                    status == RecordingStatus.IDLE ||
                    status == RecordingStatus.WAITING_INTERVAL ||
                    status == RecordingStatus.WAITING_SCHEDULE ||
                    status == RecordingStatus.PAUSED_LOW_BATTERY
                ) {
                    0.06f
                } else {
                    amplitudes[i].coerceIn(0.03f, 1f)
                }
                val barHeight = (amp * height * 0.9f).coerceAtLeast(4.dp.toPx())
                val x = i * slotWidth + (slotWidth - barWidth) / 2f
                val top = midY - barHeight / 2f

                val barColor = when {
                    status == RecordingStatus.IDLE -> idleBarColor
                    status == RecordingStatus.PAUSED_LOW_BATTERY -> primaryColor.copy(alpha = 0.35f)
                    status == RecordingStatus.WAITING_INTERVAL -> secondaryColor.copy(alpha = 0.3f)
                    status == RecordingStatus.WAITING_SCHEDULE -> tertiaryColor.copy(alpha = 0.3f)
                    status == RecordingStatus.PAUSED -> secondaryColor.copy(alpha = 0.45f)
                    status == RecordingStatus.ARMED_TRIGGER -> {
                        if (amp >= threshAmp) primaryColor else tertiaryColor.copy(alpha = 0.78f)
                    }
                    amp > 0.82f -> primaryColor
                    amp > 0.55f -> secondaryColor
                    else -> tertiaryColor
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner
                )
            }
        }

        // Corner dBFS telemetry readout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            val dbLabel = when (status) {
                RecordingStatus.ARMED_TRIGGER -> String.format(Locale.US, "ARMED • %.1f dBFS", currentLevelDb)
                RecordingStatus.RECORDING -> String.format(Locale.US, "%.1f dBFS", currentLevelDb)
                RecordingStatus.PAUSED -> "INPUT PAUSED"
                RecordingStatus.PAUSED_LOW_BATTERY -> "LOW BATTERY PAUSE"
                RecordingStatus.WAITING_INTERVAL -> "NEXT CYCLE STANDBY"
                RecordingStatus.WAITING_SCHEDULE -> "SCHEDULE STANDBY"
                RecordingStatus.IDLE -> "PCM 16-BIT"
            }

            Text(
                text = dbLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (status == RecordingStatus.RECORDING || status == RecordingStatus.ARMED_TRIGGER) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            val peakText = if (status != RecordingStatus.IDLE && peakLevelDb > RecordingState.MIN_DB) {
                String.format(Locale.US, "PEAK %.1f dB", peakLevelDb)
            } else {
                "${fileFormat.label} LOSSLESS"
            }
            Text(
                text = peakText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Interactive full-recording waveform window for the Recordings tab.
 * Supports synchronized horizontal time zoom/pan, proportional slice projection,
 * tap/drag seeking within the visible window, and off-screen playhead indicators.
 */
@Composable
fun PlaybackWaveformCanvas(
    amplitudes: List<Float>,
    playheadFraction: Float,
    fileFormat: RecordingFileFormat,
    isPlaying: Boolean,
    isLoading: Boolean,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    timeRange: ClosedFloatingPointRange<Float> = 0f..1f,
    dataStartFraction: Float = 0f,
    dataEndFraction: Float = 1f,
    durationMs: Long = 0L,
    isRefining: Boolean = false,
    onTimeRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit = {}
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val bgColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val idleBarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    val minSpan = TimeZoomMath.minSpanForDuration(durationMs)
    val timeStart = timeRange.start.coerceIn(0f, (1f - minSpan).coerceAtLeast(0f))
    val timeEnd = timeRange.endInclusive.coerceIn((timeStart + minSpan).coerceAtMost(1f), 1f)
    val timeSpan = (timeEnd - timeStart).coerceIn(minSpan, 1f)
    val isTimeZoomed = timeSpan < 0.995f

    val dataStart = dataStartFraction.coerceIn(0f, 1f)
    val dataEnd = dataEndFraction.coerceIn((dataStart + 0.0001f).coerceAtMost(1f), 1f)
    val dataSpan = (dataEnd - dataStart).coerceAtLeast(0.0001f)

    val clampedPlayhead = playheadFraction.coerceIn(0f, 1f)
    val currentOnSeek by rememberUpdatedState(onSeekFraction)
    val currentTimeRange by rememberUpdatedState(timeStart..timeEnd)
    val currentMinSpan by rememberUpdatedState(minSpan)
    val currentOnTimeRangeChanged by rememberUpdatedState(onTimeRangeChanged)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgColor,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .testTag("playback_waveform_canvas")
            .semantics {
                contentDescription = if (isPlaying) {
                    "Playing audio waveform envelope. Pinch horizontally to zoom time or tap and drag to seek."
                } else {
                    "Recorded audio waveform envelope. Pinch horizontally to zoom time or tap and drag to seek."
                }
            }
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                val padPx = 12.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var movedBeyondSlop = false
                    var usedMultiTouch = false
                    var maxPointers = 1
                    var totalPanDistance = 0f

                    fun mapXToGlobalFraction(xPx: Float, range: ClosedFloatingPointRange<Float>): Float {
                        val plotWidth = (size.width.toFloat() - padPx * 2f).coerceAtLeast(1f)
                        val normX = ((xPx - padPx) / plotWidth).coerceIn(0f, 1f)
                        val rSpan = (range.endInclusive - range.start).coerceAtLeast(0.001f)
                        return (range.start + normX * rSpan).coerceIn(0f, 1f)
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        val pressedCount = pressed.size
                        if (pressedCount > maxPointers) {
                            maxPointers = pressedCount
                        }
                        if (pressedCount >= 2) {
                            usedMultiTouch = true
                        }
                        if (pressedCount == 0) {
                            if (!movedBeyondSlop && !usedMultiTouch && maxPointers == 1) {
                                currentOnSeek(mapXToGlobalFraction(down.position.x, currentTimeRange))
                            }
                            break
                        }

                        val rangeNow = currentTimeRange
                        val spanNow = (rangeNow.endInclusive - rangeNow.start).coerceAtLeast(0.001f)
                        val pan = event.calculatePan()

                        if (pressedCount >= 2) {
                            val prevMinX = pressed.minOf { it.previousPosition.x }
                            val prevMaxX = pressed.maxOf { it.previousPosition.x }
                            val curMinX = pressed.minOf { it.position.x }
                            val curMaxX = pressed.maxOf { it.position.x }
                            val prevSpanX = prevMaxX - prevMinX
                            val curSpanX = curMaxX - curMinX
                            val horizZoom = if (prevSpanX > 12f && curSpanX > 12f) {
                                (curSpanX / prevSpanX).coerceIn(0.5f, 2.0f)
                            } else {
                                event.calculateZoom()
                            }

                            var updatedRange = rangeNow
                            var consumed = false
                            if (abs(horizZoom - 1f) > 0.004f) {
                                movedBeyondSlop = true
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val plotWidth = (size.width.toFloat() - padPx * 2f).coerceAtLeast(1f)
                                val anchorXNorm = ((centroid.x - padPx) / plotWidth).coerceIn(0f, 1f)
                                val (nextS, nextE) = TimeZoomMath.zoomRange(
                                    startFrac = updatedRange.start,
                                    endFrac = updatedRange.endInclusive,
                                    zoomFactor = horizZoom,
                                    anchorNormalizedInView = anchorXNorm,
                                    minSpan = currentMinSpan
                                )
                                updatedRange = nextS..nextE
                                consumed = true
                            }
                            val updatedSpan = updatedRange.endInclusive - updatedRange.start
                            if (updatedSpan < 0.995f && abs(pan.x) > 0.5f) {
                                movedBeyondSlop = true
                                val plotWidth = (size.width.toFloat() - padPx * 2f).coerceAtLeast(1f)
                                val deltaFrac = -(pan.x / plotWidth) * updatedSpan
                                val (nextS, nextE) = TimeZoomMath.panRange(
                                    startFrac = updatedRange.start,
                                    endFrac = updatedRange.endInclusive,
                                    deltaFrac = deltaFrac,
                                    minSpan = currentMinSpan
                                )
                                updatedRange = nextS..nextE
                                consumed = true
                            }
                            if (consumed) {
                                currentOnTimeRangeChanged(updatedRange)
                                event.changes.forEach { it.consume() }
                            }
                        } else if (pressedCount == 1 && !usedMultiTouch) {
                            totalPanDistance += abs(pan.x) + abs(pan.y)
                            if (totalPanDistance > touchSlop) {
                                movedBeyondSlop = true
                            }
                            if (movedBeyondSlop) {
                                val active = pressed.first()
                                currentOnSeek(mapXToGlobalFraction(active.position.x, rangeNow))
                                active.consume()
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .clipToBounds()
        ) {
            val width = size.width
            val height = size.height
            val midY = height / 2f

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
            drawLine(
                color = gridColor.copy(alpha = 0.65f),
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = gridColor.copy(alpha = 0.32f),
                start = Offset(0f, height * 0.2f),
                end = Offset(width, height * 0.2f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
            drawLine(
                color = gridColor.copy(alpha = 0.32f),
                start = Offset(0f, height * 0.8f),
                end = Offset(width, height * 0.8f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )

            if (amplitudes.isEmpty()) {
                val count = 48
                val slotWidth = width / count.toFloat()
                val barWidth = (slotWidth * 0.6f).coerceIn(2.dp.toPx(), 5.5.dp.toPx())
                val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
                val barHeight = (0.08f * height * 0.9f).coerceAtLeast(4.dp.toPx())
                val top = midY - barHeight / 2f
                for (i in 0 until count) {
                    val x = i * slotWidth + (slotWidth - barWidth) / 2f
                    drawRoundRect(
                        color = idleBarColor,
                        topLeft = Offset(x, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner
                    )
                }
            } else {
                val count = amplitudes.size.coerceAtLeast(1)
                for (i in 0 until count) {
                    val itemStart = dataStart + (i.toFloat() / count.toFloat()) * dataSpan
                    val itemEnd = dataStart + ((i + 1).toFloat() / count.toFloat()) * dataSpan
                    val normLeft = (itemStart - timeStart) / timeSpan
                    val normRight = (itemEnd - timeStart) / timeSpan
                    if (normRight <= 0f || normLeft >= 1f) continue

                    val slotWidth = ((normRight - normLeft) * width).coerceAtLeast(2.dp.toPx())
                    val barWidth = (slotWidth * 0.62f).coerceIn(2.dp.toPx(), 6.dp.toPx())
                    val centerX = ((normLeft + normRight) * 0.5f) * width
                    val x = centerX - barWidth / 2f
                    val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

                    val amp = amplitudes[i].coerceIn(0.03f, 1f)
                    val barHeight = (amp * height * 0.9f).coerceAtLeast(4.dp.toPx())
                    val top = midY - barHeight / 2f

                    val barCenterGlobal = (itemStart + itemEnd) * 0.5f
                    val isPlayed = barCenterGlobal <= clampedPlayhead
                    val baseColor = when {
                        amp > 0.82f -> primaryColor
                        amp > 0.55f -> secondaryColor
                        else -> tertiaryColor
                    }
                    val barColor = if (isPlayed) baseColor else baseColor.copy(alpha = 0.30f)

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner
                    )
                }
            }

            // Crisp playback cursor line when playhead falls inside the visible zoomed time slice
            val inViewFrac = (clampedPlayhead - timeStart) / timeSpan
            if (inViewFrac in 0f..1f) {
                val playX = (width * inViewFrac).coerceIn(0f, width)
                drawLine(
                    color = primaryColor,
                    start = Offset(playX, 0f),
                    end = Offset(playX, height),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = primaryColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(playX, 0f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            val leftLabel = when {
                isLoading -> "ANALYZING WAVEFORM..."
                isRefining -> "WAVEFORM • REFINING..."
                isTimeZoomed -> String.format(Locale.US, "WAVEFORM • %.1fx TIME", (1f / timeSpan))
                else -> "WAVEFORM • 16-BIT PCM"
            }
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isTimeZoomed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${fileFormat.label} • ${if (isTimeZoomed) "PINCH / TAP SEEK" else "TAP OR DRAG TO SEEK"}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        if (isTimeZoomed && (clampedPlayhead < timeStart || clampedPlayhead > timeEnd)) {
            val isLeft = clampedPlayhead < timeStart
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(if (isLeft) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isLeft) "◄ PLAYHEAD" else "PLAYHEAD ►",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Full-recording FFT spectrogram window for the Recordings tab.
 * Supports synchronized horizontal time zoom/pan, vertical frequency zoom/pan,
 * synchronized playhead tracking, and tap-to-expand.
 */
@Composable
fun PlaybackSpectrogramCanvas(
    columns: List<List<Float>>,
    playheadFraction: Float,
    peakFrequencyHz: Int,
    sampleRateHz: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    isExpanded: Boolean,
    freqRange: ClosedFloatingPointRange<Float>,
    onFreqRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    timeRange: ClosedFloatingPointRange<Float> = 0f..1f,
    dataStartFraction: Float = 0f,
    dataEndFraction: Float = 1f,
    durationMs: Long = 0L,
    isRefining: Boolean = false,
    onTimeRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit = {}
) {
    val gridColor = Color(0xFF335C6B)
    val bgColor = Color(0xFF071318)
    val labelLight = Color(0xFFFAF3EC)
    val labelMuted = Color(0xFFA9C3C9)
    val accentPeach = Color(0xFFF9D5B4)
    val accentCoral = Color(0xFFF78473)
    val borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)

    val nyquistHz = (sampleRateHz / 2).coerceAtLeast(1000)
    val startFrac = freqRange.start.coerceIn(0f, 1f - SpectrogramZoomMath.MIN_ZOOM_SPAN)
    val endFrac = freqRange.endInclusive.coerceIn(
        startFrac + SpectrogramZoomMath.MIN_ZOOM_SPAN,
        1f
    )
    val span = (endFrac - startFrac).coerceIn(SpectrogramZoomMath.MIN_ZOOM_SPAN, 1f)
    val visibleMinHz = (startFrac * nyquistHz).roundToInt()
    val visibleMaxHz = (endFrac * nyquistHz).roundToInt()

    val minTimeSpan = TimeZoomMath.minSpanForDuration(durationMs)
    val timeStart = timeRange.start.coerceIn(0f, (1f - minTimeSpan).coerceAtLeast(0f))
    val timeEnd = timeRange.endInclusive.coerceIn((timeStart + minTimeSpan).coerceAtMost(1f), 1f)
    val timeSpan = (timeEnd - timeStart).coerceIn(minTimeSpan, 1f)
    val isTimeZoomed = timeSpan < 0.995f

    val dataStart = dataStartFraction.coerceIn(0f, 1f)
    val dataEnd = dataEndFraction.coerceIn((dataStart + 0.0001f).coerceAtMost(1f), 1f)
    val dataSpan = (dataEnd - dataStart).coerceAtLeast(0.0001f)

    val clampedPlayhead = playheadFraction.coerceIn(0f, 1f)

    val animatedHeight by animateDpAsState(
        targetValue = if (isExpanded) 268.dp else 136.dp,
        label = "playback_spectrogram_height"
    )

    val currentFreqRangeState by rememberUpdatedState(startFrac..endFrac)
    val currentOnFreqRangeChange by rememberUpdatedState(onFreqRangeChanged)
    val currentTimeRangeState by rememberUpdatedState(timeStart..timeEnd)
    val currentMinTimeSpan by rememberUpdatedState(minTimeSpan)
    val currentOnTimeRangeChange by rememberUpdatedState(onTimeRangeChanged)
    val currentOnToggleExpand by rememberUpdatedState(onToggleExpand)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .testTag("playback_spectrogram_canvas")
            .semantics {
                contentDescription = "Recorded audio spectrogram. Pinch horizontally for time zoom, vertically for frequency zoom, drag to pan, or tap to expand."
            }
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                val padLeftPx = 10.dp.toPx()
                val padRightPx = 52.dp.toPx()
                val padTopPx = 26.dp.toPx()
                val padBottomPx = 10.dp.toPx()

                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var movedBeyondSlop = false
                    var usedMultiTouch = false
                    var maxPointers = 1
                    var totalPanDistance = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        val pressedCount = pressed.size
                        if (pressedCount > maxPointers) {
                            maxPointers = pressedCount
                        }
                        if (pressedCount >= 2) {
                            usedMultiTouch = true
                        }
                        if (pressedCount == 0) {
                            if (!movedBeyondSlop && !usedMultiTouch && maxPointers == 1) {
                                currentOnToggleExpand()
                            }
                            break
                        }

                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val freqRangeNow = currentFreqRangeState
                        val freqSpanNow = freqRangeNow.endInclusive - freqRangeNow.start
                        val isFreqZoomed = freqSpanNow < 0.995f
                        val timeRangeNow = currentTimeRangeState
                        val timeSpanNow = timeRangeNow.endInclusive - timeRangeNow.start
                        val isTimeZoomedNow = timeSpanNow < 0.995f

                        val plotWidth = (size.width.toFloat() - padLeftPx - padRightPx).coerceAtLeast(1f)
                        val plotHeight = (size.height.toFloat() - padTopPx - padBottomPx).coerceAtLeast(1f)

                        if (pressedCount >= 2) {
                            val prevMinX = pressed.minOf { it.previousPosition.x }
                            val prevMaxX = pressed.maxOf { it.previousPosition.x }
                            val curMinX = pressed.minOf { it.position.x }
                            val curMaxX = pressed.maxOf { it.position.x }
                            val prevSpanX = prevMaxX - prevMinX
                            val curSpanX = curMaxX - curMinX

                            val prevMinY = pressed.minOf { it.previousPosition.y }
                            val prevMaxY = pressed.maxOf { it.previousPosition.y }
                            val curMinY = pressed.minOf { it.position.y }
                            val curMaxY = pressed.maxOf { it.position.y }
                            val prevSpanY = prevMaxY - prevMinY
                            val curSpanY = curMaxY - curMinY

                            val dxSpan = abs(curSpanX - prevSpanX)
                            val dySpan = abs(curSpanY - prevSpanY)
                            val horizZoom = if (prevSpanX > 12f && curSpanX > 12f) {
                                (curSpanX / prevSpanX).coerceIn(0.5f, 2.0f)
                            } else {
                                zoom
                            }
                            val vertZoom = if (prevSpanY > 12f && curSpanY > 12f) {
                                (curSpanY / prevSpanY).coerceIn(0.5f, 2.0f)
                            } else {
                                zoom
                            }

                            val zoomHoriz = (dxSpan >= dySpan * 1.15f) ||
                                (dxSpan >= dySpan * 0.65f && abs(horizZoom - 1f) > 0.008f)
                            val zoomVert = (dySpan >= dxSpan * 1.15f) ||
                                (dySpan >= dxSpan * 0.65f && abs(vertZoom - 1f) > 0.008f)

                            val centroid = event.calculateCentroid(useCurrent = true)
                            var updatedTime = timeRangeNow
                            var consumed = false

                            if (zoomHoriz && abs(horizZoom - 1f) > 0.004f) {
                                movedBeyondSlop = true
                                val anchorXNorm = ((centroid.x - padLeftPx) / plotWidth).coerceIn(0f, 1f)
                                val (nextS, nextE) = TimeZoomMath.zoomRange(
                                    startFrac = updatedTime.start,
                                    endFrac = updatedTime.endInclusive,
                                    zoomFactor = horizZoom,
                                    anchorNormalizedInView = anchorXNorm,
                                    minSpan = currentMinTimeSpan
                                )
                                updatedTime = nextS..nextE
                                consumed = true
                            }
                            if (zoomVert && abs(vertZoom - 1f) > 0.004f) {
                                movedBeyondSlop = true
                                val anchorYNorm = (1f - ((centroid.y - padTopPx) / plotHeight)).coerceIn(0f, 1f)
                                val (nextS, nextE) = SpectrogramZoomMath.zoomRange(
                                    startFrac = freqRangeNow.start,
                                    endFrac = freqRangeNow.endInclusive,
                                    zoomFactor = vertZoom,
                                    anchorNormalizedInView = anchorYNorm
                                )
                                currentOnFreqRangeChange(nextS..nextE)
                                consumed = true
                            }
                            val updatedTimeSpan = updatedTime.endInclusive - updatedTime.start
                            if (updatedTimeSpan < 0.995f && abs(pan.x) > 0.5f) {
                                movedBeyondSlop = true
                                val deltaFrac = -(pan.x / plotWidth) * updatedTimeSpan
                                val (nextS, nextE) = TimeZoomMath.panRange(
                                    startFrac = updatedTime.start,
                                    endFrac = updatedTime.endInclusive,
                                    deltaFrac = deltaFrac,
                                    minSpan = currentMinTimeSpan
                                )
                                updatedTime = nextS..nextE
                                consumed = true
                            }
                            if (updatedTime != timeRangeNow) {
                                currentOnTimeRangeChange(updatedTime)
                            }
                            if (consumed) {
                                event.changes.forEach { it.consume() }
                            }
                        } else if (pressedCount == 1 && !usedMultiTouch) {
                            totalPanDistance += abs(pan.x) + abs(pan.y)
                            if (totalPanDistance > touchSlop) {
                                movedBeyondSlop = true
                            }
                            if (movedBeyondSlop) {
                                val preferHoriz = isTimeZoomedNow &&
                                    (!isFreqZoomed || abs(pan.x) >= abs(pan.y))
                                if (preferHoriz && abs(pan.x) > 0.5f) {
                                    val deltaFrac = -(pan.x / plotWidth) * timeSpanNow
                                    val (nextS, nextE) = TimeZoomMath.panRange(
                                        startFrac = timeRangeNow.start,
                                        endFrac = timeRangeNow.endInclusive,
                                        deltaFrac = deltaFrac,
                                        minSpan = currentMinTimeSpan
                                    )
                                    currentOnTimeRangeChange(nextS..nextE)
                                    event.changes.forEach { it.consume() }
                                } else if (isFreqZoomed && abs(pan.y) > 0.5f) {
                                    val deltaFrac = (pan.y / plotHeight) * freqSpanNow
                                    val (nextS, nextE) = SpectrogramZoomMath.panRange(
                                        startFrac = freqRangeNow.start,
                                        endFrac = freqRangeNow.endInclusive,
                                        deltaFrac = deltaFrac
                                    )
                                    currentOnFreqRangeChange(nextS..nextE)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 52.dp, top = 26.dp, bottom = 10.dp)
                .clipToBounds()
        ) {
            val width = size.width
            val height = size.height
            val colCount = columns.size.coerceAtLeast(1)

            for (cIdx in columns.indices) {
                val colStart = dataStart + (cIdx.toFloat() / colCount.toFloat()) * dataSpan
                val colEnd = dataStart + ((cIdx + 1).toFloat() / colCount.toFloat()) * dataSpan
                val normLeft = (colStart - timeStart) / timeSpan
                val normRight = (colEnd - timeStart) / timeSpan
                if (normRight <= 0f || normLeft >= 1f) continue

                val x = normLeft.coerceAtLeast(0f) * width
                val rectW = ((normRight.coerceAtMost(1f) - normLeft.coerceAtLeast(0f)) * width)
                    .coerceAtLeast(1f)

                val col = columns[cIdx]
                val binCount = col.size.coerceAtLeast(1)
                val startBin = floor(startFrac * binCount).toInt().coerceIn(0, binCount - 1)
                val endBin = ceil(endFrac * binCount).toInt().coerceIn(startBin + 1, binCount)

                for (bIdx in startBin until endBin) {
                    val intensity = col[bIdx].coerceIn(0f, 1f)
                    if (intensity <= 0.03f) continue

                    val binLowNorm = ((bIdx.toFloat() / binCount.toFloat()) - startFrac) / span
                    val binHighNorm = (((bIdx + 1).toFloat() / binCount.toFloat()) - startFrac) / span
                    val yTop = height * (1f - binHighNorm.coerceIn(0f, 1f))
                    val yBottom = height * (1f - binLowNorm.coerceIn(0f, 1f))
                    val rectH = (yBottom - yTop).coerceAtLeast(1f)

                    drawRect(
                        color = spectrogramHeatColor(intensity),
                        topLeft = Offset(x, yTop),
                        size = Size(rectW + 0.6f, rectH + 0.5f)
                    )
                }
            }

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f), 0f)
            val gridSteps = if (isExpanded) listOf(0.25f, 0.5f, 0.75f) else listOf(0.5f)
            gridSteps.forEach { frac ->
                val y = height * frac
                drawLine(
                    color = gridColor.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 0.9.dp.toPx(),
                    pathEffect = dashEffect
                )
            }

            if (isTimeZoomed) {
                listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                    val x = width * frac
                    drawLine(
                        color = gridColor.copy(alpha = 0.3f),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 0.8.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }
            }

            // Synchronized playback playhead across the zoomed spectrogram slice
            val inViewFrac = (clampedPlayhead - timeStart) / timeSpan
            if (inViewFrac in 0f..1f) {
                val playX = (width * inViewFrac).coerceIn(0f, width)
                drawLine(
                    color = accentPeach.copy(alpha = if (isPlaying) 0.95f else 0.75f),
                    start = Offset(playX, 0f),
                    end = Offset(playX, height),
                    strokeWidth = 1.8.dp.toPx()
                )
            }
        }

        val tickCount = if (isExpanded) 5 else 3
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 8.dp, top = 26.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            for (t in 0 until tickCount) {
                val fracFromTop = t.toFloat() / (tickCount - 1).toFloat()
                val tickHz = (visibleMaxHz - fracFromTop * (visibleMaxHz - visibleMinHz)).roundToInt()
                Text(
                    text = SpectrogramZoomMath.formatFreqAxisHz(tickHz),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (t == 0) labelLight else labelMuted
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val leftLabel = when {
                isLoading -> "FFT 512 • ANALYZING..."
                isRefining -> "FFT 512 • REFINING..."
                columns.isEmpty() -> "FFT 512 • STANDBY"
                else -> "SPECTROGRAM • ${SpectrogramZoomMath.formatFreqAxisHz(nyquistHz)} MAX"
            }
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPlaying || isRefining) accentPeach else labelMuted
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val rightLabel = if (peakFrequencyHz > 0) {
                    "PEAK ${SpectrogramZoomMath.formatFreqAxisHz(peakFrequencyHz)}"
                } else if (isExpanded) {
                    "PINCH / DRAG BAND"
                } else {
                    "TAP TO EXPAND"
                }
                Text(
                    text = rightLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (peakFrequencyHz > 0) accentCoral else labelMuted
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                    contentDescription = if (isExpanded) "Collapse spectrogram" else "Expand spectrogram",
                    tint = accentPeach,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (isTimeZoomed && (clampedPlayhead < timeStart || clampedPlayhead > timeEnd)) {
            val isLeft = clampedPlayhead < timeStart
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF143542).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, accentPeach.copy(alpha = 0.45f)),
                modifier = Modifier
                    .align(if (isLeft) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(start = 10.dp, end = 52.dp, bottom = 8.dp)
            ) {
                Text(
                    text = if (isLeft) "◄ PLAYHEAD" else "PLAYHEAD ►",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accentPeach,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun TimeWindowZoomToolbar(
    durationMs: Long,
    timeRange: ClosedFloatingPointRange<Float>,
    onTimeRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    isRefining: Boolean = false
) {
    val safeDurationMs = durationMs.coerceAtLeast(1L)
    val minSpan = TimeZoomMath.minSpanForDuration(safeDurationMs)
    val startFrac = timeRange.start.coerceIn(0f, (1f - minSpan).coerceAtLeast(0f))
    val endFrac = timeRange.endInclusive.coerceIn((startFrac + minSpan).coerceAtMost(1f), 1f)
    val span = (endFrac - startFrac).coerceIn(minSpan, 1f)
    val isZoomed = span < 0.995f
    val zoomX = (1f / span).coerceAtLeast(1f)

    val windowStartMs = (startFrac * safeDurationMs).toLong()
    val windowEndMs = (endFrac * safeDurationMs).toLong()
    val windowSpanMs = (windowEndMs - windowStartMs).coerceAtLeast(0L)
    val showTenths = windowSpanMs in 1L..14_999L

    val canShiftLeft = isZoomed && startFrac > 0.002f
    val canShiftRight = isZoomed && endFrac < 0.998f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("time_zoom_controls"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val (s, e) = TimeZoomMath.zoomRange(
                            startFrac = startFrac,
                            endFrac = endFrac,
                            zoomFactor = 0.6f,
                            minSpan = minSpan
                        )
                        onTimeRangeChanged(s..e)
                    },
                    enabled = isZoomed,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("time_zoom_out")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Zoom out time window",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isZoomed) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isZoomed) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier.testTag("time_zoom_range_badge")
                ) {
                    Text(
                        text = "${TimeZoomMath.formatTimeMs(windowStartMs, showTenths)}–${
                            TimeZoomMath.formatTimeMs(windowEndMs, showTenths)
                        }",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isZoomed) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        val (s, e) = TimeZoomMath.zoomRange(
                            startFrac = startFrac,
                            endFrac = endFrac,
                            zoomFactor = 1.65f,
                            minSpan = minSpan
                        )
                        onTimeRangeChanged(s..e)
                    },
                    enabled = durationMs > 0L && span > minSpan + 0.005f,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("time_zoom_in")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Zoom in time window",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val step = -(span * 0.25f).coerceAtLeast(0.01f)
                        val (s, e) = TimeZoomMath.panRange(startFrac, endFrac, step, minSpan)
                        onTimeRangeChanged(s..e)
                    },
                    enabled = canShiftLeft,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("time_shift_left")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Shift time window earlier",
                        modifier = Modifier.size(20.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        val step = (span * 0.25f).coerceAtLeast(0.01f)
                        val (s, e) = TimeZoomMath.panRange(startFrac, endFrac, step, minSpan)
                        onTimeRangeChanged(s..e)
                    },
                    enabled = canShiftRight,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("time_shift_right")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Shift time window later",
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isZoomed) {
                    FilledTonalIconButton(
                        onClick = { onTimeRangeChanged(0f..1f) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("time_zoom_reset")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset time zoom",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = isZoomed) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                RangeSlider(
                    value = startFrac..endFrac,
                    onValueChange = { raw ->
                        val clampedStart = raw.start.coerceIn(0f, (1f - minSpan).coerceAtLeast(0f))
                        val clampedEnd = raw.endInclusive.coerceIn(
                            (clampedStart + minSpan).coerceAtMost(1f),
                            1f
                        )
                        onTimeRangeChanged(clampedStart..clampedEnd)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.tertiary,
                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("time_zoom_range_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Window: %s (%.1fx)",
                            TimeZoomMath.formatTimeMs(windowSpanMs, showTenths),
                            zoomX
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = if (isRefining) "Refining slice..." else "Synced waveform & spectrum",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


