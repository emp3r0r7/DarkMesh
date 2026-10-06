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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.Portnums

class RouteDiscoveryMapTest {
    @Test
    fun tracerouteMapSkipsUnpositionedEndpointsAndKeepsDrawablePath() {
        val map = evaluateTracerouteMapAvailability(
            traceroute = fullTracerouteText,
            nodesByNum = mapOf(
                ORIGIN_NODE to namedNode(ORIGIN_NODE, "Origin"),
                RELAY_A_NODE to positionedNode(RELAY_A_NODE, "Relay A", RELAY_A_LAT, RELAY_A_LON),
                RELAY_B_NODE to positionedNode(RELAY_B_NODE, "Relay B", RELAY_B_LAT, RELAY_B_LON),
                DESTINATION_NODE to namedNode(DESTINATION_NODE, "Destination"),
            ),
            nodeRegistryMap = emptyMap(),
        )

        assertNotNull(map)
        assertEquals(listOf(RELAY_A_NODE, RELAY_B_NODE), map?.traceForwardList?.map { it.num })
        assertEquals(listOf(RELAY_B_NODE, RELAY_A_NODE), map?.traceBackList?.map { it.num })
    }

    @Test
    fun tracerouteMapAllowsForwardOnlyDrawablePath() {
        val map = evaluateTracerouteMapAvailability(
            traceroute = forwardOnlyTracerouteText,
            nodesByNum = mapOf(
                RELAY_A_NODE to positionedNode(RELAY_A_NODE, "Relay A", RELAY_A_LAT, RELAY_A_LON),
                RELAY_B_NODE to positionedNode(RELAY_B_NODE, "Relay B", RELAY_B_LAT, RELAY_B_LON),
            ),
            nodeRegistryMap = emptyMap(),
        )

        assertEquals(listOf(RELAY_A_NODE, RELAY_B_NODE), map?.traceForwardList?.map { it.num })
        assertEquals(emptyList<Node>(), map?.traceBackList)
    }

    @Test
    fun tracerouteMapUsesRegistryBackupPositions() {
        val map = evaluateTracerouteMapAvailability(
            traceroute = forwardOnlyTracerouteText,
            nodesByNum = emptyMap(),
            nodeRegistryMap = mapOf(
                relayARegistry.nodeId to relayARegistry,
                relayBRegistry.nodeId to relayBRegistry,
            ),
        )

        assertEquals(listOf(RELAY_A_NODE, RELAY_B_NODE), map?.traceForwardList?.map { it.num })
    }

    @Test
    fun tracerouteMapUnavailableWithLessThanTwoDrawablePoints() {
        val map = evaluateTracerouteMapAvailability(
            traceroute = forwardOnlyTracerouteText,
            nodesByNum = mapOf(
                RELAY_A_NODE to positionedNode(RELAY_A_NODE, "Relay A", RELAY_A_LAT, RELAY_A_LON),
                RELAY_B_NODE to namedNode(RELAY_B_NODE, "Relay B"),
            ),
            nodeRegistryMap = emptyMap(),
        )

        assertNull(map)
    }

    @Test
    fun fullRouteDiscoveryDropsLeadingRequesterFromRouteBack() {
        val packet = traceroutePacket(
            from = DESTINATION_NODE,
            to = ORIGIN_NODE,
            route = listOf(RELAY_A_NODE),
            routeBack = listOf(ORIGIN_NODE, RELAY_B_NODE),
            snrTowards = listOf(20, 16),
            snrBack = listOf(19, 15, 11),
        )

        val route = packet.fullRouteDiscovery

        assertEquals(listOf(ORIGIN_NODE, RELAY_A_NODE, DESTINATION_NODE), route?.routeList)
        assertEquals(listOf(DESTINATION_NODE, RELAY_B_NODE, ORIGIN_NODE), route?.routeBackList)
        assertEquals(listOf(15, 11), route?.snrBackList)
    }

    @Test
    fun fullRouteDiscoveryPreservesNormalRouteBackPayload() {
        val packet = traceroutePacket(
            from = DESTINATION_NODE,
            to = ORIGIN_NODE,
            route = listOf(RELAY_A_NODE),
            routeBack = listOf(RELAY_B_NODE, RELAY_A_NODE),
            snrTowards = listOf(20, 16),
            snrBack = listOf(19, 15, 11),
        )

        val route = packet.fullRouteDiscovery

        assertEquals(
            listOf(DESTINATION_NODE, RELAY_B_NODE, RELAY_A_NODE, ORIGIN_NODE),
            route?.routeBackList,
        )
        assertEquals(listOf(19, 15, 11), route?.snrBackList)
    }

    @Test
    fun tracerouteMapUsesNormalizedRouteBackWithoutSyntheticDirectSegment() {
        val packet = traceroutePacket(
            from = DESTINATION_NODE,
            to = ORIGIN_NODE,
            route = listOf(RELAY_A_NODE),
            routeBack = listOf(ORIGIN_NODE, RELAY_B_NODE),
            snrTowards = listOf(20, 16),
            snrBack = listOf(19, 15, 11),
        )
        val traceroute = packet.getTracerouteResponse { nodeNum ->
            when (nodeNum) {
                ORIGIN_NODE -> "Origin"
                DESTINATION_NODE -> "Destination"
                RELAY_A_NODE -> "Relay A"
                RELAY_B_NODE -> "Relay B"
                else -> nodeNum.toString()
            }
        }

        val map = evaluateTracerouteMapAvailability(
            traceroute = traceroute,
            nodesByNum = mapOf(
                ORIGIN_NODE to positionedNode(ORIGIN_NODE, "Origin", ORIGIN_LAT, ORIGIN_LON),
                DESTINATION_NODE to positionedNode(
                    DESTINATION_NODE,
                    "Destination",
                    DESTINATION_LAT,
                    DESTINATION_LON,
                ),
                RELAY_A_NODE to positionedNode(RELAY_A_NODE, "Relay A", RELAY_A_LAT, RELAY_A_LON),
                RELAY_B_NODE to positionedNode(RELAY_B_NODE, "Relay B", RELAY_B_LAT, RELAY_B_LON),
            ),
            nodeRegistryMap = emptyMap(),
        )

        assertEquals(
            listOf(ORIGIN_NODE, RELAY_A_NODE, DESTINATION_NODE),
            map?.traceForwardList?.map { it.num },
        )
        assertEquals(
            listOf(DESTINATION_NODE, RELAY_B_NODE, ORIGIN_NODE),
            map?.traceBackList?.map { it.num },
        )
    }

