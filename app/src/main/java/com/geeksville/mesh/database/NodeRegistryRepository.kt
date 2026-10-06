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

package com.geeksville.mesh.database

import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.database.dao.NodeRegistryDao
import com.geeksville.mesh.database.entity.NodeRegistry
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class NodeRegistryRepository @Inject constructor(
    private val nodeRegistryDaoLazy: dagger.Lazy<NodeRegistryDao>,
    private val dispatchers: CoroutineDispatchers,
) {
    private val nodeRegistryDao by lazy {
        nodeRegistryDaoLazy.get()
    }

    fun getAllNodes() = nodeRegistryDao.getAll().flowOn(dispatchers.io)

    suspend fun getNodeById(nodeId: String): NodeRegistry? = withContext(dispatchers.io) {
        nodeRegistryDao.getById(nodeId)
    }

    suspend fun upsert(node: NodeRegistry) = withContext(dispatchers.io) {
        nodeRegistryDao.upsert(node)
    }

    suspend fun deleteById(nodeId: String) = withContext(dispatchers.io) {
        nodeRegistryDao.deleteById(nodeId)
    }

    suspend fun deleteAll() = withContext(dispatchers.io) {
        nodeRegistryDao.deleteAll()
    }

    suspend fun updateLastSeen(nodeId: String, timestamp: Long) = withContext(dispatchers.io) {
        nodeRegistryDao.updateLastSeen(nodeId, timestamp)
    }

    suspend fun updatePosition(nodeId: String, latitude: Int, longitude: Int) = withContext(dispatchers.io) {
        nodeRegistryDao.updatePosition(nodeId, latitude, longitude)
    }

    suspend fun updateNodeInfo(
        nodeId: String,
        nodeNum: Int?,
        longName: String?,
        shortName: String?,
        publicKey: ByteArray? = null,
        lastSeen: Long
    ) = withContext(dispatchers.io) {

        nodeRegistryDao.updateNodeInfo(
            nodeId,
            nodeNum,
            longName,
            shortName,
            publicKey,
            lastSeen
        )
    }

    suspend fun insertNodeInfo(
        nodeId: String,
        nodeNum: Int?,
        longName: String?,
        shortName: String?,
        defaultName: String?,
        publicKey: ByteArray? = null,
        lastSeen: Long
    ) = withContext(dispatchers.io) {

        nodeRegistryDao.insertNodeInfo(
            nodeId,
            nodeNum,
            longName,
            shortName,
            defaultName,
            publicKey,
            lastSeen
        )
    }

    suspend fun updatePosition(
        nodeId: String,
        latitudeI: Int?,
        longitudeI: Int?,
        lastSeen: Long
    ) = withContext(dispatchers.io) {
        nodeRegistryDao.updatePosition(
            nodeId,
            latitudeI,
            longitudeI,
            lastSeen
        )
    }

    suspend fun insertNodeRegistryPosition(
        nodeId: String,
        longName: String,
        defaultName: String,
        shortName: String,
        latitudeI: Int?,
        longitudeI: Int?,
        lastSeen: Long
    ) = withContext(dispatchers.io) {
        nodeRegistryDao.insertNodeRegistryPosition(
            nodeId,
            longName,
            defaultName,
            shortName,
            latitudeI,
            longitudeI,
            lastSeen
        )
    }

    suspend fun searchLongName(
        longName: String
    ) = withContext(dispatchers.io) {
        nodeRegistryDao.searchLongName(longName)
    }
}
