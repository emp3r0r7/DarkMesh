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

import android.content.res.Configuration
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ChipDefaults
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.PhonelinkErase
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.android.advancedPrefs
import com.geeksville.mesh.database.entity.NodeRegistry
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.ui.components.NodeKeyStatusIcon
import com.geeksville.mesh.ui.components.NodeMenu
import com.geeksville.mesh.ui.components.NodeMenuAction
import com.geeksville.mesh.ui.components.SignalInfo
import com.geeksville.mesh.ui.compose.ElevationInfo
import com.geeksville.mesh.ui.compose.SatelliteCountInfo
import com.geeksville.mesh.ui.preview.NodePreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.AppUtil
import com.geeksville.mesh.util.AppUtil.maybeGetStatusMessage
import com.geeksville.mesh.util.ComposableUtil.rememberBooleanPreference
import com.geeksville.mesh.util.IdentIkonGen
import com.geeksville.mesh.util.toDistanceString
import kotlinx.coroutines.delay
import org.meshtastic.proto.ConfigProtos.Config.DeviceConfig
import org.meshtastic.proto.ConfigProtos.Config.DisplayConfig
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.TelemetryProtos

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NodeItem(
    thisNode: Node?,
    thatNode: Node,
    gpsFormat: Int,
    distanceUnits: Int,
    tempInFahrenheit: Boolean,
    onAction: (NodeMenuAction) -> Unit = {},
    expanded: Boolean = false,
    currentTimeMillis: Long,
    isConnected: Boolean = false,
    nodeRegistry: Map<String, NodeRegistry>,
    ourStatusMessage: String
) {

    val context = LocalContext.current

    val removeCustomIconChatPrefs by rememberBooleanPreference(
        context.advancedPrefs,
        REMOVE_CUSTOM_ICON_CHAT,
        false
    )

    val showAirUtilChUtilPrefs by rememberBooleanPreference(
        context.advancedPrefs,
        SHOW_AIRUTIL_CHUTIL,
        false
    )

    val showAirUtilChUtilAllNodesPrefs by rememberBooleanPreference(
        context.advancedPrefs,
        SHOW_AIRUTIL_CHUTIL_ALL_NODES,
        false
    )

    val isIgnored = thatNode.isIgnored

    var longName = thatNode.user.longName.ifEmpty { stringResource(id = R.string.unknown_username) }
    var shortName = thatNode.user.shortName.ifEmpty { "???" }

    var style = LocalTextStyle.current

    if(thatNode.isUnknownUser){
        style = LocalTextStyle.current.copy(fontStyle = FontStyle.Italic)

        nodeRegistry[thatNode.user.id]?.let {
            longName = it.longName ?: longName
            shortName = it.shortName ?: shortName
        }
    }

    val unmessagable = thatNode.user.isUnmessagable

    val isThisNode = thisNode?.num == thatNode.num
    val system = remember(distanceUnits) { DisplayConfig.DisplayUnits.forNumber(distanceUnits) }
    val distance = remember(thisNode, thatNode) {
        thisNode?.distance(thatNode)?.takeIf { it > 0 }?.toDistanceString(system)
    }
    val (textColor, nodeColor) = thatNode.colors

    val hwInfoString = when (val hwModel = thatNode.user.hwModel) {
        MeshProtos.HardwareModel.UNSET -> MeshProtos.HardwareModel.UNSET.name
        else -> hwModel.name.replace('_', '-').replace('p', '.').lowercase()
    }
    val roleName = if (thatNode.isUnknownUser) {
        DeviceConfig.Role.UNRECOGNIZED.name
    } else {
        thatNode.user.role.name
    }

    val infrastructure = AppUtil.isInfrastructure(roleName)
    val airUtilChUtilInfrastructureRole = roleName in setOf(
        DeviceConfig.Role.CLIENT_BASE.name,
        DeviceConfig.Role.ROUTER.name,
        DeviceConfig.Role.ROUTER_LATE.name,
    )
    val hasDeviceMetricsTelemetry =
        thatNode.deviceMetrics != TelemetryProtos.DeviceMetrics.getDefaultInstance()
    val shouldShowAirUtilChUtil =
        showAirUtilChUtilPrefs &&
            hasDeviceMetricsTelemetry &&
            (showAirUtilChUtilAllNodesPrefs || airUtilChUtilInfrastructureRole)

    val (detailsShown, showDetails) = remember { mutableStateOf(expanded) }

    val id = thatNode.user.id.ifEmpty { "???" }

    val thatStatusMessage = thatNode.nodeStatus

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .defaultMinSize(minHeight = 80.dp),
        elevation = 4.dp,
        onClick = { showDetails(!detailsShown) },
    ) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier.wrapContentSize(Alignment.TopStart),
                    ) {
                        if(removeCustomIconChatPrefs){
                            Chip(
                                modifier = Modifier
                                    .width(IntrinsicSize.Min)
                                    .defaultMinSize(minHeight = 32.dp, minWidth = 42.dp),
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = Color(nodeColor),
                                    contentColor = Color(textColor),
                                ),
                                onClick = {
                                    menuExpanded = !menuExpanded
                                },
                            ) {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = shortName,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = MaterialTheme.typography.button.fontSize,
                                    textDecoration = TextDecoration.LineThrough.takeIf { isIgnored },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            PremiumChip(
                                text = shortName,
                                nodeColor = Color(nodeColor),
                                textColor = Color(textColor),
                                icon = IdentIkonGen.generateOrGetFromHexId(id),
                                enabled = isThisNode,
                                isConnected = isConnected,
                                onClick = { menuExpanded = !menuExpanded }
                            )
                        }

                        NodeMenu(
                            nodeModel = thatNode,
                            isThisNode = isThisNode,
                            isConnected = isConnected,
                            showFullMenu = !isThisNode && isConnected,
                            onAction = onAction,
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        )
                    }
                    NodeKeyStatusIcon(
                        hasPKC = thatNode.hasPKC,
                        mismatchKey = thatNode.mismatchKey,
                        publicKey = thatNode.user.publicKey,
                        modifier = Modifier.size(32.dp)
                    )

                    if(!isThisNode && isConnected){
                        TraceIcon(
                            onTrace = {
                                onAction(NodeMenuAction.TraceRoute(thatNode))
                            }
                        )
                    }

                    Text(
                        modifier = Modifier.weight(1f),
                        text = longName,
                        style = style,
                        textDecoration = TextDecoration.LineThrough.takeIf { isIgnored },
                        softWrap = true,
                    )

                    if (!isThisNode && thatNode.isFavorite) {
                        Icon(
                            modifier = Modifier
                                .padding(horizontal = 5.dp),
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite node",
                            tint = MaterialTheme.colors.primary
                        )
                    }

                    if (infrastructure || unmessagable) {

                        var icon = Icons.Default.SettingsInputAntenna
                        var description = "Infrastructure node, may not respond to private messages."
                        var iconColor = R.color.colorAnnotation

                        if(unmessagable && !infrastructure){
                            icon = Icons.Default.PhonelinkErase
                            description = "Unmessageable node, may not respond to private messages."
                            iconColor = R.color.colorPrimaryDark

                            if(isThisNode){
                                description = "Your node is set as unmessageable."
                            }
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                            tooltip = { PlainTooltip {
                                androidx.compose.material3.Text(
                                    description
                                )
                            } },
                            state = rememberTooltipState(),
                        ) {
                            Icon(
                                modifier = Modifier
                                    .padding(horizontal = 5.dp),
                                imageVector = icon,
                                contentDescription = "Infrastructure or Unmessagable",
                                tint = colorResource(id = iconColor),
                            )
                        }
                    }

                    LastHeardInfo(
                        lastHeard = thatNode.lastHeard,
                        currentTimeMillis = currentTimeMillis
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (distance != null) {
                        Text(
                            text = distance,
                            fontSize = MaterialTheme.typography.button.fontSize,
                        )
                    } else {
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    BatteryInfo(
                        batteryLevel = thatNode.batteryLevel,
                        voltage = thatNode.voltage
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SignalInfo(
                        node = thatNode,
                        isThisNode = isThisNode
                    )
                    thatNode.validPosition?.let { position ->
                        val satCount = position.satsInView
                        if (satCount > 0) {
                            SatelliteCountInfo(satCount = satCount)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val telemetryString = thatNode.getTelemetryString(tempInFahrenheit)
                    if (telemetryString.isNotEmpty()) {
                        Text(
                            text = telemetryString,
                            color = MaterialTheme.colors.onSurface,
                            fontSize = MaterialTheme.typography.button.fontSize,
                        )
                    }
                }

                if (detailsShown || expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        thatNode.validPosition?.let {
                            LinkedCoordinates(
                                latitude = thatNode.latitude,
                                longitude = thatNode.longitude,
                                format = gpsFormat,
                                nodeName = longName
                            )
                        }
                        thatNode.validPosition?.let { position ->
                            ElevationInfo(
                                altitude = position.altitude,
                                system = system,
                                suffix = stringResource(id = R.string.elevation_suffix)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = hwInfoString,
                            fontSize = MaterialTheme.typography.button.fontSize,
                            style = style,
                        )
                        Text(
                            modifier = Modifier.weight(1f),
                            text = roleName,
                            textAlign = TextAlign.Center,
                            fontSize = MaterialTheme.typography.button.fontSize,
                            style = style,
                        )
                        Text(
                            modifier = Modifier.weight(1f),
                            text = id,
                            textAlign = TextAlign.End,
                            fontSize = MaterialTheme.typography.button.fontSize,
                            style = style,
                        )
                    }

                    if (shouldShowAirUtilChUtil) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.channel_air_util).format(
                                    thatNode.deviceMetrics.channelUtilization,
                                    thatNode.deviceMetrics.airUtilTx,
                                ),
                                fontSize = MaterialTheme.typography.button.fontSize,
                                style = style,
                            )
                        }
                    }
                }

                maybeGetStatusMessage(ourStatusMessage, thatStatusMessage, isThisNode)?.let{
                    StatusMessage(it, style, FontStyle.Italic)
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = false)
fun NodeInfoSimplePreview() {
    AppTheme {
        val thisNode = NodePreviewParameterProvider().values.first()
        val thatNode = NodePreviewParameterProvider().values.last()
        NodeItem(
            thisNode = thisNode,
            thatNode = thatNode,
            1,
            0,
            true,
            currentTimeMillis = System.currentTimeMillis(),
            nodeRegistry = HashMap(),
            ourStatusMessage =  "Hello"
        )
    }
}

