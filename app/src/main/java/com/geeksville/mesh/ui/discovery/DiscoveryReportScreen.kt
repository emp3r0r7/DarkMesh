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

@file:Suppress("TooManyFunctions")

package com.geeksville.mesh.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.database.entity.DiscoverySessionStatus
import com.geeksville.mesh.discovery.DiscoveryNodeClass
import com.geeksville.mesh.discovery.DiscoveryPresetReport
import com.geeksville.mesh.discovery.DiscoveryReport
import java.text.DateFormat
import java.util.Date

@Composable
fun DiscoveryReportScreen(
    report: DiscoveryReport,
    onBack: () -> Unit,
    onMap: (Long, DiscoveryNodeClass) -> Unit,
    onList: (Long, DiscoveryNodeClass) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val canDelete = report.session.completionStatus != DiscoverySessionStatus.IN_PROGRESS

    if (showDeleteDialog) {
        DeleteDiscoverySessionsDialog(
            count = 1,
            onConfirm = {
                showDeleteDialog = false
                onDelete(report.session.id)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    Scaffold(
        topBar = {
            DiscoveryReportTopBar(
                canDelete = canDelete,
                onBack = onBack,
                onDelete = { showDeleteDialog = true },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DiscoveryOverviewCard(
                    report = report,
                    dateText = dateFormat.format(Date(report.session.timestamp)),
                )
            }
            item { DiscoveryAnalysisCard(report = report) }
            item { SectionTitle("Preset Results") }
            items(report.presets, key = { "report-preset-${it.result.id}" }) { preset ->
                DiscoveryPresetReportCard(
                    preset = preset,
                    onMap = { nodeClass -> onMap(preset.result.id, nodeClass) },
                    onList = { nodeClass -> onList(preset.result.id, nodeClass) },
                )
            }
        }
    }
}

@Composable
private fun DiscoveryReportTopBar(
    canDelete: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) = TopAppBar(
    title = { Text("Scan Report") },
    navigationIcon = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
        }
    },
    actions = {
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    },
)

@Composable
private fun DiscoveryOverviewCard(
    report: DiscoveryReport,
    dateText: String,
) {
    val session = report.session
    val unknownNodes = (session.uniqueNodes - session.directNeighbors - session.meshNeighbors).coerceAtLeast(0)

    DiscoveryCard {
        Text("Session Overview", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
        DiscoveryMetricRow("Date", dateText)
        DiscoveryMetricRow("Status", session.completionStatus)
        DiscoveryMetricRow("Presets", session.presetsScanned.replace(",", ", "))
        DiscoveryMetricRow("Home preset", session.homePreset ?: "-")
        DiscoveryMetricRow("Unique nodes", session.uniqueNodes.toString())
        DiscoveryMetricRow("Neighbor nodes", session.directNeighbors.toString())
        DiscoveryMetricRow("Network nodes", session.meshNeighbors.toString())
        DiscoveryMetricRow("Unknown nodes", unknownNodes.toString())
        DiscoveryMetricRow("Messages", session.messageCount.toString())
        DiscoveryMetricRow("Total dwell", session.totalDwellSeconds.formatDiscoveryDuration())
    }
}

@Composable
private fun DiscoveryAnalysisCard(report: DiscoveryReport) {
    val best = report.bestPreset
    val text = if (best == null) {
        "No preset result available for this scan."
    } else {
        "${best.result.presetName} discovered ${best.result.uniqueNodes} node(s), " +
            "${best.directNodes} direct, with ${best.result.averageChannelUtilization.formatPercent()} " +
            "channel utilization. Recommendation: Use ${best.result.presetName} for this location " +
            "(scan ${report.session.completionStatus})."
    }

    DiscoveryCard {
        Text("Analysis", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.body1)
    }
}

@Composable
private fun DiscoveryPresetReportCard(
    preset: DiscoveryPresetReport,
    onMap: (DiscoveryNodeClass) -> Unit,
    onList: (DiscoveryNodeClass) -> Unit,
) {
    val result = preset.result
    DiscoveryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(result.presetName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.subtitle1)
            Text("#${preset.rank}", color = MaterialTheme.colors.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(result.dwellSeconds.formatDiscoveryDuration(), style = MaterialTheme.typography.body2)
        }
        DiscoveryMetricRow("Unique nodes", result.uniqueNodes.toString())
        DiscoveryMetricRow("Average channel utilization", result.averageChannelUtilization.formatPercent())
        DiscoveryMetricRow("Average air utilization TX", result.averageAirUtilTx.formatPercent())
        DiscoveryStatGrid(preset)
        Divider()
        Text(
            "${result.presetName}: ${result.uniqueNodes} nodes " +
                "(${preset.directNodes} neighbor, ${preset.networkNodes} network, " +
                "${preset.unknownNodes} unknown), " +
                "${result.averageChannelUtilization.formatPercent()} channel utilization.",
            style = MaterialTheme.typography.body2,
        )
        Divider()
        Text("RF Health", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
        DiscoveryMetricRow("Packets TX", result.localNumPacketsTx.formatCount())
        DiscoveryMetricRow("Packets RX", result.localNumPacketsRx.formatCount())
        DiscoveryMetricRow("Invalid packets", result.localNumPacketsRxBad.formatCount())
        DiscoveryMetricRow("Success rate", formatDiscoveryRate(result.localNumPacketsRx, result.localNumPacketsRxBad))
        DiscoveryMetricRow("Failure rate", formatDiscoveryRate(result.localNumPacketsRxBad, result.localNumPacketsRx))
        DiscoveryMetricRow(
            "Nodes online / total",
            "${result.localNumOnlineNodes.formatCount()} / ${result.uniqueNodes}",
        )
        DiscoveryResultSubmodule(
            title = "Neighbor",
            count = preset.directNodes,
            onMap = { onMap(DiscoveryNodeClass.NEIGHBOR) },
            onList = { onList(DiscoveryNodeClass.NEIGHBOR) },
        )
        DiscoveryResultSubmodule(
            title = "Network",
            count = preset.networkNodes,
            onMap = { onMap(DiscoveryNodeClass.NETWORK) },
            onList = { onList(DiscoveryNodeClass.NETWORK) },
        )
        DiscoveryMetricRow("Unknown", preset.unknownNodes.toString())
    }
}

@Composable
private fun DiscoveryStatGrid(preset: DiscoveryPresetReport) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiscoveryStatBlock(preset.directNodes.toString(), "Direct", Modifier.weight(1f))
        DiscoveryStatBlock(preset.networkNodes.toString(), "Network", Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiscoveryStatBlock(preset.result.messageCount.toString(), "Messages", Modifier.weight(1f))
        DiscoveryStatBlock(preset.result.sensorCount.toString(), "Sensor packets", Modifier.weight(1f))
    }
}

@Composable
private fun DiscoveryResultSubmodule(
    title: String,
    count: Int,
    onMap: () -> Unit,
    onList: () -> Unit,
) {
    Text("$title ($count)", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onList, enabled = count > 0, modifier = Modifier.weight(1f)) {
            Icon(Icons.AutoMirrored.Default.List, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("List")
        }
        OutlinedButton(onClick = onMap, enabled = count > 0, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Map, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Map")
        }
    }
}

@Composable
private fun DiscoveryStatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.caption)
    }
}

@Composable
private fun DiscoveryMetricRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.body1)
        Text(value, style = MaterialTheme.typography.body1, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiscoveryCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = REPORT_CARD_ALPHA),
        contentColor = MaterialTheme.colors.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun DeleteDiscoverySessionsDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete scan report") },
        text = { Text("Delete $count selected scan report(s)?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = colorResource(id = R.color.colorAnnotation))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = colorResource(id = R.color.colorAnnotation))
            }
        },
    )
}

private const val REPORT_CARD_ALPHA = 0.08f
