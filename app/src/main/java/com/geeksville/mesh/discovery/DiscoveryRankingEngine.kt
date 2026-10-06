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

import com.geeksville.mesh.database.entity.DiscoveryPresetResultEntity
import javax.inject.Inject
import kotlin.math.max

data class DiscoveryPresetRank(
    val presetResultId: Long,
    val presetName: String,
    val modemPresetValue: Int,
    val uniqueNodes: Int,
    val directNeighbors: Int,
    val networkNodes: Int,
    val unknownNodes: Int,
    val packetCount: Int,
    val averageSnr: Float?,
    val averageRssi: Float?,
    val averageChannelUtilization: Float?,
    val score: Double,
)

class DiscoveryRankingEngine @Inject constructor() {
    fun rank(results: List<DiscoveryPresetResultEntity>): List<DiscoveryPresetRank> {
        return results.map { it.toRank() }.sortedWith(
            compareByDescending<DiscoveryPresetRank> { it.uniqueNodes }
                .thenByDescending { it.directNeighbors }
                .thenByDescending { it.packetCount }
                .thenByDescending { it.averageSnr ?: Float.NEGATIVE_INFINITY }
                .thenByDescending { it.averageRssi ?: Float.NEGATIVE_INFINITY }
                .thenByDescending { it.averageChannelUtilization ?: Float.NEGATIVE_INFINITY }
                .thenBy { it.presetName }
        )
    }

    fun score(result: DiscoveryPresetResultEntity): Double {
        val rssiScore = result.averageRssi?.let { max(0f, it + RSSI_OFFSET).toDouble() } ?: 0.0
        val snrScore = result.averageSnr?.let { (it + SNR_OFFSET).toDouble() } ?: 0.0
        val channelUtilization = result.averageChannelUtilization?.toDouble() ?: 0.0

        return result.uniqueNodes * UNIQUE_NODE_WEIGHT +
                result.directNeighbors * DIRECT_NEIGHBOR_WEIGHT +
                result.packetCount * PACKET_WEIGHT +
                snrScore * SNR_WEIGHT +
                rssiScore * RSSI_WEIGHT +
                channelUtilization * CHANNEL_UTILIZATION_WEIGHT
    }

    private fun DiscoveryPresetResultEntity.toRank() = DiscoveryPresetRank(
        presetResultId = id,
        presetName = presetName,
        modemPresetValue = modemPresetValue,
        uniqueNodes = uniqueNodes,
        directNeighbors = directNeighbors,
        networkNodes = meshNeighbors,
        unknownNodes = (uniqueNodes - directNeighbors - meshNeighbors).coerceAtLeast(0),
        packetCount = packetCount,
        averageSnr = averageSnr,
        averageRssi = averageRssi,
        averageChannelUtilization = averageChannelUtilization,
        score = rankingScore.takeIf { it != 0.0 } ?: score(this),
    )

    companion object {
        private const val UNIQUE_NODE_WEIGHT = 1_000.0
        private const val DIRECT_NEIGHBOR_WEIGHT = 100.0
        private const val PACKET_WEIGHT = 10.0
        private const val SNR_WEIGHT = 1.0
        private const val RSSI_WEIGHT = 0.1
        private const val CHANNEL_UTILIZATION_WEIGHT = 0.01
        private const val RSSI_OFFSET = 200f
        private const val SNR_OFFSET = 50f
    }
}
