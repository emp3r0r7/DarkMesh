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

import com.geeksville.mesh.database.entity.DiscoveryPresetResultEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryRankingEngineTest {
    private val rankingEngine = DiscoveryRankingEngine()

    @Test
    fun ranksByNodesThenDirectNeighborsThenTrafficThenSignal() {
        val results = listOf(
            result("LONG_FAST", uniqueNodes = 3, directNeighbors = 3, packetCount = 1, snr = -3f),
            result("MEDIUM_FAST", uniqueNodes = 4, directNeighbors = 0, packetCount = 1, snr = -20f),
            result("SHORT_FAST", uniqueNodes = 3, directNeighbors = 2, packetCount = 20, snr = 10f),
        )

        val ranked = rankingEngine.rank(results)

        assertEquals(listOf("MEDIUM_FAST", "LONG_FAST", "SHORT_FAST"), ranked.map { it.presetName })
    }

    @Test
    fun tiesAreDeterministicByPresetName() {
        val results = listOf(
            result("LONG_FAST", uniqueNodes = 2, directNeighbors = 1, packetCount = 2, snr = -1f),
            result("MEDIUM_FAST", uniqueNodes = 2, directNeighbors = 1, packetCount = 2, snr = -1f),
        )

        val ranked = rankingEngine.rank(results)

        assertEquals(listOf("LONG_FAST", "MEDIUM_FAST"), ranked.map { it.presetName })
    }

    private fun result(
        presetName: String,
        uniqueNodes: Int,
        directNeighbors: Int,
        packetCount: Int,
        snr: Float?,
    ) = DiscoveryPresetResultEntity(
        sessionId = 1,
        presetName = presetName,
        modemPresetValue = 1,
        startedAt = 1,
        endedAt = 2,
        dwellSeconds = 1,
        uniqueNodes = uniqueNodes,
        directNeighbors = directNeighbors,
        meshNeighbors = 0,
        messageCount = 0,
        sensorCount = 0,
        infrastructureCount = 0,
        packetCount = packetCount,
        averageSnr = snr,
    )
}