@Composable
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
fun NodeInfoPreview(
    @PreviewParameter(NodePreviewParameterProvider::class)
    thatNode: Node
) {
    AppTheme {
        val thisNode = NodePreviewParameterProvider().values.first()
        Column {
            Text(
                text = "Details Collapsed",
                color = MaterialTheme.colors.onBackground
            )
            NodeItem(
                thisNode = thisNode,
                thatNode = thatNode,
                gpsFormat = 0,
                distanceUnits = 1,
                tempInFahrenheit = true,
                expanded = false,
                currentTimeMillis = System.currentTimeMillis(),
                nodeRegistry = HashMap(),
                ourStatusMessage =  "Hello"
            )
            Text(
                text = "Details Shown",
                color = MaterialTheme.colors.onBackground
            )
            NodeItem(
                thisNode = thisNode,
                thatNode = thatNode,
                gpsFormat = 0,
                distanceUnits = 1,
                tempInFahrenheit = true,
                expanded = true,
                currentTimeMillis = System.currentTimeMillis(),
                nodeRegistry = HashMap(),
                ourStatusMessage =  "Hello"
            )
        }
    }
}

@Composable
fun TraceIcon(
    onTrace: () -> Unit
) {
    var trigger by remember { mutableStateOf(false) }

    val baseColor = Color.Transparent
    val activeColor = Color.Green.copy(alpha = 0.5f)
    val textColor = MaterialTheme.colors.onSurface
    val animationDelay = 1500L

    val animatedColor by animateColorAsState(
        targetValue = if (trigger) activeColor else baseColor,
        animationSpec = tween(durationMillis = animationDelay.toInt())
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(35.dp)
            .padding(end = 5.dp)
            .clip(CircleShape)
            .background(animatedColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = textColor
                )
            ) {
                trigger = true
                onTrace()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Route,
            contentDescription = "Traceroute",
            tint = textColor,
            modifier = Modifier.size(22.dp)
        )
    }

    LaunchedEffect(trigger) {
        if (trigger) {
            delay(animationDelay)
            @Suppress("AssignedValueIsNeverRead")
            trigger = false
        }
    }
}

