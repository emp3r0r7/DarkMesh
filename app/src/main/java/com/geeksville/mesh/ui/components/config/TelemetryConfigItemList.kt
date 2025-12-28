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

package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference
import org.meshtastic.proto.ModuleConfigProtos.ModuleConfig.TelemetryConfig
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig

private const val MIN_FW_FOR_TELEMETRY_TOGGLE = "2.7.12"

@Composable
fun TelemetryConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    TelemetryConfigItemList(
        viewModel = viewModel,
        telemetryConfig = state.moduleConfig.telemetry,
        enabled = state.connected,
        onSaveClicked = { telemetryInput ->
            val config = moduleConfig { telemetry = telemetryInput }
            viewModel.setModuleConfig(config)
        }
    )
}

@Composable
fun TelemetryConfigItemList(
    viewModel: RadioConfigViewModel = hiltViewModel(),
    telemetryConfig: TelemetryConfig,
    enabled: Boolean,
    onSaveClicked: (TelemetryConfig) -> Unit,
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var telemetryInput by rememberSaveable { mutableStateOf(telemetryConfig) }
    val firmwareVersion = state.metadata?.firmwareVersion ?: "1"

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Telemetry Config") }

        if (DeviceVersion(firmwareVersion) >= DeviceVersion(MIN_FW_FOR_TELEMETRY_TOGGLE)) {
            item {
                SwitchPreference(
                    title = "Send Device Telemetry",
                    summary = "Enable/Disable the device telemetry module to send metrics to the mesh. " +
                            "This flag is mandatory for FW version above $MIN_FW_FOR_TELEMETRY_TOGGLE " +
                            "otherwise no voltage or telemetry will be sent over the mesh!",
                    checked = telemetryInput.deviceTelemetryEnabled,
                    enabled = state.connected,
                    onCheckedChange = {
                        telemetryInput = telemetryInput.copy { deviceTelemetryEnabled = it }
                    }
                )
            }
        }

        item {
            EditTextPreference(title = "Device metrics update interval (seconds)",
                value = telemetryInput.deviceUpdateInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    telemetryInput = telemetryInput.copy { deviceUpdateInterval = it }
                })
        }

        item {
            EditTextPreference(title = "Environment metrics update interval (seconds)",
                value = telemetryInput.environmentUpdateInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    telemetryInput = telemetryInput.copy { environmentUpdateInterval = it }
                })
        }

        item {
            SwitchPreference(title = "Environment metrics module enabled",
                checked = telemetryInput.environmentMeasurementEnabled,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { environmentMeasurementEnabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Environment metrics on-screen enabled",
                checked = telemetryInput.environmentScreenEnabled,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { environmentScreenEnabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Environment metrics use Fahrenheit",
                checked = telemetryInput.environmentDisplayFahrenheit,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { environmentDisplayFahrenheit = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Air quality metrics module enabled",
                checked = telemetryInput.airQualityEnabled,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { airQualityEnabled = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Air quality metrics update interval (seconds)",
                value = telemetryInput.airQualityInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    telemetryInput = telemetryInput.copy { airQualityInterval = it }
                })
        }

        item {
            SwitchPreference(title = "Power metrics module enabled",
                checked = telemetryInput.powerMeasurementEnabled,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { powerMeasurementEnabled = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Power metrics update interval (seconds)",
                value = telemetryInput.powerUpdateInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    telemetryInput = telemetryInput.copy { powerUpdateInterval = it }
                })
        }

        item {
            SwitchPreference(title = "Power metrics on-screen enabled",
                checked = telemetryInput.powerScreenEnabled,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { powerScreenEnabled = it }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = enabled && telemetryInput != telemetryConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    telemetryInput = telemetryConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(telemetryInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TelemetryConfigPreview() {
    TelemetryConfigItemList(
        telemetryConfig = TelemetryConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
