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

package com.geeksville.mesh.model

import com.geeksville.mesh.database.entity.NodeRegistry

internal fun evaluateNeighborDiscoveryMapAvailability(
    discovery: NeighborDiscoveryResult?,
    nodesByNum: Map<Int, Node>,
    nodeRegistryMap: Map<String, NodeRegistry>,
): NeighborDiscoveryMap? {
    val source = discovery
    val origin = source?.origin?.let { resolveNodeForNeighborMap(it, nodesByNum, nodeRegistryMap) }
    val links = if (origin?.hasNeighborMapPosition() == true) {
        source.discovered.mapNotNull { link ->
            link.toNeighborDiscoveryMapLink(origin, nodesByNum, nodeRegistryMap)
        }.distinctBy { it.discovered.num }
    } else {
        emptyList()
    }

    return if (source != null && origin != null && links.isNotEmpty()) {
        NeighborDiscoveryMap(
            origin = origin,
            discovered = links.map { it.discovered },
            links = links,
            source = source.withDistances(nodesByNum, nodeRegistryMap),
        )
    } else {
        null
    }
}

fun NeighborDiscoveryResult.withDistances(
    nodesByNum: Map<Int, Node>,
    nodeRegistryMap: Map<String, NodeRegistry>,
): NeighborDiscoveryResult {
    val originNode = resolveNodeForNeighborMap(origin, nodesByNum, nodeRegistryMap)
    return copy(
        discovered = discovered.map { link ->
            val discoveredNode = resolveNodeForNeighborMap(link.node, nodesByNum, nodeRegistryMap)
            link.copy(distanceMeters = originNode?.distance(discoveredNode ?: return@map link))
        }
    )
}

private fun resolveNodeForNeighborMap(
    node: NeighborDiscoveryNode,
    nodesByNum: Map<Int, Node>,
    nodeRegistryMap: Map<String, NodeRegistry>,
): Node? {
    val knownNode = nodesByNum[node.nodeNum]?.takeIf { it.hasNeighborMapPosition() }
    val registryNode = nodeRegistryMap[node.userId]?.toNeighborMapNode(node)
    return knownNode ?: registryNode
}

private fun NeighborDiscoveryLink.toNeighborDiscoveryMapLink(
    origin: Node,
    nodesByNum: Map<Int, Node>,
    nodeRegistryMap: Map<String, NodeRegistry>,
): NeighborDiscoveryMapLink? {
    val discovered = resolveNodeForNeighborMap(node, nodesByNum, nodeRegistryMap)
    return discovered?.takeIf { it.hasNeighborMapPosition() }?.let {
        NeighborDiscoveryMapLink(
            origin = origin,
            discovered = it,
            snr = snr,
        )
    }
}

private fun NodeRegistry.toNeighborMapNode(node: NeighborDiscoveryNode): Node? {
    val latitude = latitudeI
    val longitude = longitudeI
    val defaultName = defaultName ?: "Meshtastic ${node.userId.takeLast(DEFAULT_ID_SUFFIX_LENGTH)}"

    return if (latitude != null && longitude != null) {
        Node(
            num = nodeNum ?: node.nodeNum,
            liteNodeId = nodeId,
            liteDefaultName = defaultName,
            liteLongName = longName ?: node.longName,
            liteShortName = shortName ?: node.shortName,
            liteLatitude = latitude * COORDINATE_DEGREES,
            liteLongitude = longitude * COORDINATE_DEGREES,
        )
    } else {
        null
    }
}

private fun Node.hasNeighborMapPosition(): Boolean = validPosition != null || validLiteNode

private const val COORDINATE_DEGREES = 1e-7
private const val DEFAULT_ID_SUFFIX_LENGTH = 4
