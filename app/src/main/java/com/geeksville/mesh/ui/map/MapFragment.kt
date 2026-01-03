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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.getLocationPermissions
import com.geeksville.mesh.android.gpsDisabled
import com.geeksville.mesh.android.hasGps
import com.geeksville.mesh.android.hasLocationPermission
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.model.MapMode
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.TraceRouteMap
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.colorizeTracerouteResponse
import com.geeksville.mesh.model.map.CustomTileSource
import com.geeksville.mesh.model.map.MarkerWithLabel
import com.geeksville.mesh.model.map.clustering.RadiusMarkerClusterer
import com.geeksville.mesh.ui.ScreenFragment
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.ApiUtil
import com.geeksville.mesh.util.SqlTileWriterExt
import com.geeksville.mesh.util.addCopyright
import com.geeksville.mesh.util.addScaleBarOverlay
import com.geeksville.mesh.util.createLatLongGrid
import com.geeksville.mesh.util.formatAgo
import com.geeksville.mesh.util.zoomIn
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.meshtastic.proto.MeshProtos.Waypoint
import org.meshtastic.proto.copy
import org.meshtastic.proto.waypoint
import org.osmdroid.bonuspack.utils.BonusPackHelper.getBitmapFromVectorDrawable
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqliteArchiveTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicyException
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.DateFormat
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val TRACE_COLOR_FORWARD = android.graphics.Color.BLUE
private const val TRACE_COLOR_BACK = android.graphics.Color.RED
private const val DEFAULT_SPACING_POLYLINE_TRACE = 100.0

@AndroidEntryPoint
class MapFragment : ScreenFragment("Map Fragment"), Logging {

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    MapView(model)
                }
            }
        }
    }
}

@Composable
private fun MapView.UpdateMarkers(
    nodeMarkers: List<MarkerWithLabel>,
    waypointMarkers: List<MarkerWithLabel>,
    nodeClusterer: RadiusMarkerClusterer
) {
    debug("Showing on map: ${nodeMarkers.size} nodes ${waypointMarkers.size} waypoints")
    overlays.removeAll { it is MarkerWithLabel }
    // overlays.addAll(nodeMarkers + waypointMarkers)
    overlays.addAll(waypointMarkers)
    nodeClusterer.items.clear()
    nodeClusterer.items.addAll(nodeMarkers)
    nodeClusterer.invalidate()
}

//    private fun addWeatherLayer() {
//        if (map.tileProvider.tileSource.name()
//                .equals(CustomTileSource.getTileSource("ESRI World TOPO").name())
//        ) {
//            val layer = TilesOverlay(
//                MapTileProviderBasic(
//                    activity,
//                    CustomTileSource.OPENWEATHER_RADAR
//                ), context
//            )
//            layer.loadingBackgroundColor = Color.TRANSPARENT
//            layer.loadingLineColor = Color.TRANSPARENT
//            map.overlayManager.add(layer)
//        }
//    }

private fun cacheManagerCallback(
    onTaskComplete: () -> Unit,
    onTaskFailed: (Int) -> Unit,
) = object : CacheManager.CacheManagerCallback {
    override fun onTaskComplete() {
        onTaskComplete()
    }

    override fun onTaskFailed(errors: Int) {
        onTaskFailed(errors)
    }

    override fun updateProgress(
        progress: Int,
        currentZoomLevel: Int,
        zoomMin: Int,
        zoomMax: Int
    ) {
        // NOOP since we are using the build in UI
    }

    override fun downloadStarted() {
        // NOOP since we are using the build in UI
    }

    override fun setPossibleTilesInArea(total: Int) {
        // NOOP since we are using the build in UI
    }
}

data class MapSegment(
    val from: GeoPoint,
    val to: GeoPoint,
    val lineColor: Int
)

@Suppress("DEPRECATION", "UsePropertyAccessSyntax")
fun MapSegment.toPolyline(): Polyline =
    Polyline().apply {
        setPoints(listOf(from, to))
        setColor(lineColor)
        width = 6f
        isGeodesic = true
    }


