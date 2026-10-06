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

package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.geeksville.mesh.database.entity.NodeRegistry
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeRegistryDao {

    @Query("SELECT * FROM node_registry ORDER BY lastSeen DESC")
    fun getAll(): Flow<List<NodeRegistry>>

    @Query("SELECT * FROM node_registry WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getById(nodeId: String): NodeRegistry?

    @Upsert
    suspend fun upsert(node: NodeRegistry)

    @Query("DELETE FROM node_registry WHERE nodeId = :nodeId")
    suspend fun deleteById(nodeId: String)

    @Query("DELETE FROM node_registry")
    suspend fun deleteAll()

    @Query("""
        UPDATE node_registry
        SET lastSeen = :timestamp
        WHERE nodeId = :nodeId
    """)
    suspend fun updateLastSeen(nodeId: String, timestamp: Long)

    @Query(
        """
        UPDATE node_registry
        SET latitudeI = :latitude, longitudeI = :longitude
        WHERE nodeId = :nodeId
    """
    )
    suspend fun updatePosition(nodeId: String, latitude: Int, longitude: Int)

    @Query("""
    UPDATE node_registry
    SET
        nodeNum = :nodeNum,
        longName = :longName,
        shortName = :shortName,
        publicKey = CASE
            WHEN :publicKey IS NULL OR length(:publicKey) = 0 THEN publicKey
            ELSE :publicKey
        END,
        lastSeen = :lastSeen
    WHERE nodeId = :nodeId 
    """
    )
    suspend fun updateNodeInfo(
        nodeId: String,
        nodeNum: Int?,
        longName: String?,
        shortName: String?,
        publicKey: ByteArray?,
        lastSeen: Long
    ): Int

    @Query("""
    INSERT INTO node_registry (
        nodeId,
        nodeNum,
        longName,
        shortName,
        defaultName,
        publicKey,
        lastSeen
    )
    VALUES (
        :nodeId,
        :nodeNum,
        :longName,
        :shortName,
        :defaultName,
        :publicKey,
        :lastSeen)
    """
    )
    suspend fun insertNodeInfo(
        nodeId: String,
        nodeNum: Int?,
        longName: String?,
        shortName: String?,
        defaultName: String?,
        publicKey: ByteArray?,
        lastSeen: Long,
    )

    @Query("""
    UPDATE node_registry
    SET
        latitudeI = :latitudeI,
        longitudeI = :longitudeI,
        lastSeen = :lastSeen
    WHERE nodeId = :nodeId
    """
    )
    suspend fun updatePosition(
        nodeId: String,
        latitudeI: Int?,
        longitudeI: Int?,
        lastSeen: Long,
    ): Int

    @Query("""
    INSERT INTO node_registry (nodeId, longName, defaultName, shortName, latitudeI, longitudeI, lastSeen)
    VALUES (
        :nodeId,
        :longName,
        :defaultName,
        :shortName,
        :latitudeI,
        :longitudeI,
        :lastSeen)
    """
    )
    suspend fun insertNodeRegistryPosition(
        nodeId: String,
        longName: String,
        defaultName: String,
        shortName: String,
        latitudeI: Int?,
        longitudeI: Int?,
        lastSeen: Long,
    )

    @Query("""
        SELECT * FROM node_registry
        WHERE longName like :longName ORDER BY lastSeen DESC
    """)
    suspend fun searchLongName(longName: String?): List<NodeRegistry>
}
