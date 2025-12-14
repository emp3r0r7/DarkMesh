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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.RelayEvent
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.components.NodeFilterTextField
import com.geeksville.mesh.ui.components.NodeMenuAction
import com.geeksville.mesh.ui.components.rememberTimeTickWithLifecycle
import com.geeksville.mesh.ui.message.navigateToMessages
import com.geeksville.mesh.ui.theme.AppTheme
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

                AppTheme {
                    NodesScreen(
                        model = model,
                        relayNode = relayNode,
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
                    }
                },
                expanded = state.showDetails,
                currentTimeMillis = currentTimeMillis,
                isConnected = connectionState.isConnected(),
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelayInfoBox(relayNode: RelayEvent, model: UIViewModel) {

    val (nodeName, timeLabel) = parseNodeNameAndTimestamp(relayNode)
    val context = LocalContext.current

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
                        "Nodo Gateway in prossimità: $nodeName",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onLongClick = {

                    var longName = relayNode.nodeLongName

                    //fixme, use a more reliable way to do this
                    longName?.let {
                        /* this filter is used when the gateway is obtained from the latest
                           traceroute done by the user */
                        if(longName.contains("(") && longName.contains(")")) {

                            longName = relayNode.nodeLongName
                                ?.substringBefore("(")
                                ?.trim()
                        }
                    }

                    model.filterForNode(null, longName)
                }
            )
    )  {
        Text(
            text = "⏩ " +
                    "$nodeName • $timeLabel",
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun parseNodeNameAndTimestamp(relayNode: RelayEvent): Pair<String?, String>{
    val nodeName = relayNode.nodeLongName ?: "undefined"
    return nodeName to formatRelayTime(relayNode.timestamp)
}

private fun formatRelayTime(timestampMillis: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestampMillis))
}