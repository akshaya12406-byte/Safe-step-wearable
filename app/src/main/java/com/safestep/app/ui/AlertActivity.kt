package com.safestep.app.ui

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.safestep.app.R
import com.safestep.app.data.EventRepository
import com.safestep.app.databinding.ActivityAlertBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * AlertActivity displays a full-screen emergency alert when a fall is detected.
 * 
 * Features:
 * - Wakes device even when screen is off or locked
 * - Large, accessible buttons for elderly users
 * - Demo Mode prevents real calls during testing
 * - Auto-call with explicit opt-in and permission check
 * - Marks events as handled in Firestore
 */
class AlertActivity : AppCompatActivity() {

    companion object {
        // Intent extras
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_IMPACT_G = "impact_g"
        const val EXTRA_PITCH = "pitch"
        const val EXTRA_ROLL = "roll"
        const val EXTRA_EVENT_TYPE = "event_type"
        
        // Preferences
        private const val PREFS_NAME = "com.safestep.app.PREFS"
        private const val KEY_AUTO_CALL = "auto_call_enabled"
        private const val KEY_DEMO_MODE = "demo_mode_enabled"
        private const val KEY_EMERGENCY_NUMBER = "emergency_number"
    }

    private lateinit var binding: ActivityAlertBinding
    private val eventRepository = EventRepository()
    
    private var deviceId: String? = null
    private var eventId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Wake up device and show over lock screen
        turnOnScreen()
        
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Parse intent extras
        parseIntentData()
        
        // Setup button listeners
        setupButtons()
        
        // Cancel any ongoing notification for this event
        cancelNotification()
    }

    /**
     * Configure window to wake device and show over lock screen.
     */
    private fun turnOnScreen() {
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
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        
        // Keep screen on while alert is displayed
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Parse event data from intent extras.
     */
    private fun parseIntentData() {
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP)
        val impactG = intent.getStringExtra(EXTRA_IMPACT_G)
        val pitch = intent.getStringExtra(EXTRA_PITCH)
        val roll = intent.getStringExtra(EXTRA_ROLL)
        
        // Update UI with event data
        binding.tvDeviceInfo.text = "Device: ${deviceId ?: "Unknown"}"
        binding.tvTimestamp.text = formatTimestamp(timestamp)
        binding.tvImpactG.text = "${impactG ?: "--"}g"
        binding.tvPitch.text = "${pitch ?: "--"}°"
        binding.tvRoll.text = "${roll ?: "--"}°"
    }

    /**
     * Format ISO timestamp to readable format.
     */
    private fun formatTimestamp(isoTimestamp: String?): String {
        if (isoTimestamp.isNullOrEmpty()) return "Just now"
        
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val outputFormat = SimpleDateFormat("h:mm a", Locale.US)
            val date = inputFormat.parse(isoTimestamp)
            date?.let { outputFormat.format(it) } ?: "Just now"
        } catch (e: Exception) {
            "Just now"
        }
    }

    /**
     * Setup click listeners for action buttons.
     */
    private fun setupButtons() {
        binding.btnCall.setOnClickListener {
            initiateEmergencyCall()
        }
        
        binding.btnAcknowledge.setOnClickListener {
            markAsHandled("acknowledged")
            Toast.makeText(this, "Marked as safe", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        binding.btnDismiss.setOnClickListener {
            markAsHandled("dismissed")
            finish()
        }
    }

    /**
     * Initiate emergency call with proper permission and demo mode handling.
     * 
     * Safety rules:
     * 1. Demo Mode ON → Always show toast, never call
     * 2. Demo Mode OFF + Auto-call OFF → Open dialer (ACTION_DIAL)
     * 3. Demo Mode OFF + Auto-call ON + Permission GRANTED → Direct call (ACTION_CALL)
     * 4. Demo Mode OFF + Auto-call ON + Permission DENIED → Fallback to dialer
     */
    private fun initiateEmergencyCall() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val demoModeEnabled = prefs.getBoolean(KEY_DEMO_MODE, false) // Default OFF per spec
        val autoCallEnabled = prefs.getBoolean(KEY_AUTO_CALL, false)
        val emergencyNumber = prefs.getString(KEY_EMERGENCY_NUMBER, "911") ?: "911"
        
        // DEMO MODE: Always show toast, never make real call
        if (demoModeEnabled) {
            Toast.makeText(
                this,
                getString(R.string.demo_mode_toast, emergencyNumber),
                Toast.LENGTH_LONG
            ).show()
            markAsHandled("demo_call")
            return
        }
        
        // Check if auto-call is enabled and permission is granted
        val hasCallPermission = ContextCompat.checkSelfPermission(
            this, 
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        
        if (autoCallEnabled && hasCallPermission) {
            // Direct call with ACTION_CALL
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$emergencyNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(callIntent)
        } else {
            // Fallback to dialer with ACTION_DIAL (safe, no permission needed)
            if (autoCallEnabled && !hasCallPermission) {
                Toast.makeText(
                    this,
                    getString(R.string.call_permission_missing),
                    Toast.LENGTH_SHORT
                ).show()
            }
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$emergencyNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(dialIntent)
        }
        
        markAsHandled("call_initiated")
    }

    /**
     * Mark the event as handled in Firestore.
     */
    private fun markAsHandled(handledBy: String) {
        if (deviceId != null && eventId != null) {
            eventRepository.markEventHandled(deviceId!!, eventId!!, handledBy)
        }
    }

    /**
     * Cancel the notification for this event.
     */
    private fun cancelNotification() {
        eventId?.let { id ->
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id.hashCode())
        }
    }

    /**
     * Prevent back button from dismissing the alert too easily.
     * User must use the action buttons.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Require explicit action - don't allow back button dismissal
        Toast.makeText(this, "Please choose an action", Toast.LENGTH_SHORT).show()
    }
}
