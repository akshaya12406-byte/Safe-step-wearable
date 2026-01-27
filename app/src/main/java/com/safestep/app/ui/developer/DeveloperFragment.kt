package com.safestep.app.ui.developer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.messaging.FirebaseMessaging
import com.safestep.app.R
import com.safestep.app.ui.AlertActivity

/**
 * DeveloperFragment provides developer tools for testing.
 * Access via 7-tap on version + PIN confirmation in Settings.
 * 
 * Features:
 * - FCM token display (masked by default, full on long-press)
 * - Copy token to clipboard
 * - Simulate fall event (opens AlertActivity directly)
 * - Send test notification (local)
 */
class DeveloperFragment : Fragment() {

    private lateinit var tvFcmToken: TextView
    private lateinit var btnCopyToken: MaterialButton
    private lateinit var btnSimulateEvent: MaterialButton
    private lateinit var btnSendTestNotification: MaterialButton
    
    private var fullToken: String = ""
    private var isTokenMasked = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_developer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvFcmToken = view.findViewById(R.id.tvFcmToken)
        btnCopyToken = view.findViewById(R.id.btnCopyToken)
        btnSimulateEvent = view.findViewById(R.id.btnSimulateEvent)
        btnSendTestNotification = view.findViewById(R.id.btnSendTestNotification)

        loadFcmToken()
        setupListeners()
    }

    private fun loadFcmToken() {
        tvFcmToken.text = "Loading..."
        
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                tvFcmToken.text = "Failed to get token"
                return@addOnCompleteListener
            }
            
            fullToken = task.result ?: ""
            displayToken()
        }
    }

    private fun displayToken() {
        if (isTokenMasked && fullToken.length > 20) {
            // Show first 10 and last 10 characters
            val masked = "${fullToken.take(10)}...${fullToken.takeLast(10)}"
            tvFcmToken.text = masked
        } else {
            tvFcmToken.text = fullToken
        }
    }

    private fun setupListeners() {
        // Toggle mask on long-press
        tvFcmToken.setOnLongClickListener {
            isTokenMasked = !isTokenMasked
            displayToken()
            Toast.makeText(
                requireContext(),
                if (isTokenMasked) "Token masked" else "Token revealed",
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        // Copy token
        btnCopyToken.setOnClickListener {
            if (fullToken.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("FCM Token", fullToken)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), getString(R.string.token_copied), Toast.LENGTH_SHORT).show()
            }
        }

        // Simulate event - directly opens AlertActivity
        btnSimulateEvent.setOnClickListener {
            val intent = Intent(requireContext(), AlertActivity::class.java).apply {
                putExtra(AlertActivity.EXTRA_DEVICE_ID, "ESP32_DEMO")
                putExtra(AlertActivity.EXTRA_EVENT_ID, "evt_test_${System.currentTimeMillis()}")
                putExtra(AlertActivity.EXTRA_TIMESTAMP, java.time.Instant.now().toString())
                putExtra(AlertActivity.EXTRA_IMPACT_G, "3.45")
                putExtra(AlertActivity.EXTRA_PITCH, "15.2")
                putExtra(AlertActivity.EXTRA_ROLL, "8.7")
                putExtra(AlertActivity.EXTRA_EVENT_TYPE, "FALL_CONFIRMED")
            }
            startActivity(intent)
        }

        // Send test notification (via local broadcast)
        btnSendTestNotification.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Test notification would be sent here.\nUse the test harness script for FCM testing.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
