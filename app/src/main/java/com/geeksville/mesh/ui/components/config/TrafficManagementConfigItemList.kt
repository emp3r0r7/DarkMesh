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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import org.meshtastic.proto.ModuleConfigProtos
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig

@Composable
fun TrafficManagementConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    TrafficManagementConfigItemList(
        trafficManagementConfig = state.moduleConfig.trafficManagement,
        enabled = state.connected,
        onSaveClicked = { trafficManagementInput ->
            val config = moduleConfig { trafficManagement = trafficManagementInput }
            viewModel.setModuleConfig(config)
        }
    )
}

@Composable
fun TrafficManagementConfigItemList(
    trafficManagementConfig: ModuleConfigProtos.ModuleConfig.TrafficManagementConfig,
    enabled: Boolean,
    onSaveClicked: (ModuleConfigProtos.ModuleConfig.TrafficManagementConfig) -> Unit
) {

    val focusManager = LocalFocusManager.current
    var trafficManagementInput by rememberSaveable { mutableStateOf(trafficManagementConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {

        item { PreferenceCategory(text = "Traffic Management Config") }

        // Position Min Interval
        item {
            EditTextPreference(
                title = "Position Min Interval (secs)",
                value = trafficManagementInput.positionMinIntervalSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    trafficManagementInput = trafficManagementInput.copy {
                        positionMinIntervalSecs = it
                    }
                }
            )
        }

        item { Divider() }

        // NodeInfo Direct Response Max Hops
        item {
            EditTextPreference(
                title = "NodeInfo Direct Response Max Hops",
                value = trafficManagementInput.nodeinfoDirectResponseMaxHops,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    trafficManagementInput = trafficManagementInput.copy {
                        nodeinfoDirectResponseMaxHops = it
                    }
                }
            )
        }

        item { Divider() }

        // Rate Limit Window
        item {
            EditTextPreference(
                title = "Rate Limit Window (secs)",
                value = trafficManagementInput.rateLimitWindowSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    trafficManagementInput = trafficManagementInput.copy {
                        rateLimitWindowSecs = it
                    }
                }
            )
        }

        item { Divider() }

        // Rate Limit Max Packets
        item {
            EditTextPreference(
                title = "Rate Limit Max Packets",
                value = trafficManagementInput.rateLimitMaxPackets,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    trafficManagementInput = trafficManagementInput.copy {
                        rateLimitMaxPackets = it
                    }
                }
            )
        }

        item { Divider() }

        // Unknown Packet Threshold
        item {
            EditTextPreference(
                title = "Unknown Packet Threshold",
                value = trafficManagementInput.unknownPacketThreshold,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    trafficManagementInput = trafficManagementInput.copy {
                        unknownPacketThreshold = it
                    }
                }
            )
        }

        item { Divider() }

        // Footer con pulsanti Save/Cancel
        item {
            PreferenceFooter(
                enabled = enabled && trafficManagementInput != trafficManagementConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    trafficManagementInput = trafficManagementConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(trafficManagementInput)
                }
            )
        }
    }
}