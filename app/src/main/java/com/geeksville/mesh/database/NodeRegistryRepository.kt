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
}
