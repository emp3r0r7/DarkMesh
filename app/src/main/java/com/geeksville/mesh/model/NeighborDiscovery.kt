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

package com.geeksville.mesh.model

import android.graphics.Color
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.entity.NodeRegistry
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.Portnums
import java.util.Locale

data class NeighborDiscoveryNode(
    val nodeNum: Int,
    val userId: String,
    val longName: String,
    val shortName: String,
)

data class NeighborDiscoveryLink(
    val node: NeighborDiscoveryNode,
    val snr: Float,
)

data class NeighborDiscoveryResult(
    val origin: NeighborDiscoveryNode,
    val discovered: List<NeighborDiscoveryLink>,
)

data class NeighborDiscoveryMapLink(
    val origin: Node,
    val discovered: Node,
    val snr: Float,
)

data class NeighborDiscoveryMap(
    val origin: Node,
    val discovered: List<Node>,
    val links: List<NeighborDiscoveryMapLink>,
    val source: NeighborDiscoveryResult,
)

val MeshProtos.MeshPacket.fullNeighborInfo: MeshProtos.NeighborInfo?
    get() = with(decoded) {
        if (hasDecoded() && !wantResponse && portnum == Portnums.PortNum.NEIGHBORINFO_APP) {
            runCatching { MeshProtos.NeighborInfo.parseFrom(payload) }.getOrNull()
        } else {
            null
        }
    }

fun MeshProtos.MeshPacket.getNeighborDiscoveryResult(
    getUser: (nodeNum: Int) -> MeshProtos.User?,
): NeighborDiscoveryResult? = fullNeighborInfo?.let { info ->
    val originNum = info.nodeId.takeIf { it != 0 } ?: from
    NeighborDiscoveryResult(
        origin = resolveNeighborDiscoveryNode(originNum, getUser),
        discovered = info.neighborsList
            .filter { it.nodeId != 0 && it.nodeId != originNum }
            .map { neighbor ->
                NeighborDiscoveryLink(
                    node = resolveNeighborDiscoveryNode(neighbor.nodeId, getUser),
                    snr = neighbor.snr,
                )
            }
            .distinctBy { it.node.nodeNum }
            .sortedByDescending { it.snr }
    )
}

fun evaluateNeighborDiscoveryMapAvailability(
    discovery: NeighborDiscoveryResult?,
    nodeDb: NodeRepository,
    nodeRegistryMap: Map<String, NodeRegistry>,
): NeighborDiscoveryMap? {
    val source = discovery ?: return null
    val origin = resolveNodeForNeighborMap(source.origin, nodeDb, nodeRegistryMap) ?: return null
    if (!origin.hasNeighborMapPosition()) return null

    val links = source.discovered.mapNotNull { link ->
        val discovered = resolveNodeForNeighborMap(link.node, nodeDb, nodeRegistryMap)
            ?: return@mapNotNull null
        if (!discovered.hasNeighborMapPosition()) return@mapNotNull null

        NeighborDiscoveryMapLink(
            origin = origin,
            discovered = discovered,
            snr = link.snr,
        )
    }.distinctBy { it.discovered.num }

    if (links.isEmpty()) return null

    return NeighborDiscoveryMap(
        origin = origin,
        discovered = links.map { it.discovered },
        links = links,
        source = source,
    )
}

fun neighborDiscoverySnrColor(snr: Float?): Int = when {
    snr == null -> Color.GRAY
    snr >= SNR_GOOD_THRESHOLD -> Color.GREEN
    snr >= SNR_FAIR_THRESHOLD -> Color.rgb(255, 230, 0)
    else -> Color.rgb(247, 147, 26)
}

fun formatNeighborDiscoverySnr(snr: Float?): String = when (snr) {
    null -> "? dB"
    else -> String.format(Locale.US, "%.1f dB", snr)
}

private fun resolveNeighborDiscoveryNode(
    nodeNum: Int,
    getUser: (nodeNum: Int) -> MeshProtos.User?,
): NeighborDiscoveryNode {
    val fallbackId = DataPacket.nodeNumToDefaultId(nodeNum)
    val user = getUser(nodeNum)
    val shortName = user?.shortName?.takeIf { it.isNotBlank() } ?: fallbackId.takeLast(4)
    val longName = user?.longName?.takeIf { it.isNotBlank() } ?: "Meshtastic ${fallbackId.takeLast(4)}"

    return NeighborDiscoveryNode(
        nodeNum = nodeNum,
        userId = user?.id?.takeIf { it.isNotBlank() } ?: fallbackId,
        longName = longName,
        shortName = shortName,
    )
}

private fun resolveNodeForNeighborMap(
    node: NeighborDiscoveryNode,
    nodeDb: NodeRepository,
    nodeRegistryMap: Map<String, NodeRegistry>,
): Node? {
    nodeDb.nodeDBbyNum.value[node.nodeNum]?.let { knownNode ->
        if (knownNode.hasNeighborMapPosition()) return knownNode
    }

    val registryNode = nodeRegistryMap[node.userId] ?: return null
    val latitudeI = registryNode.latitudeI ?: return null
    val longitudeI = registryNode.longitudeI ?: return null
    val defaultName = registryNode.defaultName ?: "Meshtastic ${node.userId.takeLast(4)}"

    return Node(
        num = registryNode.nodeNum ?: node.nodeNum,
        liteNodeId = registryNode.nodeId,
        liteDefaultName = defaultName,
        liteLongName = registryNode.longName ?: node.longName,
        liteShortName = registryNode.shortName ?: node.shortName,
        liteLatitude = latitudeI * 1e-7,
        liteLongitude = longitudeI * 1e-7,
    )
}

private fun Node.hasNeighborMapPosition(): Boolean = validPosition != null || validLiteNode