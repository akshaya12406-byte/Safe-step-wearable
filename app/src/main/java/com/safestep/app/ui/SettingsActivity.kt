package com.safestep.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.safestep.app.R

class SettingsActivity : AppCompatActivity() {

    private val PREFS_NAME = "com.safestep.app.PREFS"
    private val KEY_AUTO_CALL = "auto_call_enabled"
    private val KEY_NUMBER = "emergency_number"
    private val KEY_DEMO_MODE = "demo_mode_enabled"
    private val PERM_REQUEST_CODE = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val etNumber = findViewById<EditText>(R.id.etEmergencyNumber)
        val switchAutoCall = findViewById<Switch>(R.id.switchAutoCall)
        val switchDemoMode = findViewById<Switch>(R.id.switchDemoMode)
        val btnSave = findViewById<Button>(R.id.btnSaveSettings)

        // Load existing
        etNumber.setText(prefs.getString(KEY_NUMBER, "911"))
        switchAutoCall.isChecked = prefs.getBoolean(KEY_AUTO_CALL, false)
        switchDemoMode.isChecked = prefs.getBoolean(KEY_DEMO_MODE, true) // Default ON for safety

        switchAutoCall.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Check permission
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), PERM_REQUEST_CODE)
                    switchAutoCall.isChecked = false // Reset until granted
                    Toast.makeText(this, "Please grant Call Phone permission first", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnSave.setOnClickListener {
            val number = etNumber.text.toString()
            if (number.isBlank()) {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().apply {
                putString(KEY_NUMBER, number.trim())
                putBoolean(KEY_AUTO_CALL, switchAutoCall.isChecked)
                putBoolean(KEY_DEMO_MODE, switchDemoMode.isChecked)
                apply()
            }
            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                findViewById<Switch>(R.id.switchAutoCall).isChecked = true
                Toast.makeText(this, "Permission Granted. Auto-call enabled.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission Denied. Auto-call cannot be enabled.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
