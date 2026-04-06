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

    suspend fun updateNodeInfo(nodeId: String,
                               nodeNum: Int?,
                               longName: String?,
                               shortName: String?,
                               lastSeen: Long
    ) = withContext(dispatchers.io) {

        nodeRegistryDao.updateNodeInfo(
            nodeId,
            nodeNum,
            longName,
            shortName,
            lastSeen
        )
    }

    suspend fun insertNodeInfo(nodeId: String,
                               nodeNum: Int?,
                               longName: String?,
                               shortName: String?,
                               lastSeen: Long
    ) = withContext(dispatchers.io) {

        nodeRegistryDao.insertNodeInfo(
            nodeId,
            nodeNum,
            longName,
            shortName,
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
        shortName: String,
        latitudeI: Int?,
        longitudeI: Int?,
        lastSeen: Long
    ) = withContext(dispatchers.io) {
        nodeRegistryDao.insertNodeRegistryPosition(
            nodeId,
            longName,
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
