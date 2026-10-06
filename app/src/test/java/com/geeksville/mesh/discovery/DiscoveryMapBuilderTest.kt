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
import com.geeksville.mesh.util.latLongToMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.meshtastic.proto.MeshProtos

class DiscoveryMapBuilderTest {
    @Test
    fun buildsNodeListWithPositiveDirectEvidenceAndNeighborSnrFallback() {
        val nodeList = DiscoveryNodeListBuilder.build(
            presetName = PRESET_NAME,
            localNode = Node(
                num = LOCAL_NODE,
                liteLongName = LOCAL_NODE_NAME,
            ),
            localNodeNum = LOCAL_NODE.toLong(),
            nodes = listOf(
                discoveredNode(
                    nodeNum = NODE_C,
                    neighborType = DiscoveryNeighborType.DIRECT,
                    snr = null,
                    neighborSnr = MESH_NEIGHBOR_SNR,
                ),
                discoveredNode(
                    nodeNum = NODE_B,
                    neighborType = DiscoveryNeighborType.DIRECT,
                    hopCount = DIRECT_HOPS,
                    snr = DIRECT_SNR,
                ),
                discoveredNode(
                    nodeNum = NODE_F,
                    viaNodeNum = NODE_C,
                    hopCount = THREE_HOPS,
                    snr = FAR_MESH_SNR,
                ),
            ),
        )

        assertEquals(PRESET_NAME, nodeList.presetName)
        assertEquals(LOCAL_NODE_NAME, nodeList.originName)
        assertEquals(listOf(NODE_C, NODE_B), nodeList.nodes.map { it.nodeNum })
        assertEquals(MESH_NEIGHBOR_SNR, nodeList.nodes.first().snr)
    }

    @Test
    fun buildsMapLinksAndListItemsForAllPositiveDirectEvidence() {
        val map = requireNotNull(
            DiscoveryMapBuilder.build(
                localNode = positionedNode(LOCAL_NODE, LOCAL_LATITUDE, LOCAL_LONGITUDE),
                localNodeNum = LOCAL_NODE.toLong(),
                nodes = listOf(
                    discoveredNode(
                        nodeNum = NODE_B,
                        neighborType = DiscoveryNeighborType.DIRECT,
                        latitude = NODE_B_LATITUDE,
                        longitude = NODE_B_LONGITUDE,
                        hopCount = DIRECT_HOPS,
                    ),
                    discoveredNode(
                        nodeNum = NODE_C,
                        neighborType = DiscoveryNeighborType.DIRECT,
                        latitude = NODE_C_LATITUDE,
                        longitude = NODE_C_LONGITUDE,
                        hopCount = ONE_HOP,
                    ),
                    discoveredNode(
                        nodeNum = NODE_F,
                        neighborType = DiscoveryNeighborType.MESH,
                        latitude = NODE_F_LATITUDE,
                        longitude = NODE_F_LONGITUDE,
                        viaNodeNum = NODE_C,
                        hopCount = TWO_HOPS,
                    ),
                    discoveredNode(
                        nodeNum = NODE_G,
                        neighborType = DiscoveryNeighborType.DIRECT,
                        hopCount = DIRECT_HOPS,
                    ),
                ),
            )
        )

        assertEquals(setOf(LOCAL_NODE), map.links.map { it.from.num }.toSet())
        assertEquals(listOf(NODE_B.toInt(), NODE_C.toInt()), map.links.map { it.to.num })
        assertEquals(listOf(NODE_B, NODE_C, NODE_G), map.nodeList.nodes.map { it.nodeNum })
        assertEquals(
            expectedDistanceMeters(LOCAL_LATITUDE, LOCAL_LONGITUDE, NODE_B_LATITUDE, NODE_B_LONGITUDE),
            map.nodeList.nodes.first { it.nodeNum == NODE_B }.distanceMeters,
        )
        assertNull(map.nodeList.nodes.first { it.nodeNum == NODE_G }.distanceMeters)
    }