@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
fun GeoPoint.offsetMeters(
    meters: Double,
    bearingDegrees: Double
): GeoPoint {
    val earthRadius = 6_378_137.0 // metri
    val bearingRad = Math.toRadians(bearingDegrees)

    val latRad = Math.toRadians(latitude)
    val lonRad = Math.toRadians(longitude)

    val newLat = Math.asin(
        Math.sin(latRad) * Math.cos(meters / earthRadius) +
                Math.cos(latRad) * Math.sin(meters / earthRadius) * Math.cos(bearingRad)
    )

    val newLon = lonRad + Math.atan2(
        Math.sin(bearingRad) * Math.sin(meters / earthRadius) * Math.cos(latRad),
        Math.cos(meters / earthRadius) - Math.sin(latRad) * Math.sin(newLat)
    )

    return GeoPoint(
        Math.toDegrees(newLat),
        Math.toDegrees(newLon)
    )
}

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
fun bearing(from: GeoPoint, to: GeoPoint): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)

    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) -
            Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)

    return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
}



fun MapView.drawTraceroute(trace: TraceRouteMap) {
    overlays.removeAll { it is Polyline || it is Marker }

    val forwardSegments =
        buildSegments(
            trace.traceForwardList,
            TRACE_COLOR_FORWARD,
            offsetMeters = DEFAULT_SPACING_POLYLINE_TRACE,
            side = +1
        )

    val backSegments =
        buildSegments(
            trace.traceBackList,
            TRACE_COLOR_BACK,
            offsetMeters = DEFAULT_SPACING_POLYLINE_TRACE,
            side = -1
        )


    (forwardSegments + backSegments).forEach {
        overlays.add(it.toPolyline())
    }

    invalidate()
}


fun totalDistanceKm(nodes: List<Node>): Double {
    fun haversine(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0 // km
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)

        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val h = sin(dLat / 2).pow(2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2).pow(2)

        return 2 * r * asin(sqrt(h))
    }

    return nodes
        .mapNotNull { node ->
            node.validPosition?.let { GeoPoint(node.latitude, node.longitude) } }
        .zipWithNext()
        .sumOf { (a, b) -> haversine(a, b) }
}


fun buildSegments(
    nodes: List<Node>,
    color: Int,
    offsetMeters: Double,
    side: Int
): List<MapSegment> =
    nodes.zipWithNext().mapNotNull { (a, b) ->
        if (a.validPosition == null || b.validPosition == null) return@mapNotNull null

        val rawFrom = GeoPoint(a.latitude, a.longitude)
        val rawTo = GeoPoint(b.latitude, b.longitude)

        // normalizza: sempre da sud a nord (o qualsiasi criterio stabile)
        val (from, to) =
            if (rawFrom.latitude < rawTo.latitude) rawFrom to rawTo
            else rawTo to rawFrom

        val brg = bearing(from, to)
        val perpendicular = brg + (90 * side)

        MapSegment(
            from = rawFrom.offsetMeters(offsetMeters, perpendicular),
            to = rawTo.offsetMeters(offsetMeters, perpendicular),
            lineColor = color
        )
    }


fun GeoPoint.offset(latOffset: Double, lonOffset: Double) =
    GeoPoint(latitude + latOffset, longitude + lonOffset)


