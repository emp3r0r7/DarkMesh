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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.geeksville.mesh.database.entity.DiscoveredNodeEntity
import com.geeksville.mesh.database.entity.DiscoveryPresetResultEntity
import com.geeksville.mesh.database.entity.DiscoverySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface DiscoveryDao {
    @Insert
    suspend fun insertSession(session: DiscoverySessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresetResult(result: DiscoveryPresetResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscoveredNodes(nodes: List<DiscoveredNodeEntity>)

    @Transaction
    suspend fun insertPresetResultWithNodes(
        result: DiscoveryPresetResultEntity,
        nodes: List<DiscoveredNodeEntity>,
    ): Long {
        val presetResultId = insertPresetResult(result)
        insertDiscoveredNodes(nodes.map { it.copy(presetResultId = presetResultId) })
        return presetResultId
    }

    @Query("SELECT * FROM discovery_session ORDER BY timestamp DESC")
    fun getSessions(): Flow<List<DiscoverySessionEntity>>

    @Query("SELECT * FROM discovery_session WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: Long): DiscoverySessionEntity?

    @Query(
        """
        SELECT * FROM discovery_preset_result
        WHERE session_id = :sessionId
        ORDER BY ranking_score DESC, preset_name ASC
        """
    )
    suspend fun getPresetResults(sessionId: Long): List<DiscoveryPresetResultEntity>

    @Query("SELECT * FROM discovery_preset_result WHERE id = :presetResultId LIMIT 1")
    suspend fun getPresetResult(presetResultId: Long): DiscoveryPresetResultEntity?

    @Query(
        """
        SELECT * FROM discovered_node
        WHERE preset_result_id = :presetResultId
        ORDER BY neighbor_type ASC, node_num ASC
        """
    )
    suspend fun getDiscoveredNodes(presetResultId: Long): List<DiscoveredNodeEntity>

    @Query(
        """
        SELECT discovered_node.* FROM discovered_node
        INNER JOIN discovery_preset_result
            ON discovered_node.preset_result_id = discovery_preset_result.id
        WHERE discovery_preset_result.session_id = :sessionId
        """
    )
    suspend fun getDiscoveredNodesForSession(sessionId: Long): List<DiscoveredNodeEntity>

    @Query(
        """
        UPDATE discovery_session
        SET unique_nodes = :uniqueNodes,
            direct_neighbors = :directNeighbors,
            mesh_neighbors = :meshNeighbors,
            message_count = :messageCount,
            sensor_count = :sensorCount,
            infrastructure_count = :infrastructureCount
        WHERE id = :sessionId
        """
    )
    suspend fun updateSessionAggregates(
        sessionId: Long,
        uniqueNodes: Int,
        directNeighbors: Int,
        meshNeighbors: Int,
        messageCount: Int,
        sensorCount: Int,
        infrastructureCount: Int,
    )

    @Query(
        """
        UPDATE discovery_session
        SET completion_status = :status,
            failure_message = :failureMessage
        WHERE id = :sessionId
        """
    )
    suspend fun updateSessionStatus(
        sessionId: Long,
        status: String,
        failureMessage: String? = null,
    )

    @Query(
        """
        UPDATE discovery_session
        SET completion_status = 'interrupted'
        WHERE completion_status = 'in_progress'
        """
    )
    suspend fun markInProgressSessionsInterrupted()

    @Query(
        """
        SELECT * FROM discovery_session
        WHERE device_address = :deviceAddress
          AND completion_status IN ('in_progress', 'interrupted', 'failed', 'stopped', 'restore_pending', 'restore_failed')
        ORDER BY timestamp DESC
        LIMIT 1
        """
    )
    suspend fun getLatestRecoverableSession(deviceAddress: String): DiscoverySessionEntity?

    @Query(
        """
        SELECT * FROM discovery_session
        WHERE completion_status IN ('in_progress', 'interrupted', 'failed', 'stopped', 'restore_pending', 'restore_failed')
        ORDER BY timestamp DESC
        LIMIT 1
        """
    )
    suspend fun getLatestRecoverableSession(): DiscoverySessionEntity?

    @Query("DELETE FROM discovery_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM discovery_session WHERE id IN (:sessionIds)")
    suspend fun deleteSessions(sessionIds: List<Long>)
}
