/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.ui.discovery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.database.entity.DiscoverySessionEntity
import com.geeksville.mesh.discovery.DiscoveryNodeClass
import com.geeksville.mesh.discovery.DiscoveryNodeList
import com.geeksville.mesh.discovery.DiscoveryPresetRank
import com.geeksville.mesh.discovery.DiscoveryScanState
import com.geeksville.mesh.discovery.DiscoveryHomeState
import com.geeksville.mesh.discovery.LocalMeshDiscoveryViewModel
import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.model.ModemPresetCapabilities
import com.geeksville.mesh.model.ModemPresetSupport
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.ScreenFragment
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DiscoveryFragment : ScreenFragment("Discovery") {
    private val uiViewModel: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    DiscoveryScreen(uiViewModel = uiViewModel)
                }
            }
        }
    }
}

@Composable
private fun DiscoveryScreen(
    uiViewModel: UIViewModel,
    viewModel: LocalMeshDiscoveryViewModel = hiltViewModel(),
) {
    val selectedReport by viewModel.selectedReport.collectAsStateWithLifecycle()
    val nodesUiState by uiViewModel.nodesUiState.collectAsStateWithLifecycle()
    var selectedNodeList by remember { mutableStateOf<DiscoveryNodeList?>(null) }

    DiscoveryEventCollectors(
        viewModel = viewModel,
        uiViewModel = uiViewModel,
        onNodeList = { selectedNodeList = it },
    )

    selectedNodeList?.let {
        DiscoveryNodeListDialog(
            nodeList = it,
            distanceUnits = nodesUiState.distanceUnits,
            onDismiss = { selectedNodeList = null },
        )
    }

    val report = selectedReport
    if (report != null) {
        DiscoveryReportScreen(
            report = report,
            onBack = viewModel::closeReport,
            onMap = viewModel::requestMap,
            onList = viewModel::requestList,
            onDelete = { sessionId -> viewModel.deleteSessions(setOf(sessionId)) },
        )
    } else {
        DiscoveryHomeScreen(viewModel = viewModel)
    }
}

