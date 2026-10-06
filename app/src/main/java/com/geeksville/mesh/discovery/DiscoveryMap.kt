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

import com.geeksville.mesh.model.Node

data class DiscoveryMapLink(
    val from: Node,
    val to: Node,
    val snr: Float?,
    val isDirect: Boolean,
)

data class DiscoveryNodeListItem(
    val nodeNum: Long,
    val longName: String,
    val snr: Float?,
    val distanceMeters: Int? = null,
)

data class DiscoveryNodeList(
    val presetName: String,
    val originName: String,
    val nodeClass: DiscoveryNodeClass,
    val nodes: List<DiscoveryNodeListItem>,
)

data class DiscoveryMap(
    val nodeClass: DiscoveryNodeClass,
    val localNode: Node?,
    val nodes: List<Node>,
    val links: List<DiscoveryMapLink>,
    val nodeList: DiscoveryNodeList,
)
