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

import android.text.SpannedString
import android.util.LruCache
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Job
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.database.entity.MessageIndexEntry
import com.geeksville.mesh.database.entity.NodeRegistry
import com.geeksville.mesh.database.entity.Reaction
import com.geeksville.mesh.model.ChatMessageIndex
import com.geeksville.mesh.model.Message
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.components.rememberAvatarStyle
import com.geeksville.mesh.ui.components.NodeMenu
import com.geeksville.mesh.ui.components.NodeMenuAction
import com.geeksville.mesh.ui.components.SimpleAlertDialog
import kotlinx.coroutines.delay
import com.geeksville.mesh.ui.components.detectMessageLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private class ChatRow(val message: Message, val links: SpannedString)

@Suppress("LongMethod")
@Composable
internal fun MessageList(
    history: ChatMessageIndex,
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
    val messages = history.entries
    val unreadIndex = history.unreadIndex
    val localNodeId by remember(viewModel) {
        viewModel.ourNodeInfo.map { it?.user?.id }
    }.collectAsStateWithLifecycle(initialValue = viewModel.ourNodeInfo.value?.user?.id)
    val nodesById = remember(nodes) { nodes.asReversed().associateBy { it.user.id } }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = unreadIndex.coerceAtLeast(0)
    )
    // Prefetch a page on either side so most scrolls already have their message bodies.
    val pageSize = 32
    val firstPage by remember { derivedStateOf { listState.firstVisibleItemIndex / pageSize } }
    val avatarStyle = rememberAvatarStyle()
    val lastPage by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex + (listState.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(0)) / pageSize
        }
    }
    val lastIndexPage = messages.lastIndex.coerceAtLeast(0) / pageSize
    val latestPage by remember(lastIndexPage) {
        derivedStateOf { (listState.layoutInfo.visibleItemsInfo.size / pageSize).coerceAtMost(lastIndexPage) }
    }
    val pages = remember(firstPage, lastPage, lastIndexPage, latestPage) {
        // Keep the destination warm and retain overlapping pages as the viewport moves.
        ((0..latestPage) + ((firstPage - 1).coerceAtLeast(0)..(lastPage + 1).coerceAtMost(lastIndexPage))).distinct()
    }
    val linkCache = remember(LocalConfiguration.current) {
        object : LruCache<String, SpannedString>(256) {
            override fun create(key: String) = detectMessageLinks(key)
        }
    }
    val pageMaps = pages.map { page ->
        key(page) {
            val ids = remember(history, page) {
                val start = (page * pageSize).coerceAtMost(messages.size)
                messages.subList(start, (start + pageSize).coerceAtMost(messages.size)).map { it.uuid }
            }
            val rows by remember(viewModel, ids, linkCache) {
                viewModel.messagesByIds(ids).map { batch ->
                    batch.associate { it.uuid to ChatRow(it, linkCache.get(it.text)) }
                }.flowOn(Dispatchers.Default)
            }.collectAsStateWithLifecycle(emptyMap())
            rows
        }
    }
    val window = remember(pageMaps) { buildMap { pageMaps.forEach { putAll(it) } } }
    var loadingLatest by remember { mutableStateOf(false) }
    if (loadingLatest && messages.isNotEmpty()) {
        val dragging by listState.interactionSource.collectIsDraggedAsState()
        val ready = (0 until listState.layoutInfo.visibleItemsInfo.size.coerceIn(1, messages.size)).all { messages[it].uuid in window }
        LaunchedEffect(dragging, ready) {
            if (dragging) {
                loadingLatest = false
            } else if (ready) {
                try {
                    listState.scrollToItem(0)
                } finally {
                    loadingLatest = false
                }
            }
        }
    }
    val byPacketId = history.byPacketId
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
            items(
                items = messages,
                key = { it.uuid },
                contentType = { entry -> window[entry.uuid]?.message?.let { it.node.user.id == DataPacket.ID_LOCAL } },
            ) { entry ->
                val row = window[entry.uuid]
                if (row == null) {
                    Spacer(Modifier.height(64.dp))
                    return@items
                }

                val msg = row.message
                val fromLocal = msg.node.user.id == DataPacket.ID_LOCAL
                val selected by remember { derivedStateOf { selectedIds.value.contains(msg.uuid) } }
                val isHighlighted = msg.uuid == highlightedMessageId

                if (msg.emojis.isNotEmpty()) {
                    ReactionRow(fromLocal, msg.emojis) { showReactionDialog = msg.emojis }
                }

                Box(Modifier.wrapContentSize(Alignment.TopStart)) {
                    var expandedNodeMenu by remember { mutableStateOf<Boolean?>(null) }
                    var nodeRegistry by remember { mutableStateOf<NodeRegistry?>(null) }

                    val quoteId = msg.replyId?.takeIf { it != 0 }?.let { id ->
                        byPacketId[id]?.firstOrNull { it.uuid != msg.uuid }?.uuid
                    }
                    val repliedMessage = quoteId?.let { id ->
                        window[id]?.message ?: androidx.compose.runtime.key(id) {
                            remember(viewModel, id) { viewModel.messagesByIds(listOf(id)) }
                                .collectAsStateWithLifecycle(emptyList()).value.firstOrNull()
                        }
                    }

                    SwipeReplyMessage(
                        enabled = { !inSelectionMode && !swipeLocked },
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
                                val node = nodesById[nodeId]

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
                            avatarStyle = avatarStyle,
                            detectedLinks = row.links,
                            registryName = if (nodesById[msg.node.user.id]?.isUnknownUser != false) {
                                nodeRegistryEntities[msg.node.user.id]?.longName
                            } else null,
                            localNodeId = localNodeId,
                            uiModel = viewModel
                        )
                    }

                    if (connected && expandedNodeMenu != null) {
                        NodeMenu(
                            nodeModel = msg.node,
                            showFullMenu = true,
                            expanded = expandedNodeMenu == true,
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
            listState = listState,
            onClick = {
                if (!loadingLatest && messages.isNotEmpty()) {
                    coroutineScope.launch {
                        if (messages.take(listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)).all { it.uuid in window }) {
                            listState.scrollToItem(0)
                        } else {
                            listState.stopScroll()
                            loadingLatest = true
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun ScrollToBottomButton(
    listState: LazyListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2
        }
    }

    if (showButton) {
        FloatingActionButton(
            modifier = modifier.padding(16.dp),
            onClick = onClick,
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


@Composable
private fun SwipeReplyMessage(
    enabled: () -> Boolean,
    onReply: () -> Unit,
    content: @Composable () -> Unit,
) {
    val canReply by rememberUpdatedState(enabled)
    val reply by rememberUpdatedState(onReply)
    val scope = rememberCoroutineScope()
    var displacement by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }

    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    val moving by remember { derivedStateOf { displacement != 0f } }
    Box(Modifier.pointerInput(direction) {
        var settling: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!canReply()) return@awaitEachGesture
            // Vertical drags stay with the LazyColumn. No row state changes before horizontal slop.
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                if (canReply() && overSlop * direction > 0f) {
                    settling?.cancel()
                    width = size.width.toFloat().coerceAtLeast(1f)
                    displacement = (displacement + overSlop).coerceIn(-width, width)
                    change.consume()
                }
            } ?: return@awaitEachGesture
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)
            velocity.addPosition(start.uptimeMillis, start.position)
            val released = horizontalDrag(start.id) { change ->
                val distance = (displacement + change.positionChange().x) * direction
                displacement = distance.coerceIn(0f, width) * direction
                velocity.addPosition(change.uptimeMillis, change.position)
                change.consume()
            }
            val speed = velocity.calculateVelocity().x * direction
            val flingThreshold = 125.dp.toPx()
            if (released && canReply() && displacement * direction > 0f &&
                (speed >= flingThreshold || (speed > -flingThreshold && displacement * direction >= width / 2f))
            ) reply()
            settling = scope.launch {
                animate(displacement, 0f, animationSpec = tween(150)) { value, _ -> displacement = value }
            }
        }
    }) {
        if (moving) {
            Box(Modifier.matchParentSize()) { ReplySwipeBackground(progress = kotlin.math.abs(displacement) / width) }
        }
        Box(if (moving) Modifier.graphicsLayer { translationX = displacement } else Modifier) { content() }
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
    loaded: Map<Long, ChatRow>,
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
