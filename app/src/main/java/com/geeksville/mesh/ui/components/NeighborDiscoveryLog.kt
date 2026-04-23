/*
 * Copyright (c) 2026 Meshtastic LLC
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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.NeighborDiscoveryResult
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.formatNeighborDiscoverySnr
import com.geeksville.mesh.model.getNeighborDiscoveryResult
import com.geeksville.mesh.model.neighborDiscoverySnrColor
import java.text.DateFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NeighborDiscoveryLogScreen(
    uiViewModel: UIViewModel = hiltViewModel(),
    viewModel: MetricsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    }
    var showDialog by remember { mutableStateOf<NeighborDiscoveryResult?>(null) }
    var showMapUnavailableDialog by remember { mutableStateOf<NeighborDiscoveryResult?>(null) }

    showMapUnavailableDialog?.let { discovery ->
        AlertDialog(
            onDismissRequest = {
                showMapUnavailableDialog = null
            },
            title = {},
            text = {
                Text(text = stringResource(R.string.neighbor_discovery_request_gps_prompt))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMapUnavailableDialog = null
                        uiViewModel.requestNeighborDiscoveryPositions(discovery)
                    }
                ) {
                    Text(
                        text = stringResource(android.R.string.yes),
                        color = colorResource(id = R.color.colorAnnotation),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showMapUnavailableDialog = null }) {
                    Text(
                        text = stringResource(android.R.string.no),
                        color = colorResource(id = R.color.colorAnnotation),
                    )
                }
            },
        )
    }

    showDialog?.let { discovery ->
        NeighborDiscoveryDialog(
            discovery = discovery,
            onDismiss = {
                showDialog = null
                uiViewModel.clearNeighborDiscoveryResponse()
            },
            onViewOnMap = {
                showDialog = null
                uiViewModel.neighborDiscoveryMapAvailability(discovery)
                    ?.let {
                        onClose()
                        uiViewModel.showNeighborDiscoveryMap(it)
                    }
                    ?: run {
                        showMapUnavailableDialog = discovery
                    }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(state.neighborDiscoveryRequests, key = { it.uuid }) { log ->
            val result = remember(state.neighborDiscoveryResults) {
                state.neighborDiscoveryResults.find { it.decoded.requestId == log.fromRadio.packet.id }
            }
            val discovery = remember(result) { result?.getNeighborDiscoveryResult(viewModel::getUser) }
            val time = dateFormat.format(log.received_date)
            val text = discoverySummary(discovery)
            val icon = if (discovery == null) Icons.Default.PersonOff else Icons.Default.People
            var expanded by remember { mutableStateOf(false) }

            Box {
                NeighborDiscoveryItem(
                    icon = icon,
                    text = "$time - $text",
                    modifier = Modifier.combinedClickable(
                        onLongClick = { expanded = true },
                    ) {
                        if (discovery != null) {
                            showDialog = discovery
                        }
                    }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DeleteNeighborDiscoveryItem {
                        viewModel.deleteLog(log.uuid)
                        expanded = false
                    }
                }
            }
        }
    }
}

@Composable
fun NeighborDiscoveryDialog(
    discovery: NeighborDiscoveryResult,
    onDismiss: () -> Unit,
    onViewOnMap: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {},
        text = {
            NeighborDiscoveryContent(discovery = discovery)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.okay),
                    color = colorResource(id = R.color.colorAnnotation),
                )
            }
        },
        dismissButton = {
            if (onViewOnMap != null) {
                TextButton(onClick = onViewOnMap) {
                    Text(
                        text = stringResource(R.string.view_on_map),
                        color = colorResource(id = R.color.colorAnnotation),
                    )
                }
            }
        }
    )
}

@Composable
fun NeighborDiscoveryContent(
    discovery: NeighborDiscoveryResult,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.neighbor_discovery),
            style = MaterialTheme.typography.h6,
            textAlign = TextAlign.Center,
        )
        Text(
            text = discovery.origin.longName,
            style = MaterialTheme.typography.subtitle1,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(1.dp).padding(top = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (discovery.discovered.isEmpty()) {
                Text(
                    text = stringResource(R.string.neighbor_discovery_no_neighbors),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body1,
                )
            } else {
                discovery.discovered.forEach { link ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = link.node.longName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.body1,
                        )
                        Text(
                            text = formatNeighborDiscoverySnr(link.snr),
                            color = Color(neighborDiscoverySnrColor(link.snr)),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.body1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteNeighborDiscoveryItem(onClick: () -> Unit) {
    DropdownMenuItem(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = stringResource(id = R.string.delete),
            tint = MaterialTheme.colors.error,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(id = R.string.delete),
            color = MaterialTheme.colors.error,
        )
    }
}

@Composable
private fun NeighborDiscoveryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 2.dp),
        elevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(id = R.string.neighbor_discovery),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.body1,
            )
        }
    }
}

@Composable
private fun discoverySummary(discovery: NeighborDiscoveryResult?): String = when {
    discovery == null -> stringResource(R.string.routing_error_no_response)
    discovery.discovered.isEmpty() -> stringResource(R.string.neighbor_discovery_no_neighbors)
    else -> pluralStringResource(
        R.plurals.neighbor_discovery_nodes,
        discovery.discovered.size,
        discovery.discovered.size,
    )
}