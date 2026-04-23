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

package com.geeksville.mesh.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.database.entity.NodeRegistry
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.ui.activity.PlanMsgActivity
import com.geeksville.mesh.ui.activity.PlanMsgActivity.NODE_ID_EXTRA_PARAM
import org.meshtastic.proto.copy

@Suppress("LongMethod")
@Composable
fun NodeMenu(
    nodeModel: Node,
    isThisNode: Boolean = false,
    isConnected: Boolean = false,
    showFullMenu: Boolean = false,
    onDismissRequest: () -> Unit,
    expanded: Boolean = false,
    nodeRegistry: NodeRegistry? = null,
    onAction: (NodeMenuAction) -> Unit
) {
    val context = LocalContext.current
    var displayIgnoreDialog by remember { mutableStateOf(false) }
    var displayRemoveDialog by remember { mutableStateOf(false) }

    val isKnownNode = remember(nodeRegistry) {
        derivedStateOf { nodeRegistry == null }
    }

    val node = remember(nodeRegistry, nodeModel) {
        if (nodeRegistry != null && nodeRegistry.nodeNum != null) {
            nodeModel.copy(
                num = nodeRegistry.nodeNum,
                user = nodeModel.user.copy {
                    longName = nodeRegistry.longName ?: longName
                    shortName = nodeRegistry.shortName ?: shortName
                    id = nodeRegistry.nodeId
                }
            )
        } else {
            nodeModel
        }
    }

    if (displayIgnoreDialog) {
        SimpleAlertDialog(
            title = R.string.ignore,
            text = stringResource(
                id = if (node.isIgnored) R.string.ignore_remove else R.string.ignore_add,
                node.user.longName
            ),
            onConfirm = {
                displayIgnoreDialog = false
                onAction(NodeMenuAction.Ignore(node))
            },
            onDismiss = {
                displayIgnoreDialog = false
            }
        )
    }
    if (displayRemoveDialog) {
        SimpleAlertDialog(
            title = R.string.remove,
            text = R.string.remove_node_text,
            onConfirm = {
                displayRemoveDialog = false
                onAction(NodeMenuAction.Remove(node))
            },
            onDismiss = {
                displayRemoveDialog = false
            }
        )
    }
    DropdownMenu(
        modifier = Modifier.background(MaterialTheme.colors.background.copy(alpha = 1f)),
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {

        if(isThisNode){

            val connStatus = if(isConnected){
                "Connected"
            } else {
                "Disconnected"
            }

            DropdownMenuItem(
                onClick = {
                    Toast.makeText(context,
                        "Device is $connStatus",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                content = {
                    Text(connStatus,
                        color = if(isConnected) Color.Green else Color.Red
                    )
                }
            )
        }

        if (showFullMenu) {
            DropdownMenuItem(
                enabled = isKnownNode.value,
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.DirectMessage(node))
                },
                content = { Text(stringResource(R.string.direct_message)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.RequestUserInfo(node))
                },
                content = { Text(stringResource(R.string.request_userinfo)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.RequestDeviceMetadata(node))
                },
                content = { Text(stringResource(R.string.request_usermetadata)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.RequestPosition(node))
                },
                content = { Text(stringResource(R.string.request_position)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.TraceRoute(node))
                },
                content = { Text(stringResource(R.string.traceroute)) }
            )
            DropdownMenuItem(
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.NeighborDiscovery(node))
                },
                content = { Text(stringResource(R.string.neighbor_discovery)) }
            )
            DropdownMenuItem(
                enabled = isKnownNode.value,
                onClick = {
                    onDismissRequest()
                    val intent = Intent(context, PlanMsgActivity::class.java).apply {
                        putExtra(NODE_ID_EXTRA_PARAM, node.num.toString())
                    }
                    context.startActivity(intent)
                },
                content = { Text("Plan Message") }
            )

            DropdownMenuItem(
                enabled = isKnownNode.value,
                onClick = {
                    onDismissRequest()
                    onAction(NodeMenuAction.FavoriteNode(node))
                },
                content = { Text("Set Favorite") }
            )
            DropdownMenuItem(
                enabled = isKnownNode.value,
                onClick = {
                    onDismissRequest()
                    displayIgnoreDialog = true
                },
            ) {
                Text(stringResource(R.string.ignore))
                Spacer(Modifier.weight(1f))
                Checkbox(
                    checked = node.isIgnored,
                    onCheckedChange = {
                        onDismissRequest()
                        displayIgnoreDialog = true
                    },
                    modifier = Modifier.size(24.dp),
                )
            }

            if(isKnownNode.value){
                DropdownMenuItem(
                    onClick = {
                        onDismissRequest()
                        displayRemoveDialog = true
                    },
                    enabled = !node.isIgnored,
                ) { Text(stringResource(R.string.remove)) }
            }

            Divider(Modifier.padding(vertical = 8.dp))
        }
        DropdownMenuItem(
            enabled = isKnownNode.value,
            onClick = {
                onDismissRequest()
                onAction(NodeMenuAction.MoreDetails(node))
            },
            content = { Text(stringResource(R.string.more_details)) }
        )
    }
}

sealed class NodeMenuAction {
    data class Remove(val node: Node) : NodeMenuAction()
    data class Ignore(val node: Node) : NodeMenuAction()
    data class DirectMessage(val node: Node) : NodeMenuAction()
    data class RequestUserInfo(val node: Node) : NodeMenuAction()
    data class RequestDeviceMetadata(val node: Node) : NodeMenuAction()
    data class RequestPosition(val node: Node) : NodeMenuAction()
    data class TraceRoute(val node: Node) : NodeMenuAction()
    data class NeighborDiscovery(val node: Node) : NodeMenuAction()
    data class FavoriteNode(val node: Node) : NodeMenuAction()
    data class MoreDetails(val node: Node) : NodeMenuAction()
}
