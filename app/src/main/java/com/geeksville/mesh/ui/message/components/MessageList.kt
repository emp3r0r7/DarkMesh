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

package com.geeksville.mesh.ui.message.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.database.entity.MessageIndexEntry
import com.geeksville.mesh.database.entity.NodeRegistry
import com.geeksville.mesh.database.entity.Reaction
import com.geeksville.mesh.model.Message
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.components.NodeMenu
import com.geeksville.mesh.ui.components.NodeMenuAction
import com.geeksville.mesh.ui.components.SimpleAlertDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Suppress("LongMethod")
@Composable
internal fun MessageList(
    messages: List<MessageIndexEntry>,
    viewModel: UIViewModel,
    selectedIds: MutableState<Set<Long>>,
    onUnreadChanged: (Long) -> Unit,
    contentPadding: PaddingValues,
    onSendReaction: (String, Int) -> Unit,
    onNodeMenuAction: (NodeMenuAction) -> Unit = {},
) {

    val coroutineScope = rememberCoroutineScope()
    var highlightedMessageId by remember { mutableStateOf<Long?>(null) }
    var swipeLocked by remember { mutableStateOf(false) }
    val nodeRegistryEntities by viewModel.nodeRegistryMap.collectAsStateWithLifecycle()
    val nodes by viewModel.unfilteredNodeList.collectAsStateWithLifecycle()
    val connected = viewModel.connectionState.collectAsState().value == MeshService.ConnectionState.CONNECTED
    val haptics = LocalHapticFeedback.current
    val inSelectionMode by remember { derivedStateOf { selectedIds.value.isNotEmpty() } }
    val unreadIndex = remember(messages) { messages.indexOfLast { !it.read } }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = unreadIndex.coerceAtLeast(0)
    )
    // Prefetch a page on either side so most scrolls already have their message bodies.
    val pageSize = 32
    val firstPage by remember { derivedStateOf { listState.firstVisibleItemIndex / pageSize } }
    val lastPage by remember {
        derivedStateOf {
            (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: listState.firstVisibleItemIndex) / pageSize
        }
    }
    val ids = remember(messages, firstPage, lastPage) {
        val start = ((firstPage - 1).coerceAtLeast(0) * pageSize).coerceAtMost(messages.size)
        val end = ((maxOf(firstPage, lastPage) + 2) * pageSize).coerceAtMost(messages.size)
        messages.subList(start, end).map { it.uuid }
    }
    val window by remember(ids, viewModel) {
        viewModel.messagesByIds(ids).map { page -> page.associateBy { it.uuid } }
    }.collectAsStateWithLifecycle(emptyMap())
    val byPacketId = remember(messages) { messages.groupBy { it.packetId } }
    AutoScrollToBottom(listState, messages)
    UpdateUnreadCount(listState, messages, unreadIndex, window, onUnreadChanged)

    var showStatusDialog by remember { mutableStateOf<Message?>(null) }
    if (showStatusDialog != null) {
        val msg = showStatusDialog ?: return
        val (title, text) = msg.getStatusStringRes()
        SimpleAlertDialog(title = title, text = text) { showStatusDialog = null }
    }

    fun highlightMessage(target: Message) {
        highlightedMessageId = target.uuid

        coroutineScope.launch {
            delay(1200)
            highlightedMessageId = null
        }
    }

    var showReactionDialog by remember { mutableStateOf<List<Reaction>?>(null) }
    if (showReactionDialog != null) {
        val reactions = showReactionDialog ?: return
        ReactionDialog(
            reactions,
            viewModel
        ) { showReactionDialog = null }
    }

    fun MutableState<Set<Long>>.toggle(uuid: Long) = if (value.contains(uuid)) {
        value -= uuid
    } else {
        value += uuid
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) {
                    swipeLocked = true
                } else {
                    delay(350) //timeout for reply reactivation
                    swipeLocked = false
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            contentPadding = contentPadding
        ) {
            items(messages, key = { it.uuid }) { entry ->
                val msg = window[entry.uuid]
                if (msg == null) {
                    Spacer(Modifier.height(64.dp))
                    return@items
                }

                val fromLocal = msg.node.user.id == DataPacket.ID_LOCAL
                val selected by remember { derivedStateOf { selectedIds.value.contains(msg.uuid) } }
                val isHighlighted = msg.uuid == highlightedMessageId

                ReactionRow(fromLocal, msg.emojis) { showReactionDialog = msg.emojis }

                Box(Modifier.wrapContentSize(Alignment.TopStart)) {
                    var expandedNodeMenu by remember { mutableStateOf(false) }
                    var nodeRegistry by remember { mutableStateOf<NodeRegistry?>(null) }

                    val quoteId = msg.replyId?.let { id ->
                        byPacketId[id]?.firstOrNull { it.uuid != msg.uuid }?.uuid
                    }
                    val repliedMessage = quoteId?.let { id ->
                        window[id] ?: androidx.compose.runtime.key(id) {
                            remember(viewModel, id) { viewModel.messagesByIds(listOf(id)) }
                                .collectAsStateWithLifecycle(emptyList()).value.firstOrNull()
                        }
                    }

                    SwipeReplyMessage(
                        enabled = !inSelectionMode && !swipeLocked,
                        onReply = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.startReply(msg)
                        }
                    ) {
                        MessageItem(
                            node = msg.node,
                            messageText = msg.text,
                            messageTime = msg.time,
                            messageStatus = msg.status,
                            hopsAway = msg.hopsAway,
                            compressed = msg.compressed,
                            savedBytes = msg.savedBytes,
                            savedAirtime = msg.savedAirtime,
                            selected = selected,
                            highlighted = isHighlighted,
                            repliedMessage = repliedMessage,
                            onClick = {
                                if (inSelectionMode) selectedIds.toggle(msg.uuid)
                            },
                            onLongClick = {
                                selectedIds.toggle(msg.uuid)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onChipClick = {
                                val nodeId = msg.node.user.id
                                val node = nodes.firstOrNull{ it.user.id == nodeId }

                                if(node == null || node.isUnknownUser){

                                    nodeRegistry = nodeRegistryEntities[nodeId]

                                    if(nodeRegistry != null){
                                        expandedNodeMenu = true
                                    }

                                } else if(msg.node.num != 0){
                                    expandedNodeMenu = true
                                }
                            },
                            onStatusClick = { showStatusDialog = msg },
                            onSendReaction = { onSendReaction(it, msg.packetId) },
                            onQuotedClick = { quoted -> highlightMessage(quoted) },
                            uiModel = viewModel
                        )
                    }

                    if(connected){
                        NodeMenu(
                            nodeModel = msg.node,
                            showFullMenu = true,
                            expanded = expandedNodeMenu,
                            nodeRegistry = nodeRegistry,
                            onDismissRequest = {
                                expandedNodeMenu = false
                            },
                            onAction = onNodeMenuAction
                        )
                    }
                }
            }
        }
        ScrollToBottomButton(
            listState,
            modifier = Modifier
                .align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun ScrollToBottomButton(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    val showButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2
        }
    }

    AnimatedVisibility(
        visible = showButton,
        modifier = modifier
            .padding(16.dp)
    ) {
        FloatingActionButton(
            onClick = {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
            },
            containerColor = colorResource(id = R.color.colorPrimary),
            contentColor = colorResource(id = R.color.colorPrimary)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll to bottom",
                tint = colorResource(id = R.color.design_default_color_surface),
                )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeReplyMessage(
    enabled: Boolean,
    onReply: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance ->
            totalDistance * 0.5f
        },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onReply()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            ReplySwipeBackground(progress = dismissState.progress)
        }
    ) {
        content()
    }
}

