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

sealed class DiscoveryScanState {
    data object Idle : DiscoveryScanState()
    data object Preparing : DiscoveryScanState()
    data class SwitchingPreset(val presetName: String) : DiscoveryScanState()
    data class WaitingForRadio(val presetName: String) : DiscoveryScanState()
    data class Dwelling(
        val presetName: String,
        val remainingSeconds: Long,
        val totalSeconds: Long,
    ) : DiscoveryScanState()
    data class CollectingResult(val presetName: String) : DiscoveryScanState()
    data object Analyzing : DiscoveryScanState()
    data object Restoring : DiscoveryScanState()
    data class Complete(val sessionId: Long) : DiscoveryScanState()
    data object Cancelling : DiscoveryScanState()
    data class Failed(val message: String) : DiscoveryScanState()

    val isRunning: Boolean
        get() = this !is Idle && this !is Complete && this !is Failed
}