@Composable
private fun DiscoveryEventCollectors(
    viewModel: LocalMeshDiscoveryViewModel,
    uiViewModel: UIViewModel,
    onNodeList: (DiscoveryNodeList) -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.mapEvents.collect { uiViewModel.showDiscoveryMap(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.listEvents.collect(onNodeList)
    }

    LaunchedEffect(Unit) {
        viewModel.messageEvents.collect { uiViewModel.showSnackbar(it) }
    }
}

@Suppress("LongMethod")
@Composable
private fun DiscoveryHomeScreen(viewModel: LocalMeshDiscoveryViewModel) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val rankings by viewModel.rankings.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val discoveryHome by viewModel.discoveryHome.collectAsStateWithLifecycle()
    val discoveryReady by viewModel.discoveryReady.collectAsStateWithLifecycle()
    val presetCapabilities by viewModel.modemPresetCapabilities.collectAsStateWithLifecycle()
    val selectedPresetNames by viewModel.selectedPresetNames.collectAsStateWithLifecycle()
    var dwellText by rememberSaveable { mutableStateOf(DEFAULT_DWELL_SECONDS.toString()) }
    var selectedSessionIds by remember { mutableStateOf(emptySet<Long>()) }
    var showDeleteSessionsDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    DiscoveryDeleteSelectionDialog(
        visible = showDeleteSessionsDialog,
        selectedSessionIds = selectedSessionIds,
        onConfirm = {
            viewModel.deleteSessions(selectedSessionIds)
            selectedSessionIds = emptySet()
            showDeleteSessionsDialog = false
        },
        onDismiss = { showDeleteSessionsDialog = false },
    )

    LaunchedEffect(sessions) {
        val visibleSessionIds = sessions.map { it.id }.toSet()
        selectedSessionIds = selectedSessionIds.filterTo(mutableSetOf()) { it in visibleSessionIds }
    }

    val selectedPresets = ChannelOption.entries
        .filter {
            it.name in selectedPresetNames && presetCapabilities.isSelectable(it.modemPreset)
        }
        .toSet()
    val dwellSeconds = dwellText.toLongOrNull()
    val canStart = discoveryReady && presetCapabilities.isResolved && !scanState.isRunning &&
            selectedPresets.isNotEmpty() &&
            dwellSeconds != null && dwellSeconds > 0

    DiscoveryContent(
        scanState = scanState,
        currentSession = currentSession,
        rankings = rankings,
        sessions = sessions,
        selectedPresetNames = selectedPresetNames,
        dwellText = dwellText,
        canStart = canStart,
        discoveryReady = discoveryReady,
        discoveryHome = discoveryHome,
        presetCapabilities = presetCapabilities,
        selectedSessionIds = selectedSessionIds,
        onTogglePreset = viewModel::togglePreset,
        onDwellChange = { dwellText = it.filter(Char::isDigit).take(MAX_DWELL_DIGITS) },
        onStart = {
            viewModel.startScan(selectedPresets, dwellSeconds ?: DEFAULT_DWELL_SECONDS)
        },
        onStop = viewModel::stopScan,
        onMap = viewModel::requestMap,
        onList = viewModel::requestList,
        onCurrentReport = { sessionId -> viewModel.requestReport(sessionId) },
        onSessionClick = { sessionId ->
            if (selectedSessionIds.isEmpty()) {
                viewModel.requestReport(sessionId)
            } else {
                selectedSessionIds = selectedSessionIds.toggled(sessionId)
            }
        },
        onSessionLongClick = { sessionId ->
            selectedSessionIds = selectedSessionIds.toggled(sessionId)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onClearSelection = { selectedSessionIds = emptySet() },
        onSelectAllSessions = {
            selectedSessionIds = if (selectedSessionIds.size == sessions.size) {
                emptySet()
            } else {
                sessions.map { it.id }.toSet()
            }
        },
        onDeleteSelectedSessions = { showDeleteSessionsDialog = true },
    )
}

@Composable
private fun DiscoveryDeleteSelectionDialog(
    visible: Boolean,
    selectedSessionIds: Set<Long>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    DeleteDiscoverySessionsDialog(
        count = selectedSessionIds.size,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
@Suppress("LongMethod", "LongParameterList")
private fun DiscoveryContent(
    scanState: DiscoveryScanState,
    currentSession: DiscoverySessionEntity?,
    rankings: List<DiscoveryPresetRank>,
    sessions: List<DiscoverySessionEntity>,
    selectedPresetNames: Set<String>,
    dwellText: String,
    canStart: Boolean,
    discoveryReady: Boolean,
    discoveryHome: DiscoveryHomeState?,
    presetCapabilities: ModemPresetCapabilities,
    selectedSessionIds: Set<Long>,
    onTogglePreset: (ChannelOption) -> Unit,
    onDwellChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMap: (Long, DiscoveryNodeClass) -> Unit,
    onList: (Long, DiscoveryNodeClass) -> Unit,
    onCurrentReport: (Long) -> Unit,
    onSessionClick: (Long) -> Unit,
    onSessionLongClick: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAllSessions: () -> Unit,
    onDeleteSelectedSessions: () -> Unit,
) {
    Surface {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DiscoveryControls(
                    scanState = scanState,
                    selectedPresetNames = selectedPresetNames,
                    dwellText = dwellText,
                    canStart = canStart,
                    discoveryReady = discoveryReady,
                    discoveryHome = discoveryHome,
                    presetCapabilities = presetCapabilities,
                    onTogglePreset = onTogglePreset,
                    onDwellChange = onDwellChange,
                    onStart = onStart,
                    onStop = onStop,
                )
            }

            item {
                DiscoveryStatus(
                    state = scanState,
                    session = currentSession,
                    onReport = { sessionId -> onCurrentReport(sessionId) },
                )
            }

            if (rankings.isNotEmpty()) {
                discoveryRankingSection(rankings, onMap, onList)
            }

            if (sessions.isNotEmpty()) {
                discoverySessionSection(
                    sessions = sessions,
                    selectedSessionIds = selectedSessionIds,
                    onSessionClick = onSessionClick,
                    onSessionLongClick = onSessionLongClick,
                    onClearSelection = onClearSelection,
                    onSelectAllSessions = onSelectAllSessions,
                    onDeleteSelectedSessions = onDeleteSelectedSessions,
                )
            }
        }
    }
}

@Composable
private fun DiscoveryControls(
    scanState: DiscoveryScanState,
    selectedPresetNames: Set<String>,
    dwellText: String,
    canStart: Boolean,
    discoveryReady: Boolean,
    discoveryHome: DiscoveryHomeState?,
    presetCapabilities: ModemPresetCapabilities,
    onTogglePreset: (ChannelOption) -> Unit,
    onDwellChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val enabled = discoveryReady && !scanState.isRunning
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Local Mesh Discovery", style = MaterialTheme.typography.h6)
        Text(
            text = "Home: ${discoveryHome.discoveryHomeLabel()}",
            style = MaterialTheme.typography.body2,
            color = if (discoveryReady) {
                MaterialTheme.colors.onSurface
            } else {
                MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
            },
        )
        OutlinedTextField(
            value = dwellText,
            onValueChange = onDwellChange,
            label = { Text("Dwell seconds") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        PresetGrid(
            selectedPresetNames = selectedPresetNames,
            onTogglePreset = onTogglePreset,
            enabled = enabled,
            presetCapabilities = presetCapabilities,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = canStart,
                onClick = onStart,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start")
            }
            OutlinedButton(
                enabled = scanState.isRunning,
                onClick = onStop,
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Stop")
            }
        }
    }
}

@Composable
private fun PresetGrid(
    selectedPresetNames: Set<String>,
    onTogglePreset: (ChannelOption) -> Unit,
    enabled: Boolean,
    presetCapabilities: ModemPresetCapabilities,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChannelOption.entries.chunked(PRESET_COLUMNS).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    val support = presetCapabilities.supportFor(option.modemPreset)
                    val optionEnabled = enabled && support == ModemPresetSupport.SUPPORTED
                    val supportSuffix = when (support) {
                        ModemPresetSupport.SUPPORTED -> ""
                        ModemPresetSupport.UNSUPPORTED -> " (Unsupported)"
                        ModemPresetSupport.UNKNOWN -> " (Unknown)"
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .toggleable(
                                value = option.name in selectedPresetNames,
                                enabled = optionEnabled,
                                onValueChange = { onTogglePreset(option) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = option.name in selectedPresetNames,
                            onCheckedChange = null,
                            enabled = optionEnabled,
                        )
                        Text(
                            text = option.displayName() + supportSuffix,
                            color = if (support == ModemPresetSupport.SUPPORTED) {
                                MaterialTheme.colors.onSurface
                            } else {
                                MaterialTheme.colors.onSurface.copy(alpha = DISABLED_PRESET_ALPHA)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryHomeState?.discoveryHomeLabel(): String {
    return this?.initialTarget?.displayName()
        ?: this?.presetName?.lowercase()?.replaceFirstChar { it.titlecase() }
        ?: "Unavailable"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoveryStatus(
    state: DiscoveryScanState,
    session: DiscoverySessionEntity?,
    onReport: (Long) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = session != null,
                onClick = { session?.let { onReport(it.id) } },
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(state.statusText(), style = MaterialTheme.typography.subtitle1)
            (state as? DiscoveryScanState.Dwelling)?.let { dwell ->
                LinearProgressIndicator(
                    progress = ((dwell.totalSeconds - dwell.remainingSeconds).toFloat() /
                            dwell.totalSeconds).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${dwell.remainingSeconds}s remaining on ${dwell.presetName}")
            }
            session?.let {
                Text(
                    "${it.uniqueNodes} nodes, ${it.directNeighbors} direct, ${it.meshNeighbors} mesh"
                )
                if (!it.failureMessage.isNullOrBlank()) Text(it.failureMessage)
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Column {
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.subtitle2)
        Divider()
    }
}

private const val DEFAULT_DWELL_SECONDS = 60L
private const val PRESET_COLUMNS = 2
private const val MAX_DWELL_DIGITS = 4
private const val DISABLED_PRESET_ALPHA = 0.45f
