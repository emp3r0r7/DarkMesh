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
import com.geeksville.mesh.model.Node

internal fun DiscoveredNodeEntity.toDiscoveryMapNode(knownNodeByNum: Map<Int, Node>): Node {
    knownNodeByNum[nodeNum.toInt()]?.takeIf { it.hasDiscoveryMapPosition() }?.let { return it }

    return Node(
        num = nodeNum.toInt(),
        liteNodeId = nodeId,
        liteDefaultName = defaultName ?: nodeId,
        liteLongName = longName ?: defaultName ?: nodeId,
        liteShortName = shortName ?: nodeId.takeLast(DEFAULT_SHORT_NAME_LENGTH),
        liteLatitude = latitude,
        liteLongitude = longitude,
    )
}

internal fun Node.hasDiscoveryMapPosition(): Boolean {
    return validPosition != null || validLiteNode
}

private const val DEFAULT_SHORT_NAME_LENGTH = 4
