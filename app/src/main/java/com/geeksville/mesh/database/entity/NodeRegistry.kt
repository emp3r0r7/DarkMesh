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

@Entity(
    tableName = "node_registry",
    indices = [
        Index(value = ["nodeId"]),
        Index(value = ["lastSeen"])
    ]
)
data class NodeRegistry(
    @PrimaryKey val nodeId: String,

    val shortName: String? = null,
    val defaultName: String? = null, // default longname = Meshtastic abcd
    val longName: String? = null,
    val nodeNum: Int? = null,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val publicKey: ByteArray? = null,

    val latitudeI: Int? = null,
    val longitudeI: Int? = null,

    val lastSeen: Long = System.currentTimeMillis(),

    val hopCount: Int? = null,
    val lastRssi: Int? = null
)

fun NodeRegistry.isValidForTraceMap(): Boolean {
    return longName != null &&
           defaultName != null &&
           shortName != null &&
           latitudeI != null &&
           longitudeI != null
}
