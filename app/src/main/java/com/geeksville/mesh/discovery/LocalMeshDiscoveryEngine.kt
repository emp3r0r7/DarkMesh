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

import android.os.RemoteException
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.NodeRegistryRepository
import com.geeksville.mesh.database.dao.DiscoveryDao
import com.geeksville.mesh.database.entity.DiscoveryPresetResultEntity
import com.geeksville.mesh.database.entity.DiscoverySessionEntity
import com.geeksville.mesh.database.entity.DiscoverySessionStatus
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.model.ModemPresetCapabilities
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.service.MeshService.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.proto.ChannelProtos
import org.meshtastic.proto.ConfigProtos
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
@Suppress("TooManyFunctions")
class LocalMeshDiscoveryEngine @Inject constructor(
    private val radioConfigRepository: RadioConfigRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val nodeRegistryRepository: NodeRegistryRepository,
    private val discoveryDao: DiscoveryDao,
    private val rankingEngine: DiscoveryRankingEngine,
    dispatchers: CoroutineDispatchers,
) : Logging {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val collectorMutex = Mutex()

    private val _scanState = MutableStateFlow<DiscoveryScanState>(DiscoveryScanState.Idle)
    val scanState = _scanState.asStateFlow()

    private val _currentSession = MutableStateFlow<DiscoverySessionEntity?>(null)
    val currentSession = _currentSession.asStateFlow()

    private val _rankings = MutableStateFlow<List<DiscoveryPresetRank>>(emptyList())
    val rankings = _rankings.asStateFlow()

    /** Current-device HOME, unavailable until connection and authoritative LoRa config agree. */
    val discoveryHome: StateFlow<DiscoveryHomeState?> = combine(
        radioConfigRepository.connectionState,
        radioConfigRepository.localConfigFlow,
    ) { connectionState, localConfig ->
        resolveDiscoveryHome(connectionState, localConfig, radioInterfaceService.getDeviceAddress())
    }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Discovery is actionable only when current-device HOME is authoritative. */
    val discoveryReady: StateFlow<Boolean> = discoveryHome
        .map { it != null }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /** Shared device/region capability authority; HOME remains independent of this flow. */
    val modemPresetCapabilities: StateFlow<ModemPresetCapabilities> =
        radioConfigRepository.modemPresetCapabilities
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = ModemPresetCapabilities.unknown(),
            )

    private var scanJob: Job? = null
    private var packetCollectionJob: Job? = null
    private var recoveryWatcherStarted = false
    private var stopRequested = false
    private var activeDwell: ActiveDwell? = null

    fun startRecoveryWatcher() {
        if (recoveryWatcherStarted) return
        recoveryWatcherStarted = true

        scope.launch {
            discoveryDao.markInProgressSessionsInterrupted()
            radioConfigRepository.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) restoreRecoverableSessionIfAny()
            }
        }
    }

    fun startScan(targets: Set<ChannelOption>, dwellSeconds: Long) {
        if (scanJob?.isActive == true) return

        stopRequested = false
        scanJob = scope.launch {
            runCatching {
                runScan(targets.toList(), dwellSeconds)
            }.onFailure { error ->
                if (error is CancellationException && stopRequested) return@onFailure
                errormsg("Discovery scan failed:", error)
                _scanState.value = DiscoveryScanState.Failed(error.message ?: "Discovery scan failed")
            }
        }
    }

    fun stopScan() {
        if (scanJob?.isActive != true) return
        stopRequested = true
        _scanState.value = DiscoveryScanState.Cancelling
        scanJob?.cancel(CancellationException("Discovery stopped"))
    }

    suspend fun buildDiscoveryMap(
        presetResultId: Long,
        nodeClass: DiscoveryNodeClass,
    ): DiscoveryMap? {
        val result = discoveryDao.getPresetResult(presetResultId)
        val nodes = discoveryDao.getDiscoveredNodes(presetResultId)
        val localNodeNum = radioConfigRepository.myNodeInfo.value?.myNodeNum?.toLong()
        val knownNodeByNum = radioConfigRepository.nodeDBbyNum.value
        val session = result?.sessionId?.let { discoveryDao.getSession(it) }
        val localMapNode = localNodeForDiscovery(localNodeNum, session)
        val originNodeNum = localNodeNum ?: localMapNode?.num?.toLong()
        return DiscoveryMapBuilder.build(
            nodes = nodes,
            localNodeNum = originNodeNum,
            localNode = localMapNode,
            presetName = result?.presetName.orEmpty(),
            knownNodeByNum = knownNodeByNum,
            nodeClass = nodeClass,
        )
    }

    suspend fun buildDiscoveryNodeList(
        presetResultId: Long,
        nodeClass: DiscoveryNodeClass,
    ): DiscoveryNodeList? {
        val result = discoveryDao.getPresetResult(presetResultId) ?: return null
        val nodes = discoveryDao.getDiscoveredNodes(presetResultId)
        val localNodeNum = radioConfigRepository.myNodeInfo.value?.myNodeNum?.toLong()
        val session = discoveryDao.getSession(result.sessionId)
        val localMapNode = localNodeForDiscovery(localNodeNum, session)
        return DiscoveryNodeListBuilder.build(
            presetName = result.presetName,
            nodes = nodes,
            localNode = localMapNode,
            localNodeNum = localNodeNum ?: localMapNode?.num?.toLong(),
            knownNodeByNum = radioConfigRepository.nodeDBbyNum.value,
            nodeClass = nodeClass,
        )
    }

    suspend fun buildDiscoveryReport(sessionId: Long): DiscoveryReport? {
        val session = discoveryDao.getSession(sessionId) ?: return null
        val localNodeNum = radioConfigRepository.myNodeInfo.value?.myNodeNum?.toLong()
        val localMapNode = localNodeForDiscovery(localNodeNum, session)
        val originNodeNum = localNodeNum ?: localMapNode?.num?.toLong()
        val presetResults = discoveryDao.getPresetResults(sessionId)
        val rankedResults = rankingEngine.rank(presetResults).mapIndexed { index, rank ->
            rank.presetResultId to (index + 1)
        }.toMap()
        val presets = presetResults.map { result ->
            val nodes = discoveryDao.getDiscoveredNodes(result.id)
            val neighborList = DiscoveryNodeListBuilder.build(
                presetName = result.presetName,
                nodes = nodes,
                localNode = localMapNode,
                localNodeNum = originNodeNum,
                knownNodeByNum = radioConfigRepository.nodeDBbyNum.value,
                nodeClass = DiscoveryNodeClass.NEIGHBOR,
            )
            val networkList = DiscoveryNodeListBuilder.build(
                presetName = result.presetName,
                nodes = nodes,
                localNode = localMapNode,
                localNodeNum = originNodeNum,
                knownNodeByNum = radioConfigRepository.nodeDBbyNum.value,
                nodeClass = DiscoveryNodeClass.NETWORK,
            )
            DiscoveryPresetReport(
                result = result,
                rank = rankedResults[result.id] ?: 0,
                neighborList = neighborList,
                networkList = networkList,
                unknownNodes = nodes.countDiscoveryNodes(DiscoveryNodeClass.UNKNOWN),
            )
        }.sortedWith(
            compareBy<DiscoveryPresetReport> { if (it.rank == 0) Int.MAX_VALUE else it.rank }
                .thenBy { it.result.presetName }
        )

        return DiscoveryReport(session = session, presets = presets)
    }

    suspend fun deleteDiscoverySessions(sessionIds: Set<Long>) {
        if (sessionIds.isEmpty()) return
        discoveryDao.deleteSessions(sessionIds.toList())
        if (_currentSession.value?.id in sessionIds) {
            _currentSession.value = null
            _rankings.value = emptyList()
            if (!_scanState.value.isRunning) _scanState.value = DiscoveryScanState.Idle
        }
    }

    @Suppress("LongMethod", "TooGenericExceptionCaught")
    private suspend fun runScan(targets: List<ChannelOption>, dwellSeconds: Long) {
        require(targets.isNotEmpty()) { "Select at least one preset" }
        require(dwellSeconds > 0) { "Dwell time must be greater than zero" }
        requireDiscoveryTargetsSupported(targets, modemPresetCapabilities.value)

        _scanState.value = DiscoveryScanState.Preparing
        restoreRecoverableSessionIfAny()
        waitForRadioReady("home")
        val homeSnapshot = readHomeSnapshot(targets, dwellSeconds)
        val sessionId = discoveryDao.insertSession(homeSnapshot.session)
        val session = homeSnapshot.session.copy(id = sessionId)
        _currentSession.value = session
        _rankings.value = emptyList()

        packetCollectionJob = scope.launch {
            radioConfigRepository.meshPacketFlow.collect { packet ->
                collectorMutex.withLock {
                    activeDwell?.collector?.ingest(packet)
                }
            }
        }

        try {
            targets.forEach { target ->
                coroutineContext.ensureActive()
                switchPreset(target, homeSnapshot.homeLoraConfig)
                startDwell(sessionId, target, dwellSeconds, homeSnapshot)
                requestDiscoveryPackets()
                runDwell(target.name, dwellSeconds)
                persistCurrentDwell()
                analyzeSession(sessionId)
            }

            _scanState.value = DiscoveryScanState.Analyzing
            analyzeSession(sessionId)
            _scanState.value = DiscoveryScanState.Restoring
            restoreSessionHome(session, DiscoverySessionStatus.COMPLETE, null)
            _scanState.value = DiscoveryScanState.Complete(sessionId)
        } catch (error: CancellationException) {
            if (!stopRequested) throw error
            withContext(NonCancellable) {
                _scanState.value = DiscoveryScanState.Cancelling
                persistCurrentDwell()
                analyzeSession(sessionId)
                _scanState.value = DiscoveryScanState.Restoring
                runCatching {
                    restoreSessionHome(session, DiscoverySessionStatus.RESTORED, "Stopped by user")
                }.onFailure {
                    errormsg("Discovery restore after stop failed:", it)
                    updateSessionStatus(sessionId, DiscoverySessionStatus.RESTORE_FAILED, it.message)
                    _scanState.value = DiscoveryScanState.Failed("Discovery stopped, restore failed")
                    return@withContext
                }
                _scanState.value = DiscoveryScanState.Complete(sessionId)
            }
        } catch (error: Throwable) {
            errormsg("Discovery scan error:", error)
            persistCurrentDwell()
            analyzeSession(sessionId)
            updateSessionStatus(sessionId, DiscoverySessionStatus.FAILED, error.message)
            _scanState.value = DiscoveryScanState.Restoring
            runCatching {
                restoreSessionHome(session, DiscoverySessionStatus.RESTORED, error.message)
            }.onFailure {
                errormsg("Discovery restore after failure failed:", it)
                updateSessionStatus(sessionId, DiscoverySessionStatus.RESTORE_FAILED, it.message)
                _scanState.value = DiscoveryScanState.Failed("Discovery failed, restore failed")
                return
            }
            _scanState.value = DiscoveryScanState.Failed(error.message ?: "Discovery scan failed")
        } finally {
            withContext(NonCancellable) {
                collectorMutex.withLock { activeDwell = null }
                packetCollectionJob?.cancelAndJoin()
                packetCollectionJob = null
                stopRequested = false
            }
        }
    }

    private suspend fun readHomeSnapshot(
        targets: List<ChannelOption>,
        dwellSeconds: Long,
    ): HomeSnapshot {
        val localConfig = radioConfigRepository.localConfigFlow.first { it.hasLora() }
        val homeLora = localConfig.lora
        val homePrimaryChannel = radioConfigRepository.channelSetFlow.first().settingsList.firstOrNull()
        val myNodeNum = radioConfigRepository.myNodeInfo.value?.myNodeNum
        val localNode = myNodeNum?.let { radioConfigRepository.nodeDBbyNum.value[it] }
        val originLatitude = localNode?.effectiveLatitude?.takeIf { it != ZERO_COORDINATE }
        val originLongitude = localNode?.effectiveLongitude?.takeIf { it != ZERO_COORDINATE }

        return HomeSnapshot(
            homeLoraConfig = homeLora,
            homePrimaryChannel = homePrimaryChannel,
            localNodeNum = myNodeNum,
            originLatitude = originLatitude,
            originLongitude = originLongitude,
            session = DiscoverySessionEntity(
                presetsScanned = targets.joinToString(",") { it.name },
                homePreset = Channel(loraConfig = homeLora).name,
                totalDwellSeconds = dwellSeconds * targets.size,
                deviceAddress = radioInterfaceService.getDeviceAddress(),
                userLatitude = originLatitude,
                userLongitude = originLongitude,
                homeLoraConfig = homeLora,
                homePrimaryChannel = homePrimaryChannel,
            ),
        )
    }

    private suspend fun switchPreset(
        target: ChannelOption,
        homeLoraConfig: ConfigProtos.Config.LoRaConfig,
    ) {
        requireDiscoveryTargetsSupported(listOf(target), modemPresetCapabilities.value)
        _scanState.value = DiscoveryScanState.SwitchingPreset(target.name)
        val scanLora = homeLoraConfig.toBuilder()
            .setUsePreset(true)
            .setModemPreset(target.modemPreset)
            .setChannelNum(0)
            .build()

        if (applyLoRaConfigIfChanged(scanLora, target.name)) {
            waitForRadioRestart(target.name)
        } else {
            waitForRadioReady(target.name)
        }
    }

    private suspend fun startDwell(
        sessionId: Long,
        target: ChannelOption,
        dwellSeconds: Long,
        homeSnapshot: HomeSnapshot,
    ) {
        val registrySnapshot = nodeRegistryRepository.getAllNodes()
            .first()
            .associateBy { it.nodeId }

        val collector = DiscoveryPacketCollector(
            localNodeNum = homeSnapshot.localNodeNum,
            nodeSnapshot = radioConfigRepository.nodeDBbyNum.value,
            registrySnapshot = registrySnapshot,
            originLatitude = homeSnapshot.originLatitude,
            originLongitude = homeSnapshot.originLongitude,
        )

        collectorMutex.withLock {
            activeDwell = ActiveDwell(
                sessionId = sessionId,
                target = target,
                startedAt = System.currentTimeMillis(),
                dwellSeconds = dwellSeconds,
                collector = collector,
            )
        }
    }

    private suspend fun runDwell(presetName: String, dwellSeconds: Long) {
        var remainingSeconds = dwellSeconds
        while (remainingSeconds > 0) {
            coroutineContext.ensureActive()
            if (radioConfigRepository.connectionState.value != ConnectionState.CONNECTED) {
                waitForRadioReady(presetName)
            }
            _scanState.value = DiscoveryScanState.Dwelling(
                presetName = presetName,
                remainingSeconds = remainingSeconds,
                totalSeconds = dwellSeconds,
            )
            delay(ONE_SECOND_MS)
            remainingSeconds -= 1
        }
    }

    private suspend fun persistCurrentDwell() {
        val captured = collectorMutex.withLock {
            activeDwell?.let { dwell ->
                activeDwell = null
                dwell to dwell.collector.snapshot()
            }
        } ?: return

        val (dwell, snapshot) = captured
        _scanState.value = DiscoveryScanState.CollectingResult(dwell.target.name)
        val endedAt = System.currentTimeMillis()
        val nodes = snapshot.nodes
        val baseResult = DiscoveryPresetResultEntity(
            sessionId = dwell.sessionId,
            presetName = dwell.target.name,
            modemPresetValue = dwell.target.modemPreset.number,
            startedAt = dwell.startedAt,
            endedAt = endedAt,
            dwellSeconds = ((endedAt - dwell.startedAt) / ONE_SECOND_MS).coerceAtLeast(1),
            uniqueNodes = nodes.size,
            directNeighbors = nodes.countDiscoveryNodes(DiscoveryNodeClass.NEIGHBOR),
            meshNeighbors = nodes.countDiscoveryNodes(DiscoveryNodeClass.NETWORK),
            messageCount = nodes.sumOf { it.messageCount },
            sensorCount = nodes.sumOf { it.sensorPacketCount },
            infrastructureCount = nodes.count { it.isInfrastructure },
            packetCount = snapshot.packetCount,
            averageSnr = nodes.mapNotNull { it.snr }.averageOrNull(),
            averageRssi = nodes.mapNotNull { it.rssi?.toFloat() }.averageOrNull(),
            averageChannelUtilization = snapshot.deviceMetrics.takeIf { it.size >= DEVICE_METRICS_MIN_SAMPLES }
                ?.map { it.channelUtilization }
                ?.averageOrNull(),
            averageAirUtilTx = snapshot.deviceMetrics.takeIf { it.size >= DEVICE_METRICS_MIN_SAMPLES }
                ?.map { it.airUtilTx }
                ?.averageOrNull(),
            localNumPacketsTx = snapshot.localStats?.numPacketsTx,
            localNumPacketsRx = snapshot.localStats?.numPacketsRx,
            localNumPacketsRxBad = snapshot.localStats?.numPacketsRxBad,
            localNumOnlineNodes = snapshot.localStats?.numOnlineNodes,
        )
        val result = baseResult.copy(rankingScore = rankingEngine.score(baseResult))
        discoveryDao.insertPresetResultWithNodes(result, nodes)
    }

    private suspend fun analyzeSession(sessionId: Long) {
        val presetResults = discoveryDao.getPresetResults(sessionId)
        val discoveredNodes = discoveryDao.getDiscoveredNodesForSession(sessionId)
        val nodesByNum = discoveredNodes.groupBy { it.nodeNum }

        discoveryDao.updateSessionAggregates(
            sessionId = sessionId,
            uniqueNodes = nodesByNum.size,
            directNeighbors = nodesByNum.values.count { nodes ->
                nodes.any { it.discoveryNodeClass() == DiscoveryNodeClass.NEIGHBOR }
            },
            meshNeighbors = nodesByNum.values.count { nodes ->
                nodes.none { it.discoveryNodeClass() == DiscoveryNodeClass.NEIGHBOR } &&
                    nodes.any { it.discoveryNodeClass() == DiscoveryNodeClass.NETWORK }
            },
            messageCount = presetResults.sumOf { it.messageCount },
            sensorCount = presetResults.sumOf { it.sensorCount },
            infrastructureCount = nodesByNum.values.count { nodes ->
                nodes.any { it.isInfrastructure }
            },
        )
        _currentSession.value = discoveryDao.getSession(sessionId) ?: _currentSession.value
        _rankings.value = rankingEngine.rank(presetResults)
    }

    private suspend fun restoreRecoverableSessionIfAny() {
        if (scanJob?.isActive == true) return

        val session = radioInterfaceService.getDeviceAddress()?.let { address ->
            discoveryDao.getLatestRecoverableSession(address)
        } ?: discoveryDao.getLatestRecoverableSession()

        session ?: return
        _currentSession.value = session
        _scanState.value = DiscoveryScanState.Restoring
        runCatching {
            restoreSessionHome(session, DiscoverySessionStatus.RESTORED, session.failureMessage)
        }.onFailure {
            errormsg("Discovery interrupted restore failed:", it)
            updateSessionStatus(session.id, DiscoverySessionStatus.RESTORE_FAILED, it.message)
            _scanState.value = DiscoveryScanState.Failed("Discovery restore failed")
        }.onSuccess {
            _scanState.value = DiscoveryScanState.Idle
        }
    }

    private suspend fun restoreSessionHome(
        session: DiscoverySessionEntity,
        finalStatus: String,
        failureMessage: String?,
        restorePrimaryChannel: Boolean = false,
    ) {
        val homeLora = session.homeLoraConfig
        if (homeLora == null) {
            updateSessionStatus(
                session.id,
                DiscoverySessionStatus.UNRESTORABLE,
                "Missing original LoRa config snapshot",
            )
            return
        }

        updateSessionStatus(session.id, DiscoverySessionStatus.RESTORE_PENDING, failureMessage)
        if (restorePrimaryChannel && session.homePrimaryChannel != null) {
            applyPrimaryChannel(session.homePrimaryChannel)
        }
        if (applyLoRaConfigIfChanged(homeLora, "home")) {
            waitForRadioRestart("home")
        } else {
            waitForRadioReady("home")
        }
        updateSessionStatus(session.id, finalStatus, failureMessage)
    }

    private suspend fun applyLoRaConfigIfChanged(
        loraConfig: ConfigProtos.Config.LoRaConfig,
        presetName: String,
    ): Boolean {
        val currentLora = radioConfigRepository.localConfigFlow.first { it.hasLora() }.lora
        if (currentLora == loraConfig) {
            debug("Discovery LoRa config already active for $presetName; skipping config commit")
            return false
        }

        applyLoRaConfig(loraConfig)
        return true
    }

    private fun applyLoRaConfig(loraConfig: ConfigProtos.Config.LoRaConfig) {
        val service = radioConfigRepository.meshService ?: error("Mesh service is not connected")
        runRemote("begin discovery edit") { service.beginEditSettings() }
        runRemote("set discovery LoRa config") {
            service.setConfig(
                ConfigProtos.Config.newBuilder()
                    .setLora(loraConfig)
                    .build()
                    .toByteArray()
            )
        }
        runRemote("commit discovery edit") { service.commitEditSettings() }
    }

    private fun applyPrimaryChannel(primaryChannel: ChannelProtos.ChannelSettings) {
        val service = radioConfigRepository.meshService ?: error("Mesh service is not connected")
        runRemote("restore discovery primary channel") {
            service.setChannel(
                ChannelProtos.Channel.newBuilder()
                    .setIndex(PRIMARY_CHANNEL_INDEX)
                    .setSettings(primaryChannel)
                    .setRole(ChannelProtos.Channel.Role.PRIMARY)
                    .build()
                    .toByteArray()
            )
        }
    }

    private fun requestDiscoveryPackets() {
        val service = radioConfigRepository.meshService ?: return
        val localNodeNum = radioConfigRepository.myNodeInfo.value?.myNodeNum ?: return

        runCatching {
            service.requestNeighborInfo(service.nextPacketId(), localNodeNum)
            service.requestLocalStats(service.nextPacketId())
        }.onFailure {
            warn("Discovery probe request failed: ${it.message}")
        }
    }

    private suspend fun waitForRadioReady(presetName: String) {
        if (radioConfigRepository.connectionState.value != ConnectionState.CONNECTED) {
            _scanState.value = DiscoveryScanState.WaitingForRadio(presetName)
            val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                radioConfigRepository.connectionState.first { it == ConnectionState.CONNECTED }
            }
            require(connected == ConnectionState.CONNECTED) {
                "Timed out waiting for radio connection"
            }
        }
        delay(RADIO_READY_SETTLE_MS)
    }

    private suspend fun waitForRadioRestart(presetName: String) {
        val disconnected = withTimeoutOrNull(RESTART_DETECTION_TIMEOUT_MS) {
            radioConfigRepository.connectionState.first { it != ConnectionState.CONNECTED }
        }
        if (disconnected == null) {
            delay(CONFIG_SETTLE_MS)
        } else {
            waitForRadioReady(presetName)
        }
    }

    private suspend fun updateSessionStatus(
        sessionId: Long,
        status: String,
        failureMessage: String?,
    ) {
        discoveryDao.updateSessionStatus(sessionId, status, failureMessage)
        _currentSession.value = _currentSession.value?.takeIf { it.id == sessionId }?.copy(
            completionStatus = status,
            failureMessage = failureMessage,
        ) ?: _currentSession.value
    }

    private fun runRemote(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (ex: RemoteException) {
            throw IllegalStateException("Could not $operation", ex)
        }
    }

    private fun IMeshService.nextPacketId(): Int {
        return try {
            packetId
        } catch (ex: RemoteException) {
            throw IllegalStateException("Could not generate packet id", ex)
        }
    }

    private fun List<Float>.averageOrNull(): Float? {
        return takeIf { it.isNotEmpty() }?.average()?.toFloat()
    }

    private fun localNodeForDiscovery(
        localNodeNum: Long?,
        session: DiscoverySessionEntity?,
    ): Node? {
        val knownNode = localNodeNum?.let { radioConfigRepository.nodeDBbyNum.value[it.toInt()] }
        val latitude = session?.userLatitude?.takeIf { it != ZERO_COORDINATE }
        val longitude = session?.userLongitude?.takeIf { it != ZERO_COORDINATE }

        return when {
            knownNode?.validPosition != null || knownNode?.validLiteNode == true -> knownNode
            latitude != null && longitude != null -> localNodeWithSessionPosition(
                knownNode = knownNode,
                localNodeNum = localNodeNum,
                latitude = latitude,
                longitude = longitude,
            )
            else -> knownNode
        }
    }

    private fun localNodeWithSessionPosition(
        knownNode: Node?,
        localNodeNum: Long?,
        latitude: Double,
        longitude: Double,
    ): Node {
        return (knownNode ?: Node(num = localNodeNum?.toInt() ?: REPORT_LOCAL_NODE_NUM)).copy(
            liteNodeId = "local",
            liteDefaultName = "Local Mesh Discovery",
            liteLongName = "Local Mesh Discovery",
            liteShortName = "LMD",
            liteLatitude = latitude,
            liteLongitude = longitude,
        )
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 120_000L
        private const val CONFIG_SETTLE_MS = 3_000L
        private const val RESTART_DETECTION_TIMEOUT_MS = 15_000L
        private const val RADIO_READY_SETTLE_MS = 1_000L
        private const val ONE_SECOND_MS = 1_000L
        private const val DEVICE_METRICS_MIN_SAMPLES = 2
        private const val PRIMARY_CHANNEL_INDEX = 0
        private const val ZERO_COORDINATE = 0.0
        private const val REPORT_LOCAL_NODE_NUM = 0
    }
}

internal fun requireDiscoveryTargetsSupported(
    targets: Collection<ChannelOption>,
    capabilities: ModemPresetCapabilities,
) {
    val rejected = targets.filterNot { capabilities.isSelectable(it.modemPreset) }
    require(rejected.isEmpty()) {
        "Unsupported or unresolved discovery presets: ${rejected.joinToString { it.name }}"
    }
}

private data class HomeSnapshot(
    val homeLoraConfig: ConfigProtos.Config.LoRaConfig,
    val homePrimaryChannel: ChannelProtos.ChannelSettings?,
    val localNodeNum: Int?,
    val originLatitude: Double?,
    val originLongitude: Double?,
    val session: DiscoverySessionEntity,
)

private data class ActiveDwell(
    val sessionId: Long,
    val target: ChannelOption,
    val startedAt: Long,
    val dwellSeconds: Long,
    val collector: DiscoveryPacketCollector,
)
