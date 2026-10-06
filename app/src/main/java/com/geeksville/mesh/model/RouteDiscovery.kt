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


import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.geeksville.mesh.discovery.DiscoveryMap
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.entity.NodeRegistry
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.MeshProtos.RouteDiscovery
import org.meshtastic.proto.Portnums

const val SNR_GOOD_THRESHOLD = -7f
const val SNR_FAIR_THRESHOLD = -15f

val MeshProtos.MeshPacket.fullRouteDiscovery: RouteDiscovery?
    get() = with(decoded) {
        if (hasDecoded() && !wantResponse && portnum == Portnums.PortNum.TRACEROUTE_APP) {
            runCatching { RouteDiscovery.parseFrom(payload).toBuilder() }.getOrNull()?.apply {
                val fullRoute = listOf(to) + routeList + from
                clearRoute()
                addAllRoute(fullRoute)

                val (fullRouteBack, normalizedSnrBack) = normalizedRouteBack(from, to)
                clearRouteBack()
                clearSnrBack()
                if (hopStart > 0 && normalizedSnrBack.isNotEmpty()) { // otherwise back route is invalid
                    addAllRouteBack(fullRouteBack)
                    addAllSnrBack(normalizedSnrBack)
                }
            }?.build()
        } else {
            null
        }
    }

private fun RouteDiscovery.Builder.normalizedRouteBack(
    packetFrom: Int,
    packetTo: Int,
): Pair<List<Int>, List<Int>> {
    var routeBack = routeBackList.toList()
    var snrBack = snrBackList.toList()

    if (routeBack.firstOrNull() == packetTo) {
        routeBack = routeBack.drop(1)
        val expectedSnrCount = routeBack.size + 1
        if (snrBack.size > expectedSnrCount) {
            snrBack = snrBack.drop(1)
        }
    }

    return (listOf(packetFrom) + routeBack + packetTo) to snrBack
}

data class TraceRouteMap(
    val traceForwardList: List<Node>,
    val traceBackList: List<Node>,
    val sourceTrace: String?
)

sealed class MapMode {
    data object Normal : MapMode()
    data class Traceroute(val trace: TraceRouteMap) : MapMode()
    data class NeighborDiscovery(val discovery: NeighborDiscoveryMap) : MapMode()
    data class Discovery(val discovery: DiscoveryMap) : MapMode()
}

@Suppress("MagicNumber")
private fun formatTraceroutePath(nodesList: List<String>, snrList: List<Int>): String {
    // nodesList should include both origin and destination nodes
    // origin will not have an SNR value, but destination should
    val snrStr = if (snrList.size == nodesList.size - 1) {
        snrList
    } else {
        // use unknown SNR for entire route if snrList has invalid size
        List(nodesList.size - 1) { -128 }
    }.map { snr ->
        val str = if (snr == -128) "?" else "${snr / 4f}"
        "⇊ $str dB"
    }

    return nodesList.map { userName ->
        "■ $userName"
    }.flatMapIndexed { i, nodeStr ->
        if (i == 0) listOf(nodeStr) else listOf(snrStr[i - 1], nodeStr)
    }.joinToString("\n")
}

private fun RouteDiscovery.getTracerouteResponse(
    getUser: (nodeNum: Int) -> String,
): String = buildString {
    if (routeList.isNotEmpty()) { //todo creazione invio qui payload da inviare
        append("Route traced toward destination:\n\n")
        append(formatTraceroutePath(routeList.map(getUser), snrTowardsList))
    }
    if (routeBackList.isNotEmpty()) { //todo creazione qui payload ritorno da inviare
        append("\n\n")
        append("Route traced back to us:\n\n")
        append(formatTraceroutePath(routeBackList.map(getUser), snrBackList))
    }
}

fun MeshProtos.MeshPacket.getTracerouteResponse(
    getUser: (nodeNum: Int) -> String,
): String? = fullRouteDiscovery?.getTracerouteResponse(getUser)

fun evaluateTracerouteMapAvailability(
    traceroute: String?,
    nodeDb: NodeRepository,
    nodeRegistryMap: Map<String, NodeRegistry>,
): TraceRouteMap? {
    return evaluateTracerouteMapAvailability(
        traceroute = traceroute,
        nodesByNum = nodeDb.nodeDBbyNum.value,
        nodeRegistryMap = nodeRegistryMap,
    )
}

internal fun evaluateTracerouteMapAvailability(
    traceroute: String?,
    nodesByNum: Map<Int, Node>,
    nodeRegistryMap: Map<String, NodeRegistry>,
): TraceRouteMap? = evaluateTracerouteMapAvailabilityFromNodes(traceroute, nodesByNum, nodeRegistryMap)

fun colorizeTracerouteResponse(input: String?): SpannableString {
    if (input == null) return SpannableString("")

    val spannable = SpannableString(input)
    val snrRegex = Regex("""⇊ ([\d.?-]+) dB""")

    snrRegex.findAll(input).forEach { match ->
        val snrValue = match.groupValues.getOrNull(1)?.toFloatOrNull()

        val color = when {
            snrValue == null -> Color.GRAY
            snrValue >= SNR_GOOD_THRESHOLD -> Color.GREEN
            snrValue >= SNR_FAIR_THRESHOLD -> Color.rgb(255, 230, 0)
            else -> Color.rgb(247, 147, 26)
        }

        val start = match.range.first
        val end = match.range.last + 1
        spannable.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    return spannable
}
