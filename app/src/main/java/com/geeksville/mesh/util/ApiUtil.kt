package com.geeksville.mesh.util

import android.content.Context
import com.geeksville.mesh.android.BuildUtils.errormsg
import com.geeksville.mesh.model.DeviceHardware
import com.geeksville.mesh.model.DeviceHardwareDto
import com.geeksville.mesh.model.custom.TracerouteJson
import com.geeksville.mesh.model.custom.TracerouteNode
import com.geeksville.mesh.model.custom.TraceroutePath
import com.geeksville.mesh.model.fullRouteDiscovery
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.protobuf.util.JsonFormat
import kotlinx.serialization.json.Json
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.ConfigProtos.Config.DisplayConfig.DeprecatedGpsCoordinateFormat
import org.meshtastic.proto.MeshProtos.MeshPacket
import org.meshtastic.proto.Portnums

object ApiUtil {

    private val jsonParser: Json = Json {
        ignoreUnknownKeys = true
    }

    fun loadDeviceHardwareList(context: Context): List<DeviceHardware> {
        return try {
            val jsonString = context.assets
                .open("device_hardware.json")
                .bufferedReader()
                .use { it.readText() }

            jsonParser
                .decodeFromString<List<DeviceHardwareDto>>(jsonString)
                .map { it.toDeviceHardware() }

        } catch (ex: Exception) {
            errormsg("Error loading device hardware: ${ex.message}")
            emptyList()
        }
    }


    fun safeGpsFormat(gps: DeprecatedGpsCoordinateFormat): Int {
        return if (gps == DeprecatedGpsCoordinateFormat.UNRECOGNIZED)
            DeprecatedGpsCoordinateFormat.UNUSED.getNumber()

        else gps.getNumber()
    }

    fun isInfrastructure(role: String) : Boolean {
        return ConfigProtos.Config.DeviceConfig.Role.ROUTER.name == role  ||
               ConfigProtos.Config.DeviceConfig.Role.ROUTER_LATE.name == role ||
               ConfigProtos.Config.DeviceConfig.Role.CLIENT_BASE.name == role ||
               ConfigProtos.Config.DeviceConfig.Role.REPEATER.name == role
    }

    fun mergePacketAndPayload(myNodeID: String,
                              packet: MeshPacket): String {

        //if needed, get the type from this helper func!
        //val type = getPortNameFromValue(messageType)

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


    @Suppress("unused") //to be used in case of necessity for a traceroute parsing
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

    @Suppress("unused") //to be used in case of necessity if you need the explicit type
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