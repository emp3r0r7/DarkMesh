package com.geeksville.mesh

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.android.advancedPrefs


const val ADV_SETTINGS_PREFS = "darkmesh_advanced_settings"
const val TRACE_MAX_PRIORITY_PREF = "trace_max_priority"

class AdvancedSettings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        val tracerouteSwitch =
            findViewById<SwitchCompat>(R.id.tracerouteMaxPriority)

        val traceMax = advancedPrefs.getBoolean(TRACE_MAX_PRIORITY_PREF, false)
        tracerouteSwitch.isChecked = traceMax

        tracerouteSwitch.setOnCheckedChangeListener { _, isChecked ->

            if(isChecked){
                advancedPrefs.edit { putBoolean(TRACE_MAX_PRIORITY_PREF, true) }
            } else {
                advancedPrefs.edit { putBoolean(TRACE_MAX_PRIORITY_PREF, false) }
            }
        }
    }


}
