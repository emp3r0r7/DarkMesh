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

package com.geeksville.mesh.util

import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.database.entity.NodeRegistry
import com.google.protobuf.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.user

class PkiUtilsTest {

    private fun publicKey(seed: Int): ByteString =
        ByteString.copyFrom(ByteArray(PkiUtils.PUBLIC_KEY_SIZE_BYTES) { (seed + it).toByte() })

    private fun meshUser(
        nodeNum: Int,
        publicKey: ByteString = ByteString.EMPTY,
        longName: String = "Node $nodeNum",
    ): MeshProtos.User = user {
        id = DataPacket.nodeNumToDefaultId(nodeNum)
        this.longName = longName
        shortName = "N$nodeNum"
        hwModel = MeshProtos.HardwareModel.ANDROID_SIM
        if (!publicKey.isEmpty) this.publicKey = publicKey
    }

    private fun node(
        nodeNum: Int,
        publicKey: ByteString = ByteString.EMPTY,
        channel: Int = 3,
    ) = NodeEntity(
        num = nodeNum,
        user = meshUser(nodeNum, publicKey),
        longName = "Node $nodeNum",
        shortName = "N$nodeNum",
        channel = channel,
    )

    @Test
    fun privateMessagesUsePkiWhenBothNodesHaveKeys() {
        val localNode = node(1, publicKey(1))
        val targetNode = node(2, publicKey(2), channel = 5)

        assertEquals(
            DataPacket.PKC_CHANNEL_INDEX,
            PkiUtils.privateMessageChannel(localNode, targetNode),
        )
    }

    @Test
    fun adminAndMetadataUsePkiWhenBothNodesHaveKeys() {
        val localNode = node(1, publicKey(1))
        val targetNode = node(2, publicKey(2))

        val channel = if (PkiUtils.shouldUsePki(localNode, targetNode)) {
            DataPacket.PKC_CHANNEL_INDEX
        } else {
            4
        }

        assertEquals(DataPacket.PKC_CHANNEL_INDEX, channel)
    }

    @Test
    fun registryPublicKeySurvivesReload() {
        val key = publicKey(8)
        val registry = NodeRegistry(
            nodeId = DataPacket.nodeNumToDefaultId(2),
            nodeNum = 2,
            publicKey = key.toByteArray(),
        )

        assertEquals(key, PkiUtils.effectivePublicKey(node = null, registry = registry))
        assertArrayEquals(key.toByteArray(), PkiUtils.publicKeyBytes(key))
    }

    @Test
    fun emptyNodeInfoPublicKeyPreservesExistingTrustedKey() {
        val trustedKey = publicKey(9)
        val existingUser = meshUser(2, trustedKey)
        val incomingUser = meshUser(2, publicKey = ByteString.EMPTY, longName = "Renamed")

        val result = PkiUtils.mergeUserPublicKey(existingUser, incomingUser)

        assertEquals(PublicKeyMergeState.PRESERVED, result.state)
        assertEquals("Renamed", result.user.longName)
        assertEquals(trustedKey, result.user.publicKey)
    }

    @Test
    fun equalPublicKeysCompareByContent() {
        val first = publicKey(10)
        val second = ByteString.copyFrom(first.toByteArray())

        val result = PkiUtils.mergeUserPublicKey(
            existingUser = meshUser(2, first),
            incomingUser = meshUser(2, second),
        )

        assertTrue(PkiUtils.publicKeysEqual(first, second))
        assertEquals(PublicKeyMergeState.ACCEPTED, result.state)
        assertEquals(first, result.user.publicKey)
    }

    @Test
    fun changedPublicKeyBecomesMismatchSentinel() {
        val result = PkiUtils.mergeUserPublicKey(
            existingUser = meshUser(2, publicKey(11)),
            incomingUser = meshUser(2, publicKey(12)),
        )

        assertEquals(PublicKeyMergeState.MISMATCH, result.state)
        assertTrue(PkiUtils.isMismatchPublicKey(result.user.publicKey))
        assertFalse(PkiUtils.hasUsablePublicKey(result.user.publicKey))
    }

    @Test
    fun duplicatedPublicKeyBecomesMismatchSentinel() {
        val duplicateKey = publicKey(13)

        val result = PkiUtils.mergeUserPublicKey(
            existingUser = null,
            incomingUser = meshUser(2, duplicateKey),
            duplicateNodeNum = 7,
        )

        assertEquals(PublicKeyMergeState.DUPLICATE, result.state)
        assertEquals(7, result.duplicateNodeNum)
        assertTrue(PkiUtils.isMismatchPublicKey(result.user.publicKey))
    }

    @Test
    fun staleMissingPublicKeyErrorIsRecoverableWhenKeyExists() {
        val key = publicKey(14)

        assertTrue(
            PkiUtils.isStaleMissingPublicKeyError(
                MeshProtos.Routing.Error.PKI_SEND_FAIL_PUBLIC_KEY,
                key,
            )
        )
        assertTrue(
            PkiUtils.isStaleMissingPublicKeyError(
                MeshProtos.Routing.Error.PKI_UNKNOWN_PUBKEY,
                key,
            )
        )

        val result = PkiUtils.mergeUserPublicKey(
            existingUser = meshUser(2, PkiUtils.MISMATCH_PUBLIC_KEY),
            incomingUser = meshUser(2, key),
            persistedPublicKey = key,
        )
        assertEquals(PublicKeyMergeState.ACCEPTED, result.state)
        assertEquals(key, result.user.publicKey)
    }

    @Test
    fun registryOnlyTargetCanUsePki() {
        val key = publicKey(15)
        val registry = NodeRegistry(
            nodeId = DataPacket.nodeNumToDefaultId(2),
            nodeNum = 2,
            publicKey = key.toByteArray(),
        )

        assertTrue(PkiUtils.shouldUsePki(node(1, publicKey(1)), targetNode = null, targetRegistry = registry))
        assertEquals(key, PkiUtils.effectivePublicKey(node = null, registry = registry))
    }

    @Test
    fun normalMessagesAndAdminFallbackRemainUnencryptedWithoutKeys() {
        val localNode = node(1, publicKey(1))
        val targetNode = node(2, publicKey = ByteString.EMPTY, channel = 6)

        assertEquals(6, PkiUtils.privateMessageChannel(localNode, targetNode))
        val adminChannel = if (PkiUtils.shouldUsePki(localNode, targetNode)) {
            DataPacket.PKC_CHANNEL_INDEX
        } else {
            2
        }

        assertEquals(2, adminChannel)
        assertNull(PkiUtils.effectivePublicKey(targetNode))
    }
}
