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

import com.geeksville.mesh.database.entity.DiscoveryNeighborType
import com.google.protobuf.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.Portnums

class DiscoveryPacketCollectorTest {
    @Test
    fun deduplicatesNodesAndAccumulatesPacketCounts() {
        val collector = DiscoveryPacketCollector(
            localNodeNum = LOCAL_NODE,
            nodeSnapshot = emptyMap(),
            registrySnapshot = emptyMap(),
            originLatitude = null,
            originLongitude = null,
        )

        collector.ingest(textPacket(from = NODE_TWO))
        collector.ingest(textPacket(from = NODE_TWO))

        val snapshot = collector.snapshot()
        val node = snapshot.nodes.single()
        assertEquals(1, snapshot.nodes.size)
        assertEquals(2, snapshot.packetCount)
        assertEquals(2, node.packetCount)
        assertEquals(2, node.messageCount)
        assertEquals(DiscoveryNeighborType.DIRECT, node.neighborType)
    }

    @Test
    fun parsesNodeInfoAndPositionWithoutPromotingAbsentRssi() {
        val collector = DiscoveryPacketCollector(
            localNodeNum = LOCAL_NODE,
            nodeSnapshot = emptyMap(),
            registrySnapshot = emptyMap(),
            originLatitude = null,
            originLongitude = null,
        )

        collector.ingest(
            packet(
                from = NODE_TWO,
                portNum = Portnums.PortNum.NODEINFO_APP_VALUE,
                payload = MeshProtos.User.newBuilder()
                    .setId("!00000002")
                    .setLongName("Alpha")
                    .setShortName("A")
                    .build()
                    .toByteString(),
                rxRssi = null,
            )
        )
        collector.ingest(
            packet(
                from = NODE_TWO,
                portNum = Portnums.PortNum.POSITION_APP_VALUE,
                payload = MeshProtos.Position.newBuilder()
                    .setLatitudeI(450_000_000)
                    .setLongitudeI(90_000_000)
                    .build()
                    .toByteString(),
                rxRssi = null,
            )
        )

        val node = collector.snapshot().nodes.single()
        assertEquals("Alpha", node.longName)
        assertEquals("A", node.shortName)
        assertEquals(45.0, node.latitude!!, 0.0)
        assertEquals(9.0, node.longitude!!, 0.0)
        assertNull(node.rssi)
    }

    @Test
    fun preservesExplicitZeroRssiAndRxTime() {
        val collector = DiscoveryPacketCollector(
            localNodeNum = LOCAL_NODE,
            nodeSnapshot = emptyMap(),
            registrySnapshot = emptyMap(),
            originLatitude = null,
            originLongitude = null,
        )

        collector.ingest(textPacket(from = NODE_TWO, rxRssi = 0, rxTime = 0))

        val node = collector.snapshot().nodes.single()
        assertEquals(0, node.rssi)
        assertEquals(0L, node.lastSeen)
    }

    @Test
    fun unknownHopEvidenceRemainsUnknown() {
        val collector = DiscoveryPacketCollector(
            localNodeNum = LOCAL_NODE,
            nodeSnapshot = emptyMap(),
            registrySnapshot = emptyMap(),
            originLatitude = null,
            originLongitude = null,
        )

        collector.ingest(
            packet(
                from = NODE_TWO,
                portNum = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                payload = ByteString.copyFromUtf8("unknown route"),
                hopStart = 0,
                hopLimit = 3,
            )
        )

        val node = collector.snapshot().nodes.single()
        assertEquals(DiscoveryNeighborType.UNKNOWN, node.neighborType)
        assertNull(node.hopCount)
    }

