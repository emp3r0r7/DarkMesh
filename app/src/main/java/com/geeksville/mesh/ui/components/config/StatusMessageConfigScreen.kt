package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig

@Composable
fun StatusMessageConfigScreen(radioViewModel: RadioConfigViewModel = hiltViewModel()){

    val state by radioViewModel.radioConfigState.collectAsStateWithLifecycle()
    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = radioViewModel::clearPacketResponse,
        )
    }

    val statusMessageConfig = state.moduleConfig.statusmessage
    var statusMessageInput by rememberSaveable { mutableStateOf(statusMessageConfig) }
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {

        item { PreferenceCategory(text = "Status Message Config") }

        item {
            Text(
                text = "The node will periodically broadcast a status message that nearby nodes can receive and display in the node list.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        item {

            TextField(
                value = statusMessageInput.nodeStatus ?: "",
                onValueChange = {
                    val trimmed = it.take(79)
                    statusMessageInput = statusMessageInput.copy {
                        nodeStatus = trimmed
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 4,
                placeholder = { Text("Set your status...") }
            )
        }

        item {
            PreferenceFooter(
                enabled = state.connected && statusMessageInput != statusMessageConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    statusMessageInput = statusMessageConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    val config = moduleConfig { statusmessage = statusMessageInput }
                    radioViewModel.setModuleConfig(config)
                }
            )
        }
    }
}
