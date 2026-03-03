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

package com.geeksville.mesh.ui

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.net.InetAddresses
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import com.emp3r0r7.darkmesh.R
import com.emp3r0r7.darkmesh.databinding.SettingsFragmentBinding
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.getBluetoothPermissions
import com.geeksville.mesh.android.getLocationPermissions
import com.geeksville.mesh.android.getNotificationPermissions
import com.geeksville.mesh.android.gpsDisabled
import com.geeksville.mesh.android.hasGps
import com.geeksville.mesh.android.hasLocationPermission
import com.geeksville.mesh.android.hasNotificationPermission
import com.geeksville.mesh.android.hideKeyboard
import com.geeksville.mesh.android.isGooglePlayAvailable
import com.geeksville.mesh.android.permissionMissing
import com.geeksville.mesh.android.rationaleDialog
import com.geeksville.mesh.android.shouldShowRequestPermissionRationale
import com.geeksville.mesh.service.BatteryAlertScope
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.RegionInfo
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.UIViewModel.Companion.getPreferences
import com.geeksville.mesh.repository.location.LocationRepository
import com.geeksville.mesh.service.DEFAULT_BATTERY_ALERT_PERCENT_THRESHOLD
import com.geeksville.mesh.service.DEFAULT_BATTERY_ALERT_VOLTAGE_THRESHOLD
import com.geeksville.mesh.service.MAX_BATTERY_ALERT_VOLTAGE_THRESHOLD
import com.geeksville.mesh.service.DistressService.PREF_STRESSTEST_ENABLED
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.PREF_BATTERY_ALERTS_ENABLED
import com.geeksville.mesh.service.PREF_BATTERY_ALERT_CONNECTED_SOUND_URI
import com.geeksville.mesh.service.PREF_BATTERY_ALERT_PERCENT_THRESHOLD
import com.geeksville.mesh.service.PREF_BATTERY_ALERT_MESH_SOUND_URI
import com.geeksville.mesh.service.PREF_BATTERY_ALERT_SCOPE
import com.geeksville.mesh.service.PREF_BATTERY_ALERT_VOLTAGE_THRESHOLD
import com.geeksville.mesh.util.exceptionToSnackbar
import com.geeksville.mesh.util.onEditorAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import org.meshtastic.proto.ConfigProtos
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : ScreenFragment("Settings"), Logging {
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

    private var _binding: SettingsFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val scanModel: BTScanModel by activityViewModels()
    private val bluetoothViewModel: BluetoothViewModel by activityViewModels()
    private val model: UIViewModel by activityViewModels()

    @Inject
    internal lateinit var locationRepository: LocationRepository

    private val hasGps by lazy { requireContext().hasGps() }
    private var updatingBatteryAlertUi = false
    private var batteryAlertScopeOptions: List<BatteryAlertScopeOption> = emptyList()
    private var pendingBatteryAlertSoundTarget: BatteryAlertSoundTarget? = null
    private val batteryAlertSoundPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val target = pendingBatteryAlertSoundTarget
            pendingBatteryAlertSoundTarget = null
            if (target == null || result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val pickedUri = result.data.getPickedRingtoneUri()
            val preferences = getPreferences(requireContext())
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Pull the latest device info from the model and into the GUI
     */
    private fun updateNodeInfo() {
        val connectionState = model.connectionState.value
        val isConnected = connectionState == MeshService.ConnectionState.CONNECTED

        binding.nodeSettings.visibility = if (isConnected) View.VISIBLE else View.GONE
        binding.provideLocationCheckbox.visibility = if (isConnected) View.VISIBLE else View.GONE

        binding.usernameEditText.isEnabled = isConnected && !model.isManaged

        if (hasGps) {
            binding.provideLocationCheckbox.isEnabled = true
        } else {
            binding.provideLocationCheckbox.isChecked = false
            binding.provideLocationCheckbox.isEnabled = false
        }

        // update the region selection from the device
        val region = model.region
        val spinner = binding.regionSpinner
        spinner.onItemSelectedListener = null

        debug("current region is $region")
        var regionIndex = regions.indexOfFirst { it.regionCode == region }
        if (regionIndex == -1) { // Not found, probably because the device has a region our app doesn't yet understand.  Punt and say Unset
            regionIndex = ConfigProtos.Config.LoRaConfig.RegionCode.UNSET_VALUE
        }

        // We don't want to be notified of our own changes, so turn off listener while making them
        spinner.setSelection(regionIndex, false)
        spinner.onItemSelectedListener = regionSpinnerListener
        spinner.isEnabled = !model.isManaged

        // Update the status string (highest priority messages first)
        val regionUnset = region == ConfigProtos.Config.LoRaConfig.RegionCode.UNSET
        val info = model.myNodeInfo.value
        when (connectionState) {
            MeshService.ConnectionState.CONNECTED ->
                if (regionUnset) R.string.must_set_region else R.string.connected_to
            MeshService.ConnectionState.DISCONNECTED -> R.string.not_connected
            MeshService.ConnectionState.DEVICE_SLEEP -> R.string.connected_sleeping
            else -> null
        }?.let {
            val firmwareString = info?.firmwareString ?: getString(R.string.unknown)
            scanModel.setErrorText(getString(it, firmwareString))
        }
    }

    private val regionSpinnerListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>,
            view: View,
            position: Int,
            id: Long
        ) {
            val item = RegionInfo.entries[position]
            val asProto = item.regionCode
            exceptionToSnackbar(requireView()) {
                debug("regionSpinner onItemSelected $asProto")
                if (asProto != model.region) model.region = asProto
            }
            updateNodeInfo() // We might have just changed Unset to set
        }

        override fun onNothingSelected(parent: AdapterView<*>) {
            // TODO("Not yet implemented")
        }
    }

    private val regions = RegionInfo.entries

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
        getPreferences(requireContext()).edit { putBoolean(PREF_BATTERY_ALERTS_ENABLED, enabled) }
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
        val ringtoneTitle = RingtoneManager.getRingtone(requireContext(), soundUri)
            ?.getTitle(requireContext())
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

    private fun initCommonUI() {
        val preferences = getPreferences(requireContext())

        val requestLocationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) {
                    model.provideLocation.value = true
                    model.meshService?.startProvideLocation()
                } else {
                    debug("User denied location permission")
                    model.showSnackbar(getString(R.string.why_background_required))
                }
                bluetoothViewModel.permissionsUpdated()
            }
        val requestNotificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) {
                    setBatteryAlertsEnabled(true)
                } else {
                    setBatteryAlertsEnabled(false)
                    model.showSnackbar(getString(R.string.notification_denied))
                }
            }

        // init our region spinner
        val spinner = binding.regionSpinner
        val regionAdapter = object : ArrayAdapter<RegionInfo>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            regions
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.text = regions[position].name
                return view
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.text = regions[position].description
                return view
            }
        }
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = regionAdapter

        model.ourNodeInfo.asLiveData().observe(viewLifecycleOwner) { node ->
            binding.usernameEditText.setText(node?.user?.longName.orEmpty())
        }

        scanModel.devices.observe(viewLifecycleOwner) { devices ->
            updateDevicesButtons(devices)
        }

        // Only let user edit their name or set software update while connected to a radio
        model.connectionState.asLiveData().observe(viewLifecycleOwner) {
            updateNodeInfo()
        }

        model.localConfig.asLiveData().observe(viewLifecycleOwner) {
            if (model.isConnected()) updateNodeInfo()
        }

        // Also watch myNodeInfo because it might change later
        model.myNodeInfo.asLiveData().observe(viewLifecycleOwner) {
            updateNodeInfo()
        }

        scanModel.errorText.observe(viewLifecycleOwner) { errMsg ->
            if (errMsg != null) {
                binding.scanStatusText.text = errMsg
            }
        }

        var scanDialog: AlertDialog? = null
        scanModel.scanResult.observe(viewLifecycleOwner) { results ->
            val devices = results.values.ifEmpty { return@observe }
            scanDialog?.dismiss()
            scanDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select a Bluetooth device")
                .setSingleChoiceItems(
                    devices.map { it.name }.toTypedArray(),
                    -1
                ) { dialog, position ->
                    val selectedDevice = devices.elementAt(position)
                    scanModel.onSelected(selectedDevice)
                    scanModel.clearScanResults()
                    dialog.dismiss()
                    scanDialog = null
                }
                .setPositiveButton(R.string.cancel) { dialog, _ ->
                    scanModel.clearScanResults()
                    dialog.dismiss()
                    scanDialog = null
                }
                .show()
        }

        // show the spinner when [spinner] is true
        scanModel.spinner.observe(viewLifecycleOwner) { show ->
            binding.changeRadioButton.isEnabled = !show
            binding.scanProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        }

        binding.usernameEditText.onEditorAction(EditorInfo.IME_ACTION_DONE) {
            debug("received IME_ACTION_DONE")
            val n = binding.usernameEditText.text.toString().trim()
            if (n.isNotEmpty()) model.setOwner(n)
            requireActivity().hideKeyboard()
        }

        // Observe receivingLocationUpdates state and update provideLocationCheckbox
        locationRepository.receivingLocationUpdates.asLiveData().observe(viewLifecycleOwner) {
            binding.provideLocationCheckbox.isChecked = it
        }

        binding.provideLocationCheckbox.setOnCheckedChangeListener { view, isChecked ->
            // Don't check the box until the system setting changes
            view.isChecked = isChecked && requireContext().hasLocationPermission()

            if (view.isPressed) { // We want to ignore changes caused by code (as opposed to the user)
                debug("User changed location tracking to $isChecked")
                model.provideLocation.value = isChecked
                if (isChecked && !view.isChecked) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.background_required)
                        .setMessage(R.string.why_background_required)
                        .setNeutralButton(R.string.cancel) { _, _ ->
                            debug("User denied background permission")
                        }
                        .setPositiveButton(getString(R.string.accept)) { _, _ ->
                            // Make sure we have location permission (prerequisite)
                            if (!requireContext().hasLocationPermission()) {
                                requestLocationPermissionLauncher.launch(requireContext().getLocationPermissions())
                            }
                        }
                        .show()
                }
            }
            if (view.isChecked) {
                checkLocationEnabled(getString(R.string.location_disabled))
                model.meshService?.startProvideLocation()
            } else {
                model.meshService?.stopProvideLocation()
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
                requireContext(),
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

            if (view.isPressed && isChecked && !requireContext().hasNotificationPermission()) {
                updatingBatteryAlertUi = true
                view.isChecked = false
                updatingBatteryAlertUi = false

                val notificationPermissions = requireContext().getNotificationPermissions()
                requireContext().rationaleDialog(
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

        val app = (requireContext().applicationContext as GeeksvilleApplication)
        val isGooglePlayAvailable = requireContext().isGooglePlayAvailable()
        val isAnalyticsAllowed = app.isAnalyticsAllowed && isGooglePlayAvailable

        // Set analytics checkbox
        binding.analyticsOkayCheckbox.isEnabled = isGooglePlayAvailable
        binding.analyticsOkayCheckbox.isChecked = isAnalyticsAllowed

        binding.analyticsOkayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            debug("User changed analytics to $isChecked")
            app.isAnalyticsAllowed = isChecked
            binding.reportBugButton.isEnabled = isAnalyticsAllowed
        }

        // report bug button only enabled if analytics is allowed
        binding.reportBugButton.isEnabled = isAnalyticsAllowed
        binding.reportBugButton.setOnClickListener(::showReportBugDialog)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showReportBugDialog(view: View) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.report_a_bug)
            .setMessage(getString(R.string.report_bug_text))
            .setNeutralButton(R.string.cancel) { _, _ ->
                debug("Decided not to report a bug")
            }
            .setPositiveButton(getString(R.string.report)) { _, _ ->
                reportError("Clicked Report A Bug")
                model.showSnackbar("Bug report sent!")
            }
            .show()
    }

    private var tapCount = 0
    private var lastTapTime: Long = 0

    private fun addDeviceButton(device: BTScanModel.DeviceListEntry, enabled: Boolean) {
        val b = RadioButton(requireActivity())
        b.text = device.name
        b.id = View.generateViewId()
        b.isEnabled = enabled
        b.isChecked = device.fullAddress == scanModel.selectedNotNull
        binding.deviceRadioGroup.addView(b)

        b.setOnClickListener {
            if (device.fullAddress == "n") {
                val currentTapTime = System.currentTimeMillis()
                if (currentTapTime - lastTapTime > TAP_THRESHOLD) {
                    tapCount = 0
                }
                lastTapTime = currentTapTime
                tapCount++

                if (tapCount >= TAP_TRIGGER) {
                    model.showSnackbar("Demo Mode enabled")
                    scanModel.showMockInterface()
                }
            }
            if (!device.bonded) { // If user just clicked on us, try to bond
                binding.scanStatusText.setText(R.string.starting_pairing)
            }
            b.isChecked = scanModel.onSelected(device)
        }
    }

    private fun addManualDeviceButton() {
        val deviceSelectIPAddress = binding.radioButtonManual
        val inputIPAddress = binding.editManualAddress

        deviceSelectIPAddress.isEnabled = inputIPAddress.text.isIPAddress()
        deviceSelectIPAddress.setOnClickListener {
            deviceSelectIPAddress.isChecked = scanModel.onSelected(BTScanModel.DeviceListEntry("", "t" + inputIPAddress.text, true))
        }

        binding.deviceRadioGroup.addView(deviceSelectIPAddress)
        binding.deviceRadioGroup.addView(inputIPAddress)

        inputIPAddress.doAfterTextChanged {
            deviceSelectIPAddress.isEnabled = inputIPAddress.text.isIPAddress()
        }
    }

    private fun updateDevicesButtons(devices: MutableMap<String, BTScanModel.DeviceListEntry>?) {
        // Remove the old radio buttons and repopulate
        binding.deviceRadioGroup.removeAllViews()

        if (devices == null) return

        var hasShownOurDevice = false
        devices.values.forEach { device ->
            if (device.fullAddress == scanModel.selectedNotNull) {
                hasShownOurDevice = true
            }
            addDeviceButton(device, true)
        }

        // The selected device is not in the scan; it is either offline, or it doesn't advertise
        // itself (most BLE devices don't advertise when connected).
        // Show it in the list, greyed out based on connection status.
        if (!hasShownOurDevice) {
            // Note: we pull this into a tempvar, because otherwise some other thread can change selectedAddress after our null check
            // and before use
            val curAddr = scanModel.selectedAddress
            if (curAddr != null) {
                val curDevice = BTScanModel.DeviceListEntry(curAddr.substring(1), curAddr, false)
                addDeviceButton(curDevice, model.isConnected())
            }
        }

        addManualDeviceButton()

        // get rid of the warning text once at least one device is paired.
        // If we are running on an emulator, always leave this message showing so we can test the worst case layout
        val curRadio = scanModel.selectedAddress

        if (curRadio != null && curRadio != "m") {
            binding.warningNotPaired.visibility = View.GONE
        } else if (bluetoothViewModel.enabled.value == true) {
            binding.warningNotPaired.visibility = View.VISIBLE
            scanModel.setErrorText(getString(R.string.not_paired_yet))
        }
    }

    // per https://developer.android.com/guide/topics/connectivity/bluetooth/find-ble-devices
    private var scanning = false
    private fun scanLeDevice() {
        if (!checkBTEnabled()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) checkLocationEnabled()

        if (!scanning) { // Stops scanning after a pre-defined scan period.
            Handler(Looper.getMainLooper()).postDelayed({
                scanning = false
                scanModel.stopScan()
            }, SCAN_PERIOD)
            scanning = true
            scanModel.startScan()
        } else {
            scanning = false
            scanModel.stopScan()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initCommonUI()

        val requestPermissionAndScanLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) {
                    info("Bluetooth permissions granted")
                    scanLeDevice()
                } else {
                    warn("Bluetooth permissions denied")
                    model.showSnackbar(requireContext().permissionMissing)
                }
                bluetoothViewModel.permissionsUpdated()
            }

        binding.changeRadioButton.setOnClickListener {
            debug("User clicked changeRadioButton")
            val bluetoothPermissions = requireContext().getBluetoothPermissions()
            if (bluetoothPermissions.isEmpty()) {
                scanLeDevice()
            } else {
                requireContext().rationaleDialog(
                    shouldShowRequestPermissionRationale(bluetoothPermissions)
                ) {
                    requestPermissionAndScanLauncher.launch(bluetoothPermissions)
                }
            }
        }
    }

    // If the user has not turned on location access throw up a warning
    private fun checkLocationEnabled(
        // Default warning valid only for classic bluetooth scan
        warningReason: String = getString(R.string.location_disabled_warning)
    ) {
        if (requireContext().gpsDisabled()) {
            warn("Telling user we need location access")
            model.showSnackbar(warningReason)
        }
    }

    private fun checkBTEnabled(): Boolean = (bluetoothViewModel.enabled.value == true).also { enabled ->
        if (!enabled) {
            warn("Telling user bluetooth is disabled")
            model.showSnackbar(R.string.bluetooth_disabled)
        }
    }

    override fun onResume() {
        super.onResume()

        val beaconing = getPreferences(requireContext())
            .getBoolean(PREF_STRESSTEST_ENABLED, false)

        val locationCheckbox = binding.provideLocationCheckbox
        // Warn user if BLE device is selected but BLE disabled
        if (scanModel.selectedBluetooth) checkBTEnabled()

        // Warn user if provide location is selected but location disabled
        if (locationCheckbox.isChecked) {
            checkLocationEnabled(getString(R.string.location_disabled))
        }

        locationCheckbox.isEnabled = !beaconing
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val SCAN_PERIOD: Long = 10000 // Stops scanning after 10 seconds
        private const val TAP_TRIGGER: Int = 7
        private const val TAP_THRESHOLD: Long = 500 // max 500 ms between taps
    }

    private fun Editable.isIPAddress(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            InetAddresses.isNumericAddress(this.toString())
        } else {
            @Suppress("DEPRECATION")
            Patterns.IP_ADDRESS.matcher(this).matches()
        }
    }

    private fun Intent?.getPickedRingtoneUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            this?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
    }
}
