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

import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.database.entity.DiscoveredNodeEntity
import com.geeksville.mesh.database.entity.DiscoveryNeighborType
import com.geeksville.mesh.database.entity.NodeRegistry
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.getNeighborDiscoveryResult
import com.geeksville.mesh.util.latLongToMeter
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.TelemetryProtos

data class DiscoveryDeviceMetricsSample(
    val channelUtilization: Float,
    val airUtilTx: Float,
)

data class DiscoveryCollectionSnapshot(
    val nodes: List<DiscoveredNodeEntity>,
    val packetCount: Int,
    val deviceMetrics: List<DiscoveryDeviceMetricsSample>,
    val localStats: TelemetryProtos.LocalStats?,
)

@Suppress("TooManyFunctions", "ReturnCount")
class DiscoveryPacketCollector(
    private val localNodeNum: Int?,
    private val nodeSnapshot: Map<Int, Node>,
    private val registrySnapshot: Map<String, NodeRegistry>,
    private val originLatitude: Double?,
    private val originLongitude: Double?,
) {
    private val collectedNodes = mutableMapOf<Int, MutableDiscoveredNode>()
    private val deviceMetrics = mutableListOf<DiscoveryDeviceMetricsSample>()
    private var localStats: TelemetryProtos.LocalStats? = null
    private var packetCount = 0

    fun ingest(packet: MeshProtos.MeshPacket) {
        if (!packet.hasDecoded()) return

        val fromNum = packet.from
        val decoded = packet.decoded
        val portNum = decoded.portnumValue
        val isLocalPacket = localNodeNum != null && fromNum == localNodeNum

        if (portNum == Portnums.PortNum.NEIGHBORINFO_APP_VALUE) {
            ingestNeighborInfo(packet)
            return
        }

        if (isLocalPacket) {
            ingestLocalTelemetry(decoded)
            return
        }

        if (fromNum == 0) return

        packetCount += 1
        val node = nodeFor(fromNum)
        node.updatePacketMetadata(packet)

        when (portNum) {
            Portnums.PortNum.NODEINFO_APP_VALUE -> node.ingestUser(decoded.payload.toByteArray())
            Portnums.PortNum.POSITION_APP_VALUE -> node.ingestPosition(decoded.payload.toByteArray())
            Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
            Portnums.PortNum.TEXT_MESSAGE_COMPRESSED_APP_VALUE -> node.messageCount += 1
            Portnums.PortNum.TELEMETRY_APP_VALUE -> ingestRemoteTelemetry(node, decoded)
        }
    }

    fun snapshot(presetResultId: Long = 0): DiscoveryCollectionSnapshot {
        return DiscoveryCollectionSnapshot(
            nodes = collectedNodes.values.map { it.toEntity(presetResultId) },
            packetCount = packetCount,
            deviceMetrics = deviceMetrics.toList(),
            localStats = localStats,
        )
    }

    private fun ingestNeighborInfo(packet: MeshProtos.MeshPacket) {
        val discovery = packet.getNeighborDiscoveryResult { nodeNum ->
            nodeSnapshot[nodeNum]?.user
        } ?: return

        val originNum = discovery.origin.nodeNum
        if (originNum != localNodeNum) {
            nodeFor(originNum).updatePacketMetadata(packet)
        }

        discovery.discovered.forEach { link ->
            val nodeNum = link.node.nodeNum
            if (nodeNum == localNodeNum) return@forEach
            val node = nodeFor(nodeNum)
            node.applyNeighborInfo(link.node, originNum, link.snr)
        }
    }

    private fun ingestLocalTelemetry(decoded: MeshProtos.Data) {
        val telemetry = runCatching {
            TelemetryProtos.Telemetry.parseFrom(decoded.payload)
        }.getOrNull() ?: return

        if (telemetry.hasLocalStats()) localStats = telemetry.localStats
    }

    private fun ingestRemoteTelemetry(
        node: MutableDiscoveredNode,
        decoded: MeshProtos.Data,
    ) {
        val telemetry = runCatching {
            TelemetryProtos.Telemetry.parseFrom(decoded.payload)
        }.getOrNull() ?: return

        when {
            telemetry.hasDeviceMetrics() -> {
                val metrics = telemetry.deviceMetrics
                deviceMetrics += DiscoveryDeviceMetricsSample(
                    channelUtilization = metrics.channelUtilization,
                    airUtilTx = metrics.airUtilTx,
                )
            }
            telemetry.hasEnvironmentMetrics() || telemetry.hasAirQualityMetrics() -> {
                node.sensorPacketCount += 1
            }
        }
    }

    private fun nodeFor(nodeNum: Int): MutableDiscoveredNode {
        return collectedNodes.getOrPut(nodeNum) {
            val known = nodeSnapshot[nodeNum]
            val nodeId = known?.user?.id?.takeIf { it.isNotBlank() }
                ?: DataPacket.nodeNumToDefaultId(nodeNum)
            val registry = registrySnapshot[nodeId]

            MutableDiscoveredNode(
                nodeNum = nodeNum,
                nodeId = nodeId,
                longName = known?.user?.longName?.takeIf { it.isNotBlank() }
                    ?: registry?.longName,
                shortName = known?.user?.shortName?.takeIf { it.isNotBlank() }
                    ?: registry?.shortName,
                defaultName = known?.user?.id?.takeIf { it.isNotBlank() }
                    ?: registry?.defaultName
                    ?: nodeId,
                latitude = known?.effectiveLatitude?.takeIf { it != ZERO_COORDINATE }
                    ?: registry?.latitudeI?.let { it * COORDINATE_SCALE },
                longitude = known?.effectiveLongitude?.takeIf { it != ZERO_COORDINATE }
                    ?: registry?.longitudeI?.let { it * COORDINATE_SCALE },
                hopCount = known?.hopsAway?.takeIf { it >= 0 },
                snr = known?.snr?.takeIf { it != Float.MAX_VALUE && it != 0f },
                rssi = known?.rssi?.takeIf { it != Int.MAX_VALUE && it != 0 },
                isInfrastructure = isInfrastructureRole(known?.user?.roleValue),
            )
        }
    }

    private fun MutableDiscoveredNode.updatePacketMetadata(packet: MeshProtos.MeshPacket) {
        packetCount += 1
        val observedHops = packet.hopsAway()
        when {
            observedHops in DIRECT_HOP_RANGE -> neighborType = DiscoveryNeighborType.DIRECT
            observedHops > MAX_DIRECT_HOPS && neighborType != DiscoveryNeighborType.DIRECT -> {
                neighborType = DiscoveryNeighborType.MESH
            }
        }

        if (packet.rxSnr != 0f) snr = packet.rxSnr
        if (packet.hasRxRssi()) rssi = packet.rxRssi
        observedHops.takeIf { it >= 0 }?.let { hops ->
            hopCount = hopCount?.let { minOf(it, hops) } ?: hops
        }
        lastSeen = if (packet.hasRxTime()) packet.rxTime.toLong() else System.currentTimeMillis()
    }

    private fun MutableDiscoveredNode.ingestUser(payload: ByteArray) {
        val user = runCatching { MeshProtos.User.parseFrom(payload) }.getOrNull() ?: return
        nodeId = user.id.takeIf { it.isNotBlank() } ?: nodeId
        longName = user.longName.takeIf { it.isNotBlank() } ?: longName
        shortName = user.shortName.takeIf { it.isNotBlank() } ?: shortName
        defaultName = user.id.takeIf { it.isNotBlank() } ?: defaultName
        isInfrastructure = isInfrastructure || isInfrastructureRole(user.roleValue)
    }

    private fun MutableDiscoveredNode.ingestPosition(payload: ByteArray) {
        val position = runCatching { MeshProtos.Position.parseFrom(payload) }.getOrNull() ?: return
        val lat = position.latitudeI * COORDINATE_SCALE
        val lon = position.longitudeI * COORDINATE_SCALE
        if (isValidPosition(lat, lon)) {
            latitude = lat
            longitude = lon
        }
    }

    private fun MutableDiscoveredNode.applyNeighborInfo(
        neighbor: com.geeksville.mesh.model.NeighborDiscoveryNode,
        reporterNodeNum: Int,
        linkSnr: Float,
    ) {
        if (reporterNodeNum == localNodeNum) {
            neighborType = DiscoveryNeighborType.DIRECT
        } else if (neighborType != DiscoveryNeighborType.DIRECT) {
            neighborType = DiscoveryNeighborType.MESH
        }
        if (longName.isNullOrBlank()) longName = neighbor.longName
        if (shortName.isNullOrBlank()) shortName = neighbor.shortName
        if (nodeId.isBlank()) nodeId = neighbor.userId
        viaNodeNum = reporterNodeNum.takeIf { it != 0 && it != localNodeNum }
        val currentNeighborSnr = neighborSnr
        if (currentNeighborSnr == null || linkSnr > currentNeighborSnr) neighborSnr = linkSnr
    }

    private fun MutableDiscoveredNode.toEntity(presetResultId: Long): DiscoveredNodeEntity {
        return DiscoveredNodeEntity(
            presetResultId = presetResultId,
            nodeNum = nodeNum.toLong(),
            nodeId = nodeId,
            longName = longName,
            shortName = shortName,
            defaultName = defaultName,
            neighborType = neighborType,
            latitude = latitude,
            longitude = longitude,
            distanceMeters = distanceMeters(),
            hopCount = hopCount,
            snr = snr,
            rssi = rssi,
            messageCount = messageCount,
            sensorPacketCount = sensorPacketCount,
            packetCount = packetCount,
            isInfrastructure = isInfrastructure,
            viaNodeNum = viaNodeNum?.toLong(),
            neighborSnr = neighborSnr,
            lastSeen = lastSeen,
        )
    }

    private fun MutableDiscoveredNode.distanceMeters(): Double? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        val originLat = originLatitude ?: return null
        val originLon = originLongitude ?: return null
        return latLongToMeter(originLat, originLon, lat, lon)
    }

    private fun MeshProtos.MeshPacket.hopsAway(): Int {
        return if (hopStart == 0 || hopLimit > hopStart) {
            -1
        } else {
            hopStart - hopLimit
        }
    }

    private fun isInfrastructureRole(roleValue: Int?): Boolean {
        return roleValue in INFRASTRUCTURE_ROLE_VALUES
    }

    private fun isValidPosition(lat: Double, lon: Double): Boolean {
        val nonZero = lat != ZERO_COORDINATE && lon != ZERO_COORDINATE
        val latitudeValid = lat in MIN_LATITUDE..MAX_LATITUDE
        val longitudeValid = lon in MIN_LONGITUDE..MAX_LONGITUDE
        return nonZero && latitudeValid && longitudeValid
    }

    private companion object {
        private const val ZERO_COORDINATE = 0.0
        private const val COORDINATE_SCALE = 1e-7
        private const val MIN_LATITUDE = -90.0
        private const val MAX_LATITUDE = 90.0
        private const val MIN_LONGITUDE = -180.0
        private const val MAX_LONGITUDE = 180.0
        private const val MIN_DIRECT_HOPS = 0
        private const val MAX_DIRECT_HOPS = 0
        private val DIRECT_HOP_RANGE = MIN_DIRECT_HOPS..MAX_DIRECT_HOPS
        private const val ROUTER_ROLE_VALUE = 2
        private const val ROUTER_LATE_ROLE_VALUE = 11
        private const val CLIENT_BASE_ROLE_VALUE = 12
        private val INFRASTRUCTURE_ROLE_VALUES = setOf(
            ROUTER_ROLE_VALUE,
            ROUTER_LATE_ROLE_VALUE,
            CLIENT_BASE_ROLE_VALUE,
        )
    }
}

private data class MutableDiscoveredNode(
    val nodeNum: Int,
    var nodeId: String,
    var longName: String?,
    var shortName: String?,
    var defaultName: String?,
    var neighborType: String = DiscoveryNeighborType.UNKNOWN,
    var latitude: Double?,
    var longitude: Double?,
    var hopCount: Int?,
    var snr: Float?,
    var rssi: Int?,
    var messageCount: Int = 0,
    var sensorPacketCount: Int = 0,
    var packetCount: Int = 0,
    var isInfrastructure: Boolean = false,
    var viaNodeNum: Int? = null,
    var neighborSnr: Float? = null,
    var lastSeen: Long = System.currentTimeMillis(),
)
