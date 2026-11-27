package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.UpdateUiState
import com.example.update.AppUpdateInfo
import com.example.update.UpdateCheckFrequency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdateAvailableDialog(
    update: AppUpdateInfo,
    installedVersion: String,
    onConfirmYesUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkipVersion: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "App Update Available",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Installed v$installedVersion  →  New v${update.versionName}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        if (update.releaseTitle.isNotBlank() &&
                            !update.releaseTitle.equals(update.tagName, ignoreCase = true) &&
                            !update.releaseTitle.equals("v${update.versionName}", ignoreCase = true)
                        ) {
                            Text(
                                text = update.releaseTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                val notes = update.parsedNotes
                if (notes.isEmpty) {
                    Text(
                        text = "A new APK release (v${update.versionName}) is available on GitHub Releases.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (notes.features.isNotEmpty()) {
                        ReleaseNoteSection(
                            title = "New Features",
                            items = notes.features,
                            icon = Icons.Rounded.AutoAwesome,
                            accentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (notes.corrections.isNotEmpty()) {
                        ReleaseNoteSection(
                            title = "Fixes & Corrections",
                            items = notes.corrections,
                            icon = Icons.Rounded.Build,
                            accentColor = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (notes.generalNotes.isNotEmpty()) {
                        ReleaseNoteSection(
                            title = if (notes.features.isEmpty() && notes.corrections.isEmpty()) {
                                "What's Included"
                            } else {
                                "Other Notes"
                            },
                            items = notes.generalNotes,
                            icon = Icons.Rounded.CheckCircle,
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = if (update.hasDirectApkAsset) {
                        "Clicking \"Yes, Update\" downloads the new APK from GitHub. Tap the finished download notification to confirm Android's Update prompt."
                    } else {
                        "Clicking \"Yes, Update\" opens the GitHub Release page so you can download the new APK."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmYesUpdate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("update_dialog_yes_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.update_dialog_yes))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onSkipVersion,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("update_dialog_skip_button")
                ) {
                    Text(stringResource(R.string.update_dialog_skip))
                }
                TextButton(
                    onClick = onLater,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("update_dialog_later_button")
                ) {
                    Text(stringResource(R.string.update_dialog_later))
                }
            }
        }
    )
}

@Composable
private fun ReleaseNoteSection(
    title: String,
    items: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        items.forEach { bullet ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
                Text(
                    text = bullet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AppUpdatesCard(
    githubRepo: String,
    frequency: UpdateCheckFrequency,
    lastCheckMs: Long,
    updateUiState: UpdateUiState,
    isRecordingActive: Boolean,
    onSaveRepo: (String) -> Unit,
    onFrequencySelected: (UpdateCheckFrequency) -> Unit,
    onCheckNow: () -> Unit,
    onViewUpdatePopup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var repoDraft by rememberSaveable { mutableStateOf(githubRepo) }
    LaunchedEffect(githubRepo) {
        if (repoDraft != githubRepo) {
            repoDraft = githubRepo
        }
    }

    val hasPendingUpdate = updateUiState.availableUpdate != null

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (hasPendingUpdate) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = if (hasPendingUpdate) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.update_card_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Installed v${updateUiState.installedVersion} (build ${updateUiState.installedVersionCode})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = repoDraft,
                    onValueChange = { repoDraft = it },
                    label = { Text("GitHub repo (owner/repo)") },
                    placeholder = { Text("owner/PocketBioacoustics") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("github_repo_input")
                )
                FilledTonalButton(
                    onClick = { onSaveRepo(repoDraft) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("save_github_repo_button")
                ) {
                    Text("Save")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Launch Check Schedule (only when online & idle)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UpdateCheckFrequency.entries.forEach { option ->
                        val selected = option == frequency
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) {
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onFrequencySelected(option) }
                                .testTag("update_freq_${option.name.lowercase()}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = option.shortLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            val lastCheckedLabel = if (lastCheckMs > 0L) {
                val fmt = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                "Last online check: ${fmt.format(Date(lastCheckMs))}"
            } else {
                "Checks once on app launch (${frequency.label.lowercase()}) when internet is available"
            }
            Text(
                text = lastCheckedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(
                visible = updateUiState.lastError != null || updateUiState.statusMessage != null
            ) {
                val isError = updateUiState.lastError != null
                val bannerText = updateUiState.lastError ?: updateUiState.statusMessage.orEmpty()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isError) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Rounded.Info else Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = if (isError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = bannerText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onCheckNow,
                    enabled = !updateUiState.isChecking && !isRecordingActive,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("check_updates_now_button")
                ) {
                    if (updateUiState.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRecordingActive) "Idle to Check" else "Check Now")
                    }
                }

                val pendingUpdate = updateUiState.availableUpdate
                if (pendingUpdate != null && !isRecordingActive) {
                    Button(
                        onClick = onViewUpdatePopup,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("view_found_update_button")
                    ) {
                        Text("Update v${pendingUpdate.versionName}")
                    }
                }
            }
        }
    }
}
