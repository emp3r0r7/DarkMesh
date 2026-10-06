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
import com.geeksville.mesh.database.dao.DiscoveryDao
import com.geeksville.mesh.database.entity.DiscoveredNodeEntity
import com.geeksville.mesh.database.entity.DiscoveryNeighborType
import com.geeksville.mesh.database.entity.DiscoveryPresetResultEntity
import com.geeksville.mesh.database.entity.DiscoverySessionEntity
import com.geeksville.mesh.database.entity.DiscoverySessionStatus
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.database.entity.NodeRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.proto.ChannelProtos
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.user

@RunWith(AndroidJUnit4::class)
class DiscoveryDaoTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var discoveryDao: DiscoveryDao

    private val publicKey = ByteArray(32) { (it + 1).toByte() }

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MeshtasticDatabase::class.java).build()
        discoveryDao = database.discoveryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertSessionResultAndNodesWithoutMutatingNodeDbOrRegistry() = runBlocking {
        val nodeDbNode = nodeEntity(num = 2, longName = "NodeDB Node")
        database.nodeInfoDao().upsert(nodeDbNode)
        database.nodeRegistryDao().upsert(
            NodeRegistry(
                nodeId = "!00000002",
                nodeNum = 2,
                longName = "Registry Node",
                shortName = "REG",
                publicKey = publicKey,
            )
        )

        val sessionId = discoveryDao.insertSession(discoverySession())
        val resultId = discoveryDao.insertPresetResultWithNodes(
            result = discoveryPresetResult(sessionId),
            nodes = listOf(
                DiscoveredNodeEntity(
                    presetResultId = 0,
                    nodeNum = 2,
                    nodeId = "!00000002",
                    longName = "Discovery Node",
                    shortName = "DSC",
                    defaultName = "!00000002",
                    neighborType = DiscoveryNeighborType.DIRECT,
                    latitude = 45.0,
                    longitude = 9.0,
                    snr = -4.5f,
                    rssi = -100,
                    packetCount = 3,
                )
            )
        )

        val session = discoveryDao.getSessions().first().single()
        val nodes = discoveryDao.getDiscoveredNodes(resultId)
        val nodeDbStored = database.nodeInfoDao().nodeDBbyNum().first().getValue(2).node
        val registryNode = database.nodeRegistryDao().getById("!00000002")

        assertEquals(sessionId, session.id)
        assertEquals("LONG_FAST", discoveryDao.getPresetResults(sessionId).single().presetName)
        assertEquals("Discovery Node", nodes.single().longName)
        assertEquals("NodeDB Node", nodeDbStored.longName)
        assertEquals(45.0, nodeDbStored.latitude, 0.0)
        assertEquals(9.0, nodeDbStored.longitude, 0.0)
        assertEquals("Registry Node", registryNode?.longName)
        assertArrayEquals(publicKey, registryNode?.publicKey)
    }

    @Test
    fun deletingSessionCascadesDiscoveryRows() = runBlocking {
        val sessionId = discoveryDao.insertSession(discoverySession())
        val resultId = discoveryDao.insertPresetResultWithNodes(
            result = discoveryPresetResult(sessionId),
            nodes = listOf(
                DiscoveredNodeEntity(
                    presetResultId = 0,
                    nodeNum = 3,
                    nodeId = "!00000003",
                    neighborType = DiscoveryNeighborType.MESH,
                )
            )
        )

        discoveryDao.deleteSession(sessionId)

        assertTrue(discoveryDao.getPresetResults(sessionId).isEmpty())
        assertTrue(discoveryDao.getDiscoveredNodes(resultId).isEmpty())
    }

    @Test
    fun deletingSessionsCascadesDiscoveryRowsWithoutClearingNodeDb() = runBlocking {
        database.nodeInfoDao().upsert(nodeEntity(num = 4, longName = "Persistent Node"))
        val firstSessionId = discoveryDao.insertSession(discoverySession())
        val firstResultId = discoveryDao.insertPresetResultWithNodes(
            result = discoveryPresetResult(firstSessionId),
            nodes = listOf(discoveredNode(nodeNum = 4)),
        )
        val secondSessionId = discoveryDao.insertSession(discoverySession())
        val secondResultId = discoveryDao.insertPresetResultWithNodes(
            result = discoveryPresetResult(secondSessionId),
            nodes = listOf(discoveredNode(nodeNum = 5)),
        )

        discoveryDao.deleteSessions(listOf(firstSessionId, secondSessionId))

        val nodeDbStored = database.nodeInfoDao().nodeDBbyNum().first().getValue(4).node
        assertTrue(discoveryDao.getPresetResults(firstSessionId).isEmpty())
        assertTrue(discoveryDao.getPresetResults(secondSessionId).isEmpty())
        assertTrue(discoveryDao.getDiscoveredNodes(firstResultId).isEmpty())
        assertTrue(discoveryDao.getDiscoveredNodes(secondResultId).isEmpty())
        assertEquals("Persistent Node", nodeDbStored.longName)
    }

    private fun discoverySession() = DiscoverySessionEntity(
        presetsScanned = "LONG_FAST",
        homePreset = "LongFast",
        completionStatus = DiscoverySessionStatus.IN_PROGRESS,
        totalDwellSeconds = 60,
        deviceAddress = "x00:11:22:33:44:55",
        homeLoraConfig = ConfigProtos.Config.LoRaConfig.newBuilder()
            .setUsePreset(true)
            .setModemPreset(ConfigProtos.Config.LoRaConfig.ModemPreset.LONG_FAST)
            .build(),
        homePrimaryChannel = ChannelProtos.ChannelSettings.newBuilder()
            .setName("home")
            .build(),
    )

    private fun discoveryPresetResult(sessionId: Long) = DiscoveryPresetResultEntity(
        sessionId = sessionId,
        presetName = "LONG_FAST",
        modemPresetValue = ConfigProtos.Config.LoRaConfig.ModemPreset.LONG_FAST.number,
        startedAt = 1,
        endedAt = 2,
        dwellSeconds = 1,
        uniqueNodes = 1,
        directNeighbors = 1,
        meshNeighbors = 0,
        messageCount = 0,
        sensorCount = 0,
        infrastructureCount = 0,
        packetCount = 3,
    )

    private fun discoveredNode(nodeNum: Long) = DiscoveredNodeEntity(
        presetResultId = 0,
        nodeNum = nodeNum,
        nodeId = "!%08x".format(nodeNum),
        neighborType = DiscoveryNeighborType.DIRECT,
    )

    private fun nodeEntity(
        num: Int,
        longName: String,
    ): NodeEntity {
        return NodeEntity(
            num = num,
            user = user {
                id = "!%08x".format(num)
                this.longName = longName
                shortName = longName.take(3)
                hwModel = MeshProtos.HardwareModel.ANDROID_SIM
            },
            longName = longName,
            shortName = longName.take(3),
        ).apply {
            setPosition(
                MeshProtos.Position.newBuilder()
                    .setLatitudeI(450_000_000)
                    .setLongitudeI(90_000_000)
                    .build()
            )
        }
    }
}
