package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.screens.ArchiveScreen
import com.example.ui.screens.RecorderScreen

const val ROUTE_STUDIO = "studio"
const val ROUTE_ARCHIVE = "archive"

@Composable
fun WaveLoopAppShell(
    viewModel: RecorderViewModel
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_STUDIO

    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val audioCapabilities by viewModel.audioCapabilities.collectAsStateWithLifecycle()
    val batteryUsageEstimate by viewModel.batteryUsageEstimate.collectAsStateWithLifecycle()
    val storageUsageEstimate by viewModel.storageUsageEstimate.collectAsStateWithLifecycle()
    val archiveState by viewModel.archiveUiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val playbackInspectorState by viewModel.playbackInspectorState.collectAsStateWithLifecycle()
    val transientMessage by viewModel.transientMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val activeMessage = transientMessage
        ?: recordingState.errorMessage
        ?: playbackState.errorMessage

    LaunchedEffect(activeMessage) {
        if (activeMessage != null) {
            snackbarHostState.showSnackbar(activeMessage)
            viewModel.clearTransientMessage()
        }
    }

    fun navigateTo(route: String) {
        if (route == ROUTE_ARCHIVE) {
            viewModel.refreshRecordings()
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_STUDIO,
                            onClick = { navigateTo(ROUTE_STUDIO) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_STUDIO) {
                                        Icons.Rounded.GraphicEq
                                    } else {
                                        Icons.Outlined.GraphicEq
                                    },
                                    contentDescription = stringResource(R.string.nav_studio)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_studio)) },
                            modifier = Modifier.testTag("nav_studio_tab")
                        )

                        NavigationBarItem(
                            selected = currentRoute == ROUTE_ARCHIVE,
                            onClick = { navigateTo(ROUTE_ARCHIVE) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (archiveState.totalFiles > 0) {
                                            Badge { Text("${archiveState.totalFiles}") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentRoute == ROUTE_ARCHIVE) {
                                            Icons.Rounded.LibraryMusic
                                        } else {
                                            Icons.Outlined.LibraryMusic
                                        },
                                        contentDescription = stringResource(R.string.nav_archive)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_archive)) },
                            modifier = Modifier.testTag("nav_archive_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()
                    ) {
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_STUDIO,
                            onClick = { navigateTo(ROUTE_STUDIO) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_STUDIO) {
                                        Icons.Rounded.GraphicEq
                                    } else {
                                        Icons.Outlined.GraphicEq
                                    },
                                    contentDescription = stringResource(R.string.nav_studio)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_studio)) },
                            modifier = Modifier.testTag("nav_studio_rail")
                        )

                        NavigationRailItem(
                            selected = currentRoute == ROUTE_ARCHIVE,
                            onClick = { navigateTo(ROUTE_ARCHIVE) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (archiveState.totalFiles > 0) {
                                            Badge { Text("${archiveState.totalFiles}") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentRoute == ROUTE_ARCHIVE) {
                                            Icons.Rounded.LibraryMusic
                                        } else {
                                            Icons.Outlined.LibraryMusic
                                        },
                                        contentDescription = stringResource(R.string.nav_archive)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_archive)) },
                            modifier = Modifier.testTag("nav_archive_rail")
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_STUDIO
                    ) {
                        composable(ROUTE_STUDIO) {
                            RecorderScreen(
                                recordingState = recordingState,
                                settings = settings,
                                audioCapabilities = audioCapabilities,
                                batteryUsageEstimate = batteryUsageEstimate,
                                storageUsageEstimate = storageUsageEstimate,
                                onProbeCapabilities = viewModel::probeAudioCapabilities,
                                onRefreshStorage = viewModel::refreshStorage,
                                onStartRecording = { viewModel.startRecording(context) },
                                onPauseRecording = { viewModel.pauseRecording(context) },
                                onResumeRecording = { viewModel.resumeRecording(context) },
                                onSplitSegmentNow = { viewModel.splitSegmentNow(context) },
                                onTriggerNextNow = { viewModel.triggerNextCycleNow(context) },
                                onStopRecording = { viewModel.stopRecording(context) },
                                onRecordingDurationChanged = viewModel::setRecordingDurationSeconds,
                                onIntervalChanged = viewModel::setIntervalSeconds,
                                onContinuousNoGapChanged = viewModel::setContinuousNoGap,
                                onQualityChanged = viewModel::setAudioQuality,
                                onFileFormatChanged = viewModel::setFileFormat,
                                onVisualizerModeChanged = viewModel::setVisualizerMode,
                                onGpsTagModeChanged = viewModel::setGpsTagMode,
                                onLowCutFilterChanged = viewModel::setLowCutFilterHz,
                                onAcousticTriggerChanged = viewModel::setAcousticTriggerConfig,
                                onShowMessage = viewModel::showMessage,
                                onScheduleChanged = viewModel::setSchedule,
                                onBatteryProtectionChanged = viewModel::setBatteryProtectionEnabled,
                                onBatteryThresholdChanged = viewModel::setMinBatteryThresholdPercent,
                                onOpenArchive = { navigateTo(ROUTE_ARCHIVE) }
                            )
                        }
                        composable(ROUTE_ARCHIVE) {
                            ArchiveScreen(
                                archiveState = archiveState,
                                playbackState = playbackState,
                                playbackInspectorState = playbackInspectorState,
                                storageUsageEstimate = storageUsageEstimate,
                                onRefresh = viewModel::refreshRecordings,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onSortOrderChange = viewModel::updateSortOrder,
                                onInspectRecording = viewModel::inspectRecording,
                                onTimeRangeChanged = viewModel::updatePlaybackTimeRange,
                                onTogglePlay = viewModel::togglePlayback,
                                onSeekPlayback = viewModel::seekPlayback,
                                onStopPlayback = viewModel::stopPlayback,
                                onRenameRecording = viewModel::renameRecording,
                                onDeleteRecording = viewModel::deleteRecording,
                                onExportRecording = viewModel::exportRecording,
                                onGoToStudio = { navigateTo(ROUTE_STUDIO) }
                            )
                        }
                    }
                }
            }
        }
    }
}
