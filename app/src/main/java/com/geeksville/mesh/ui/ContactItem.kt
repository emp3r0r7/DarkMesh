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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.DataPacket.CREATOR.ID_BROADCAST
import com.geeksville.mesh.android.advancedPrefs
import com.geeksville.mesh.model.Contact
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.ComposableUtil.rememberBooleanPreference
import com.geeksville.mesh.util.IdentIkonGen

@Suppress("LongMethod")
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun ContactItem(
    contact: Contact,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    viewModel: UIViewModel = hiltViewModel()
) = with(contact) {

    val isChannel = contactKey.contains(ID_BROADCAST)

    val context = LocalContext.current
    val removeCustomIconChatPrefs by rememberBooleanPreference(
        context.advancedPrefs,
        REMOVE_CUSTOM_ICON_CHAT,
        false
    )

    // zero is default like medium fast, short turbo.. etc
    val isDefaultChannnel = isChannel && "0" == contact.shortName

    Card(
        modifier = Modifier
            .background(color = if (selected) Color.Gray else MaterialTheme.colors.background)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Surface(
            modifier = modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
            color = MaterialTheme.colors.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if(isChannel){

                    Image(
                        painter = painterResource(R.drawable.ic_twotone_people_24),
                        contentDescription = "Channel Chat",
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .width(50.dp),
                        colorFilter = if (isDefaultChannnel){
                            ColorFilter.tint(Color.Green)
                        } else {
                            ColorFilter.tint(MaterialTheme.colors.primary)
                        }
                    )
                } else {

                    if(removeCustomIconChatPrefs){
                        Chip(
                            onClick = { },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .width(72.dp),
                        ) {
                            Text(
                                text = shortName,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = MaterialTheme.typography.button.fontSize,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Image(
                            bitmap = IdentIkonGen.generateOrGetFromHexId(
                                nodeId ?: "???",
                            ),
                            contentDescription = null,
                            modifier = Modifier
                                .size(45.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.width(13.dp))
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        var name = longName
                        var fontStyle = FontStyle.Normal

                        if (nodeId != null && !isChannel) {
                            val node = viewModel.getNode(nodeId)

                            if (node.isUnknownUser) {
                                fontStyle = FontStyle.Italic
                                val registryNode = viewModel.getNodeRegistry(node.user.id)
                                name = registryNode?.longName ?: longName
                            }
                        }

                        if(isDefaultChannnel){
                            name += " (Public)"
                        }

                        Text(
                            text = name,
                            modifier = Modifier.weight(1f),
                            fontStyle = fontStyle
                        )
                        Text(
                            text = lastMessageTime.orEmpty(),
                            color = MaterialTheme.colors.onSurface,
                            fontSize = MaterialTheme.typography.button.fontSize,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = lastMessageText.orEmpty(),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colors.onSurface,
                            fontSize = MaterialTheme.typography.button.fontSize,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                        )
                        AnimatedVisibility(visible = isMuted) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_twotone_volume_off_24),
                                contentDescription = null,
                            )
                        }
                        AnimatedVisibility(visible = unreadCount > 0) {
                            Text(
                                text = unreadCount.toString(),
                                modifier = Modifier
                                    .background(MaterialTheme.colors.primary, shape = CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                color = MaterialTheme.colors.onPrimary,
                                style = MaterialTheme.typography.caption,
                            )
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ContactItemPreview() {
    AppTheme {
        ContactItem(
            contact = Contact(
                contactKey = "!asd1235",
                shortName = stringResource(R.string.some_username),
                longName = stringResource(R.string.unknown_username),
                lastMessageTime = "3 minutes ago",
                lastMessageText = stringResource(R.string.sample_message),
                unreadCount = 2,
                messageCount = 10,
                nodeId = "12346578",
                isMuted = true,
            ),
            selected = false,
        )
    }
}
