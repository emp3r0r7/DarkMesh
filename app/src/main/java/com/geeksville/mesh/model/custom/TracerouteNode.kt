package com.geeksville.mesh.model.custom

data class TracerouteNode(
    val nodeNum: Int,
    val userName: String,
    val snr: Float? = null
)

data class TraceroutePath(
    val direction: String, // "forward" / "backward"
    val path: List<TracerouteNode>
)

data class TracerouteJson(
    val idHunter: String,
    val from: Long,
    val to: Long,
    val id: Long,
    val rxSnr: Float,
    val rxRssi: Int,
    val hopStart: Int,
    val forward: TraceroutePath?,
    val backward: TraceroutePath?,
    val type: String = "traceroute"
)
