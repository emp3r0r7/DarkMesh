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

import com.geeksville.mesh.database.entity.DiscoveryPresetResultEntity
import com.geeksville.mesh.database.entity.DiscoverySessionEntity

data class DiscoveryReport(
    val session: DiscoverySessionEntity,
    val presets: List<DiscoveryPresetReport>,
) {
    val bestPreset: DiscoveryPresetReport? = presets.firstOrNull()
}

data class DiscoveryPresetReport(
    val result: DiscoveryPresetResultEntity,
    val rank: Int,
    val neighborList: DiscoveryNodeList,
    val networkList: DiscoveryNodeList,
    val unknownNodes: Int,
) {
    val directNodes: Int = neighborList.nodes.size
    val networkNodes: Int = networkList.nodes.size
    val totalNodes: Int = directNodes + networkNodes + unknownNodes
}
