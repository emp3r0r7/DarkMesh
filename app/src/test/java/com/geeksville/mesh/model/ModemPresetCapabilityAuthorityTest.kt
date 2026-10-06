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

package com.geeksville.mesh.model

import com.geeksville.mesh.discovery.requireDiscoveryTargetsSupported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.proto.ConfigProtos.Config.LoRaConfig
import org.meshtastic.proto.ConfigProtos.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.ConfigProtos.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.MeshProtos.DeviceMetadata

class ModemPresetCapabilityAuthorityTest {
    private val authority = ModemPresetCapabilityAuthority()

    @Test
    fun exactFirmwareAndEu868ResolveDeviceRegionAndSelectableSets() {
        val capabilities = resolve(lora(ModemPreset.MEDIUM_FAST, RegionCode.EU_868))

        assertEquals(ModemPreset.MEDIUM_FAST, capabilities.currentPreset)
        assertEquals(DARKMESH_2_7_26_DEVICE_PRESETS, capabilities.deviceSupportedPresets)
        assertEquals(9, capabilities.deviceSupportedPresets.size)
        assertEquals(7, capabilities.selectablePresets.size)
        assertFalse(ModemPreset.LONG_TURBO in capabilities.selectablePresets)
        assertFalse(ModemPreset.SHORT_TURBO in capabilities.selectablePresets)
        assertTrue(ModemPreset.MEDIUM_FAST in capabilities.selectablePresets)
    }

    @Test
    fun settingsChoicesContainSupportedAndHideUnsupported() {
        val capabilities = resolve(lora(ModemPreset.MEDIUM_FAST, RegionCode.EU_868))
        val choices = capabilities.settingsPresetChoices()

        assertTrue(ModemPreset.MEDIUM_FAST in choices)
        assertTrue(ModemPreset.LONG_SLOW in choices)
        assertFalse(ModemPreset.LITE_FAST in choices)
        assertFalse(ModemPreset.LONG_TURBO in choices)
    }

    @Test
    fun discoveryCatalogueKeepsUnsupportedVisibleButNonSelectable() {
        val capabilities = resolve(lora(ModemPreset.MEDIUM_FAST, RegionCode.EU_868))
        val catalogue = capabilities.discoveryPresetCatalogue().associateBy { it.option }

        assertEquals(ModemPresetSupport.SUPPORTED, catalogue.getValue(ChannelOption.MEDIUM_FAST).support)
        assertTrue(catalogue.getValue(ChannelOption.MEDIUM_FAST).selectable)
        assertEquals(ModemPresetSupport.UNSUPPORTED, catalogue.getValue(ChannelOption.LITE_FAST).support)
        assertFalse(catalogue.getValue(ChannelOption.LITE_FAST).selectable)
        assertEquals(ChannelOption.entries.size, catalogue.size)
    }

    @Test
    fun unsupportedAndUnknownDiscoveryTargetsAreRejectedBeforeWritePath() {
        val capabilities = resolve(lora(ModemPreset.MEDIUM_FAST, RegionCode.EU_868))

        requireDiscoveryTargetsSupported(listOf(ChannelOption.MEDIUM_FAST), capabilities)
        assertThrows(IllegalArgumentException::class.java) {
            requireDiscoveryTargetsSupported(listOf(ChannelOption.LITE_FAST), capabilities)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireDiscoveryTargetsSupported(
                listOf(ChannelOption.MEDIUM_FAST),
                ModemPresetCapabilities.unknown(ModemPreset.MEDIUM_FAST, RegionCode.EU_868),
            )
        }
    }

    @Test
    fun unknownCapabilityDoesNotExpandEnumAndKeepsCurrentRepresentable() {
        val currentLora = lora(ModemPreset.MEDIUM_FAST, RegionCode.EU_868)
        val unknown = authority.resolve(
            connected = true,
            metadata = metadata("unproven-build"),
            loraConfig = currentLora,
        )

        assertFalse(unknown.isResolved)
        assertTrue(unknown.selectablePresets.isEmpty())
        assertEquals(listOf(ModemPreset.MEDIUM_FAST), unknown.settingsPresetChoices())
        assertEquals(ModemPresetSupport.UNKNOWN, unknown.supportFor(ModemPreset.MEDIUM_FAST))
        assertTrue(unknown.allowsLoRaSave(currentLora, currentLora))
        assertFalse(
            unknown.allowsLoRaSave(
                currentLora,
                currentLora.toBuilder().setModemPreset(ModemPreset.SHORT_FAST).build(),
            )
        )
    }

    @Test
    fun disconnectAndSecondDeviceResolutionCannotRetainFirstSelectableSet() {
        val deviceA = resolve(lora(ModemPreset.MEDIUM_FAST, RegionCode.EU_868))
        val disconnected = authority.resolve(
            connected = false,
            metadata = exactMetadata(),
            loraConfig = lora(ModemPreset.MEDIUM_FAST, RegionCode.EU_868),
        )
        val deviceB = resolve(lora(ModemPreset.SHORT_TURBO, RegionCode.US))

        assertEquals(7, deviceA.selectablePresets.size)
        assertTrue(disconnected.selectablePresets.isEmpty())
        assertEquals(ModemPreset.MEDIUM_FAST, disconnected.currentPreset)
        assertEquals(9, deviceB.selectablePresets.size)
        assertEquals(ModemPreset.SHORT_TURBO, deviceB.currentPreset)
        assertTrue(ModemPreset.SHORT_TURBO in deviceB.selectablePresets)
    }

    @Test
    fun settingsAndDiscoveryViewsProjectTheSameSelectableAuthority() {
        val capabilities = resolve(lora(ModemPreset.MEDIUM_FAST, RegionCode.EU_868))
        val settingsChoices = capabilities.settingsPresetChoices().toSet()
        val discoveryChoices = capabilities.discoveryPresetCatalogue()
            .filter { it.selectable }
            .mapTo(mutableSetOf()) { it.option.modemPreset }

        assertEquals(capabilities.selectablePresets, settingsChoices)
        assertEquals(capabilities.selectablePresets, discoveryChoices)
    }

    private fun resolve(loraConfig: LoRaConfig): ModemPresetCapabilities {
        return authority.resolve(
            connected = true,
            metadata = exactMetadata(),
            loraConfig = loraConfig,
        )
    }

    private fun exactMetadata() = metadata(DARKMESH_2_7_26_FIRMWARE_VERSION)

    private fun metadata(firmwareVersion: String): DeviceMetadata {
        return DeviceMetadata.newBuilder().setFirmwareVersion(firmwareVersion).build()
    }

    private fun lora(
        preset: ModemPreset,
        region: RegionCode,
    ): LoRaConfig = LoRaConfig.newBuilder()
        .setUsePreset(true)
        .setModemPreset(preset)
        .setRegion(region)
        .build()
}
