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
import androidx.room.Index
import androidx.room.PrimaryKey
import org.meshtastic.proto.ChannelProtos
import org.meshtastic.proto.ConfigProtos

@Entity(
    tableName = "discovery_session",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["device_address"]),
        Index(value = ["completion_status"]),
    ],
)
data class DiscoverySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "presets_scanned")
    val presetsScanned: String,
    @ColumnInfo(name = "home_preset")
    val homePreset: String?,
    @ColumnInfo(name = "unique_nodes")
    val uniqueNodes: Int = 0,
    @ColumnInfo(name = "direct_neighbors")
    val directNeighbors: Int = 0,
    @ColumnInfo(name = "mesh_neighbors")
    val meshNeighbors: Int = 0,
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0,
    @ColumnInfo(name = "sensor_count")
    val sensorCount: Int = 0,
    @ColumnInfo(name = "infrastructure_count")
    val infrastructureCount: Int = 0,
    @ColumnInfo(name = "completion_status", defaultValue = "'in_progress'")
    val completionStatus: String = DiscoverySessionStatus.IN_PROGRESS,
    @ColumnInfo(name = "failure_message")
    val failureMessage: String? = null,
    @ColumnInfo(name = "total_dwell_seconds")
    val totalDwellSeconds: Long,
    @ColumnInfo(name = "device_address")
    val deviceAddress: String?,
    @ColumnInfo(name = "user_latitude")
    val userLatitude: Double? = null,
    @ColumnInfo(name = "user_longitude")
    val userLongitude: Double? = null,
    @ColumnInfo(name = "home_lora_config", typeAffinity = ColumnInfo.BLOB)
    val homeLoraConfig: ConfigProtos.Config.LoRaConfig?,
    @ColumnInfo(name = "home_primary_channel", typeAffinity = ColumnInfo.BLOB)
    val homePrimaryChannel: ChannelProtos.ChannelSettings? = null,
)
