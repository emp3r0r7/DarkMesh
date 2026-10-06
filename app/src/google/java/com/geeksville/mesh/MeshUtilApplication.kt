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

package com.geeksville.mesh

import android.os.Debug
import com.emp3r0r7.darkmesh.BuildConfig
import com.geeksville.mesh.android.AppPrefs
import com.geeksville.mesh.android.BuildUtils.isEmulator
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.util.Exceptions
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.crashlytics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MeshUtilApplication : GeeksvilleApplication() {

    override fun onCreate() {
        super.onCreate()

        Logging.showLogs = BuildConfig.DEBUG

        // We default to off in the manifest - we turn on here if the user approves
        // leave off when running in the debugger
        if (shouldConfigureCrashlytics()) {
            val crashlytics = Firebase.crashlytics
            crashlytics.setCrashlyticsCollectionEnabled(isAnalyticsAllowed)
            crashlytics.setCustomKey("debug_build", BuildConfig.DEBUG)

            val pref = AppPrefs(this)
            crashlytics.setUserId(pref.getInstallId()) // be able to group all bugs per anonymous user

            // Crashlytics receives logs locally and only sends them when we report an exception.
            // This keeps logs available if analytics is enabled just before reporting a bug.
            // send all log messages through crashyltics, so if we do crash we'll have those in the report
            val standardLogger = Logging.printlog
            Logging.printlog = { level, tag, message ->
                crashlytics.log("$tag: $message")
                standardLogger(level, tag, message)
            }

            fun sendCrashReports() {
                if (isAnalyticsAllowed) {
                    crashlytics.sendUnsentReports()
                }
            }

            // Send any old reports if user approves
            sendCrashReports()

            // Attach to our exception wrapper
            Exceptions.reporter = { exception, _, _ ->
                crashlytics.recordException(exception)
                sendCrashReports() // Send the new report
            }
        }
    }

    private fun shouldConfigureCrashlytics(): Boolean {
        val physicalDevice = !isEmulator
        val reportingAllowedForBuild = !BuildConfig.DEBUG || !Debug.isDebuggerConnected()
        return physicalDevice && reportingAllowedForBuild && ensureFirebaseConfigured()
    }

    private fun ensureFirebaseConfigured(): Boolean {
        val app = runCatching {
            FirebaseApp.getApps(this).firstOrNull() ?: FirebaseApp.initializeApp(this)
        }.onFailure { ex ->
            warn("Firebase initialization failed: ${ex.message}")
        }.getOrNull()

        if (app == null) {
            warn("Crashlytics disabled: missing Firebase configuration")
            return false
        }

        return true
    }
}
