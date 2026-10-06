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

package com.geeksville.mesh

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geeksville.mesh.database.MeshtasticDatabase
import com.geeksville.mesh.database.dao.NodeRegistryDao
import com.geeksville.mesh.database.entity.NodeRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeRegistryDaoTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var nodeRegistryDao: NodeRegistryDao

    private val publicKey = ByteArray(32) { (it + 1).toByte() }

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MeshtasticDatabase::class.java).build()
        nodeRegistryDao = database.nodeRegistryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun upsertPersistsPublicKey() = runBlocking {
        val node = NodeRegistry(
            nodeId = "!00000002",
            nodeNum = 2,
            longName = "Remote",
            shortName = "REM",
            publicKey = publicKey,
        )

        nodeRegistryDao.upsert(node)

        val stored = nodeRegistryDao.getById("!00000002")
        assertArrayEquals(publicKey, stored?.publicKey)
    }

    @Test
    fun updateNodeInfoWithoutPublicKeyPreservesExistingKey() = runBlocking {
        nodeRegistryDao.insertNodeInfo(
            nodeId = "!00000002",
            nodeNum = 2,
            longName = "Remote",
            shortName = "REM",
            defaultName = "Meshtastic 0002",
            publicKey = publicKey,
            lastSeen = 1L,
        )

        val updated = nodeRegistryDao.updateNodeInfo(
            nodeId = "!00000002",
            nodeNum = 2,
            longName = "Renamed",
            shortName = "NEW",
            publicKey = null,
            lastSeen = 2L,
        )

        val stored = nodeRegistryDao.getById("!00000002")
        assertEquals(1, updated)
        assertEquals("Renamed", stored?.longName)
        assertArrayEquals(publicKey, stored?.publicKey)
    }
}
