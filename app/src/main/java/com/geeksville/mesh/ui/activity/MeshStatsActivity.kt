package com.geeksville.mesh.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.android.statsPrefs
import com.geeksville.mesh.util.MeshStatsUtil
import java.util.Locale

class MeshStatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_mesh_stats)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.title = "Mesh Stats"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        setupTraceStatsComponents()
        setupCompressedMessagesComponents()
    }

    private fun setupCompressedMessagesComponents() {

        val sentCountTv = findViewById<TextView>(R.id.compressedSentCount)?.also {
            it.text = statsPrefs.getString(MeshStatsUtil.STATS_COMPRESSION_SENT_TOTAL, "0")
        }

        val bytesSavedTv = findViewById<TextView>(R.id.compresedBytesSavedCount)?.also { view ->

            val savedBytes = statsPrefs.getString(MeshStatsUtil.STATS_COMPRESSION_BYTES_SAVED, "0")

            savedBytes?.toLong()?.takeIf {
                it > 0
            }?.let {
                val mb = it.toDouble() / 1_000_000.0
                val enriched = "$it byte - ${"%.6f".format(mb)} MB"
                view.text = enriched
            }?: run {
                view.text = "0"
            }
        }

        findViewById<Button>(R.id.resetCompressionStats).setOnClickListener{
            statsPrefs.edit{
                putString(MeshStatsUtil.STATS_COMPRESSION_SENT_TOTAL, "0")
                sentCountTv?.text = "0"

                putString(MeshStatsUtil.STATS_COMPRESSION_BYTES_SAVED, "0")
                bytesSavedTv?.text = "0"
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupTraceStatsComponents(){

        val totalCountTv = findViewById<TextView>(R.id.tracerouteExecutedCount)?.also {
            it.text = statsPrefs.getString(MeshStatsUtil.STATS_TRACE_TOTAL, "0")
        }

        val traceSuccessCountTv = findViewById<TextView>(R.id.tracerouteSuccessCount)?.also {
            it.text = statsPrefs.getString(MeshStatsUtil.STATS_TRACE_SUCCESS, "0")
        }

        val tracerouteRateCountTv = findViewById<TextView>(R.id.tracerouteRateCount)?.also {

            val total = totalCountTv?.text
                .toString()
                .toLong()

            val success = traceSuccessCountTv?.text
                .toString()
                .toLong()

            if(total > 0 && total >= success){
                val perc = (success.toDouble() / total.toDouble()) * 100.0
                val formatted = String.format(Locale.ROOT, "%.2f", perc)

                statsPrefs.edit {
                    putString(MeshStatsUtil.STATS_TRACE_RATE, formatted)
                }

                it.text = "$formatted %"
            } else {
                it.text = "0.0 %"
            }
        }

        val longestTraceTv = findViewById<TextView>(R.id.tracerouteLongestCount)?.also {
            val longest = statsPrefs.getString(MeshStatsUtil.STATS_TRACE_LONGEST, "0")
            it.text = "$longest Km"
        }

        val maxTraveledTv = findViewById<TextView>(R.id.tracerouteMaxTraveledCount)?.also {
            val maxTraveled = statsPrefs.getString(MeshStatsUtil.STATS_TRACE_MAXTRAVELED, "0")
            it.text = "$maxTraveled Km"
        }

        findViewById<Button>(R.id.resetTracerouteStats).setOnClickListener{
            statsPrefs.edit{
                putString(MeshStatsUtil.STATS_TRACE_TOTAL, "0")
                totalCountTv?.text = "0"

                putString(MeshStatsUtil.STATS_TRACE_SUCCESS, "0")
                traceSuccessCountTv?.text = "0"

                putString(MeshStatsUtil.STATS_TRACE_RATE, "0")
                tracerouteRateCountTv?.text = "0"

                putString(MeshStatsUtil.STATS_TRACE_LONGEST, "0")
                longestTraceTv?.text = "0"

                putString(MeshStatsUtil.STATS_TRACE_MAXTRAVELED, "0")
                maxTraveledTv?.text = "0"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        return true
    }
}