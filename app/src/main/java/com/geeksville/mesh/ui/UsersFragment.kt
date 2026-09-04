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

package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.advancedPrefs
import com.geeksville.mesh.database.DbImportState
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.PacketActivityEvent
import com.geeksville.mesh.model.RelayEvent
import com.geeksville.mesh.model.SNR_FAIR_THRESHOLD
import com.geeksville.mesh.model.SNR_GOOD_THRESHOLD
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.components.NodeFilterTextField
import com.geeksville.mesh.ui.components.NodeMenuAction
import com.geeksville.mesh.ui.components.Quality
import com.geeksville.mesh.ui.components.RSSI_FAIR_THRESHOLD
import com.geeksville.mesh.ui.components.RSSI_GOOD_THRESHOLD
import com.geeksville.mesh.ui.components.determineSignalQuality
import com.geeksville.mesh.ui.components.rememberTimeTickWithLifecycle
import com.geeksville.mesh.ui.message.navigateToMessages
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.AppUtil
import com.geeksville.mesh.util.ComposableUtil.rememberBooleanPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.TelemetryProtos.DeviceMetrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class UsersFragment : ScreenFragment("Users"), Logging {

    private val model: UIViewModel by activityViewModels()

    private fun navigateToMessages(node: Node) = node.user.let { user ->
        val hasPKC = model.ourNodeInfo.value?.hasPKC == true && node.hasPKC // TODO use meta.hasPKC
        val channel = if (hasPKC) DataPacket.PKC_CHANNEL_INDEX else node.channel
        val contactKey = "$channel${user.id}"
        info("calling MessagesFragment filter: $contactKey")
        parentFragmentManager.navigateToMessages(contactKey)
    }

    private fun navigateToNodeDetails(nodeNum: Int) {
        info("calling NodeDetails --> destNum: $nodeNum")
        parentFragmentManager.navigateToNavGraph(nodeNum, "NodeDetails")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {

                val relayNode by model.lastRelayNode.collectAsStateWithLifecycle()
                val contact by DbImportState.importProgress.collectAsStateWithLifecycle()

                AppTheme {
                    NodesScreen(
                        model = model,
                        relayNode = relayNode,
                        contact = contact,
                        navigateToMessages = ::navigateToMessages,
                        navigateToNodeDetails = ::navigateToNodeDetails,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod")
fun NodesScreen(
    model: UIViewModel = hiltViewModel(),
    relayNode: RelayEvent?,
    contact: String?,
    navigateToMessages: (Node) -> Unit,
    navigateToNodeDetails: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val state by model.nodesUiState.collectAsStateWithLifecycle()
    val nodes by model.nodeList.collectAsStateWithLifecycle()
    val ourNode by model.ourNodeInfo.collectAsStateWithLifecycle()
    val lastMinPacketCount by model.lastMinPacketCount.collectAsStateWithLifecycle()
    val packetHits = remember { mutableStateListOf<PacketActivityEvent>() }

    LaunchedEffect(model) {
        model.packetActivityEvents.collect { event ->
            packetHits.removeAll { event.timestamp - it.timestamp > 60_000 }
            packetHits.add(event)
        }
    }

    val listState = rememberLazyListState()

    val currentTimeMillis = rememberTimeTickWithLifecycle()
    val connectionState by model.connectionState.collectAsStateWithLifecycle()
    val nodeRegistry by model.nodeRegistryMap.collectAsStateWithLifecycle()
    val ourStatusMessage by model.statusMessage.collectAsStateWithLifecycle()

    val showRxActivityBar by rememberBooleanPreference(
        ctx.advancedPrefs,
        RX_ACTIVITY_PREF,
        true //enabled by default
    )

//    //filters nodes with same long name as ours which can occur when switching to SENSOR MODE
//    //fixme maybe set arbitrary randomized name when db init occurs in FW!
//    val filteredNodes = run {
//        val ourNodeName = ourNode?.user?.longName
//        val ourNodeId = ourNode?.user?.id
//
//        if (ourNodeName != null && ourNodeId != null && ConfigProtos.Config.DeviceConfig.Role.SENSOR == role) {
//            nodes.filterNot { it.user.longName == ourNodeName && it.user.id != ourNodeId }
//        } else nodes
//    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {

            //we make sure this box is populated only for nodes != ournode
            if (relayNode != null && relayNode.relayNodeNum != ourNode?.num) {
                RelayInfoBox(relayNode, model)
            }

            lastMinPacketCount?.let { count ->
                if(showRxActivityBar){ MeshHealthBox(count, packetHits) }
            }

            if(DbImportState.importInProgress()){
                DbImportInfoBox(contact!!, model)
            }

            NodeFilterTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                filterText = state.filter,
                onTextChange = model::setNodeFilterText,
                currentSortOption = state.sort,
                onSortSelect = model::setSortOption,
                includeUnknown = state.includeUnknown,
                onToggleIncludeUnknown = model::toggleIncludeUnknown,
                showDetails = state.showDetails,
                onToggleShowDetails = model::toggleShowDetails,
            )

        }

        items(nodes, key = { it.num }) { node ->
            NodeItem(
                thisNode = ourNode,
                thatNode = node,
                gpsFormat = state.gpsFormat,
                distanceUnits = state.distanceUnits,
                tempInFahrenheit = state.tempInFahrenheit,
                onAction = { menuItem ->
                    when (menuItem) {
                        is NodeMenuAction.Remove -> model.removeNode(node.num)
                        is NodeMenuAction.Ignore -> model.ignoreNode(node)
                        is NodeMenuAction.DirectMessage -> navigateToMessages(node)
                        is NodeMenuAction.RequestUserInfo -> model.requestUserInfo(node.num)
                        is NodeMenuAction.RequestPosition -> model.requestPosition(node.num)
                        is NodeMenuAction.TraceRoute -> model.requestTraceroute(node.num)
                        is NodeMenuAction.NeighborDiscovery -> model.requestNeighborDiscovery(node.num)
                        is NodeMenuAction.MoreDetails -> navigateToNodeDetails(node.num)
                        is NodeMenuAction.FavoriteNode -> model.handleFavorite(node)
                        is NodeMenuAction.RequestDeviceMetadata -> model.requestDeviceMetadata(node)
                    }
                },
                expanded = state.showDetails,
                currentTimeMillis = currentTimeMillis,
                isConnected = connectionState.isConnected(),
                nodeRegistry = nodeRegistry,
                ourStatusMessage = ourStatusMessage
            )
        }
    }
}

@Composable
fun MeshHealthBox(
    lastMinPacketCount: Int,
    packetHits: List<PacketActivityEvent> = emptyList()
) {
    val maxProgressbarCap = 15
    var showLegend by remember { mutableStateOf(false) }

    val (label, quality) = when {
        lastMinPacketCount == 0 -> "NO TRAFFIC" to Quality.BAD
        lastMinPacketCount < 5 -> "POOR" to Quality.BAD
        lastMinPacketCount < 8 -> "FAIR" to Quality.FAIR
        lastMinPacketCount < maxProgressbarCap -> "GOOD" to Quality.GOOD
        else -> "EXCEPTIONAL" to Quality.EXCEPTIONAL
    }

    androidx.compose.material.Surface(
        elevation = 4.dp,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "RX Activity",
                            fontSize = 14.sp
                        )

                        IconButton(
                            onClick = { showLegend = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Packet activity legend",
                                tint = colorResource(id = R.color.colorAnnotation),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "$lastMinPacketCount packets/min",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .background(
                            color = quality.color,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        color = Color.Black,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            PacketActivityTimeline(
                packetHits = packetHits,
                color = quality.color
            )
        }
    }

    if (showLegend) {
        PacketActivityLegend(onDismiss = { showLegend = false })
    }
}

@Composable
private fun PacketActivityTimeline(
    packetHits: List<PacketActivityEvent>,
    color: Color,
) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val darkOverlayAlpha = if (surfaceVariant.luminance() > 0.5f) 0.52f else 0.28f
    val timelineBackground = Color.Black
        .copy(alpha = darkOverlayAlpha)
        .compositeOver(surfaceVariant)
    val timelineBackgroundEnd = Color.Black
        .copy(alpha = 0.12f)
        .compositeOver(timelineBackground)
    val timelineShape = RoundedCornerShape(50)

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(100)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(timelineShape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(timelineBackground, timelineBackgroundEnd)
                )
            )
            .border(1.dp, color.copy(alpha = 0.18f), timelineShape)
    ) {
        repeat(5) { index ->
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = 1.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    x = size.width * (index + 1) / 6f,
                    y = size.height / 2f
                )
            )
        }

        val edgePadding = 2.dp.toPx()
        val minimumHitSpacing = 9.dp.toPx()
        var nextAvailableX = size.width - edgePadding

        packetHits.asReversed().forEach { event ->
            val ageMillis = nowMillis - event.timestamp
            if (ageMillis in 0..60_000) {
                val desiredX = edgePadding +
                    (size.width - edgePadding * 2) * (1f - ageMillis / 60_000f)
                val x = minOf(desiredX, nextAvailableX)
                nextAvailableX = x - minimumHitSpacing

                if (x < edgePadding) return@forEach

                val intensity = 1f - ageMillis / 60_000f
                val signalQuality = determineSignalQuality(event.rxSnr, event.rxRssi)
                val hitColor = signalQuality.color
                val hitHeight = size.height * 0.70f
                val type = packetVisualType(event.portNum)

                drawPacketHit(
                    type = type,
                    centerX = x,
                    height = hitHeight + 2.dp.toPx(),
                    width = 8.dp.toPx(),
                    color = hitColor.copy(alpha = 0.08f + intensity * 0.16f),
                )
                drawPacketHit(
                    type = type,
                    centerX = x,
                    height = hitHeight,
                    width = 5.dp.toPx(),
                    color = hitColor.copy(alpha = 0.35f + intensity * 0.65f),
                )

                if (ageMillis < 1_200) {
                    val pulseProgress = ageMillis / 1_200f
                    drawCircle(
                        color = hitColor.copy(alpha = (1f - pulseProgress) * 0.35f),
                        radius = (3.dp + 5.dp * pulseProgress).toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, size.height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPacketHit(
    type: PacketVisualType,
    centerX: Float,
    height: Float,
    width: Float,
    color: Color,
) {
    val top = (size.height - height) / 2f
    val left = centerX - width / 2f

    when (type) {
        PacketVisualType.MESSAGE -> drawCircle(
            color = color,
            radius = height * 0.275f,
            center = androidx.compose.ui.geometry.Offset(centerX, size.height / 2f)
        )

        PacketVisualType.POSITION -> drawPath(
            path = Path().apply {
                moveTo(centerX, top)
                lineTo(centerX + width / 2f, size.height / 2f)
                lineTo(centerX, top + height)
                lineTo(centerX - width / 2f, size.height / 2f)
                close()
            },
            color = color
        )

        PacketVisualType.TELEMETRY -> drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(width, height)
        )

        PacketVisualType.ROUTING -> drawPath(
            path = Path().apply {
                moveTo(centerX, top)
                lineTo(centerX + width / 2f, top + height)
                lineTo(centerX - width / 2f, top + height)
                close()
            },
            color = color
        )

        PacketVisualType.OTHER -> drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(centerX, top),
            end = androidx.compose.ui.geometry.Offset(centerX, top + height),
            strokeWidth = width / 2f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PacketActivityLegend(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = colorResource(id = R.color.colorAnnotation)
            )
        },
        title = { Text("RX Timeline") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Shape indicates packet type",
                    style = MaterialTheme.typography.labelMedium
                )
                PacketTypeLegendItem(PacketVisualType.MESSAGE, "Message")
                PacketTypeLegendItem(PacketVisualType.POSITION, "Position / Waypoint")
                PacketTypeLegendItem(PacketVisualType.TELEMETRY, "Telemetry / Node Info")
                PacketTypeLegendItem(PacketVisualType.ROUTING, "Routing / Mesh Control")
                PacketTypeLegendItem(PacketVisualType.OTHER, "Other")

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Color indicates signal quality",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SignalLegendItem("Good", Quality.GOOD.color)
                    SignalLegendItem("Fair", Quality.FAIR.color)
                    SignalLegendItem("Poor", Quality.BAD.color)
                    SignalLegendItem("None", Quality.NONE.color)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun PacketTypeLegendItem(type: PacketVisualType, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(width = 18.dp, height = 20.dp)) {
            drawPacketHit(
                type = type,
                centerX = size.width / 2f,
                height = size.height * 0.82f,
                width = 7.dp.toPx(),
                color = Quality.GOOD.color
            )
        }
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SignalLegendItem(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, RoundedCornerShape(50))
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

private enum class PacketVisualType {
    MESSAGE,
    POSITION,
    TELEMETRY,
    ROUTING,
    OTHER,
}

private fun packetVisualType(portNum: Int): PacketVisualType = when (portNum) {
    Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
    Portnums.PortNum.TEXT_MESSAGE_COMPRESSED_APP_VALUE,
    Portnums.PortNum.REPLY_APP_VALUE -> PacketVisualType.MESSAGE

    Portnums.PortNum.POSITION_APP_VALUE,
    Portnums.PortNum.WAYPOINT_APP_VALUE,
    Portnums.PortNum.MAP_REPORT_APP_VALUE -> PacketVisualType.POSITION

    Portnums.PortNum.TELEMETRY_APP_VALUE,
    Portnums.PortNum.NODEINFO_APP_VALUE,
    Portnums.PortNum.NODE_STATUS_APP_VALUE,
    Portnums.PortNum.PAXCOUNTER_APP_VALUE -> PacketVisualType.TELEMETRY

    Portnums.PortNum.ROUTING_APP_VALUE,
    Portnums.PortNum.TRACEROUTE_APP_VALUE,
    Portnums.PortNum.NEIGHBORINFO_APP_VALUE,
    Portnums.PortNum.ADMIN_APP_VALUE -> PacketVisualType.ROUTING

    else -> PacketVisualType.OTHER
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun RelayInfoBox(relayNode: RelayEvent, model: UIViewModel) {

    val context = LocalContext.current
    val nodes by model.unfilteredNodeList.collectAsStateWithLifecycle()
    val nodeName = relayNode.nodeLongName ?: "undefined"
    val shortName = relayNode.nodeShortName ?: "undefined"
    val nodeNum = relayNode.relayNodeNum
    val timeLabel = formatRelayTime(relayNode.timestamp)
    val rxSnr = relayNode.rxSnr
    val rxRssi = relayNode.rxRssi
    val relayMetrics = nodes
        .firstOrNull { it.num == nodeNum }
        ?.deviceMetrics
        ?.takeIf { it != DeviceMetrics.getDefaultInstance() }
    val (foregroundColor, backgroundColor) = AppUtil.getNodeColorLabel(nodeNum)

    val snrColor = when {
        rxSnr >= SNR_GOOD_THRESHOLD -> Quality.GOOD.color
        rxSnr >= SNR_FAIR_THRESHOLD -> Quality.FAIR.color
        else -> Quality.BAD.color
    }

    val rssiColor = when {
        rxRssi > RSSI_GOOD_THRESHOLD -> Quality.GOOD.color
        rxRssi > RSSI_FAIR_THRESHOLD -> Quality.FAIR.color
        else -> Quality.BAD.color
    }

    val chUtilColor = relayMetrics
        ?.channelUtilization
        ?.let(AppUtil::channelUtilizationColor)
        ?: Quality.FAIR.color

    val airUtilColor = relayMetrics
        ?.airUtilTx
        ?.let(AppUtil::airUtilTxColor)
        ?: Quality.FAIR.color

    val confidenceColor = AppUtil.relayNodePacketLabelColor(relayNode.confidence)
    var confidence = relayNode.confidence.toString() + "%"

    if (relayNode.isTraceroute) {
        confidence += " (TRACE)"
    } else if (relayNode.isDirect) {
        confidence += " (DIRECT)"
    }

    var highlight by remember { mutableStateOf(false) }

    LaunchedEffect(relayNode.timestamp) {
        highlight = true
        delay(1000)
        highlight = false
    }

    val borderColor by animateColorAsState(
        targetValue = if (highlight) Color.Green else Color.Transparent,
        animationSpec = tween(durationMillis = 900)
    )

    androidx.compose.material.Surface(
        elevation = 4.dp,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(2.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(
                onClick = {
                    Toast.makeText(
                        context,
                        "Closest Relay: $nodeName",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onLongClick = {
                    model.filterForNode(null, nodeName)
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(backgroundColor),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        color = Color(foregroundColor),
                        text = shortName,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "Relay Confidence :",
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .background(
                            confidenceColor,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = confidence,
                        color = Color.Black,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = timeLabel,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RelayMetricBadge(
                    text = if (rxSnr != Float.MAX_VALUE) "SNR ${rxSnr}dB" else "SNR --",
                    color = if (rxSnr != Float.MAX_VALUE) snrColor else Quality.FAIR.color,
                    modifier = Modifier.weight(1f),
                )

                RelayMetricBadge(
                    text = if (rxRssi != Int.MAX_VALUE) "RSSI ${rxRssi}dBm" else "RSSI --",
                    color = if (rxRssi != Int.MAX_VALUE) rssiColor else Quality.FAIR.color,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RelayMetricBadge(
                    text = relayMetrics?.let {
                        "ChUtil ${formatRelayPercent(it.channelUtilization)}"
                    } ?: "ChUtil --",
                    color = if (relayMetrics != null) chUtilColor else Quality.FAIR.color,
                    modifier = Modifier.weight(1f),
                )

                RelayMetricBadge(
                    text = relayMetrics?.let {
                        "AirUtil ${formatRelayPercent(it.airUtilTx)}"
                    } ?: "AirUtil --",
                    color = if (relayMetrics != null) airUtilColor else Quality.FAIR.color,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RelayMetricBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = text,
            color = Color.Black,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatRelayPercent(value: Float): String =
    "${String.format(Locale.getDefault(), "%.1f", value)}%"

@Composable
fun DbImportInfoBox(
    contact: String,
    model: UIViewModel
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(6.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorResource(id = R.color.colorAnnotation),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                CircularProgressIndicator(
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )

                Spacer(modifier = Modifier.width(16.dp))

                val name = if(contact.length > 15){
                    contact.take(15) + "..."
                } else {
                    contact
                }

                Text(
                    text = "FW Sync: $name",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = {
                    model.meshService?.clearPacketQueue()
                    DbImportState.interruptRunningImport()
                }) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Stop import",
                        tint = Color.Red,
                        modifier = Modifier.size(35.dp)
                    )
                }
            }
        }
    }
}

private fun formatRelayTime(timestampMillis: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestampMillis))
}

/* debug purposes only
@Preview(
    name = "RelayInfoBox Light",
    showBackground = true
)
@Composable
fun PreviewRelayInfoBoxLight() {

    val fakeRelay = RelayEvent(
        nodeLongName = "Rome Gateway (TR)",
        nodeShortName = "QQQQ",
        relayNodeNum = 12345616,
        rxSnr = 9.5f,
        rxRssi = -142,
        confidence = 100,
        isTraceroute = true,
        timestamp = System.currentTimeMillis()
    )

    AppTheme {
        RelayInfoBox(
            relayNode = fakeRelay,
        )
    }
}

@Preview(
    name = "RelayInfoBox Dark",
    showBackground = true
)
@Composable
fun PreviewRelayInfoBoxDark() {

    val fakeRelay = RelayEvent(
        nodeLongName = "Rome Gateway (TR)",
        nodeShortName = "QQQQ",
        rxSnr = 4.2f,
        rxRssi = -145,
        confidence = 40,
        isTraceroute = true,
        timestamp = System.currentTimeMillis()
    )

    AppTheme(darkTheme = true) {
        RelayInfoBox(
            relayNode = fakeRelay,
        )
    }
}
*/

