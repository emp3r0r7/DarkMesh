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

package com.geeksville.mesh.discovery

import com.geeksville.mesh.database.entity.DiscoveredNodeEntity
import com.geeksville.mesh.model.Node

internal object DiscoveryMapBuilder {
    fun build(
        nodes: List<DiscoveredNodeEntity>,
        localNode: Node?,
        localNodeNum: Long?,
        presetName: String = "",
        knownNodeByNum: Map<Int, Node> = emptyMap(),
        nodeClass: DiscoveryNodeClass = DiscoveryNodeClass.NEIGHBOR,
    ): DiscoveryMap? {
        if (nodes.isEmpty()) return null

        val classifiedNodes = nodes.filter { it.discoveryNodeClass() == nodeClass }
        val mapNodes = classifiedNodes
            .map { it.toDiscoveryMapNode(knownNodeByNum) }
            .filter { it.hasDiscoveryMapPosition() }
        val positionedLocalNode = localNode?.takeIf { it.hasDiscoveryMapPosition() }
        val mapNodeByNum = (listOfNotNull(positionedLocalNode) + mapNodes)
            .associateBy { it.num.toLong() }
        val localMapNode = localNodeNum
            ?.let(mapNodeByNum::get)
        val links = localMapNode?.takeIf { nodeClass == DiscoveryNodeClass.NEIGHBOR }?.let { origin ->
            classifiedNodes.mapNotNull { discovered ->
                val toNode = mapNodeByNum[discovered.nodeNum]?.takeIf { it.hasMapPosition() }
                    ?: return@mapNotNull null
                DiscoveryMapLink(
                    from = origin,
                    to = toNode,
                    snr = discovered.snr,
                    isDirect = true,
                )
            }
        }.orEmpty()

        return DiscoveryMap(
            nodeClass = nodeClass,
            localNode = localMapNode,
            nodes = when (nodeClass) {
                DiscoveryNodeClass.NEIGHBOR -> (listOfNotNull(localMapNode) + mapNodes).distinctBy { it.num }
                DiscoveryNodeClass.NETWORK,
                DiscoveryNodeClass.UNKNOWN -> mapNodes.distinctBy { it.num }
            },
            links = links.distinctBy {
                "${it.from.num}:${it.to.num}"
            },
            nodeList = DiscoveryNodeListBuilder.build(
                presetName = presetName,
                nodes = nodes,
                localNode = localNode,
                localNodeNum = localNodeNum,
                knownNodeByNum = knownNodeByNum,
                nodeClass = nodeClass,
            ),
        )
    }

    private fun Node.hasMapPosition(): Boolean {
        return validPosition != null || validLiteNode
    }
}
