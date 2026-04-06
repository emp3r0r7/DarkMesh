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
        lastSeen = :lastSeen
    WHERE nodeId = :nodeId 
    """
    )
    suspend fun updateNodeInfo(
        nodeId: String,
        nodeNum: Int?,
        longName: String?,
        shortName: String?,
        lastSeen: Long
    ): Int

    @Query("""
    INSERT INTO node_registry (
        nodeId,
        nodeNum,
        longName,
        shortName,
        defaultName,
        lastSeen
    )
    VALUES (
        :nodeId,
        :nodeNum,
        :longName,
        :shortName,
        :defaultName,
        :lastSeen)
    """
    )
    suspend fun insertNodeInfo(
        nodeId: String,
        nodeNum: Int?,
        longName: String?,
        shortName: String?,
        defaultName: String?,
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