private fun Context.purgeTileSource(onResult: (String) -> Unit) {
    val cache = SqlTileWriterExt()
    val builder = MaterialAlertDialogBuilder(this)
    builder.setTitle(R.string.map_tile_source)
    val sources = cache.sources
    val sourceList = mutableListOf<String>()
    for (i in sources.indices) {
        sourceList.add(sources[i].source as String)
    }
    val selected: BooleanArray? = null
    val selectedList = mutableListOf<Int>()
    builder.setMultiChoiceItems(
        sourceList.toTypedArray(),
        selected
    ) { _, i, b ->
        if (b) {
            selectedList.add(i)
        } else {
            selectedList.remove(i)
        }
    }
    builder.setPositiveButton(R.string.clear) { _, _ ->
        for (x in selectedList) {
            val item = sources[x]
            val b = cache.purgeCache(item.source)
            onResult(
                if (b) {
                    getString(R.string.map_purge_success, item.source)
                } else {
                    getString(R.string.map_purge_fail)
                }
            )
        }
    }
    builder.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
    builder.show()
}

@Composable
fun MapView(
    model: UIViewModel = viewModel(),
) {
    val mapMode by model.mapMode.collectAsStateWithLifecycle()

    val totalKm: Double? = when (val mode = mapMode) {
        is MapMode.Traceroute -> {
            totalDistanceKm(mode.trace.traceForwardList) +
                    totalDistanceKm(mode.trace.traceBackList)
        }
        else -> null
    }

    // UI Elements
    var cacheEstimate by remember { mutableStateOf("") }

    var zoomLevelMin by remember { mutableDoubleStateOf(0.0) }
    var zoomLevelMax by remember { mutableDoubleStateOf(0.0) }

    // Map Elements
    var downloadRegionBoundingBox: BoundingBox? by remember { mutableStateOf(null) }
    var myLocationOverlay: MyLocationNewOverlay? by remember { mutableStateOf(null) }

    var showDownloadButton: Boolean by remember { mutableStateOf(false) }
    var showEditWaypointDialog by remember { mutableStateOf<Waypoint?>(null) }
    var showCurrentCacheInfo by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val density = LocalDensity.current

    val haptic = LocalHapticFeedback.current
    fun performHapticFeedback() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)

    val hasGps = remember { context.hasGps() }

    fun loadOnlineTileSourceBase(): ITileSource {
        val id = model.mapStyleId
        debug("mapStyleId from prefs: $id")
        return CustomTileSource.getTileSource(id).also {
            zoomLevelMax = it.maximumZoomLevel.toDouble()
            showDownloadButton =
                if (it is OnlineTileSourceBase) it.tileSourcePolicy.acceptsBulkDownload() else false
        }
    }

    val cameraView = remember {
        val geoPoints = model.nodesWithPosition.map { GeoPoint(it.latitude, it.longitude) }
        BoundingBox.fromGeoPoints(geoPoints)
    }
    val map = rememberMapViewWithLifecycle(cameraView, loadOnlineTileSourceBase())

    val nodeClusterer = remember { RadiusMarkerClusterer(context) }

    fun MapView.toggleMyLocation() {
        if (context.gpsDisabled()) {
            debug("Telling user we need location turned on for MyLocationNewOverlay")
            model.showSnackbar(R.string.location_disabled)
            return
        }
        debug("user clicked MyLocationNewOverlay ${myLocationOverlay == null}")
        if (myLocationOverlay == null) {
            myLocationOverlay = MyLocationNewOverlay(this).apply {
                enableMyLocation()
                enableFollowLocation()
                getBitmapFromVectorDrawable(context, R.drawable.ic_map_location_dot_24)?.let {
                    setPersonIcon(it)
                    setPersonAnchor(0.5f, 0.5f)
                }
                getBitmapFromVectorDrawable(context, R.drawable.ic_map_navigation_24)?.let {
                    setDirectionIcon(it)
                    setDirectionAnchor(0.5f, 0.5f)
                }
            }
            overlays.add(myLocationOverlay)
        } else {
            myLocationOverlay?.apply {
                disableMyLocation()
                disableFollowLocation()
            }
            overlays.remove(myLocationOverlay)
            myLocationOverlay = null
        }
    }

    val requestPermissionAndToggleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.all { it.value }) map.toggleMyLocation()
        }

    val nodes by model.nodeList.collectAsStateWithLifecycle()

    val visibleNodes: Collection<Node> = when (val mode = mapMode) {
        is MapMode.Normal -> nodes
        is MapMode.Traceroute -> {
            (mode.trace.traceForwardList + mode.trace.traceBackList)
                .distinctBy { it.num }
        }
    }

    val waypoints by model.waypoints.collectAsStateWithLifecycle(emptyMap())

    val markerIcon = remember {
        AppCompatResources.getDrawable(context, R.drawable.ic_baseline_location_on_24)
    }

    fun MapView.onNodesChanged(nodes: Collection<Node>): List<MarkerWithLabel> {
        val nodesWithPosition = nodes.filter { it.validPosition != null }
        val ourNode = model.ourNodeInfo.value

        val gpsFormat = ApiUtil.safeGpsFormat(model.config.display.gpsFormat)
        val displayUnits = model.config.display.units.number
        return nodesWithPosition.map { node ->
            val (p, u) = node.position to node.user
            val nodePosition = GeoPoint(node.latitude, node.longitude)
            MarkerWithLabel(
                mapView = this,
                label = "${u.shortName} ${formatAgo(p.time)}"
            ).apply {
                id = u.id
                title = "${u.longName} ${node.batteryStr}"
                snippet = node.gpsString(gpsFormat)
                ourNode?.distanceStr(node, displayUnits)?.let { dist ->
                    subDescription =
                        context.getString(R.string.map_subDescription, ourNode.bearing(node), dist)
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                position = nodePosition
                icon = markerIcon

                setOnLongClickListener{
                    performHapticFeedback()
                    model.filterForNode(node, null)
                    true
                }

                setNodeColors(node.colors)
                setPrecisionBits(p.precisionBits)
            }
        }
    }

    fun showDeleteMarkerDialog(waypoint: Waypoint) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(R.string.waypoint_delete)
        builder.setNeutralButton(R.string.cancel) { _, _ ->
            debug("User canceled marker delete dialog")
        }
        builder.setNegativeButton(R.string.delete_for_me) { _, _ ->
            debug("User deleted waypoint ${waypoint.id} for me")
            model.deleteWaypoint(waypoint.id)
        }
        if (waypoint.lockedTo in setOf(0, model.myNodeNum ?: 0) && model.isConnected()) {
            builder.setPositiveButton(R.string.delete_for_everyone) { _, _ ->
                debug("User deleted waypoint ${waypoint.id} for everyone")
                model.sendWaypoint(waypoint.copy { expire = 1 })
                model.deleteWaypoint(waypoint.id)
            }
        }
        val dialog = builder.show()
        for (button in setOf(
            AlertDialog.BUTTON_NEUTRAL,
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_POSITIVE
        )) with(dialog.getButton(button)) { textSize = 12F; isAllCaps = false }
    }

    fun showMarkerLongPressDialog(id: Int) {
        performHapticFeedback()
        debug("marker long pressed id=$id")
        val waypoint = waypoints[id]?.data?.waypoint ?: return
        // edit only when unlocked or lockedTo myNodeNum
        if (waypoint.lockedTo in setOf(0, model.myNodeNum ?: 0) && model.isConnected()) {
            showEditWaypointDialog = waypoint
        } else {
            showDeleteMarkerDialog(waypoint)
        }
    }

    fun getUsername(id: String?) = if (id == DataPacket.ID_LOCAL) {
        context.getString(R.string.you)
    } else {
        model.getUser(id).longName
    }

    fun MapView.onWaypointChanged(waypoints: Collection<Packet>): List<MarkerWithLabel> {
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        return waypoints.mapNotNull { waypoint ->
            val pt = waypoint.data.waypoint ?: return@mapNotNull null
            val lock = if (pt.lockedTo != 0) "\uD83D\uDD12" else ""
            val time = dateFormat.format(waypoint.received_time)
            val label = pt.name + " " + formatAgo((waypoint.received_time / 1000).toInt())
            val emoji = String(Character.toChars(if (pt.icon == 0) 128205 else pt.icon))
            MarkerWithLabel(this, label, emoji).apply {
                id = "${pt.id}"
                title = "${pt.name} (${getUsername(waypoint.data.from)}$lock)"
                snippet = "[$time] " + pt.description
                position = GeoPoint(pt.latitudeI * 1e-7, pt.longitudeI * 1e-7)
                setVisible(false)
                setOnLongClickListener {
                    showMarkerLongPressDialog(pt.id)
                    true
                }
            }
        }
    }

    LaunchedEffect(showCurrentCacheInfo) {
        if (!showCurrentCacheInfo) return@LaunchedEffect
        model.showSnackbar(R.string.calculating)
        val cacheManager = CacheManager(map) // Make sure CacheManager has latest from map
        val cacheCapacity = cacheManager.cacheCapacity()
        val currentCacheUsage = cacheManager.currentCacheUsage()

        val mapCacheInfoText = context.getString(
            R.string.map_cache_info,
            cacheCapacity / (1024.0 * 1024.0),
            currentCacheUsage / (1024.0 * 1024.0)
        )

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.map_cache_manager)
            .setMessage(mapCacheInfoText)
            .setPositiveButton(R.string.close) { dialog, _ ->
                showCurrentCacheInfo = false
                dialog.dismiss()
            }
            .show()
    }

    LaunchedEffect(mapMode) {
        when (val mode = mapMode) {
            is MapMode.Traceroute -> {
                map.drawTraceroute(mode.trace)

                val points =
                    buildSegments(
                        mode.trace.traceForwardList,
                        TRACE_COLOR_FORWARD,
                        DEFAULT_SPACING_POLYLINE_TRACE,
                        +1
                    )
                        .flatMap { listOf(it.from, it.to) } +
                            buildSegments(
                                mode.trace.traceBackList,
                                TRACE_COLOR_BACK,
                                DEFAULT_SPACING_POLYLINE_TRACE,
                                -1
                            )
                                .flatMap { listOf(it.from, it.to) }

                if (points.isNotEmpty()) {
                    map.zoomToBoundingBox(
                        BoundingBox.fromGeoPoints(points),
                        true
                    )
                }
            }

            MapMode.Normal -> {
                map.overlays.removeAll { it is Polyline }
                map.invalidate()
            }
        }
    }

    val mapEventsReceiver = object : MapEventsReceiver {
        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
            InfoWindow.closeAllInfoWindowsOn(map)
            return true
        }

        override fun longPressHelper(p: GeoPoint): Boolean {
            performHapticFeedback()
            val enabled = model.isConnected() && downloadRegionBoundingBox == null

            if (enabled) showEditWaypointDialog = waypoint {
                latitudeI = (p.latitude * 1e7).toInt()
                longitudeI = (p.longitude * 1e7).toInt()
            }
            return true
        }
    }

    fun MapView.drawOverlays() {
        if (overlays.none { it is MapEventsOverlay }) {
            overlays.add(0, MapEventsOverlay(mapEventsReceiver))
        }
        if (myLocationOverlay != null && overlays.none { it is MyLocationNewOverlay }) {
            overlays.add(myLocationOverlay)
        }
        if (overlays.none { it is RadiusMarkerClusterer }) {
            overlays.add(nodeClusterer)
        }

        addCopyright() // Copyright is required for certain map sources
        addScaleBarOverlay(density)
        createLatLongGrid(false)

        invalidate()
    }

    with(map) {

        var wpts = onWaypointChanged(waypoints.values)

        if(mapMode is MapMode.Traceroute){
            wpts = emptyList()
        }

        UpdateMarkers(
            onNodesChanged(visibleNodes),
            wpts,
            nodeClusterer
        )
    }

    /**
     * Creates Box overlay showing what area can be downloaded
     */
    fun MapView.generateBoxOverlay() {
        overlays.removeAll { it is Polygon }
        val zoomFactor = 1.3 // zoom difference between view and download area polygon
        zoomLevelMin = minOf(zoomLevelDouble, zoomLevelMax)
        downloadRegionBoundingBox = boundingBox.zoomIn(zoomFactor)
        val polygon = Polygon().apply {
            points = Polygon.pointsAsRect(downloadRegionBoundingBox).map {
                GeoPoint(it.latitude, it.longitude)
            }
        }
        overlays.add(polygon)
        invalidate()
        val tileCount: Int = CacheManager(this).possibleTilesInArea(
            downloadRegionBoundingBox,
            zoomLevelMin.toInt(),
            zoomLevelMax.toInt(),
        )
        cacheEstimate = context.getString(R.string.map_cache_tiles, tileCount)
    }

    val boxOverlayListener = object : MapListener {
        override fun onScroll(event: ScrollEvent): Boolean {
            if (downloadRegionBoundingBox != null) {
                event.source.generateBoxOverlay()
            }
            return true
        }

        override fun onZoom(event: ZoomEvent): Boolean {
            return false
        }
    }

    fun startDownload() {
        val boundingBox = downloadRegionBoundingBox ?: return
        try {
            val outputName = buildString {
                append(Configuration.getInstance().osmdroidBasePath.absolutePath)
                append(File.separator)
                append("mainFile.sqlite") // TODO: Accept filename input param from user
            }
            val writer = SqliteArchiveTileWriter(outputName)
            // Make sure cacheManager has latest from map
            val cacheManager = CacheManager(map, writer)
            // this triggers the download
            cacheManager.downloadAreaAsync(
                context,
                boundingBox,
                zoomLevelMin.toInt(),
                zoomLevelMax.toInt(),
                cacheManagerCallback(
                    onTaskComplete = {
                        model.showSnackbar(R.string.map_download_complete)
                        writer.onDetach()
                    },
                    onTaskFailed = { errors ->
                        model.showSnackbar(context.getString(R.string.map_download_errors, errors))
                        writer.onDetach()
                    },
                )
            )
        } catch (ex: TileSourcePolicyException) {
            debug("Tile source does not allow archiving: ${ex.message}")
        } catch (ex: Exception) {
            debug("Tile source exception: ${ex.message}")
        }
    }

    fun showMapStyleDialog() {
        val builder = MaterialAlertDialogBuilder(context)
        val mapStyles: Array<CharSequence> = CustomTileSource.mTileSources.values.toTypedArray()

        val mapStyleInt = model.mapStyleId
        builder.setSingleChoiceItems(mapStyles, mapStyleInt) { dialog, which ->
            debug("Set mapStyleId pref to $which")
            model.mapStyleId = which
            dialog.dismiss()
            map.setTileSource(loadOnlineTileSourceBase())
        }
        val dialog = builder.create()
        dialog.show()
    }

    fun Context.showCacheManagerDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.map_offline_manager)
            .setItems(
                arrayOf<CharSequence>(
                    getString(R.string.map_cache_size),
                    getString(R.string.map_download_region),
                    getString(R.string.map_clear_tiles),
                    getString(R.string.cancel)
                )
            ) { dialog, which ->
                when (which) {
                    0 -> showCurrentCacheInfo = true
                    1 -> {
                        map.generateBoxOverlay()
                        dialog.dismiss()
                    }

                    2 -> purgeTileSource { model.showSnackbar(it) }
                    else -> dialog.dismiss()
                }
            }.show()
    }

    Scaffold(
        floatingActionButton = {
            DownloadButton(showDownloadButton && downloadRegionBoundingBox == null) {
                context.showCacheManagerDialog()
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AndroidView(
                factory = {
                    map.apply {
                        setDestroyMode(false) // keeps map instance alive when in the background
                        addMapListener(boxOverlayListener)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { map -> map.drawOverlays() },
            )

            if (mapMode is MapMode.Traceroute && totalKm != null) {
                TracerouteLegend(
                    totalKm = totalKm,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }

            if (downloadRegionBoundingBox != null) CacheLayout(
                cacheEstimate = cacheEstimate,
                onExecuteJob = { startDownload() },
                onCancelDownload = {
                    downloadRegionBoundingBox = null
                    map.overlays.removeAll { it is Polygon }
                    map.invalidate()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) else Column(
                modifier = Modifier
                    .padding(top = 16.dp, end = 16.dp)
                    .align(Alignment.TopEnd),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapButton(
                    onClick = ::showMapStyleDialog,
                    icon = Icons.Outlined.Layers,
                    contentDescription = R.string.map_style_selection,
                )
                MapButton(
                    enabled = hasGps,
                    icon = if (myLocationOverlay == null) {
                        Icons.Outlined.MyLocation
                    } else {
                        Icons.Default.LocationDisabled
                    },
                    contentDescription = null,
                ) {
                    if (context.hasLocationPermission()) {
                        map.toggleMyLocation()
                    } else {
                        requestPermissionAndToggleLauncher.launch(context.getLocationPermissions())
                    }
                }

                if(mapMode is MapMode.Traceroute){

                    val trace = (mapMode as MapMode.Traceroute).trace.sourceTrace

                    MapButton(
                        icon = Icons.Default.Clear,
                        onClick = {
                            model.exitTracerouteMode()
                        },
                        contentDescription = "Clear Traceroute",
                    )

                    MapButton(
                        icon = Icons.Default.Route,
                        onClick = {

                            val coloredResponse = colorizeTracerouteResponse(trace)
                            val dialog = MaterialAlertDialogBuilder(context)
                                .setCancelable(false)
                                .setTitle(R.string.traceroute)
                                .setMessage(coloredResponse)
                                .setPositiveButton(R.string.okay) { _, _ -> }
                                .show()

                            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                ?.setTextColor(ContextCompat.getColor(context, R.color.colorAnnotation))

                            model.clearTracerouteResponse()
                        },
                        contentDescription = "Show Traceroute",
                    )

                }
            }
        }
    }

    if (showEditWaypointDialog != null) {
        EditWaypointDialog(
            waypoint = showEditWaypointDialog ?: return,
            onSendClicked = { waypoint ->
                debug("User clicked send waypoint ${waypoint.id}")
                showEditWaypointDialog = null
                model.sendWaypoint(waypoint.copy {
                    if (id == 0) id = model.generatePacketId() ?: return@EditWaypointDialog
                    expire = Int.MAX_VALUE // TODO add expire picker
                    lockedTo = if (waypoint.lockedTo != 0) model.myNodeNum ?: 0 else 0
                })
            },
            onDeleteClicked = { waypoint ->
                debug("User clicked delete waypoint ${waypoint.id}")
                showEditWaypointDialog = null
                showDeleteMarkerDialog(waypoint)
            },
            onDismissRequest = {
                debug("User clicked cancel marker edit dialog")
                showEditWaypointDialog = null
            },
        )
    }
}

@Composable
fun TracerouteLegend(
    totalKm: Double,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LegendRow(
            color = androidx.compose.ui.graphics.Color(TRACE_COLOR_FORWARD),
            label = "Forward"
        )
        LegendRow(
            color = androidx.compose.ui.graphics.Color(TRACE_COLOR_BACK),
            label = "Backward"
        )

        Spacer(modifier = Modifier.padding(top = 4.dp))

        androidx.compose.material.Text(
            text = "Total: %.1f km".format(totalKm),
            color = androidx.compose.ui.graphics.Color.White,
            style = androidx.compose.material.MaterialTheme.typography.caption
        )
    }
}


@Composable
private fun LegendRow(
    color: androidx.compose.ui.graphics.Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
        androidx.compose.material.Text(
            text = label,
            color = androidx.compose.ui.graphics.Color.White,
            style = androidx.compose.material.MaterialTheme.typography.caption
        )
    }
}