    @Test
    fun directEvidenceIsNotDowngradedByLaterRelayedPacket() {
        val collector = DiscoveryPacketCollector(
            localNodeNum = LOCAL_NODE,
            nodeSnapshot = emptyMap(),
            registrySnapshot = emptyMap(),
            originLatitude = null,
            originLongitude = null,
        )

        collector.ingest(textPacket(from = NODE_TWO))
        collector.ingest(
            packet(
                from = NODE_TWO,
                portNum = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                payload = ByteString.copyFromUtf8("relayed copy"),
                hopStart = 3,
                hopLimit = 1,
            )
        )

        val node = collector.snapshot().nodes.single()
        assertEquals(DiscoveryNeighborType.DIRECT, node.neighborType)
        assertEquals(0, node.hopCount)
    }

    @Test
    fun neighborInfoCreatesMeshNodeWithoutOverridingDirectNode() {
        val collector = DiscoveryPacketCollector(
            localNodeNum = LOCAL_NODE,
            nodeSnapshot = emptyMap(),
            registrySnapshot = emptyMap(),
            originLatitude = null,
            originLongitude = null,
        )

        collector.ingest(textPacket(from = NODE_TWO))
        collector.ingest(
            packet(
                from = NODE_THREE,
                portNum = Portnums.PortNum.NEIGHBORINFO_APP_VALUE,
                payload = MeshProtos.NeighborInfo.newBuilder()
                    .setNodeId(NODE_THREE)
                    .addNeighbors(
                        MeshProtos.Neighbor.newBuilder()
                            .setNodeId(NODE_TWO)
                            .setSnr(-2.5f)
                    )
                    .build()
                    .toByteString(),
            )
        )

        val nodes = collector.snapshot().nodes.associateBy { it.nodeNum.toInt() }
        assertEquals(DiscoveryNeighborType.DIRECT, nodes.getValue(NODE_TWO).neighborType)
        assertEquals(DiscoveryNeighborType.DIRECT, nodes.getValue(NODE_THREE).neighborType)
        assertEquals(NODE_THREE.toLong(), nodes.getValue(NODE_TWO).viaNodeNum)
    }

    @Test
    fun localNeighborInfoMarksDiscoveredNodeDirect() {
        val collector = DiscoveryPacketCollector(
            localNodeNum = LOCAL_NODE,
            nodeSnapshot = emptyMap(),
            registrySnapshot = emptyMap(),
            originLatitude = null,
            originLongitude = null,
        )

        collector.ingest(
            packet(
                from = LOCAL_NODE,
                portNum = Portnums.PortNum.NEIGHBORINFO_APP_VALUE,
                payload = MeshProtos.NeighborInfo.newBuilder()
                    .setNodeId(LOCAL_NODE)
                    .addNeighbors(
                        MeshProtos.Neighbor.newBuilder()
                            .setNodeId(NODE_TWO)
                            .setSnr(-1.5f)
                    )
                    .build()
                    .toByteString(),
            )
        )

        val node = collector.snapshot().nodes.single()
        assertEquals(DiscoveryNeighborType.DIRECT, node.neighborType)
        assertNull(node.viaNodeNum)
        assertEquals(-1.5f, node.neighborSnr)
    }

    @Test
    fun localDirectEvidenceFollowedByRelayedObservationsRemainsNeighbor() {
        listOf(1, 2).forEach { observedHops ->
            val collector = DiscoveryPacketCollector(
                localNodeNum = LOCAL_NODE,
                nodeSnapshot = emptyMap(),
                registrySnapshot = emptyMap(),
                originLatitude = null,
                originLongitude = null,
            )

            collector.ingest(localNeighborInfoPacket(NODE_TWO))
            collector.ingest(
                packet(
                    from = NODE_TWO,
                    portNum = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                    payload = ByteString.copyFromUtf8("relayed observation"),
                    hopStart = 3,
                    hopLimit = 3 - observedHops,
                )
            )

            val node = collector.snapshot().nodes.single()
            assertEquals(DiscoveryNeighborType.DIRECT, node.neighborType)
            assertEquals(observedHops, node.hopCount)
            assertEquals(DiscoveryNodeClass.NEIGHBOR, node.discoveryNodeClass())
        }
    }

