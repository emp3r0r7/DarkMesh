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
import com.geeksville.mesh.util.latLongToMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.meshtastic.proto.MeshProtos

class NeighborDiscoveryMapTest {
    @Test
    fun enrichesNeighborDiscoveryDistanceFromNodeDb() {
        val enriched = neighborDiscovery.withDistances(
            nodesByNum = mapOf(
                ORIGIN_NODE to positionedNode(ORIGIN_NODE, ORIGIN_LAT, ORIGIN_LON),
                DISCOVERED_NODE to positionedNode(DISCOVERED_NODE, DISCOVERED_LAT, DISCOVERED_LON),
            ),
            nodeRegistryMap = emptyMap(),
        )

        assertEquals(expectedDistanceMeters(), enriched.discovered.single().distanceMeters)
    }

    @Test
    fun enrichesNeighborDiscoveryDistanceFromRegistryFallback() {
        val enriched = neighborDiscovery.withDistances(
            nodesByNum = mapOf(
                ORIGIN_NODE to positionedNode(ORIGIN_NODE, ORIGIN_LAT, ORIGIN_LON),
            ),
            nodeRegistryMap = mapOf(discoveredRegistry.nodeId to discoveredRegistry),
        )

        assertEquals(expectedDistanceMeters(), enriched.discovered.single().distanceMeters)
    }

    @Test
    fun neighborDiscoveryMapSourceCarriesListDistances() {
        val map = evaluateNeighborDiscoveryMapAvailability(
            discovery = neighborDiscovery,
            nodesByNum = mapOf(
                ORIGIN_NODE to positionedNode(ORIGIN_NODE, ORIGIN_LAT, ORIGIN_LON),
                DISCOVERED_NODE to positionedNode(DISCOVERED_NODE, DISCOVERED_LAT, DISCOVERED_LON),
            ),
            nodeRegistryMap = emptyMap(),
        )

        assertNotNull(map)
        assertEquals(expectedDistanceMeters(), map?.source?.discovered?.single()?.distanceMeters)
    }

    @Test
    fun neighborDiscoveryMapUnavailableWhenDiscoveredNodeHasNoPosition() {
        val map = evaluateNeighborDiscoveryMapAvailability(
            discovery = neighborDiscovery,
            nodesByNum = mapOf(
                ORIGIN_NODE to positionedNode(ORIGIN_NODE, ORIGIN_LAT, ORIGIN_LON),
                DISCOVERED_NODE to namedNode(DISCOVERED_NODE),
            ),
            nodeRegistryMap = emptyMap(),
        )

        assertNull(map)
    }

    private companion object {
        const val ORIGIN_NODE = 1
        const val DISCOVERED_NODE = 2
        const val ORIGIN_LAT = 45.0
        const val ORIGIN_LON = 9.0
        const val DISCOVERED_LAT = 45.1
        const val DISCOVERED_LON = 9.1
        const val COORDINATE_SCALE = 1e7

        val neighborDiscovery = NeighborDiscoveryResult(
            origin = discoveryNode(ORIGIN_NODE),
            discovered = listOf(
                NeighborDiscoveryLink(
                    node = discoveryNode(DISCOVERED_NODE),
                    snr = -4.5f,
                )
            ),
        )

        val discoveredRegistry = NodeRegistry(
            nodeId = "!%08x".format(DISCOVERED_NODE),
            nodeNum = DISCOVERED_NODE,
            longName = "Node $DISCOVERED_NODE",
            shortName = "$DISCOVERED_NODE",
            defaultName = "Node $DISCOVERED_NODE",
            latitudeI = (DISCOVERED_LAT * COORDINATE_SCALE).toInt(),
            longitudeI = (DISCOVERED_LON * COORDINATE_SCALE).toInt(),
        )

        fun discoveryNode(nodeNum: Int): NeighborDiscoveryNode {
            return NeighborDiscoveryNode(
                nodeNum = nodeNum,
                userId = "!%08x".format(nodeNum),
                longName = "Node $nodeNum",
                shortName = "$nodeNum",
            )
        }

        fun namedNode(nodeNum: Int): Node {
            return Node(
                num = nodeNum,
                user = MeshProtos.User.newBuilder()
                    .setId("!%08x".format(nodeNum))
                    .setLongName("Node $nodeNum")
                    .setShortName("$nodeNum")
                    .build(),
            )
        }

        fun positionedNode(
            nodeNum: Int,
            latitude: Double,
            longitude: Double,
        ): Node {
            return namedNode(nodeNum).copy(
                position = MeshProtos.Position.newBuilder()
                    .setLatitudeI((latitude * COORDINATE_SCALE).toInt())
                    .setLongitudeI((longitude * COORDINATE_SCALE).toInt())
                    .build(),
            )
        }

        fun expectedDistanceMeters(): Int {
            return latLongToMeter(ORIGIN_LAT, ORIGIN_LON, DISCOVERED_LAT, DISCOVERED_LON).toInt()
        }
    }
}
