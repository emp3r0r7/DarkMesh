package com.geeksville.mesh.ui

import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import com.emp3r0r7.darkmesh.R
import com.emp3r0r7.darkmesh.databinding.ActivityBatteryNotificationBinding
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.android.getNotificationPermissions
import com.geeksville.mesh.android.hasNotificationPermission
import com.geeksville.mesh.android.rationaleDialog
import com.geeksville.mesh.android.shouldShowRequestPermissionRationale
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.BatteryAlertScope
import com.geeksville.mesh.service.DEFAULT_BATTERY_ALERT_PERCENT_THRESHOLD
import com.geeksville.mesh.service.DEFAULT_BATTERY_ALERT_VOLTAGE_THRESHOLD
import com.geeksville.mesh.service.MAX_BATTERY_ALERT_VOLTAGE_THRESHOLD
import com.geeksville.mesh.service.PREF_BATTERY_ALERTS_ENABLED
import com.geeksville.mesh.service.PREF_BATTERY_ALERT_CONNECTED_SOUND_URI
import com.geeksville.mesh.service.PREF_BATTERY_ALERT_MESH_SOUND_URI
import com.geeksville.mesh.service.PREF_BATTERY_ALERT_PERCENT_THRESHOLD
import com.geeksville.mesh.service.PREF_BATTERY_ALERT_SCOPE
import com.geeksville.mesh.service.PREF_BATTERY_ALERT_VOLTAGE_THRESHOLD
import java.util.Locale

class BatteryNotification : AppCompatActivity() {

    private val model: UIViewModel by viewModels()

    private data class BatteryAlertScopeOption(
        val scope: BatteryAlertScope,
        val label: String,
    ) {
        override fun toString(): String = label
    }

    private enum class BatteryAlertSoundTarget {
        CONNECTED_NODE,
        MESH,
    }

    private var _binding: ActivityBatteryNotificationBinding? = null

    private val binding get() = _binding!!

    private var updatingBatteryAlertUi = false
    private var batteryAlertScopeOptions: List<BatteryAlertScopeOption> = emptyList()
    private var pendingBatteryAlertSoundTarget: BatteryAlertSoundTarget? = null
    private val batteryAlertSoundPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val target = pendingBatteryAlertSoundTarget
            pendingBatteryAlertSoundTarget = null
            if (target == null || result.resultCode != RESULT_OK) return@registerForActivityResult

            val pickedUri = result.data.getPickedRingtoneUri()
            val preferences = UIViewModel.getPreferences(this)
            val key = when (target) {
                BatteryAlertSoundTarget.CONNECTED_NODE -> PREF_BATTERY_ALERT_CONNECTED_SOUND_URI
                BatteryAlertSoundTarget.MESH -> PREF_BATTERY_ALERT_MESH_SOUND_URI
            }
            val normalizedUri = pickedUri
                ?.takeUnless { it == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }
                ?.toString()

            preferences.edit {
                if (normalizedUri == null) {
                    remove(key)
                } else {
                    putString(key, normalizedUri)
                }
            }
            updateBatteryAlertSoundLabels(preferences)
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        _binding = ActivityBatteryNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.title = "Battery Notification"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val preferences = UIViewModel.getPreferences(this)