    @Test
    fun remoteNeighborInfoLinkIsNotAHomeNeighbor() {
        val collector = DiscoveryPacketCollector(
            localNodeNum = LOCAL_NODE,
            nodeSnapshot = emptyMap(),
            registrySnapshot = emptyMap(),
            originLatitude = null,
            originLongitude = null,
        )

        collector.ingest(
            packet(
                from = NODE_THREE,
                portNum = Portnums.PortNum.NEIGHBORINFO_APP_VALUE,
                payload = MeshProtos.NeighborInfo.newBuilder()
                    .setNodeId(NODE_THREE)
                    .addNeighbors(
                        MeshProtos.Neighbor.newBuilder()
                            .setNodeId(NODE_TWO)
                            .setSnr(-2.5f)
                    )
                    .build()
                    .toByteString(),
            )
        )

        val remoteLink = collector.snapshot().nodes.single { it.nodeNum == NODE_TWO.toLong() }
        assertEquals(DiscoveryNeighborType.MESH, remoteLink.neighborType)
        assertEquals(NODE_THREE.toLong(), remoteLink.viaNodeNum)
        assertEquals(DiscoveryNodeClass.NETWORK, remoteLink.discoveryNodeClass())
    }

    @Test
    fun localStatsFromLocalTelemetryDoNotCreateDiscoveredNode() {
        val collector = DiscoveryPacketCollector(
            localNodeNum = LOCAL_NODE,
            nodeSnapshot = emptyMap(),
            registrySnapshot = emptyMap(),
            originLatitude = null,
            originLongitude = null,
        )

        collector.ingest(
            packet(
                from = LOCAL_NODE,
                portNum = Portnums.PortNum.TELEMETRY_APP_VALUE,
                payload = org.meshtastic.proto.TelemetryProtos.Telemetry.newBuilder()
                    .setLocalStats(
                        org.meshtastic.proto.TelemetryProtos.LocalStats.newBuilder()
                            .setNumPacketsRx(7)
                    )
                    .build()
                    .toByteString(),
            )
        )

        val snapshot = collector.snapshot()
        assertEquals(0, snapshot.nodes.size)
        assertEquals(7, snapshot.localStats?.numPacketsRx)
    }

    private fun textPacket(
        from: Int,
        rxRssi: Int? = -100,
        rxTime: Int? = 123,
    ) = packet(
        from = from,
        portNum = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
        payload = ByteString.copyFromUtf8("hello"),
        rxRssi = rxRssi,
        rxTime = rxTime,
    )

    private fun localNeighborInfoPacket(nodeNum: Int) = packet(
        from = LOCAL_NODE,
        portNum = Portnums.PortNum.NEIGHBORINFO_APP_VALUE,
        payload = MeshProtos.NeighborInfo.newBuilder()
            .setNodeId(LOCAL_NODE)
            .addNeighbors(
                MeshProtos.Neighbor.newBuilder()
                    .setNodeId(nodeNum)
                    .setSnr(-1.5f)
            )
            .build()
            .toByteString(),
    )

    private fun packet(
        from: Int,
        portNum: Int,
        payload: ByteString,
        rxRssi: Int? = -100,
        rxTime: Int? = 123,
        hopStart: Int = 3,
        hopLimit: Int = 3,
    ): MeshProtos.MeshPacket {
        val builder = MeshProtos.MeshPacket.newBuilder()
            .setFrom(from)
            .setTo(LOCAL_NODE)
            .setHopStart(hopStart)
            .setHopLimit(hopLimit)
            .setRxSnr(-4.5f)
            .setDecoded(
                MeshProtos.Data.newBuilder()
                    .setPortnumValue(portNum)
                    .setPayload(payload)
            )

        rxRssi?.let(builder::setRxRssi)
        rxTime?.let(builder::setRxTime)

        return builder.build()
    }

    private companion object {
        const val LOCAL_NODE = 1
        const val NODE_TWO = 2
        const val NODE_THREE = 3
    }
}
