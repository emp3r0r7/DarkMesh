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
    val longName: String? = null,
    val nodeNum: Int? = null,

    val latitudeI: Int? = null,
    val longitudeI: Int? = null,

    val lastSeen: Long = System.currentTimeMillis(),

    val hopCount: Int? = null,
    val lastRssi: Int? = null
)
