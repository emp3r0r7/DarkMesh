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
import com.geeksville.mesh.model.ModemPresetCapabilitySource
import com.geeksville.mesh.service.MeshService.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.LocalOnlyProtos.LocalConfig

class DiscoveryHomeStateTest {
    @Test
    fun readinessRequiresConnectionAndAuthoritativeLoraConfig() {
        val mediumFast = ConfigProtos.Config.LoRaConfig.newBuilder()
            .setUsePreset(true)
            .setModemPreset(ConfigProtos.Config.LoRaConfig.ModemPreset.MEDIUM_FAST)
            .build()
        val authoritativeConfig = LocalConfig.newBuilder().setLora(mediumFast).build()

        assertNull(resolveDiscoveryHome(ConnectionState.DISCONNECTED, authoritativeConfig, RADIO_A))
        assertNull(resolveDiscoveryHome(ConnectionState.CONNECTED, LocalConfig.getDefaultInstance(), RADIO_A))
        assertEquals(
            ChannelOption.MEDIUM_FAST,
            resolveDiscoveryHome(ConnectionState.CONNECTED, authoritativeConfig, RADIO_A)?.initialTarget,
        )
    }

    @Test
    fun delayedAuthoritativeHomeInitializesMatchingPresetOnce() {
        val selection = DiscoveryTargetSelection()

        assertEquals(
            emptySet<String>(),
            selection.onHomeChanged(home = null, capabilities = capabilities(), scanRunning = false),
        )
        assertEquals(
            setOf(ChannelOption.MEDIUM_FAST.name),
            selection.onHomeChanged(
                home(ChannelOption.MEDIUM_FAST, RADIO_A),
                capabilities(),
                scanRunning = false,
            ),
        )
    }

    @Test
    fun unresolvedCapabilityStaysNonActionableUntilProvenSetArrives() {
        val selection = DiscoveryTargetSelection()
        val mediumHome = home(ChannelOption.MEDIUM_FAST, RADIO_A)

        assertEquals(
            emptySet<String>(),
            selection.onHomeChanged(
                mediumHome,
                ModemPresetCapabilities.unknown(ChannelOption.MEDIUM_FAST.modemPreset),
                scanRunning = false,
            ),
        )
        assertEquals(
            setOf(ChannelOption.MEDIUM_FAST.name),
            selection.onHomeChanged(mediumHome, capabilities(), scanRunning = false),
        )
    }

    @Test
    fun unsupportedToggleCannotEnterTargetSelection() {
        val selection = DiscoveryTargetSelection()
        val capabilities = capabilities()
        selection.onHomeChanged(home(ChannelOption.MEDIUM_FAST, RADIO_A), capabilities, scanRunning = false)

        assertEquals(
            setOf(ChannelOption.MEDIUM_FAST.name),
            selection.toggle(ChannelOption.LITE_FAST, capabilities),
        )
    }

    @Test
    fun userTargetRemainsIndependentAcrossOrdinaryHomeRecomposition() {
        val selection = DiscoveryTargetSelection()
        val mediumHome = home(ChannelOption.MEDIUM_FAST, RADIO_A)

        val capabilities = capabilities()
        selection.onHomeChanged(mediumHome, capabilities, scanRunning = false)
        selection.toggle(ChannelOption.MEDIUM_FAST, capabilities)
        selection.toggle(ChannelOption.LONG_FAST, capabilities)

        assertEquals(
            setOf(ChannelOption.LONG_FAST.name),
            selection.onHomeChanged(mediumHome, capabilities, scanRunning = false),
        )
    }

    @Test
    fun disconnectClearsOldTargetAndNewDeviceInitializesItsOwnPreset() {
        val selection = DiscoveryTargetSelection()

        val capabilities = capabilities()
        selection.onHomeChanged(home(ChannelOption.MEDIUM_FAST, RADIO_A), capabilities, scanRunning = false)
        assertEquals(
            emptySet<String>(),
            selection.onHomeChanged(home = null, capabilities = capabilities, scanRunning = false),
        )
        assertEquals(
            setOf(ChannelOption.SHORT_FAST.name),
            selection.onHomeChanged(home(ChannelOption.SHORT_FAST, RADIO_B), capabilities, scanRunning = false),
        )
    }

    @Test
    fun runningScanDoesNotRebindTargetDuringTemporaryRadioRestart() {
        val selection = DiscoveryTargetSelection()
        val capabilities = capabilities()
        selection.onHomeChanged(home(ChannelOption.MEDIUM_FAST, RADIO_A), capabilities, scanRunning = false)
        selection.toggle(ChannelOption.MEDIUM_FAST, capabilities)
        selection.toggle(ChannelOption.LONG_FAST, capabilities)

        assertEquals(
            setOf(ChannelOption.LONG_FAST.name),
            selection.onHomeChanged(home = null, capabilities = capabilities, scanRunning = true),
        )
        assertEquals(
            setOf(ChannelOption.LONG_FAST.name),
            selection.onHomeChanged(
                home(ChannelOption.LONG_FAST, RADIO_A),
                capabilities,
                scanRunning = true,
            ),
        )
    }

    @Test
    fun customLoraHomeDoesNotFabricateNamedPreset() {
        val lora = ConfigProtos.Config.LoRaConfig.newBuilder()
            .setUsePreset(false)
            .setBandwidth(250)
            .setSpreadFactor(9)
            .setCodingRate(5)
            .build()

        val home = lora.toDiscoveryHomeState(RADIO_A)

        assertEquals("CUSTOM", home.presetName)
        assertNull(home.initialTarget)
        assertEquals(
            emptySet<String>(),
            DiscoveryTargetSelection().onHomeChanged(home, capabilities(), scanRunning = false),
        )
    }

    private fun home(
        option: ChannelOption,
        deviceAddress: String,
    ): DiscoveryHomeState = DiscoveryHomeState(
        deviceAddress = deviceAddress,
        presetName = option.name,
        initialTarget = option,
    )

    private fun capabilities(): ModemPresetCapabilities {
        val presets = setOf(
            ChannelOption.MEDIUM_FAST.modemPreset,
            ChannelOption.LONG_FAST.modemPreset,
            ChannelOption.SHORT_FAST.modemPreset,
        )
        return ModemPresetCapabilities(
            source = ModemPresetCapabilitySource.DARKMESH_2_7_26_EXACT_SOURCE,
            currentPreset = ChannelOption.MEDIUM_FAST.modemPreset,
            currentRegion = ConfigProtos.Config.LoRaConfig.RegionCode.EU_868,
            deviceSupportedPresets = presets,
            regionValidPresets = presets,
        )
    }

    private companion object {
        const val RADIO_A = "radio-a"
        const val RADIO_B = "radio-b"
    }
}
