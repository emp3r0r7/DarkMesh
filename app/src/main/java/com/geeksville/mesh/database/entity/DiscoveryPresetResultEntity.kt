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

package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "discovery_preset_result",
    foreignKeys = [
        ForeignKey(
            entity = DiscoverySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["session_id", "preset_name"], unique = true),
    ],
)
data class DiscoveryPresetResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "preset_name")
    val presetName: String,
    @ColumnInfo(name = "modem_preset_value")
    val modemPresetValue: Int,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "ended_at")
    val endedAt: Long,
    @ColumnInfo(name = "dwell_seconds")
    val dwellSeconds: Long,
    @ColumnInfo(name = "unique_nodes")
    val uniqueNodes: Int,
    @ColumnInfo(name = "direct_neighbors")
    val directNeighbors: Int,
    @ColumnInfo(name = "mesh_neighbors")
    val meshNeighbors: Int,
    @ColumnInfo(name = "message_count")
    val messageCount: Int,
    @ColumnInfo(name = "sensor_count")
    val sensorCount: Int,
    @ColumnInfo(name = "infrastructure_count")
    val infrastructureCount: Int,
    @ColumnInfo(name = "packet_count")
    val packetCount: Int,
    @ColumnInfo(name = "average_snr")
    val averageSnr: Float? = null,
    @ColumnInfo(name = "average_rssi")
    val averageRssi: Float? = null,
    @ColumnInfo(name = "average_channel_utilization")
    val averageChannelUtilization: Float? = null,
    @ColumnInfo(name = "average_air_util_tx")
    val averageAirUtilTx: Float? = null,
    @ColumnInfo(name = "local_num_packets_tx")
    val localNumPacketsTx: Int? = null,
    @ColumnInfo(name = "local_num_packets_rx")
    val localNumPacketsRx: Int? = null,
    @ColumnInfo(name = "local_num_packets_rx_bad")
    val localNumPacketsRxBad: Int? = null,
    @ColumnInfo(name = "local_num_online_nodes")
    val localNumOnlineNodes: Int? = null,
    @ColumnInfo(name = "ranking_score")
    val rankingScore: Double = 0.0,
)