    @Test
    fun usesKnownNodeDbPositionWhenDirectDiscoveryNodeHasNoStoredPosition() {
        val map = requireNotNull(
            DiscoveryMapBuilder.build(
                localNode = positionedNode(LOCAL_NODE, LOCAL_LATITUDE, LOCAL_LONGITUDE),
                localNodeNum = LOCAL_NODE.toLong(),
                nodes = listOf(
                    discoveredNode(
                        nodeNum = NODE_B,
                        neighborType = DiscoveryNeighborType.DIRECT,
                        hopCount = DIRECT_HOPS,
                    ),
                ),
                knownNodeByNum = mapOf(
                    NODE_B.toInt() to positionedNode(NODE_B.toInt(), NODE_B_LATITUDE, NODE_B_LONGITUDE)
                ),
            )
        )

        val link = map.links.single()
        assertEquals(NODE_B.toInt(), link.to.num)
        assertEquals(
            expectedDistanceMeters(LOCAL_LATITUDE, LOCAL_LONGITUDE, NODE_B_LATITUDE, NODE_B_LONGITUDE),
            map.nodeList.nodes.single().distanceMeters,
        )
    }

    @Test
    fun preservesStoredLocalDiscoveryDistanceInNodeList() {
        val nodeList = DiscoveryNodeListBuilder.build(
            presetName = PRESET_NAME,
            localNode = null,
            localNodeNum = LOCAL_NODE.toLong(),
            nodes = listOf(
                discoveredNode(
                    nodeNum = NODE_B,
                    neighborType = DiscoveryNeighborType.DIRECT,
                    hopCount = DIRECT_HOPS,
                    distanceMeters = STORED_DISTANCE_METERS.toDouble(),
                ),
            ),
        )

        assertEquals(STORED_DISTANCE_METERS, nodeList.nodes.single().distanceMeters)
    }

    private fun positionedNode(
        nodeNum: Int,
        latitude: Double,
        longitude: Double,
    ) = Node(
        num = nodeNum,
        position = MeshProtos.Position.newBuilder()
            .setLatitudeI((latitude * COORDINATE_SCALE).toInt())
            .setLongitudeI((longitude * COORDINATE_SCALE).toInt())
            .build(),
    )

    private fun discoveredNode(
        nodeNum: Long,
        neighborType: String = DiscoveryNeighborType.MESH,
        latitude: Double? = null,
        longitude: Double? = null,
        viaNodeNum: Long? = null,
        hopCount: Int? = null,
        distanceMeters: Double? = null,
        snr: Float? = DEFAULT_SNR,
        neighborSnr: Float? = DEFAULT_SNR,
    ) = DiscoveredNodeEntity(
        presetResultId = PRESET_RESULT_ID,
        nodeNum = nodeNum,
        nodeId = "!$nodeNum",
        longName = "Node $nodeNum",
        shortName = "$nodeNum",
        defaultName = "Node $nodeNum",
        neighborType = neighborType,
        latitude = latitude,
        longitude = longitude,
        distanceMeters = distanceMeters,
        hopCount = hopCount,
        snr = snr,
        viaNodeNum = viaNodeNum,
        neighborSnr = neighborSnr,
    )

    private fun expectedDistanceMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Int {
        return latLongToMeter(fromLatitude, fromLongitude, toLatitude, toLongitude).toInt()
    }

    private companion object {
        const val PRESET_NAME = "TINY_FAST"
        const val LOCAL_NODE_NAME = "Local node"
        const val PRESET_RESULT_ID = 1L
        const val LOCAL_NODE = 1
        const val NODE_B = 2L
        const val NODE_C = 3L
        const val NODE_F = 6L
        const val NODE_G = 7L
        const val DIRECT_HOPS = 0
        const val ONE_HOP = 1
        const val TWO_HOPS = 2
        const val THREE_HOPS = 3
        const val LOCAL_LATITUDE = 44.0
        const val LOCAL_LONGITUDE = 8.0
        const val NODE_B_LATITUDE = 45.0
        const val NODE_B_LONGITUDE = 9.0
        const val NODE_C_LATITUDE = 45.5
        const val NODE_C_LONGITUDE = 9.5
        const val NODE_F_LATITUDE = 46.0
        const val NODE_F_LONGITUDE = 10.0
        const val DEFAULT_SNR = -3.5f
        const val DIRECT_SNR = -12.0f
        const val MESH_NEIGHBOR_SNR = 4.5f
        const val FAR_MESH_SNR = 8.0f
        const val STORED_DISTANCE_METERS = 1234
        const val COORDINATE_SCALE = 1e7
    }
}