@Composable
fun PremiumChip(
    text: String,
    nodeColor: Color,
    textColor: Color,
    icon: ImageBitmap,
    enabled: Boolean,
    isConnected: Boolean,
    onClick: () -> Unit
) {

    val shape = RoundedCornerShape(50)

    Box {

        if (enabled && !isConnected) {

            val transition = rememberInfiniteTransition(label = "shimmer")

            val offsetA by transition.animateFloat(
                initialValue = 0f,
                targetValue = 2000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(12000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "flowA"
            )

            val offsetB by transition.animateFloat(
                initialValue = 2000f,
                targetValue = -2000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(18000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "flowB"
            )

            val baseColors = listOf(
                Color(0xFF450A0A),
                Color(0xFF7F1D1D),
                Color(0xFFB91C1C),
                Color(0xFFEF4444),
                Color(0xFFFCA5A5),
                Color(0xFFEF4444),
                Color(0xFFB91C1C),
                Color(0xFF7F1D1D),
                Color(0xFF450A0A)
            )

            Canvas(
                modifier = Modifier
                    .matchParentSize()
            ) {

                val colors = baseColors.map { it.toArgb() }.toIntArray()
                val inset = 3.dp.toPx()

                drawIntoCanvas { canvas ->

                    val shaderA = LinearGradient(
                        offsetA,
                        0f,
                        offsetA + size.width * 3f,
                        size.height,
                        colors,
                        null,
                        Shader.TileMode.MIRROR
                    )

                    val shaderB = LinearGradient(
                        offsetB,
                        size.height,
                        offsetB + size.width * 2f,
                        0f,
                        colors,
                        null,
                        Shader.TileMode.MIRROR
                    )

                    val paintA = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeWidth = 6.dp.toPx()
                        maskFilter = BlurMaskFilter(
                            26f,
                            BlurMaskFilter.Blur.NORMAL
                        )
                        shader = shaderA
                    }

                    val paintB = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeWidth = 6.dp.toPx()
                        maskFilter = BlurMaskFilter(
                            18f,
                            BlurMaskFilter.Blur.NORMAL
                        )
                        shader = shaderB
                    }

                    canvas.nativeCanvas.drawRoundRect(
                        inset,
                        inset,
                        size.width - inset,
                        size.height - inset,
                        size.height / 2,
                        size.height / 2,
                        paintA
                    )

                    canvas.nativeCanvas.drawRoundRect(
                        inset,
                        inset,
                        size.width - inset,
                        size.height - inset,
                        size.height / 2,
                        size.height / 2,
                        paintB
                    )
                }
            }
        }

        Surface(
            shape = shape,
            color = nodeColor,
            modifier = Modifier.clickable { onClick() }
        ) {

            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 32.dp, minWidth = 32.dp)
                    .padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = (-2).dp)
                        .size(28.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = text,
                    color = textColor,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .offset(x = (-3).dp)
                )
            }
        }
    }
}

@Composable
fun StatusMessage(
    statusMessage: String,
    style: TextStyle,
    fontStyle: FontStyle
){
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Notes,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.Green
        )
        Text(
            text = statusMessage,
            fontSize = MaterialTheme.typography.button.fontSize,
            maxLines = 2,
            fontStyle = fontStyle,
            style = style,
            overflow = TextOverflow.Ellipsis,
        )
    }
}