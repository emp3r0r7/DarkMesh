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
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class MapSegment(
    val from: GeoPoint,
    val to: GeoPoint,
    val lineColor: Int,
)

@Suppress("DEPRECATION", "UsePropertyAccessSyntax")
fun MapSegment.toPolyline(): Polyline =
    Polyline().apply {
        setPoints(listOf(from, to))
        setColor(lineColor)
        width = POLYLINE_WIDTH
        isGeodesic = true
    }

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
fun GeoPoint.offsetMeters(
    meters: Double,
    bearingDegrees: Double,
): GeoPoint {
    val bearingRad = Math.toRadians(bearingDegrees)
    val latRad = Math.toRadians(latitude)
    val lonRad = Math.toRadians(longitude)
    val newLat = Math.asin(
        Math.sin(latRad) * Math.cos(meters / EARTH_RADIUS_METERS) +
            Math.cos(latRad) * Math.sin(meters / EARTH_RADIUS_METERS) * Math.cos(bearingRad)
    )
    val newLon = lonRad + Math.atan2(
        Math.sin(bearingRad) * Math.sin(meters / EARTH_RADIUS_METERS) * Math.cos(latRad),
        Math.cos(meters / EARTH_RADIUS_METERS) - Math.sin(latRad) * Math.sin(newLat)
    )

    return GeoPoint(Math.toDegrees(newLat), Math.toDegrees(newLon))
}

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
fun bearing(from: GeoPoint, to: GeoPoint): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) -
        Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)

    return (Math.toDegrees(Math.atan2(y, x)) + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
}

fun totalDistanceKm(nodes: List<Node>): Double {
    fun haversine(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KILOMETERS * asin(sqrt(h))
    }

    return nodes
        .mapNotNull { node -> node.toGeoPointOrNull() }
        .zipWithNext()
        .sumOf { (a, b) -> haversine(a, b) }
}

private fun validCoordinates(lat: Double, lon: Double): Boolean {
    return !lat.isNaN() && !lon.isNaN()
}

private fun GeoPoint.hasValidCoordinates(): Boolean {
    return validCoordinates(latitude, longitude)
}

internal fun Node.toGeoPointOrNull(): GeoPoint? = when {
    validPosition != null -> GeoPoint(latitude, longitude)
    validLiteNode && liteLatitude != null && liteLongitude != null -> GeoPoint(liteLatitude, liteLongitude)
    else -> null
}

@Suppress("SameParameterValue")
internal fun buildSegmentForNeighbor(
    fromNode: Node,
    toNode: Node,
    color: Int,
    offsetMeters: Double,
    side: Int,
): MapSegment? {
    val rawFrom = fromNode.toGeoPointOrNull()
    val rawTo = toNode.toGeoPointOrNull()

    return when {
        rawFrom?.hasValidCoordinates() != true || rawTo?.hasValidCoordinates() != true -> null
        offsetMeters == 0.0 -> MapSegment(
            from = rawFrom,
            to = rawTo,
            lineColor = color,
        )
        else -> rawFrom.toOffsetSegment(rawTo, color, offsetMeters, side)
    }
}

fun buildSegmentsForTraceroute(
    nodes: List<Node>,
    color: Int,
    offsetMeters: Double,
    side: Int,
): List<MapSegment> =
    nodes
        .mapNotNull { it.toGeoPointOrNull() }
        .filter { it.hasValidCoordinates() }
        .zipWithNext()
        .map { (rawFrom, rawTo) ->
            rawFrom.toOffsetSegment(rawTo, color, offsetMeters, side)
        }

private fun GeoPoint.toOffsetSegment(
    rawTo: GeoPoint,
    color: Int,
    offsetMeters: Double,
    side: Int,
): MapSegment {
    val (from, to) =
        if (latitude < rawTo.latitude) {
            this to rawTo
        } else {
            rawTo to this
        }
    val perpendicular = bearing(from, to) + (RIGHT_ANGLE_DEGREES * side)

    return MapSegment(
        from = this.offsetMeters(offsetMeters, perpendicular),
        to = rawTo.offsetMeters(offsetMeters, perpendicular),
        lineColor = color,
    )
}

private const val POLYLINE_WIDTH = 6f
private const val EARTH_RADIUS_METERS = 6_378_137.0
private const val EARTH_RADIUS_KILOMETERS = 6371.0
private const val FULL_CIRCLE_DEGREES = 360.0
private const val RIGHT_ANGLE_DEGREES = 90
