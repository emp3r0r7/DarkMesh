package com.geeksville.mesh

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.android.advancedPrefs
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.DistressService.PREF_STRESSTEST_DEFAULT_PREFIX
import com.geeksville.mesh.service.DistressService.PREF_STRESSTEST_PREFIX


const val ADV_SETTINGS_PREFS = "darkmesh_advanced_settings"
const val TRACE_MAX_PRIORITY_PREF = "trace_max_priority"
const val SKIP_MQTT_ENTIRELY = "skip_mqtt_entirely"
const val OVERRIDE_TELEMETRY_ALL_VERSIONS = "override_telemetry_all_versions"


class AdvancedSettings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        val uiModelPrefs = UIViewModel.getPreferences(this)

        val tracerouteSwitch =
            findViewById<SwitchCompat>(R.id.tracerouteMaxPriority)

        val skipMqttSwitch =
            findViewById<SwitchCompat>(R.id.skipMqttEntirelySwitch)

        val distressBeaconPrefix =
            findViewById<EditText>(R.id.distressPrefix)

        val beaconPrefixBtn =
            findViewById<Button>(R.id.setBeaconPrefixBtn)

        val overrideTelemetrySwitch =
            findViewById<SwitchCompat>(R.id.overrideTelSwitch)

        val traceMaxPref = advancedPrefs.getBoolean(TRACE_MAX_PRIORITY_PREF, false)
        val skipMqttPref = advancedPrefs.getBoolean(SKIP_MQTT_ENTIRELY, false)

        val distressPrefix = uiModelPrefs.getString(
            PREF_STRESSTEST_PREFIX,
            PREF_STRESSTEST_DEFAULT_PREFIX
        )

        distressBeaconPrefix.setText(distressPrefix)

        tracerouteSwitch.isChecked = traceMaxPref
        skipMqttSwitch.isChecked = skipMqttPref

        setSwitchListener(tracerouteSwitch, TRACE_MAX_PRIORITY_PREF)
        setSwitchListener(skipMqttSwitch, SKIP_MQTT_ENTIRELY)
        setSwitchListener(overrideTelemetrySwitch, OVERRIDE_TELEMETRY_ALL_VERSIONS)

        beaconPrefixBtn.setOnClickListener {

            val value = distressBeaconPrefix.text.toString().trim()

            if(value.length > 5){

                Toast.makeText(
                    this,
                    "Long prefixes are not allowed!", Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            uiModelPrefs.edit {
                putString(PREF_STRESSTEST_PREFIX, value).commit()
            }

            val successMsg: String = if(value.isEmpty()){
                "Prefix has been removed!"
            } else {
                "Prefix $value has been set!"
            }

            Toast.makeText(
                this, successMsg, Toast.LENGTH_LONG
            ).show()
        }

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
