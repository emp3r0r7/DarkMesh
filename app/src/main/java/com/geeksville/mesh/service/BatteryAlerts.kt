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

package com.geeksville.mesh.service

import android.content.SharedPreferences
import org.meshtastic.proto.TelemetryProtos

const val PREF_BATTERY_ALERTS_ENABLED = "battery-alerts-enabled"
const val PREF_BATTERY_ALERT_PERCENT_THRESHOLD = "battery-alert-percent-threshold"
const val PREF_BATTERY_ALERT_VOLTAGE_THRESHOLD = "battery-alert-voltage-threshold"
const val PREF_BATTERY_ALERT_SCOPE = "battery-alert-scope"
const val PREF_BATTERY_ALERT_CONNECTED_SOUND_URI = "battery-alert-connected-sound-uri"
const val PREF_BATTERY_ALERT_MESH_SOUND_URI = "battery-alert-mesh-sound-uri"

val BATTERY_ALERT_PREFERENCE_KEYS = setOf(
    PREF_BATTERY_ALERTS_ENABLED,
    PREF_BATTERY_ALERT_PERCENT_THRESHOLD,
    PREF_BATTERY_ALERT_VOLTAGE_THRESHOLD,
    PREF_BATTERY_ALERT_SCOPE,
    PREF_BATTERY_ALERT_CONNECTED_SOUND_URI,
    PREF_BATTERY_ALERT_MESH_SOUND_URI,
)

const val DEFAULT_BATTERY_ALERT_PERCENT_THRESHOLD = 20
const val DEFAULT_BATTERY_ALERT_VOLTAGE_THRESHOLD = 3.7f
const val MAX_BATTERY_ALERT_VOLTAGE_THRESHOLD = 6f
private const val CRITICAL_BATTERY_PERCENT_THRESHOLD = 10
private const val CRITICAL_BATTERY_VOLTAGE_THRESHOLD = 3.5f
private const val BATTERY_PERCENT_HYSTERESIS = 3
private const val BATTERY_VOLTAGE_HYSTERESIS = 0.15f

enum class BatteryAlertScope(val preferenceValue: String) {
    ALL_NODES("all_nodes"),
    CONNECTED_NODE_ONLY("connected_node_only"),
    FAVORITES_ONLY("favorites_only");

    companion object {
        fun fromPreferenceValue(value: String?): BatteryAlertScope =
            entries.firstOrNull { it.preferenceValue == value } ?: ALL_NODES
    }
}

enum class BatteryAlertSource {
    CONNECTED_NODE,
    MESH,
    MESH_FAVORITE
}

enum class BatteryAlertLevel {
    NONE,
    LOW,
    CRITICAL,
}

data class BatteryAlertSettings(
    val enabled: Boolean = false,
    val percentThreshold: Int = DEFAULT_BATTERY_ALERT_PERCENT_THRESHOLD,
    val voltageThreshold: Float = DEFAULT_BATTERY_ALERT_VOLTAGE_THRESHOLD,
    val scope: BatteryAlertScope = BatteryAlertScope.ALL_NODES,
    val connectedNodeSoundUri: String? = null,
    val meshSoundUri: String? = null,
) {
    val hasEnabledThresholds: Boolean
        get() = percentThreshold > 0 || voltageThreshold > 0f

    fun allows(source: BatteryAlertSource): Boolean = when (scope) {
        BatteryAlertScope.ALL_NODES -> true
        BatteryAlertScope.CONNECTED_NODE_ONLY -> source == BatteryAlertSource.CONNECTED_NODE
        BatteryAlertScope.FAVORITES_ONLY -> source == BatteryAlertSource.MESH_FAVORITE
    }

    fun soundUriFor(source: BatteryAlertSource): String? = when (source) {
        BatteryAlertSource.CONNECTED_NODE -> connectedNodeSoundUri
        BatteryAlertSource.MESH,
        BatteryAlertSource.MESH_FAVORITE -> meshSoundUri
    }.takeUnless { it.isNullOrBlank() }
}

data class BatterySnapshot(
    val batteryLevel: Int,
    val voltage: Float,
) {
    val hasBatteryLevel: Boolean
        get() = batteryLevel in 1..100

    val hasVoltage: Boolean
        get() = voltage > 0f
}

