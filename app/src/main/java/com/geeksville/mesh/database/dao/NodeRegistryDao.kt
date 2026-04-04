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
}