    @Test
    fun fullRouteDiscoveryKeepsDirectRouteBackSnrWhenRequesterIsOnlyPayloadNode() {
        val packet = traceroutePacket(
            from = DESTINATION_NODE,
            to = ORIGIN_NODE,
            route = emptyList(),
            routeBack = listOf(ORIGIN_NODE),
            snrTowards = listOf(20),
            snrBack = listOf(17),
        )

        val route = packet.fullRouteDiscovery

        assertEquals(listOf(DESTINATION_NODE, ORIGIN_NODE), route?.routeBackList)
        assertEquals(listOf(17), route?.snrBackList)
    }

    private val fullTracerouteText = """
        Route traced toward destination:

        ■ Origin (ORG)
        ⇊ -5.0 dB
        ■ Relay A (RA)
        ⇊ -6.0 dB
        ■ Relay B (RB)
        ⇊ -7.0 dB
        ■ Destination (DST)

        Route traced back to us:

        ■ Destination (DST)
        ⇊ -8.0 dB
        ■ Relay B (RB)
        ⇊ -9.0 dB
        ■ Relay A (RA)
        ⇊ -10.0 dB
        ■ Origin (ORG)
    """.trimIndent()

    private val forwardOnlyTracerouteText = """
        Route traced toward destination:

        ■ Relay A (RA)
        ⇊ -6.0 dB
        ■ Relay B (RB)
    """.trimIndent()

    private val relayARegistry = registryNode(
        nodeNum = RELAY_A_NODE,
        nodeId = "!00000003",
        longName = "Relay A",
        shortName = "RA",
        latitude = RELAY_A_LAT,
        longitude = RELAY_A_LON,
    )

    private val relayBRegistry = registryNode(
        nodeNum = RELAY_B_NODE,
        nodeId = "!00000004",
        longName = "Relay B",
        shortName = "RB",
        latitude = RELAY_B_LAT,
        longitude = RELAY_B_LON,
    )

    private companion object {
        const val ORIGIN_NODE = 1
        const val DESTINATION_NODE = 2
        const val RELAY_A_NODE = 3
        const val RELAY_B_NODE = 4
        const val ORIGIN_LAT = 44.9
        const val ORIGIN_LON = 8.9
        const val DESTINATION_LAT = 45.3
        const val DESTINATION_LON = 9.3
        const val RELAY_A_LAT = 45.0
        const val RELAY_A_LON = 9.0
        const val RELAY_B_LAT = 45.2
        const val RELAY_B_LON = 9.2

        fun namedNode(
            nodeNum: Int,
            longName: String,
        ): Node {
            return Node(
                num = nodeNum,
                user = MeshProtos.User.newBuilder()
                    .setId("!%08x".format(nodeNum))
                    .setLongName(longName)
                    .setShortName(longName.take(4))
                    .build(),
            )
        }

        fun positionedNode(
            nodeNum: Int,
            longName: String,
            latitude: Double,
            longitude: Double,
        ): Node {
            return namedNode(nodeNum, longName).copy(
                position = MeshProtos.Position.newBuilder()
                    .setLatitudeI((latitude * COORDINATE_SCALE).toInt())
                    .setLongitudeI((longitude * COORDINATE_SCALE).toInt())
                    .build(),
            )
        }

        fun registryNode(
            nodeNum: Int,
            nodeId: String,
            longName: String,
            shortName: String,
            latitude: Double,
            longitude: Double,
        ): NodeRegistry {
            return NodeRegistry(
                nodeId = nodeId,
                nodeNum = nodeNum,
                longName = longName,
                shortName = shortName,
                defaultName = longName,
                latitudeI = (latitude * COORDINATE_SCALE).toInt(),
                longitudeI = (longitude * COORDINATE_SCALE).toInt(),
            )
        }

        fun traceroutePacket(
            from: Int,
            to: Int,
            route: List<Int>,
            routeBack: List<Int>,
            snrTowards: List<Int>,
            snrBack: List<Int>,
        ): MeshProtos.MeshPacket {
            val routeDiscovery = MeshProtos.RouteDiscovery.newBuilder()
                .addAllRoute(route)
                .addAllRouteBack(routeBack)
                .addAllSnrTowards(snrTowards)
                .addAllSnrBack(snrBack)
                .build()

            return MeshProtos.MeshPacket.newBuilder()
                .setFrom(from)
                .setTo(to)
                .setHopStart(1)
                .setDecoded(
                    MeshProtos.Data.newBuilder()
                        .setPortnum(Portnums.PortNum.TRACEROUTE_APP)
                        .setPayload(routeDiscovery.toByteString()),
                )
                .build()
        }

        const val COORDINATE_SCALE = 1e7
    }
}
