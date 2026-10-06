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

internal object DiscoveryNodeListBuilder {
    fun build(
        presetName: String,
        nodes: List<DiscoveredNodeEntity>,
        localNode: Node?,
        localNodeNum: Long?,
        knownNodeByNum: Map<Int, Node> = emptyMap(),
        nodeClass: DiscoveryNodeClass = DiscoveryNodeClass.NEIGHBOR,
    ): DiscoveryNodeList {
        val originName = localNode?.discoveryListName()
            ?: localNodeNum?.let { "Local node ${formatNodeNum(it)}" }
            ?: "Local Mesh Discovery"

        return DiscoveryNodeList(
            presetName = presetName,
            originName = originName,
            nodeClass = nodeClass,
            nodes = nodes
                .filter { it.discoveryNodeClass() == nodeClass }
                .distinctBy { it.nodeNum }
                .sortedWith(
                    compareByDescending<DiscoveredNodeEntity> {
                        it.snr ?: it.neighborSnr ?: Float.NEGATIVE_INFINITY
                    }
                        .thenBy { it.discoveryListName() }
                )
                .map {
                    val discoveredNode = it.toDiscoveryMapNode(knownNodeByNum)
                    DiscoveryNodeListItem(
                        nodeNum = it.nodeNum,
                        longName = it.discoveryListName(),
                        snr = it.snr ?: it.neighborSnr,
                        distanceMeters = it.distanceMeters?.toInt()
                            ?: localNode?.distance(discoveredNode),
                    )
                },
        )
    }

    private fun DiscoveredNodeEntity.discoveryListName(): String {
        return longName?.takeIf { it.isNotBlank() }
            ?: defaultName?.takeIf { it.isNotBlank() }
            ?: nodeId.takeIf { it.isNotBlank() }
            ?: formatNodeNum(nodeNum)
    }

    private fun Node.discoveryListName(): String? {
        return user.longName.takeIf { it.isNotBlank() }
            ?: liteLongName?.takeIf { it.isNotBlank() }
            ?: liteDefaultName?.takeIf { it.isNotBlank() }
            ?: liteNodeId?.takeIf { it.isNotBlank() }
    }

    private fun formatNodeNum(nodeNum: Long): String {
        return "!%08x".format(nodeNum)
    }
}