@Composable
fun ReplySwipeBackground(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            tint = colorResource(id = R.color.colorAnnotation),
            contentDescription = "Reply",
            modifier = Modifier
                .scale(0.9f + progress * 0.3f)
                .alpha(progress.coerceIn(0.3f, 1f))
        )
    }
}



@Composable
private fun <T> AutoScrollToBottom(
    listState: LazyListState,
    list: List<T>,
    itemThreshold: Int = 3,
) = with(listState) {
    val shouldAutoScroll by remember { derivedStateOf { firstVisibleItemIndex < itemThreshold } }
    if (shouldAutoScroll) {
        LaunchedEffect(list) {
            if (!isScrollInProgress) {
                scrollToItem(0)
            }
        }
    }
}

@Composable
private fun UpdateUnreadCount(
    listState: LazyListState,
    messages: List<MessageIndexEntry>,
    unreadIndex: Int,
    loaded: Map<Long, Message>,
    onUnreadChanged: (Long) -> Unit,
) {
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val entry = messages.getOrNull(firstVisible)
    if (entry != null && firstVisible <= unreadIndex && entry.uuid in loaded) {
        LaunchedEffect(entry.uuid, entry.receivedTime) {
            delay(500L)
            onUnreadChanged(entry.receivedTime)
        }
    }
}
