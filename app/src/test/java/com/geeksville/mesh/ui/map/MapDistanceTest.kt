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

package com.geeksville.mesh.ui.map

import com.geeksville.mesh.model.Node
import com.geeksville.mesh.util.latLongToMeter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.proto.MeshProtos

class MapDistanceTest {
    @Test
    fun tracerouteSegmentsBridgeNodesWithoutPositionData() {
        val segments = buildSegmentsForTraceroute(
            nodes = listOf(
                positionedNode(START_NODE, START_LAT, START_LON),
                Node(num = MISSING_NODE),
                positionedNode(END_NODE, END_LAT, END_LON),
            ),
            color = LINE_COLOR,
            offsetMeters = 0.0,
            side = 1,
        )

        assertEquals(1, segments.size)
        assertEquals(START_LAT, segments.single().from.latitude, COORDINATE_DELTA)
        assertEquals(START_LON, segments.single().from.longitude, COORDINATE_DELTA)
        assertEquals(END_LAT, segments.single().to.latitude, COORDINATE_DELTA)
        assertEquals(END_LON, segments.single().to.longitude, COORDINATE_DELTA)
    }

    @Test
    fun totalDistanceUsesForwardAndBackDrawablePointsIncludingLiteNodes() {
        val forward = listOf(
            positionedNode(START_NODE, START_LAT, START_LON),
            Node(num = MISSING_NODE),
            liteNode(RELAY_NODE, RELAY_LAT, RELAY_LON),
        )
        val back = listOf(
            liteNode(RELAY_NODE, RELAY_LAT, RELAY_LON),
            positionedNode(END_NODE, END_LAT, END_LON),
        )

        val expectedDistanceKm = (
            latLongToMeter(START_LAT, START_LON, RELAY_LAT, RELAY_LON) +
                latLongToMeter(RELAY_LAT, RELAY_LON, END_LAT, END_LON)
            ) / METERS_PER_KILOMETER

        assertEquals(expectedDistanceKm, totalDistanceKm(forward) + totalDistanceKm(back), 0.05)
    }

    private companion object {
        const val START_NODE = 1
        const val MISSING_NODE = 2
        const val RELAY_NODE = 3
        const val END_NODE = 4
        const val START_LAT = 45.0
        const val START_LON = 9.0
        const val RELAY_LAT = 45.1
        const val RELAY_LON = 9.1
        const val END_LAT = 45.2
        const val END_LON = 9.2
        const val LINE_COLOR = 123
        const val COORDINATE_SCALE = 1e7
        const val COORDINATE_DELTA = 0.00001
        const val METERS_PER_KILOMETER = 1000.0

        fun positionedNode(
            nodeNum: Int,
            latitude: Double,
            longitude: Double,
        ): Node {
            return Node(
                num = nodeNum,
                position = MeshProtos.Position.newBuilder()
                    .setLatitudeI((latitude * COORDINATE_SCALE).toInt())
                    .setLongitudeI((longitude * COORDINATE_SCALE).toInt())
                    .build(),
            )
        }

        fun liteNode(
            nodeNum: Int,
            latitude: Double,
            longitude: Double,
        ): Node {
            return Node(
                num = nodeNum,
                liteNodeId = "!%08x".format(nodeNum),
                liteDefaultName = "Node $nodeNum",
                liteLongName = "Node $nodeNum",
                liteShortName = "$nodeNum",
                liteLatitude = latitude,
                liteLongitude = longitude,
            )
        }
    }
}
