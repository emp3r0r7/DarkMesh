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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.DbImportState
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.RelayEvent
import com.geeksville.mesh.model.SNR_FAIR_THRESHOLD
import com.geeksville.mesh.model.SNR_GOOD_THRESHOLD
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.components.NodeFilterTextField
import com.geeksville.mesh.ui.components.NodeMenuAction
import com.geeksville.mesh.ui.components.Quality
import com.geeksville.mesh.ui.components.RSSI_FAIR_THRESHOLD
import com.geeksville.mesh.ui.components.RSSI_GOOD_THRESHOLD
import com.geeksville.mesh.ui.components.rememberTimeTickWithLifecycle
import com.geeksville.mesh.ui.message.navigateToMessages
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.AppUtil
import dagger.hilt.android.AndroidEntryPoint
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
    val state by model.nodesUiState.collectAsStateWithLifecycle()

    val nodes by model.nodeList.collectAsStateWithLifecycle()
    val ourNode by model.ourNodeInfo.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    val currentTimeMillis = rememberTimeTickWithLifecycle()
    val connectionState by model.connectionState.collectAsStateWithLifecycle()


    //filters nodes with same long name as ours which can occur when switching to SENSOR MODE
    //fix me maybe set arbitrary randomized name when db init occurs in FW!
    val filteredNodes = run {
        val ourNodeName = ourNode?.user?.longName
        val ourNodeId = ourNode?.user?.id

        if (ourNodeName != null && ourNodeId != null) {
            nodes.filterNot { it.user.longName == ourNodeName && it.user.id != ourNodeId }
        } else nodes
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {

            if (relayNode != null) {
                RelayInfoBox(relayNode, model)
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

        items(filteredNodes, key = { it.num }) { node ->
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
                        is NodeMenuAction.MoreDetails -> navigateToNodeDetails(node.num)
                        is NodeMenuAction.FavoriteNode -> model.handleFavorite(node)
                        is NodeMenuAction.RequestDeviceMetadata -> model.requestDeviceMetadata(node)
                    }
                },
                expanded = state.showDetails,
                currentTimeMillis = currentTimeMillis,
                isConnected = connectionState.isConnected(),
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun RelayInfoBox(relayNode: RelayEvent, model: UIViewModel) {

    val nodeName = relayNode.nodeLongName ?: "undefined"
    val shortName = relayNode.nodeShortName ?: "undefined"
    val nodeNum = relayNode.relayNodeNum
    val timeLabel = formatRelayTime(relayNode.timestamp)
    val context = LocalContext.current
    val rxSnr = relayNode.rxSnr
    val rxRssi = relayNode.rxRssi
    val (foregroundColor, backgroundColor) = AppUtil.getNodeColorLabel(nodeNum)

    androidx.compose.material.Surface(
        elevation = 4.dp,
        shape = RoundedCornerShape(10.dp),
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
    )  {

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

        val confidenceColor = AppUtil.relayNodePacketLabelColor(relayNode.confidence)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    4.dp,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "Closest Relay Node Info Confidence :",
                    fontSize = 14.sp
                )

                Box(
                    modifier = Modifier
                        .background(
                            confidenceColor,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "${relayNode.confidence}%",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    9.dp,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .background(
                            color = Color(backgroundColor),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )  {
                    Text(
                        color = Color(foregroundColor),
                        text = shortName,
                        fontSize = 14.sp
                    )
                }

                if (rxSnr != Float.MAX_VALUE) {
                    Box(
                        modifier = Modifier
                            .background(
                                snrColor,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SNR ${rxSnr}dB",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (rxRssi != Int.MAX_VALUE) {
                    Box(
                        modifier = Modifier
                            .background(
                                rssiColor,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "RSSI ${rxRssi}dBm",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Text(
                    text = timeLabel,
                    fontSize = 14.sp
                )
            }
        }
    }
}

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
        confidence = 80,
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
        timestamp = System.currentTimeMillis()
    )

    AppTheme(darkTheme = true) {
        RelayInfoBox(
            relayNode = fakeRelay,
        )
    }
}
*/

