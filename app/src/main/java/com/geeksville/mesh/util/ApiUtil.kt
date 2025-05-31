package com.geeksville.mesh.util

import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.Portnums
import com.geeksville.mesh.model.custom.TracerouteJson
import com.geeksville.mesh.model.custom.TracerouteNode
import com.geeksville.mesh.model.custom.TraceroutePath
import com.geeksville.mesh.model.fullRouteDiscovery
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.util.JsonFormat

object ApiUtil {

    fun mergePacketAndPayload(myNodeID: String,
                              packet: MeshPacket,
                              messageType: Int,
                              getUserName: (Int) -> String): String {

        val type = getPortNameFromValue(messageType)

        val packetJsonStr = JsonFormat
            .printer()
            .includingDefaultValueFields()
            .print(packet)


        val packetJson = JsonParser.parseString(packetJsonStr).asJsonObject

        val merged = JsonObject()
        //merged.addProperty("name", getUserName(packet.from))
        merged.add("packet", packetJson)
        merged.addProperty("idHunter", myNodeID)
        //merged.add(type, payloadJson)

        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(merged)
    }


    fun MeshPacket.buildTracerouteJson(
        myNodeID: String,
        getUser: (nodeNum: Int) -> String
    ): String {
        val rd = fullRouteDiscovery ?: return "{}"

        val forward = if (rd.routeList.isNotEmpty()) {
            TraceroutePath(
                direction = "forward",
                path = rd.routeList.mapIndexed { i, nodeNum ->
                    TracerouteNode(
                        nodeNum = nodeNum,
                        userName = getUser(nodeNum),
                        snr = rd.snrTowardsList.getOrNull(i - 1)?.let { it / 4f }
                    )
                }
            )
        } else null

        val backward = if (rd.routeBackList.isNotEmpty()) {
            TraceroutePath(
                direction = "backward",
                path = rd.routeBackList.mapIndexed { i, nodeNum ->
                    TracerouteNode(
                        nodeNum = nodeNum,
                        userName = getUser(nodeNum),
                        snr = rd.snrBackList.getOrNull(i - 1)?.let { it / 4f }
                    )
                }
            )
        } else null

        val full = TracerouteJson(
            idHunter = myNodeID,
            from = this.from.toLong() and 0xFFFFFFFFL,
            to = this.to.toLong() and 0xFFFFFFFFL,
            id = this.id.toLong() and 0xFFFFFFFFL,
            rxSnr = this.rxSnr,
            rxRssi = this.rxRssi,
            hopStart = this.hopStart,
            forward = forward,
            backward = backward
        )

        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(full)
    }

    private fun getPortNameFromValue(value: Int): String = when (value) {
        Portnums.PortNum.UNKNOWN_APP_VALUE -> "unknown"
        Portnums.PortNum.TEXT_MESSAGE_APP_VALUE -> "text_message"
        Portnums.PortNum.REMOTE_HARDWARE_APP_VALUE -> "remote_hardware"
        Portnums.PortNum.POSITION_APP_VALUE -> "position"
        Portnums.PortNum.NODEINFO_APP_VALUE -> "node_info"
        Portnums.PortNum.ROUTING_APP_VALUE -> "routing"
        Portnums.PortNum.ADMIN_APP_VALUE -> "admin"
        Portnums.PortNum.TEXT_MESSAGE_COMPRESSED_APP_VALUE -> "text_message_compressed"
        Portnums.PortNum.WAYPOINT_APP_VALUE -> "waypoint"
        Portnums.PortNum.AUDIO_APP_VALUE -> "audio"
        Portnums.PortNum.DETECTION_SENSOR_APP_VALUE -> "detection_sensor"
        Portnums.PortNum.ALERT_APP_VALUE -> "alert"
        Portnums.PortNum.REPLY_APP_VALUE -> "reply"
        Portnums.PortNum.IP_TUNNEL_APP_VALUE -> "ip_tunnel"
        Portnums.PortNum.PAXCOUNTER_APP_VALUE -> "paxcounter"
        Portnums.PortNum.SERIAL_APP_VALUE -> "serial"
        Portnums.PortNum.STORE_FORWARD_APP_VALUE -> "store_forward"
        Portnums.PortNum.RANGE_TEST_APP_VALUE -> "range_test"
        Portnums.PortNum.TELEMETRY_APP_VALUE -> "telemetry"
        Portnums.PortNum.ZPS_APP_VALUE -> "zps"
        Portnums.PortNum.SIMULATOR_APP_VALUE -> "simulator"
        Portnums.PortNum.TRACEROUTE_APP_VALUE -> "traceroute"
        Portnums.PortNum.NEIGHBORINFO_APP_VALUE -> "neighbor_info"
        Portnums.PortNum.ATAK_PLUGIN_VALUE -> "atak_plugin"
        Portnums.PortNum.MAP_REPORT_APP_VALUE -> "map_report"
        Portnums.PortNum.POWERSTRESS_APP_VALUE -> "powerstress"
        Portnums.PortNum.PRIVATE_APP_VALUE -> "private_app"
        Portnums.PortNum.ATAK_FORWARDER_VALUE -> "atak_forwarder"
        Portnums.PortNum.MAX_VALUE -> "max"
        else -> "unknown_$value"
    }


}