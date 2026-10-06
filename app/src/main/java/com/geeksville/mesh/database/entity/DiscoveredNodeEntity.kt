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

object DiscoveryNeighborType {
    const val DIRECT = "direct"
    const val MESH = "mesh"
    const val UNKNOWN = "unknown"
}

@Entity(
    tableName = "discovered_node",
    foreignKeys = [
        ForeignKey(
            entity = DiscoveryPresetResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["preset_result_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["preset_result_id"]),
        Index(value = ["preset_result_id", "node_num"], unique = true),
        Index(value = ["via_node_num"]),
    ],
)
data class DiscoveredNodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "preset_result_id")
    val presetResultId: Long,
    @ColumnInfo(name = "node_num")
    val nodeNum: Long,
    @ColumnInfo(name = "node_id")
    val nodeId: String,
    @ColumnInfo(name = "long_name")
    val longName: String? = null,
    @ColumnInfo(name = "short_name")
    val shortName: String? = null,
    @ColumnInfo(name = "default_name")
    val defaultName: String? = null,
    @ColumnInfo(name = "neighbor_type")
    val neighborType: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(name = "distance_meters")
    val distanceMeters: Double? = null,
    @ColumnInfo(name = "hop_count")
    val hopCount: Int? = null,
    val snr: Float? = null,
    val rssi: Int? = null,
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0,
    @ColumnInfo(name = "sensor_packet_count")
    val sensorPacketCount: Int = 0,
    @ColumnInfo(name = "packet_count")
    val packetCount: Int = 0,
    @ColumnInfo(name = "is_infrastructure")
    val isInfrastructure: Boolean = false,
    @ColumnInfo(name = "via_node_num")
    val viaNodeNum: Long? = null,
    @ColumnInfo(name = "neighbor_snr")
    val neighborSnr: Float? = null,
    @ColumnInfo(name = "last_seen")
    val lastSeen: Long = System.currentTimeMillis(),
)
