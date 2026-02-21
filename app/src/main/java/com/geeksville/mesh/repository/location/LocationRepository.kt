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

package com.geeksville.mesh.repository.location

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.location.LocationCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.core.location.altitude.AltitudeConverterCompat
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Policy per:
 * 1) come richiedere update al [LocationManager] (request*)
 * 2) come filtrare/limitare gli update emessi ai consumer (emit*)
 *
 * Nota: NON decide quando trasmettere over-the-air via LoRa (quello è firmware/config).
 * Serve a ridurre jitter e chatter (telefono -> radio via BLE/USB) e granularità di tracking.
 */
data class LocationEmitPolicy(
    // Request side (LocationManager)
    val requestIntervalMs: Long = 30_000L,
    val requestMinDistanceM: Float = 0f,
    val requestQuality: Int = LocationRequestCompat.QUALITY_HIGH_ACCURACY,

    // Emit side (debounce) — emetti se (Δd >= X) OR (Δt >= T)
    val emitMinDistanceM: Float = 25f,
    val emitMaxIntervalMs: Long = 5 * 60_000L,

    // Safety gate: scarta fix con accuracy troppo scarsa (anti "garbage fixes")
    val emitMaxAccuracyM: Float = 200f,

    // Emetti subito se l'accuracy migliora "tanto" rispetto all'ultimo fix emesso
    val emitOnAccuracyImprovementFactor: Float = 0.5f, // 2x better
    val emitOnAccuracyImprovementAbsM: Float = 50f,
) {
    init {
        require(requestIntervalMs > 0) { "requestIntervalMs must be > 0" }
        require(requestMinDistanceM >= 0f) { "requestMinDistanceM must be >= 0" }
        require(emitMinDistanceM >= 0f) { "emitMinDistanceM must be >= 0" }
        require(emitMaxIntervalMs > 0) { "emitMaxIntervalMs must be > 0" }
        require(emitMaxAccuracyM > 0f) { "emitMaxAccuracyM must be > 0" }
        require(emitOnAccuracyImprovementFactor in 0f..1f) { "emitOnAccuracyImprovementFactor must be in [0,1]" }
        require(emitOnAccuracyImprovementAbsM >= 0f) { "emitOnAccuracyImprovementAbsM must be >= 0" }
    }

    companion object {
        /** Default “LoRa-friendly”: riduce jitter quando fermi, senza cambiare request GPS di base. */
        val Default = LocationEmitPolicy()

        /**
         * Live tracking (es. distress): più frequente e permissivo.
         * Usare solo quando serve davvero (emergenza / tracking intenzionale).
         */
        val Live = LocationEmitPolicy(
            requestIntervalMs = 10_000L,
            emitMinDistanceM = 5f,
            emitMaxIntervalMs = 30_000L,
            emitMaxAccuracyM = 300f,
        )
    }
}

/**
 * Stateful debouncer (logica pura) usato dal listener.
 *
 * Nota: [Location] è mutabile, quindi qui si conserva una copia difensiva.
 */
internal class LocationDebouncer(private val policy: LocationEmitPolicy) {
    private var lastEmitted: Location? = null
    private var lastEmittedElapsedMs: Long? = null

    fun shouldEmit(next: Location, nowElapsedMs: Long): Boolean {
        // Drop garbage fixes (ma non troppo aggressivo: default 200m)
        if (next.hasAccuracy() && next.accuracy > policy.emitMaxAccuracyM) return false

        val prev = lastEmitted ?: return true // always emit first acceptable fix

        // Emit immediately if accuracy improved significantly (e.g. first fix was coarse)
        if (prev.hasAccuracy() && next.hasAccuracy()) {
            val prevAcc = prev.accuracy
            val nextAcc = next.accuracy
            val absImprovement = prevAcc - nextAcc
            if (absImprovement >= policy.emitOnAccuracyImprovementAbsM ||
                (prevAcc > 0f && nextAcc <= prevAcc * policy.emitOnAccuracyImprovementFactor)
            ) {
                return true
            }
        }

        val prevElapsed = lastEmittedElapsedMs ?: return true
        val deltaT = max(0L, nowElapsedMs - prevElapsed)

        val deltaM = try {
            prev.distanceTo(next)
        } catch (_: Exception) {
            Float.POSITIVE_INFINITY
        }

        return (deltaM >= policy.emitMinDistanceM) || (deltaT >= policy.emitMaxIntervalMs)
    }

