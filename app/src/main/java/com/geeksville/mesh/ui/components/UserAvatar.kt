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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.android.advancedPrefs
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.ui.REMOVE_CUSTOM_ICON_CHAT
import com.geeksville.mesh.ui.preview.NodePreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.ComposableUtil.rememberBooleanPreference
import com.geeksville.mesh.util.IdentIkonGen

internal data class AvatarStyle(val useInitials: Boolean, val width: Dp, val text: TextStyle)

@Composable
internal fun rememberAvatarStyle(): AvatarStyle {
    val initials by rememberBooleanPreference(LocalContext.current.advancedPrefs, REMOVE_CUSTOM_ICON_CHAT, false)
    val measurer = rememberTextMeasurer()
    val text = MaterialTheme.typography.button.copy(fontWeight = FontWeight.Normal)
    val density = LocalDensity.current
    return remember(measurer, text, density, initials) {
        val width = with(density) { measurer.measure("MMMM", style = text).size.width.toDp() }
        AvatarStyle(initials, width, text)
    }
}

@Composable
internal fun UserAvatar(
    node: Node,
    modifier: Modifier = Modifier,
    style: AvatarStyle = rememberAvatarStyle(),
    onClick: () -> Unit = {}
) {
    val nodeId = node.user.id
    val textStyle = style.text
    if (!style.useInitials && nodeId != null){

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.clickable(onClick = onClick)
        ) {
            Spacer(modifier = Modifier.height(3.dp))
            Image(
                bitmap = remember(nodeId) { IdentIkonGen.generateOrGetFromHexId(nodeId) },
                contentDescription = null,
                modifier = Modifier
                    .size(style.width / 1.3f)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = node.user.shortName.ifEmpty { "?" },
                color = MaterialTheme.colors.onSurface,
                style = textStyle.copy(fontSize = textStyle.fontSize * 0.8),
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(style.width)
                .background(
                    color = Color(node.colors.second),
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
        ) {
            Text(
                text = node.user.shortName.ifEmpty { "?" },
                color = Color(node.colors.first),
                style = textStyle,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AvatarPreview(
    @PreviewParameter(NodePreviewParameterProvider::class)
    node: Node
) {
    AppTheme {
        UserAvatar(node)
    }
}
