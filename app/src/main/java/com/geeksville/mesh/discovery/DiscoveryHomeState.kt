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

import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.model.ModemPresetCapabilities
import com.geeksville.mesh.service.MeshService.ConnectionState
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.LocalOnlyProtos.LocalConfig

data class DiscoveryHomeState(
    val deviceAddress: String?,
    val presetName: String,
    val initialTarget: ChannelOption?,
)

internal fun ConfigProtos.Config.LoRaConfig.toDiscoveryHomeState(
    deviceAddress: String?,
): DiscoveryHomeState {
    val target = takeIf { usePreset }?.let { lora ->
        ChannelOption.entries.firstOrNull { it.modemPreset == lora.modemPreset }
    }
    return DiscoveryHomeState(
        deviceAddress = deviceAddress,
        presetName = if (usePreset) modemPreset.name else CUSTOM_HOME_NAME,
        initialTarget = target,
    )
}

internal fun resolveDiscoveryHome(
    connectionState: ConnectionState,
    localConfig: LocalConfig,
    deviceAddress: String?,
): DiscoveryHomeState? {
    return if (connectionState == ConnectionState.CONNECTED && localConfig.hasLora()) {
        localConfig.lora.toDiscoveryHomeState(deviceAddress)
    } else {
        null
    }
}

internal class DiscoveryTargetSelection {
    private var initializedForCurrentLifecycle = false
    private var initializedDeviceAddress: String? = null
    private var selectedPresetNames: Set<String> = emptySet()

    @Suppress("ReturnCount")
    fun onHomeChanged(
        home: DiscoveryHomeState?,
        capabilities: ModemPresetCapabilities,
        scanRunning: Boolean,
    ): Set<String> {
        if (scanRunning) return selectedPresetNames

        if (home == null) {
            initializedForCurrentLifecycle = false
            initializedDeviceAddress = null
            selectedPresetNames = emptySet()
            return selectedPresetNames
        }

        if (!initializedForCurrentLifecycle || initializedDeviceAddress != home.deviceAddress) {
            initializedDeviceAddress = home.deviceAddress
            initializedForCurrentLifecycle = false
            selectedPresetNames = emptySet()
        }

        if (!capabilities.isResolved) {
            initializedForCurrentLifecycle = false
            selectedPresetNames = emptySet()
            return selectedPresetNames
        }

        val selectableNames = ChannelOption.entries
            .filter { capabilities.isSelectable(it.modemPreset) }
            .mapTo(mutableSetOf()) { it.name }
        selectedPresetNames = selectedPresetNames.intersect(selectableNames)

        if (!initializedForCurrentLifecycle) {
            initializedForCurrentLifecycle = true
            selectedPresetNames = home.initialTarget
                ?.takeIf { it.name in selectableNames }
                ?.let { setOf(it.name) }
                .orEmpty()
        }
        return selectedPresetNames
    }

    fun toggle(
        option: ChannelOption,
        capabilities: ModemPresetCapabilities,
    ): Set<String> {
        if (!capabilities.isSelectable(option.modemPreset)) return selectedPresetNames
        selectedPresetNames = if (option.name in selectedPresetNames) {
            selectedPresetNames - option.name
        } else {
            selectedPresetNames + option.name
        }
        return selectedPresetNames
    }
}

private const val CUSTOM_HOME_NAME = "CUSTOM"
