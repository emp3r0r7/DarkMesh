package com.geeksville.mesh.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.geeksville.mesh.android.statsPrefs
import java.util.concurrent.Semaphore
import kotlin.math.roundToLong

object MeshStatsUtil {

    const val TAG = "MESHSTAT"

    const val MESH_STATS_PREFS = "mesh_stats_prefs"

    //trace stats
    const val STATS_TRACE_TOTAL = "stats_trace_total"
    const val STATS_TRACE_SUCCESS = "stats_trace_success"
    const val STATS_TRACE_RATE = "stats_trace_rate"
    const val STATS_TRACE_LONGEST = "stats_trace_longest"
    const val STATS_TRACE_MAXTRAVELED = "stats_trace_maxtraveled"

    //compression stats
    const val STATS_COMPRESSION_SENT_TOTAL = "stats_compression_sent_total"
    const val STATS_COMPRESSION_BYTES_SAVED = "stats_compression_bytes_saved"

    private val semaphore = Semaphore(1)

    private inline fun withStatsLock(
        block: () -> Unit
    ) {
        try {
            semaphore.acquire()
            block()
        } catch (e: Exception) {
            Log.e(TAG, "An error occurred while performing operation ${e.message}", e)
        } finally {
            semaphore.release()
        }
    }

    fun incrementStat(
        ctx: Context,
        pref: String
    ) = withStatsLock {
        incrementPrefs(ctx, pref)
    }

    private fun incrementPrefs(
        ctx: Context,
        pref: String){
        ctx.statsPrefs.getString(pref, "0")?.toLong()?.inc()?.let {
            ctx.statsPrefs.edit(commit = true) {
                putString(pref, it.toString())
            }
        }
    }

    fun addSavedBytesAndSent(
        ctx: Context,
        bytesLenght: Int
    ) = withStatsLock {
        ctx.statsPrefs.getString(STATS_COMPRESSION_BYTES_SAVED, "0")
            ?.toDouble()
            ?.let {
                val total = it.roundToLong() + bytesLenght
                ctx.statsPrefs.edit(commit = true) {
                    putString(STATS_COMPRESSION_BYTES_SAVED, total.toString())
                }
            }

        incrementPrefs(ctx, STATS_COMPRESSION_SENT_TOTAL)
    }

    fun compareLongestTraceAndAdd(
        ctx: Context,
        distance: Double
    ) = withStatsLock{
        ctx.statsPrefs.getString(STATS_TRACE_LONGEST, "0")
            ?.toDouble()
            ?.let {
                val prevRounded = it.roundToLong()
                val currentRounded = distance.roundToLong()

                if(prevRounded < currentRounded){
                    ctx.statsPrefs.edit(commit = true) {
                        putString(STATS_TRACE_LONGEST, currentRounded.toString())
                    }
                }
            }

        ctx.statsPrefs.getString(STATS_TRACE_MAXTRAVELED, "0")
            ?.toLong()
            ?.let { maxTraveled ->
                val sum = maxTraveled + distance.roundToLong()
                ctx.statsPrefs.edit(commit = true){
                    putString(STATS_TRACE_MAXTRAVELED, sum.toString())
                }
            }
    }
}