    fun markEmitted(location: Location, nowElapsedMs: Long) {
        lastEmitted = Location(location)
        lastEmittedElapsedMs = nowElapsedMs
    }
}

@Singleton
class LocationRepository @Inject constructor(
    private val context: Application,
    private val locationManager: dagger.Lazy<LocationManager>,
) : Logging {

    /**
     * Status of whether the app is actively subscribed to location changes.
     */
    private val _receivingLocationUpdates: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val receivingLocationUpdates: StateFlow<Boolean> get() = _receivingLocationUpdates

    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    private fun LocationManager.requestLocationUpdates(policy: LocationEmitPolicy): Flow<Location> = callbackFlow {
        var started = false

        val locationRequest = LocationRequestCompat.Builder(policy.requestIntervalMs)
            .setMinUpdateDistanceMeters(policy.requestMinDistanceM)
            .setQuality(policy.requestQuality)
            .build()

        val debouncer = LocationDebouncer(policy)

        // Serializza i callback di più provider: evita race sullo stato del debouncer
        val callbackExecutor = Dispatchers.IO.asExecutor()

        fun nowElapsedMs(): Long = SystemClock.elapsedRealtime()

        val locationListener = LocationListenerCompat { raw ->
            val location = Location(raw) // defensive copy

            val elapsedMs = nowElapsedMs()
            if (!debouncer.shouldEmit(location, elapsedMs)) return@LocationListenerCompat

            // Best-effort MSL altitude enrichment
            if (location.hasAltitude() && !LocationCompat.hasMslAltitude(location)) {
                try {
                    AltitudeConverterCompat.addMslAltitudeToLocation(context, location)
                } catch (e: Exception) {
                    errormsg("addMslAltitudeToLocation() failed", e)
                }
            }

            // Mark emitted only if actually delivered to the channel
            if (trySend(Location(location)).isSuccess) {
                debouncer.markEmitted(location, elapsedMs)
            }
        }

        val providerList = buildList {
            val providers = allProviders
            if (Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in providers) {
                add(LocationManager.FUSED_PROVIDER)
            } else {
                if (LocationManager.GPS_PROVIDER in providers) add(LocationManager.GPS_PROVIDER)
                if (LocationManager.NETWORK_PROVIDER in providers) add(LocationManager.NETWORK_PROVIDER)
            }
        }

        if (providerList.isEmpty()) {
            close(IllegalStateException("No location providers available"))
        } else {
            info(
                "Starting location updates with $providerList " +
                    "intervalMs=${policy.requestIntervalMs}ms minDistanceM=${policy.requestMinDistanceM}m " +
                    "emitMinDistanceM=${policy.emitMinDistanceM}m emitMaxIntervalMs=${policy.emitMaxIntervalMs}ms"
            )
            _receivingLocationUpdates.value = true
            GeeksvilleApplication.analytics.track("location_start")
            started = true

            try {
                providerList.forEach { provider ->
                    LocationManagerCompat.requestLocationUpdates(
                        this@requestLocationUpdates,
                        provider,
                        locationRequest,
                        callbackExecutor,
                        locationListener,
                    )
                }
            } catch (e: SecurityException) {
                close(e)
            } catch (e: Exception) {
                close(e)
            }
        }

        awaitClose {
            info("Stopping location requests")
            _receivingLocationUpdates.value = false
            if (started) GeeksvilleApplication.analytics.track("location_stop")

            LocationManagerCompat.removeUpdates(this@requestLocationUpdates, locationListener)
        }
    }.conflate()

    /** Backward-compatible default. */
    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    fun getLocations(): Flow<Location> =
        locationManager.get().requestLocationUpdates(LocationEmitPolicy.Default)

    /** Optional override: es. distress live. */
    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    fun getLocations(policy: LocationEmitPolicy): Flow<Location> =
        locationManager.get().requestLocationUpdates(policy)
}
