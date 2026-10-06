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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.discovery.DiscoveryNodeList
import com.geeksville.mesh.discovery.DiscoveryNodeListItem
import com.geeksville.mesh.discovery.DiscoveryNodeClass
import com.geeksville.mesh.model.formatNeighborDiscoverySnr
import com.geeksville.mesh.model.neighborDiscoverySnrColor

@Composable
fun DiscoveryNodeListDialog(
    nodeList: DiscoveryNodeList,
    distanceUnits: Int = 0,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface,
            elevation = NODE_LIST_DIALOG_ELEVATION_DP.dp,
        ) {
            Column(
                modifier = Modifier.padding(
                    start = NODE_LIST_DIALOG_PADDING_DP.dp,
                    top = NODE_LIST_DIALOG_PADDING_DP.dp,
                    end = NODE_LIST_DIALOG_PADDING_DP.dp,
                    bottom = NODE_LIST_DIALOG_BUTTON_PADDING_DP.dp,
                ),
            ) {
                DiscoveryNodeListContent(nodeList = nodeList, distanceUnits = distanceUnits)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.okay),
                            color = colorResource(id = R.color.colorAnnotation),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveryNodeListContent(
    nodeList: DiscoveryNodeList,
    distanceUnits: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DiscoveryNodeListHeader(nodeList = nodeList)
        DiscoveryNodeListCard(nodeList = nodeList, distanceUnits = distanceUnits)
    }
}

@Composable
private fun DiscoveryNodeListHeader(nodeList: DiscoveryNodeList) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Local Mesh Discovery",
            style = MaterialTheme.typography.h6,
            textAlign = TextAlign.Center,
        )
        Text(
            text = nodeList.presetName.ifBlank { nodeList.originName },
            style = MaterialTheme.typography.subtitle1,
            textAlign = TextAlign.Center,
        )
        if (nodeList.presetName.isNotBlank()) {
            Text(
                text = nodeList.originName,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = "${nodeList.nodeClass.displayName()} nodes (${nodeList.nodes.size})",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DiscoveryNodeListCard(
    nodeList: DiscoveryNodeList,
    distanceUnits: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = LIST_CARD_ALPHA),
        contentColor = MaterialTheme.colors.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = NODE_LIST_MAX_HEIGHT_DP.dp)
                .verticalScroll(rememberScrollState())
                .padding(LIST_CARD_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(NODE_LIST_ROW_SPACING_DP.dp),
        ) {
            DiscoveryNodeListRows(nodeList = nodeList, distanceUnits = distanceUnits)
        }
    }
}

@Composable
private fun DiscoveryNodeListRows(
    nodeList: DiscoveryNodeList,
    distanceUnits: Int,
) {
    if (nodeList.nodes.isEmpty()) {
        Text(
            text = "No ${nodeList.nodeClass.displayName().lowercase()} nodes",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.body1,
        )
    } else {
        nodeList.nodes.forEach { node ->
            DiscoveryNodeListRow(node = node, distanceUnits = distanceUnits)
        }
    }
}

private fun DiscoveryNodeClass.displayName(): String = name.lowercase().replaceFirstChar { it.titlecase() }

@Composable
private fun DiscoveryNodeListRow(
    node: DiscoveryNodeListItem,
    distanceUnits: Int,
) {
    val distanceText = node.distanceMeters.formatDistanceMeters(distanceUnits)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = node.longName,
                style = MaterialTheme.typography.body1,
            )
            distanceText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.caption,
                )
            }
        }
        Text(
            text = formatNeighborDiscoverySnr(node.snr),
            color = Color(neighborDiscoverySnrColor(node.snr)),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.body1,
        )
    }
}

private const val NODE_LIST_MAX_HEIGHT_DP = 360
private const val NODE_LIST_DIALOG_PADDING_DP = 24
private const val NODE_LIST_DIALOG_BUTTON_PADDING_DP = 8
private const val NODE_LIST_DIALOG_ELEVATION_DP = 24
private const val NODE_LIST_ROW_SPACING_DP = 8
private const val LIST_CARD_PADDING_DP = 12
private const val LIST_CARD_ALPHA = 0.08f
