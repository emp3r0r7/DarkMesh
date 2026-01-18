package com.geeksville.mesh

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.android.advancedPrefs


const val ADV_SETTINGS_PREFS = "darkmesh_advanced_settings"
const val TRACE_MAX_PRIORITY_PREF = "trace_max_priority"
const val SKIP_MQTT_ENTIRELY = "skip_mqtt_entirely"

class AdvancedSettings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        val tracerouteSwitch =
            findViewById<SwitchCompat>(R.id.tracerouteMaxPriority)

        val skipMqttSwitch =
            findViewById<SwitchCompat>(R.id.skipMqttEntirelySwitch)

        val traceMaxPref = advancedPrefs.getBoolean(TRACE_MAX_PRIORITY_PREF, false)
        val skipMqttPref = advancedPrefs.getBoolean(SKIP_MQTT_ENTIRELY, false)

        tracerouteSwitch.isChecked = traceMaxPref
        skipMqttSwitch.isChecked = skipMqttPref

        setSwitchListener(tracerouteSwitch, TRACE_MAX_PRIORITY_PREF)
        setSwitchListener(skipMqttSwitch, SKIP_MQTT_ENTIRELY)

    }

    private fun setSwitchListener(switchCompat: SwitchCompat, prefsFlag: String){
        switchCompat.setOnCheckedChangeListener { _, isChecked ->

            if(isChecked){
                advancedPrefs.edit { putBoolean(prefsFlag, true) }
            } else {
                advancedPrefs.edit { putBoolean(prefsFlag, false) }
            }
        }
    }


}
