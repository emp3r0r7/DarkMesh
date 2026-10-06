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

package com.geeksville.mesh.ui.discovery

import com.geeksville.mesh.discovery.DiscoveryScanState
import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.util.toDistanceString
import org.meshtastic.proto.ConfigProtos.Config.DisplayConfig
import java.util.Locale
import kotlin.math.roundToInt

internal fun DiscoveryScanState.statusText(): String = when (this) {
    DiscoveryScanState.Idle -> "Idle"
    DiscoveryScanState.Preparing -> "Preparing"
    is DiscoveryScanState.SwitchingPreset -> "Switching to $presetName"
    is DiscoveryScanState.WaitingForRadio -> "Waiting for radio on $presetName"
    is DiscoveryScanState.Dwelling -> "Dwelling on $presetName"
    is DiscoveryScanState.CollectingResult -> "Saving $presetName"
    DiscoveryScanState.Analyzing -> "Analyzing"
    DiscoveryScanState.Restoring -> "Restoring"
    is DiscoveryScanState.Complete -> "Complete"
    DiscoveryScanState.Cancelling -> "Cancelling"
    is DiscoveryScanState.Failed -> "Failed: $message"
}

internal fun ChannelOption.displayName(): String {
    return name.lowercase()
        .split("_")
        .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
}

internal fun Float?.formatDb(): String {
    return this?.let { "${(it * DECIMAL_SCALE).roundToInt() / DECIMAL_SCALE} dB" } ?: "-"
}

internal fun Float?.formatRssi(): String {
    return this?.let { "${it.roundToInt()} dBm" } ?: "-"
}

internal fun Float?.formatPercent(): String {
    return this?.let { String.format(Locale.getDefault(), "%.1f%%", it) } ?: "-"
}

internal fun Int?.formatCount(): String {
    return this?.toString() ?: "-"
}

internal fun Int?.formatDistanceMeters(displayUnits: Int): String? {
    val meters = this?.takeIf { it > 0 } ?: return null
    val system = DisplayConfig.DisplayUnits.forNumber(displayUnits)
        ?: DisplayConfig.DisplayUnits.METRIC
    return meters.toDistanceString(system)
}

internal fun Long.formatDiscoveryDuration(): String {
    val minutes = this / SECONDS_PER_MINUTE
    val seconds = this % SECONDS_PER_MINUTE
    return when {
        minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

internal fun formatDiscoveryRate(successCount: Int?, failureCount: Int?): String {
    val counts = successCount?.let { success ->
        failureCount?.let { failure -> success to success + failure }
    }
    return counts?.takeIf { (_, total) -> total != 0 }?.let { (success, total) ->
        String.format(Locale.getDefault(), "%.1f%%", success * PERCENT_SCALE / total)
    } ?: "-"
}

private const val DECIMAL_SCALE = 10f
private const val SECONDS_PER_MINUTE = 60
private const val PERCENT_SCALE = 100f
