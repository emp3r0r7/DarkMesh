package com.geeksville.mesh.database.entity

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
    val defaultName: String? = null, //default longname = Meshtastic abcd
    val longName: String? = null,
    val nodeNum: Int? = null,

    val latitudeI: Int? = null,
    val longitudeI: Int? = null,

    val lastSeen: Long = System.currentTimeMillis(),

    val hopCount: Int? = null,
    val lastRssi: Int? = null
)

fun NodeRegistry.isValidForTraceMap() : Boolean {
    return longName != null &&
           defaultName != null &&
           shortName != null &&
           latitudeI != null &&
           longitudeI != null
}