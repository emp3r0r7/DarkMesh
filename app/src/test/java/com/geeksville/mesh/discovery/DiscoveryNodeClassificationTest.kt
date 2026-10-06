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
import com.geeksville.mesh.database.entity.DiscoveryNeighborType
import com.geeksville.mesh.model.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryNodeClassificationTest {
    @Test
    fun localNeighborInfoEvidenceWinsOverObservedHopCounts() {
        val hardwareNodes = listOf(
            node(0x849a8ce0, DiscoveryNeighborType.DIRECT, hopCount = 1),
            node(0xdb58b1cc, DiscoveryNeighborType.DIRECT, hopCount = 2),
            node(0xf9b35b48, DiscoveryNeighborType.DIRECT, hopCount = 1),
        )

        assertTrue(hardwareNodes.all { it.discoveryNodeClass() == DiscoveryNodeClass.NEIGHBOR })
    }

    @Test
    fun nonDirectPositiveHopEvidenceIsNetwork() {
        assertEquals(
            DiscoveryNodeClass.NETWORK,
            node(1, DiscoveryNeighborType.MESH, hopCount = 1).discoveryNodeClass(),
        )
        assertEquals(
            DiscoveryNodeClass.NETWORK,
            node(2, DiscoveryNeighborType.MESH, hopCount = 2).discoveryNodeClass(),
        )
    }

    @Test
    fun retainedHardwareDatasetPartitionsEightNeighborsAndFifteenNetworkNodes() {
        val direct = (1L..8L).map { index ->
            val observedHops = when (index) {
                1L, 3L -> 1
                2L -> 2
                else -> 0
            }
            node(index, DiscoveryNeighborType.DIRECT, hopCount = observedHops)
        }
        val network = (9L..23L).map { index ->
            node(index, DiscoveryNeighborType.MESH, hopCount = if (index % 2L == 0L) 2 else 1)
        }
        val retainedDataset = direct + network

        assertEquals(23, retainedDataset.size)
        assertEquals(8, retainedDataset.countDiscoveryNodes(DiscoveryNodeClass.NEIGHBOR))
        assertEquals(15, retainedDataset.countDiscoveryNodes(DiscoveryNodeClass.NETWORK))
        assertEquals(0, retainedDataset.countDiscoveryNodes(DiscoveryNodeClass.UNKNOWN))
    }

    @Test
    fun partitionsNeighborNetworkAndUnknownWithoutOverlap() {
        val nodes = listOf(
            node(1, DiscoveryNeighborType.DIRECT, hopCount = 0),
            node(2, DiscoveryNeighborType.DIRECT, hopCount = null),
            node(3, DiscoveryNeighborType.MESH, hopCount = 2),
            node(4, DiscoveryNeighborType.MESH, hopCount = null, viaNodeNum = 9),
            node(5, DiscoveryNeighborType.MESH, hopCount = null),
            node(6, DiscoveryNeighborType.UNKNOWN, hopCount = null),
        )

        assertEquals(2, nodes.countDiscoveryNodes(DiscoveryNodeClass.NEIGHBOR))
        assertEquals(2, nodes.countDiscoveryNodes(DiscoveryNodeClass.NETWORK))
        assertEquals(2, nodes.countDiscoveryNodes(DiscoveryNodeClass.UNKNOWN))
        assertEquals(
            nodes.size,
            DiscoveryNodeClass.entries.sumOf(nodes::countDiscoveryNodes),
        )
    }

    @Test
    fun networkMapHasPositionedMarkersAndNoFabricatedLinks() {
        val map = requireNotNull(
            DiscoveryMapBuilder.build(
                nodes = listOf(
                    node(
                        nodeNum = 3,
                        neighborType = DiscoveryNeighborType.MESH,
                        hopCount = 2,
                        latitude = 45.0,
                        longitude = 9.0,
                    )
                ),
                localNode = positionedNode(1, 44.0, 8.0),
                localNodeNum = 1,
                nodeClass = DiscoveryNodeClass.NETWORK,
            )
        )

        assertEquals(listOf(3), map.nodes.map { it.num })
        assertTrue(map.links.isEmpty())
        assertEquals(DiscoveryNodeClass.NETWORK, map.nodeList.nodeClass)
    }

    @Test
    fun neighborMapAllowsMarkerWithoutInventingLinkWhenHomePositionIsUnavailable() {
        val map = requireNotNull(
            DiscoveryMapBuilder.build(
                nodes = listOf(
                    node(
                        nodeNum = 2,
                        neighborType = DiscoveryNeighborType.DIRECT,
                        hopCount = 0,
                        latitude = 45.0,
                        longitude = 9.0,
                    )
                ),
                localNode = Node(num = 1),
                localNodeNum = 1,
                nodeClass = DiscoveryNodeClass.NEIGHBOR,
            )
        )

        assertEquals(listOf(2), map.nodes.map { it.num })
        assertTrue(map.links.isEmpty())
    }

    private fun node(
        nodeNum: Long,
        neighborType: String,
        hopCount: Int?,
        viaNodeNum: Long? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ) = DiscoveredNodeEntity(
        presetResultId = 1,
        nodeNum = nodeNum,
        nodeId = "!$nodeNum",
        neighborType = neighborType,
        hopCount = hopCount,
        viaNodeNum = viaNodeNum,
        latitude = latitude,
        longitude = longitude,
    )

    private fun positionedNode(
        nodeNum: Int,
        latitude: Double,
        longitude: Double,
    ) = Node(
        num = nodeNum,
        liteLatitude = latitude,
        liteLongitude = longitude,
    )
}
