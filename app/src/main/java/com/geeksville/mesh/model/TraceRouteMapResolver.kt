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

package com.geeksville.mesh.model

import com.geeksville.mesh.database.entity.NodeRegistry
import com.geeksville.mesh.database.entity.isValidForTraceMap
import com.geeksville.mesh.util.AppUtil.hexIdToNodeNum

internal fun evaluateTracerouteMapAvailabilityFromNodes(
    traceroute: String?,
    nodesByNum: Map<Int, Node>,
    nodeRegistryMap: Map<String, NodeRegistry>,
): TraceRouteMap? {
    val tracedRoutes = traceroute?.split("Route traced back to us:")
    val traceBackList = parseNodeFromTraceroute(
        tracedRoutes,
        1,
        nodesByNum,
        nodeRegistryMap,
    )
    val traceForwardList = parseNodeFromTraceroute(
        tracedRoutes,
        0,
        nodesByNum,
        nodeRegistryMap,
    )

    return TraceRouteMap(
        traceForwardList = traceForwardList,
        traceBackList = traceBackList,
        sourceTrace = traceroute,
    ).takeIf {
        listOf(traceForwardList, traceBackList).any { route ->
            route.size >= MIN_DRAWABLE_TRACE_POINTS
        }
    }
}

private fun parseNodeFromTraceroute(
    tracedNodes: List<String>?,
    searchIndex: Int,
    nodesByNum: Map<Int, Node>,
    nodeRegistryMap: Map<String, NodeRegistry>,
): ArrayList<Node> {
    return tracedNodes.traceNodeNames(searchIndex)
        .mapNotNull { traceNodeName ->
            resolveTraceNode(traceNodeName, nodesByNum, nodeRegistryMap)
        }
        .toCollection(ArrayList())
}

private fun List<String>?.traceNodeNames(searchIndex: Int): List<String> {
    return this?.getOrNull(searchIndex)
        ?.trim()
        ?.split("■")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
}

private fun resolveTraceNode(
    traceNodeName: String,
    nodesByNum: Map<Int, Node>,
    nodeRegistryMap: Map<String, NodeRegistry>,
): Node? {
    return nodesByNum.values.firstOrNull { candidate ->
        val longName = candidate.user.longName.takeIf { it.isNotBlank() }
        longName != null && traceNodeName.contains(longName) && candidate.hasTraceMapPosition()
    } ?: nodeRegistryMap.values
        .filter { it.isValidForTraceMap() }
        .firstOrNull { it.matchesTraceNodeName(traceNodeName) }
        ?.toTraceMapNode()
}

private fun NodeRegistry.matchesTraceNodeName(traceNodeName: String): Boolean {
    val defaultName = defaultName
    val longName = longName
    return defaultName != null && longName != null &&
        (traceNodeName.contains(defaultName) || traceNodeName.contains(longName))
}

private fun NodeRegistry.toTraceMapNode(): Node? {
    val nodeNum = runCatching { hexIdToNodeNum(nodeId) }.getOrNull()
    val latitudeI = latitudeI
    val longitudeI = longitudeI
    return if (nodeNum != null && latitudeI != null && longitudeI != null) {
        Node(
            num = nodeNum,
            liteNodeId = nodeId,
            liteDefaultName = defaultName,
            liteLongName = longName,
            liteShortName = shortName,
            liteLatitude = latitudeI * COORDINATE_DEGREES,
            liteLongitude = longitudeI * COORDINATE_DEGREES,
        )
    } else {
        null
    }
}

private fun Node.hasTraceMapPosition(): Boolean = validPosition != null || validLiteNode

private const val MIN_DRAWABLE_TRACE_POINTS = 2
private const val COORDINATE_DEGREES = 1e-7
