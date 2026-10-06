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

package com.geeksville.mesh.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.database.dao.DiscoveryDao
import com.geeksville.mesh.model.ChannelOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalMeshDiscoveryViewModel @Inject constructor(
    private val engine: LocalMeshDiscoveryEngine,
    discoveryDao: DiscoveryDao,
) : ViewModel() {
    val scanState = engine.scanState
    val rankings = engine.rankings
    val currentSession = engine.currentSession
    val sessions = discoveryDao.getSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val discoveryHome = engine.discoveryHome

    /**
     * Flow indicating whether Discovery is ready to start.
     * Requires both: device connected AND authoritative LoRa configuration available.
     */
    val discoveryReady = engine.discoveryReady
    val modemPresetCapabilities = engine.modemPresetCapabilities

    private val targetSelection = DiscoveryTargetSelection()
    private val _selectedPresetNames = MutableStateFlow<Set<String>>(emptySet())
    val selectedPresetNames = _selectedPresetNames.asStateFlow()

    private val _selectedReport = MutableStateFlow<DiscoveryReport?>(null)
    val selectedReport = _selectedReport.asStateFlow()

    private val _mapEvents = MutableSharedFlow<DiscoveryMap>()
    val mapEvents = _mapEvents.asSharedFlow()

    private val _listEvents = MutableSharedFlow<DiscoveryNodeList>()
    val listEvents = _listEvents.asSharedFlow()

    private val _messageEvents = MutableSharedFlow<String>()
    val messageEvents = _messageEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(discoveryHome, scanState, modemPresetCapabilities) { home, state, capabilities ->
                Triple(home, state.isRunning, capabilities)
            }.collect { (home, scanRunning, capabilities) ->
                    _selectedPresetNames.value = targetSelection.onHomeChanged(
                        home = home,
                        capabilities = capabilities,
                        scanRunning = scanRunning,
                    )
                }
        }
    }

    fun togglePreset(option: ChannelOption) {
        if (!discoveryReady.value || scanState.value.isRunning) return
        _selectedPresetNames.value = targetSelection.toggle(option, modemPresetCapabilities.value)
    }

    fun startScan(selectedPresets: Set<ChannelOption>, dwellSeconds: Long) {
        engine.startScan(selectedPresets, dwellSeconds)
    }

    fun stopScan() {
        engine.stopScan()
    }

    fun requestMap(
        presetResultId: Long,
        nodeClass: DiscoveryNodeClass,
    ) {
        viewModelScope.launch {
            val map = engine.buildDiscoveryMap(presetResultId, nodeClass)
            val hasClassifiedMarker = map?.nodes?.any { it.num != map.localNode?.num } == true
            if (!hasClassifiedMarker) {
                _messageEvents.emit("${nodeClass.displayName()} map unavailable: missing GPS positions")
            } else {
                _mapEvents.emit(requireNotNull(map))
            }
        }
    }

    fun requestList(
        presetResultId: Long,
        nodeClass: DiscoveryNodeClass,
    ) {
        viewModelScope.launch {
            val nodeList = engine.buildDiscoveryNodeList(presetResultId, nodeClass)
            if (nodeList == null) {
                _messageEvents.emit("${nodeClass.displayName()} list unavailable")
            } else {
                _listEvents.emit(nodeList)
            }
        }
    }

    fun requestReport(sessionId: Long) {
        viewModelScope.launch {
            val report = engine.buildDiscoveryReport(sessionId)
            if (report == null) {
                _messageEvents.emit("Discovery report unavailable")
            } else {
                _selectedReport.value = report
            }
        }
    }

    fun closeReport() {
        _selectedReport.value = null
    }

    fun deleteSessions(sessionIds: Set<Long>) {
        viewModelScope.launch {
            engine.deleteDiscoverySessions(sessionIds)
            if (_selectedReport.value?.session?.id in sessionIds) {
                _selectedReport.value = null
            }
            _messageEvents.emit("Discovery report deleted")
        }
    }

    private fun DiscoveryNodeClass.displayName(): String = name.lowercase().replaceFirstChar { it.titlecase() }
}