        val requestNotificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) {
                    setBatteryAlertsEnabled(true)
                } else {
                    setBatteryAlertsEnabled(false)
                    model.showSnackbar(getString(R.string.notification_denied))
                }
            }

        updatingBatteryAlertUi = true
        binding.batteryAlertsCheckbox.isChecked =
            preferences.getBoolean(PREF_BATTERY_ALERTS_ENABLED, false)
        binding.batteryAlertPercentThresholdEditText.setText(
            preferences.getInt(
                PREF_BATTERY_ALERT_PERCENT_THRESHOLD,
                DEFAULT_BATTERY_ALERT_PERCENT_THRESHOLD
            ).toString()
        )
        binding.batteryAlertVoltageThresholdEditText.setText(
            formatBatteryAlertVoltageThreshold(
                preferences.getFloat(
                    PREF_BATTERY_ALERT_VOLTAGE_THRESHOLD,
                    DEFAULT_BATTERY_ALERT_VOLTAGE_THRESHOLD
                )
            )
        )
        batteryAlertScopeOptions = listOf(
            BatteryAlertScopeOption(
                scope = BatteryAlertScope.ALL_NODES,
                label = getString(R.string.battery_alert_scope_all_nodes)
            ),
            BatteryAlertScopeOption(
                scope = BatteryAlertScope.CONNECTED_NODE_ONLY,
                label = getString(R.string.battery_alert_scope_connected_bt_node)
            ),
        )
        binding.batteryAlertScopeDropdown.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                batteryAlertScopeOptions
            )
        )
        setBatteryAlertScopeSelection(selectedBatteryAlertScope(preferences))
        updateBatteryAlertSoundLabels(preferences)
        updatingBatteryAlertUi = false
        updateBatteryAlertInputs(binding.batteryAlertsCheckbox.isChecked)

        binding.batteryAlertsCheckbox.setOnCheckedChangeListener { view, isChecked ->
            if (updatingBatteryAlertUi) return@setOnCheckedChangeListener

            if (view.isPressed && isChecked && !hasNotificationPermission()) {
                updatingBatteryAlertUi = true
                view.isChecked = false
                updatingBatteryAlertUi = false

                val notificationPermissions = getNotificationPermissions()
                rationaleDialog(
                    shouldShowRequestPermissionRationale(notificationPermissions),
                    R.string.notification_required,
                    getString(R.string.why_notification_required),
                ) {
                    requestNotificationPermissionLauncher.launch(notificationPermissions)
                }
                return@setOnCheckedChangeListener
            }

            preferences.edit { putBoolean(PREF_BATTERY_ALERTS_ENABLED, isChecked) }
            updateBatteryAlertInputs(isChecked)
        }

        binding.batteryAlertScopeDropdown.setOnClickListener {
            if (binding.batteryAlertScopeDropdown.isEnabled) {
                binding.batteryAlertScopeDropdown.showDropDown()
            }
        }
        binding.batteryAlertScopeDropdown.setOnItemClickListener { parent, _, position, _ ->
            if (updatingBatteryAlertUi) return@setOnItemClickListener

            val selected = parent.getItemAtPosition(position) as BatteryAlertScopeOption
            preferences.edit {
                putString(PREF_BATTERY_ALERT_SCOPE, selected.scope.preferenceValue)
            }
        }

        binding.batteryAlertBtSoundChooseButton.setOnClickListener {
            launchBatteryAlertSoundPicker(
                target = BatteryAlertSoundTarget.CONNECTED_NODE,
                existingUriString = preferences.getString(PREF_BATTERY_ALERT_CONNECTED_SOUND_URI, null)
            )
        }
        binding.batteryAlertBtSoundDefaultButton.setOnClickListener {
            preferences.edit { remove(PREF_BATTERY_ALERT_CONNECTED_SOUND_URI) }
            updateBatteryAlertSoundLabels(preferences)
        }
        binding.batteryAlertMeshSoundChooseButton.setOnClickListener {
            launchBatteryAlertSoundPicker(
                target = BatteryAlertSoundTarget.MESH,
                existingUriString = preferences.getString(PREF_BATTERY_ALERT_MESH_SOUND_URI, null)
            )
        }
        binding.batteryAlertMeshSoundDefaultButton.setOnClickListener {
            preferences.edit { remove(PREF_BATTERY_ALERT_MESH_SOUND_URI) }
            updateBatteryAlertSoundLabels(preferences)
        }

        binding.batteryAlertPercentThresholdEditText.doAfterTextChanged { text ->
            if (updatingBatteryAlertUi) return@doAfterTextChanged

            val threshold = text?.toString()?.trim()?.toIntOrNull()?.coerceIn(0, 100) ?: 0
            preferences.edit { putInt(PREF_BATTERY_ALERT_PERCENT_THRESHOLD, threshold) }
        }

        binding.batteryAlertVoltageThresholdEditText.doAfterTextChanged { text ->
            if (updatingBatteryAlertUi) return@doAfterTextChanged

            val threshold = text?.toString()?.trim()?.toFloatOrNull()
                ?.coerceIn(0f, MAX_BATTERY_ALERT_VOLTAGE_THRESHOLD) ?: 0f
            preferences.edit { putFloat(PREF_BATTERY_ALERT_VOLTAGE_THRESHOLD, threshold) }
        }

    }

    private fun updateBatteryAlertInputs(enabled: Boolean) {
        binding.batteryAlertPercentThresholdLayout.isEnabled = enabled
        binding.batteryAlertPercentThresholdEditText.isEnabled = enabled
        binding.batteryAlertVoltageThresholdLayout.isEnabled = enabled
        binding.batteryAlertVoltageThresholdEditText.isEnabled = enabled
        binding.batteryAlertScopeLayout.isEnabled = enabled
        binding.batteryAlertScopeDropdown.isEnabled = enabled
        binding.batteryAlertBtSoundChooseButton.isEnabled = enabled
        binding.batteryAlertBtSoundDefaultButton.isEnabled = enabled
        binding.batteryAlertMeshSoundChooseButton.isEnabled = enabled
        binding.batteryAlertMeshSoundDefaultButton.isEnabled = enabled

        val enabledAlpha = if (enabled) 1f else 0.5f
        binding.batteryAlertScopeLayout.alpha = enabledAlpha
        binding.batteryAlertBtSoundSection.alpha = enabledAlpha
        binding.batteryAlertMeshSoundSection.alpha = enabledAlpha
    }
    private fun setBatteryAlertsEnabled(enabled: Boolean) {
        UIViewModel.getPreferences(this)
            .edit { putBoolean(PREF_BATTERY_ALERTS_ENABLED, enabled) }
        updatingBatteryAlertUi = true
        binding.batteryAlertsCheckbox.isChecked = enabled
        updatingBatteryAlertUi = false
        updateBatteryAlertInputs(enabled)
    }

    private fun formatBatteryAlertVoltageThreshold(value: Float): String =
        if (value == 0f) {
            "0"
        } else {
            String.format(Locale.getDefault(), "%.2f", value)
        }


    private fun selectedBatteryAlertScope(preferences: SharedPreferences): BatteryAlertScope =
        BatteryAlertScope.fromPreferenceValue(
            preferences.getString(
                PREF_BATTERY_ALERT_SCOPE,
                BatteryAlertScope.ALL_NODES.preferenceValue
            )
        )

    private fun setBatteryAlertScopeSelection(scope: BatteryAlertScope) {
        val binding = _binding ?: return
        val label = batteryAlertScopeOptions.firstOrNull { it.scope == scope }?.label ?: return
        binding.batteryAlertScopeDropdown.setText(label, false)
    }

    private fun batteryAlertDefaultSoundUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun batteryAlertSoundTitle(uriString: String?): String {
        val soundUri = uriString?.takeUnless { it.isBlank() }?.let(Uri::parse) ?: batteryAlertDefaultSoundUri()
        val ringtoneTitle = RingtoneManager.getRingtone(this, soundUri)
            ?.getTitle(this)
            ?: getString(R.string.battery_alert_sound_unavailable)

        return if (uriString.isNullOrBlank()) {
            getString(R.string.battery_alert_sound_default_format, ringtoneTitle)
        } else {
            ringtoneTitle
        }
    }

    private fun updateBatteryAlertSoundLabels(preferences: SharedPreferences) {
        val binding = _binding ?: return
        binding.batteryAlertBtSoundValue.text = batteryAlertSoundTitle(
            preferences.getString(PREF_BATTERY_ALERT_CONNECTED_SOUND_URI, null)
        )
        binding.batteryAlertMeshSoundValue.text = batteryAlertSoundTitle(
            preferences.getString(PREF_BATTERY_ALERT_MESH_SOUND_URI, null)
        )
    }

    private fun launchBatteryAlertSoundPicker(
        target: BatteryAlertSoundTarget,
        existingUriString: String?
    ) {
        pendingBatteryAlertSoundTarget = target
        batteryAlertSoundPickerLauncher.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, batteryAlertDefaultSoundUri())
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    existingUriString?.takeUnless { it.isBlank() }?.let(Uri::parse) ?: batteryAlertDefaultSoundUri()
                )
            }
        )
    }

    private fun Intent?.getPickedRingtoneUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            this?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        return true
    }
}