package com.safestep.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.safestep.app.BuildConfig
import com.safestep.app.R

/**
 * SettingsFragment for configuring emergency contact, call behavior, and device pairing.
 * 
 * Features:
 * - Emergency number configuration
 * - Demo Mode toggle (prevents real calls)
 * - Auto-call toggle (with permission request and consent dialog)
 * - Device pairing navigation
 * - Hidden Developer Mode access (7-tap on version)
 */
class SettingsFragment : Fragment() {

    companion object {
        private const val PREFS_NAME = "com.safestep.app.PREFS"
        private const val KEY_EMERGENCY_NUMBER = "emergency_number"
        private const val KEY_DEMO_MODE = "demo_mode_enabled"
        private const val KEY_AUTO_CALL = "auto_call_enabled"
        private const val KEY_DEV_MODE = "developer_mode_enabled"
        private const val DEV_TAP_COUNT_REQUIRED = 7
        private const val DEV_PIN = "1234" // Default dev PIN - documented in README
    }

    private var devTapCount = 0
    private var lastTapTime = 0L

    private lateinit var etEmergencyNumber: EditText
    private lateinit var switchDemoMode: SwitchMaterial
    private lateinit var switchAutoCall: SwitchMaterial
    private lateinit var tvVersion: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnPairDevice: MaterialButton

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            switchAutoCall.isChecked = true
            saveAutoCallPreference(true)
            Toast.makeText(requireContext(), getString(R.string.permission_granted), Toast.LENGTH_SHORT).show()
        } else {
            switchAutoCall.isChecked = false
            Toast.makeText(requireContext(), getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        etEmergencyNumber = view.findViewById(R.id.etEmergencyNumber)
        switchDemoMode = view.findViewById(R.id.switchDemoMode)
        switchAutoCall = view.findViewById(R.id.switchAutoCall)
        tvVersion = view.findViewById(R.id.tvVersion)
        btnSave = view.findViewById(R.id.btnSave)
        btnPairDevice = view.findViewById(R.id.btnPairDevice)

        loadPreferences()
        setupListeners()
        setupDeveloperModeAccess()
    }

    private fun loadPreferences() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        etEmergencyNumber.setText(prefs.getString(KEY_EMERGENCY_NUMBER, "911"))
        switchDemoMode.isChecked = prefs.getBoolean(KEY_DEMO_MODE, false) // Default OFF per spec
        switchAutoCall.isChecked = prefs.getBoolean(KEY_AUTO_CALL, false)
        
        tvVersion.text = BuildConfig.VERSION_NAME
    }

    private fun setupListeners() {
        // Auto-call toggle with consent dialog and permission check
        switchAutoCall.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // First check if demo mode is on
                if (switchDemoMode.isChecked) {
                    Toast.makeText(requireContext(), "Demo Mode is enabled - calls will be simulated", Toast.LENGTH_SHORT).show()
                }
                
                // Show consent dialog
                showAutoCallConsentDialog()
            } else {
                saveAutoCallPreference(false)
            }
        }

        // Demo mode toggle
        switchDemoMode.setOnCheckedChangeListener { _, isChecked ->
            saveDemoModePreference(isChecked)
        }

        // Pair device button
        btnPairDevice.setOnClickListener {
            findNavController().navigate(R.id.pairingFragment)
        }

        // Save button
        btnSave.setOnClickListener {
            saveAllSettings()
        }
    }

    private fun showAutoCallConsentDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.auto_call_consent_title)
            .setMessage(R.string.auto_call_consent_message)
            .setPositiveButton(R.string.consent_enable) { _, _ ->
                checkAndRequestCallPermission()
            }
            .setNegativeButton(R.string.consent_cancel) { _, _ ->
                switchAutoCall.isChecked = false
            }
            .setOnCancelListener {
                switchAutoCall.isChecked = false
            }
            .show()
    }

    private fun checkAndRequestCallPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                saveAutoCallPreference(true)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE) -> {
                // Show rationale then request
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.permission_call_title)
                    .setMessage(R.string.permission_call_rationale)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->
                        switchAutoCall.isChecked = false
                    }
                    .show()
            }
            else -> {
                // Request permission directly
                requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }

    private fun saveAutoCallPreference(enabled: Boolean) {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_CALL, enabled)
            .apply()
    }

    private fun saveDemoModePreference(enabled: Boolean) {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEMO_MODE, enabled)
            .apply()
    }

    private fun saveAllSettings() {
        val number = etEmergencyNumber.text.toString().trim()
        
        if (number.isBlank()) {
            Toast.makeText(requireContext(), "Please enter a valid number", Toast.LENGTH_SHORT).show()
            return
        }

        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMERGENCY_NUMBER, number)
            .putBoolean(KEY_DEMO_MODE, switchDemoMode.isChecked)
            .putBoolean(KEY_AUTO_CALL, switchAutoCall.isChecked)
            .apply()

        Toast.makeText(requireContext(), getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
    }

    /**
     * Setup 7-tap on version text to enable Developer Mode.
     */
    private fun setupDeveloperModeAccess() {
        tvVersion.setOnClickListener {
            val now = System.currentTimeMillis()
            
            // Reset if more than 2 seconds between taps
            if (now - lastTapTime > 2000) {
                devTapCount = 0
            }
            lastTapTime = now
            devTapCount++

            when {
                devTapCount >= DEV_TAP_COUNT_REQUIRED -> {
                    devTapCount = 0
                    showDevPinDialog()
                }
                devTapCount >= 4 -> {
                    val remaining = DEV_TAP_COUNT_REQUIRED - devTapCount
                    Toast.makeText(requireContext(), "$remaining more taps to Developer Mode", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDevPinDialog() {
        val input = EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = "Enter PIN"

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.enter_dev_pin)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (input.text.toString() == DEV_PIN) {
                    enableDeveloperMode()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.invalid_pin), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun enableDeveloperMode() {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEV_MODE, true)
            .apply()

        Toast.makeText(requireContext(), getString(R.string.dev_mode_enabled), Toast.LENGTH_SHORT).show()
        
        // Navigate to Developer Fragment
        findNavController().navigate(R.id.developerFragment)
    }
}
