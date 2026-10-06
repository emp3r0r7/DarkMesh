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
import org.meshtastic.proto.MeshProtos

enum class PublicKeyMergeState {
    ACCEPTED,
    PRESERVED,
    MISSING,
    MISMATCH,
    DUPLICATE,
}

data class PublicKeyMergeResult(
    val user: MeshProtos.User,
    val state: PublicKeyMergeState,
    val duplicateNodeNum: Int? = null,
)

object PkiUtils {
    const val PUBLIC_KEY_SIZE_BYTES = 32

    val MISMATCH_PUBLIC_KEY: ByteString = ByteString.copyFrom(ByteArray(PUBLIC_KEY_SIZE_BYTES))

    fun isMismatchPublicKey(publicKey: ByteString?): Boolean =
        publicKey != null &&
            publicKey.size() == PUBLIC_KEY_SIZE_BYTES &&
            publicKey.isAllZero()

    fun hasUsablePublicKey(publicKey: ByteString?): Boolean =
        publicKey != null &&
            publicKey.size() == PUBLIC_KEY_SIZE_BYTES &&
            !isMismatchPublicKey(publicKey)

    fun publicKeysEqual(left: ByteString?, right: ByteString?): Boolean {
        if (left == null || right == null || left.size() != right.size()) return false
        return left.toByteArray().contentEquals(right.toByteArray())
    }

    fun registryPublicKey(registry: NodeRegistry?): ByteString? =
        registry?.publicKey
            ?.let(ByteString::copyFrom)
            ?.takeIf(::hasUsablePublicKey)

    fun publicKeyBytes(publicKey: ByteString?): ByteArray? =
        publicKey
            ?.takeIf(::hasUsablePublicKey)
            ?.toByteArray()

    fun effectivePublicKey(node: NodeEntity?, registry: NodeRegistry? = null): ByteString? {
        val nodePublicKey = node?.user?.publicKey
        return if (node?.user?.isLicensed == true || isMismatchPublicKey(nodePublicKey)) {
            null
        } else {
            nodePublicKey?.takeIf(::hasUsablePublicKey) ?: registryPublicKey(registry)
        }
    }

    fun shouldUsePki(localNode: NodeEntity?, targetNode: NodeEntity?, targetRegistry: NodeRegistry? = null): Boolean =
        effectivePublicKey(localNode) != null && effectivePublicKey(targetNode, targetRegistry) != null

    fun privateMessageChannel(
        localNode: NodeEntity?,
        targetNode: NodeEntity,
        targetRegistry: NodeRegistry? = null,
    ): Int =
        if (shouldUsePki(localNode, targetNode, targetRegistry)) {
            DataPacket.PKC_CHANNEL_INDEX
        } else {
            targetNode.channel
        }

    fun mergeUserPublicKey(
        existingUser: MeshProtos.User?,
        incomingUser: MeshProtos.User,
        persistedPublicKey: ByteString? = null,
        duplicateNodeNum: Int? = null,
    ): PublicKeyMergeResult {
        val incomingPublicKey = incomingUser.publicKey
        val incomingHasPublicKey = hasUsablePublicKey(incomingPublicKey)
        val existingPublicKey = existingUser?.publicKey
        val existingIsMismatch = isMismatchPublicKey(existingPublicKey)
        val persistedTrustedKey = persistedPublicKey?.takeIf(::hasUsablePublicKey)
        val trustedPublicKey = trustedPublicKey(
            existingPublicKey = existingPublicKey,
            existingIsMismatch = existingIsMismatch,
            incomingPublicKey = incomingPublicKey,
            incomingHasPublicKey = incomingHasPublicKey,
            persistedTrustedKey = persistedTrustedKey,
        )

        return when {
            incomingUser.isLicensed -> PublicKeyMergeResult(
                user = incomingUser.withPublicKey(ByteString.EMPTY),
                state = PublicKeyMergeState.MISSING,
            )

            isConflictingDuplicate(
                duplicateNodeNum,
                incomingHasPublicKey,
                trustedPublicKey,
                incomingPublicKey,
            ) -> PublicKeyMergeResult(
                user = incomingUser.withPublicKey(MISMATCH_PUBLIC_KEY),
                state = PublicKeyMergeState.DUPLICATE,
                duplicateNodeNum = duplicateNodeNum,
            )

            existingIsMismatch && trustedPublicKey == null -> PublicKeyMergeResult(
                user = incomingUser.withPublicKey(MISMATCH_PUBLIC_KEY),
                state = PublicKeyMergeState.MISMATCH,
            )

            trustedPublicKey == null -> PublicKeyMergeResult(
                user = incomingUser,
                state = if (incomingHasPublicKey) PublicKeyMergeState.ACCEPTED else PublicKeyMergeState.MISSING,
            )

            !incomingHasPublicKey -> PublicKeyMergeResult(
                user = incomingUser.withPublicKey(trustedPublicKey),
                state = PublicKeyMergeState.PRESERVED,
            )

            publicKeysEqual(trustedPublicKey, incomingPublicKey) -> PublicKeyMergeResult(
                user = incomingUser.withPublicKey(trustedPublicKey),
                state = PublicKeyMergeState.ACCEPTED,
            )

            else -> PublicKeyMergeResult(
                user = incomingUser.withPublicKey(MISMATCH_PUBLIC_KEY),
                state = PublicKeyMergeState.MISMATCH,
            )
        }
    }

    fun isStaleMissingPublicKeyError(
        routingError: MeshProtos.Routing.Error,
        publicKey: ByteString?,
    ): Boolean =
        routingError in setOf(
            MeshProtos.Routing.Error.PKI_UNKNOWN_PUBKEY,
            MeshProtos.Routing.Error.PKI_SEND_FAIL_PUBLIC_KEY,
        ) && hasUsablePublicKey(publicKey)
}

private fun trustedPublicKey(
    existingPublicKey: ByteString?,
    existingIsMismatch: Boolean,
    incomingPublicKey: ByteString,
    incomingHasPublicKey: Boolean,
    persistedTrustedKey: ByteString?,
): ByteString? = when {
    PkiUtils.hasUsablePublicKey(existingPublicKey) -> existingPublicKey
    existingIsMismatch &&
        incomingHasPublicKey &&
        PkiUtils.publicKeysEqual(persistedTrustedKey, incomingPublicKey) -> persistedTrustedKey

    !existingIsMismatch -> persistedTrustedKey
    else -> null
}

private fun isConflictingDuplicate(
    duplicateNodeNum: Int?,
    incomingHasPublicKey: Boolean,
    trustedPublicKey: ByteString?,
    incomingPublicKey: ByteString,
): Boolean =
    duplicateNodeNum != null &&
        incomingHasPublicKey &&
        (trustedPublicKey == null || !PkiUtils.publicKeysEqual(trustedPublicKey, incomingPublicKey))

private fun MeshProtos.User.withPublicKey(publicKey: ByteString): MeshProtos.User =
    toBuilder().setPublicKey(publicKey).build()

private fun ByteString.isAllZero(): Boolean =
    !isEmpty && (0 until size()).all { byteAt(it).toInt() == 0 }
