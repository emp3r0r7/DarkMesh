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

package com.geeksville.mesh.discovery

import com.geeksville.mesh.database.entity.DiscoveredNodeEntity
import com.geeksville.mesh.database.entity.DiscoveryNeighborType

internal fun DiscoveredNodeEntity.hasPositiveHomeDirectEvidence(): Boolean {
    return neighborType == DiscoveryNeighborType.DIRECT
}

internal fun DiscoveredNodeEntity.discoveryNodeClass(): DiscoveryNodeClass = when {
    hasPositiveHomeDirectEvidence() -> DiscoveryNodeClass.NEIGHBOR
    hopCount != null && hopCount >= 1 -> DiscoveryNodeClass.NETWORK
    neighborType == DiscoveryNeighborType.MESH && viaNodeNum != null -> DiscoveryNodeClass.NETWORK
    else -> DiscoveryNodeClass.UNKNOWN
}

internal fun List<DiscoveredNodeEntity>.countDiscoveryNodes(nodeClass: DiscoveryNodeClass): Int {
    return distinctBy { it.nodeNum }.count { it.discoveryNodeClass() == nodeClass }
}