fun SharedPreferences.getBatteryAlertSettings(): BatteryAlertSettings = BatteryAlertSettings(
    enabled = getBoolean(PREF_BATTERY_ALERTS_ENABLED, false),
    percentThreshold = getInt(
        PREF_BATTERY_ALERT_PERCENT_THRESHOLD,
        DEFAULT_BATTERY_ALERT_PERCENT_THRESHOLD
    ).coerceIn(0, 100),
    voltageThreshold = getFloat(
        PREF_BATTERY_ALERT_VOLTAGE_THRESHOLD,
        DEFAULT_BATTERY_ALERT_VOLTAGE_THRESHOLD
    ).coerceIn(0f, MAX_BATTERY_ALERT_VOLTAGE_THRESHOLD),
    scope = BatteryAlertScope.fromPreferenceValue(
        getString(PREF_BATTERY_ALERT_SCOPE, BatteryAlertScope.ALL_NODES.preferenceValue)
    ),
    connectedNodeSoundUri = getString(PREF_BATTERY_ALERT_CONNECTED_SOUND_URI, null),
    meshSoundUri = getString(PREF_BATTERY_ALERT_MESH_SOUND_URI, null),
)

fun TelemetryProtos.DeviceMetrics.toBatterySnapshot(): BatterySnapshot = BatterySnapshot(
    batteryLevel = batteryLevel,
    voltage = voltage,
)

object BatteryAlertEvaluator {

    fun nextLevel(
        previousLevel: BatteryAlertLevel,
        snapshot: BatterySnapshot,
        settings: BatteryAlertSettings,
    ): BatteryAlertLevel {
        if (!settings.enabled || !settings.hasEnabledThresholds) return BatteryAlertLevel.NONE

        if (isCritical(snapshot)) return BatteryAlertLevel.CRITICAL
        if (previousLevel == BatteryAlertLevel.CRITICAL && !hasRecoveredFromCritical(snapshot)) {
            return BatteryAlertLevel.CRITICAL
        }

        if (isLow(snapshot, settings)) return BatteryAlertLevel.LOW

        return when (previousLevel) {
            BatteryAlertLevel.NONE -> BatteryAlertLevel.NONE
            BatteryAlertLevel.LOW,
            BatteryAlertLevel.CRITICAL -> {
                if (hasRecoveredFromLow(snapshot, settings)) BatteryAlertLevel.NONE else BatteryAlertLevel.LOW
            }
        }
    }

    private fun isCritical(snapshot: BatterySnapshot): Boolean =
        (snapshot.hasBatteryLevel && snapshot.batteryLevel <= CRITICAL_BATTERY_PERCENT_THRESHOLD) ||
            (snapshot.hasVoltage && snapshot.voltage <= CRITICAL_BATTERY_VOLTAGE_THRESHOLD)

    private fun isLow(snapshot: BatterySnapshot, settings: BatteryAlertSettings): Boolean =
        (settings.percentThreshold > 0 &&
            snapshot.hasBatteryLevel &&
            snapshot.batteryLevel <= settings.percentThreshold) ||
            (settings.voltageThreshold > 0f &&
                snapshot.hasVoltage &&
                snapshot.voltage <= settings.voltageThreshold)

    private fun hasRecoveredFromCritical(snapshot: BatterySnapshot): Boolean {
        val percentRecovered = !snapshot.hasBatteryLevel ||
            snapshot.batteryLevel > CRITICAL_BATTERY_PERCENT_THRESHOLD + BATTERY_PERCENT_HYSTERESIS
        val voltageRecovered = !snapshot.hasVoltage ||
            snapshot.voltage > CRITICAL_BATTERY_VOLTAGE_THRESHOLD + BATTERY_VOLTAGE_HYSTERESIS
        return percentRecovered && voltageRecovered
    }

    private fun hasRecoveredFromLow(
        snapshot: BatterySnapshot,
        settings: BatteryAlertSettings,
    ): Boolean {
        val percentRecovered = settings.percentThreshold <= 0 ||
            !snapshot.hasBatteryLevel ||
            snapshot.batteryLevel > settings.percentThreshold + BATTERY_PERCENT_HYSTERESIS
        val voltageRecovered = settings.voltageThreshold <= 0f ||
            !snapshot.hasVoltage ||
            snapshot.voltage > settings.voltageThreshold + BATTERY_VOLTAGE_HYSTERESIS
        return percentRecovered && voltageRecovered
    }
}
