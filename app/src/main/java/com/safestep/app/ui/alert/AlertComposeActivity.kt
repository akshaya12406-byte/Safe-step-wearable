package com.safestep.app.ui.alert

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.safestep.app.ui.theme.SafeStepAlertTheme

/**
 * AlertActivity - Full-screen emergency alert powered by Jetpack Compose
 * 
 * Features:
 * - Shows over lock screen
 * - Wakes device
 * - Handles CALL and ACKNOWLEDGE actions
 * - Respects Demo Mode (no real calls)
 * - Writes acknowledgment to Firestore
 * 
 * Launched by: SafeStepFirebaseService via full-screen intent
 */
class AlertComposeActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_IMPACT_G = "impact_g"
        const val EXTRA_PITCH = "pitch"
        const val EXTRA_ROLL = "roll"
        const val EXTRA_EVENT_TYPE = "event_type"
        
        private const val PREF_EMERGENCY_NUMBER = "emergency_number"
        private const val PREF_DEMO_MODE = "demo_mode"
        private const val DEFAULT_EMERGENCY_NUMBER = "911"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable showing over lock screen
        setupLockScreenBehavior()
        enableEdgeToEdge()
        
        // Extract intent extras
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: "FALL_CONFIRMED"
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: "Unknown Device"
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""
        val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP) ?: ""
        val impactG = intent.getStringExtra(EXTRA_IMPACT_G) ?: ""
        val pitch = intent.getStringExtra(EXTRA_PITCH) ?: ""
        val roll = intent.getStringExtra(EXTRA_ROLL) ?: ""
        
        // Load preferences
        val prefs = getSharedPreferences("safestep_prefs", MODE_PRIVATE)
        val isDemoMode = prefs.getBoolean(PREF_DEMO_MODE, false)
        val emergencyNumber = prefs.getString(PREF_EMERGENCY_NUMBER, DEFAULT_EMERGENCY_NUMBER) 
            ?: DEFAULT_EMERGENCY_NUMBER
        
        setContent {
            SafeStepAlertTheme {
                AlertScreen(
                    eventType = eventType,
                    deviceId = deviceId,
                    timestamp = timestamp,
                    impactG = impactG,
                    pitch = pitch,
                    roll = roll,
                    eventId = eventId,
                    isDemoMode = isDemoMode,
                    emergencyNumber = emergencyNumber,
                    onCallClick = { handleCallClick(isDemoMode, emergencyNumber) },
                    onAcknowledgeClick = { handleAcknowledgeClick(deviceId, eventId) },
                    onDismissClick = { handleDismissClick() }
                )
            }
        }
    }
    
    private fun setupLockScreenBehavior() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    private fun handleCallClick(isDemoMode: Boolean, emergencyNumber: String) {
        if (isDemoMode) {
            Toast.makeText(
                this,
                "Demo Mode: Would call $emergencyNumber",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Check CALL_PHONE permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) 
            == PackageManager.PERMISSION_GRANTED) {
            // Place call
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$emergencyNumber")
            }
            startActivity(callIntent)
        } else {
            // Open dialer instead
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$emergencyNumber")
            }
            startActivity(dialIntent)
            
            Toast.makeText(
                this,
                "Grant call permission in Settings for auto-dial",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun handleAcknowledgeClick(deviceId: String, eventId: String) {
        // Update Firestore event as handled
        if (eventId.isNotEmpty() && deviceId.isNotEmpty()) {
            FirebaseFirestore.getInstance()
                .collection("devices")
                .document(deviceId)
                .collection("events")
                .document(eventId)
                .update(
                    mapOf(
                        "handled" to true,
                        "acknowledged_by" to "caregiver_app",
                        "acknowledged_at" to com.google.firebase.Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    Toast.makeText(this, "Alert acknowledged", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    // Still close, acknowledgment is secondary
                }
        }
        
        finish()
    }
    
    private fun handleDismissClick() {
        Toast.makeText(this, "Alert dismissed", Toast.LENGTH_SHORT).show()
        finish()
    }
}
