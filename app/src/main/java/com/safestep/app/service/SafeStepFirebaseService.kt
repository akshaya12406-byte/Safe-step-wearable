package com.safestep.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.safestep.app.R
import com.safestep.app.ui.alert.AlertComposeActivity

/**
 * SafeStepFirebaseService handles incoming FCM messages for fall detection alerts.
 * 
 * Key features:
 * - Parses data-only payloads (works in foreground and background)
 * - Creates high-priority notifications with full-screen intent
 * - Wakes device even when locked
 * - Passes event data to AlertComposeActivity (Jetpack Compose UI)
 * 
 * FCM Payload expected format:
 * {
 *   "to": "<token>",
 *   "priority": "high",
 *   "data": {
 *     "event_type": "FALL_CONFIRMED",
 *     "device_id": "ESP32_01",
 *     "timestamp": "2026-01-27T15:50:00Z",
 *     "impact_g": "3.05",
 *     "pitch": "12.4",
 *     "roll": "5.1",
 *     "event_id": "evt_123456"
 *   }
 * }
 */
class SafeStepFirebaseService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SafeStepFCM"
        const val CHANNEL_ID = "CHANNEL_EMERGENCY"
        
        // Data payload keys
        const val KEY_EVENT_TYPE = "event_type"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_IMPACT_G = "impact_g"
        const val KEY_PITCH = "pitch"
        const val KEY_ROLL = "roll"
        const val KEY_EVENT_ID = "event_id"
        
        // Event types
        const val EVENT_FALL_CONFIRMED = "FALL_CONFIRMED"
        const val EVENT_IMPACT_ALERT = "IMPACT_ALERT"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Message received from: ${remoteMessage.from}")
        Log.d(TAG, "Data payload: ${remoteMessage.data}")

        // Only process messages with data payload
        if (remoteMessage.data.isNotEmpty()) {
            processDataPayload(remoteMessage.data)
        }
    }

    /**
     * Process the data payload and determine action based on event type.
     */
    private fun processDataPayload(data: Map<String, String>) {
        val eventType = data[KEY_EVENT_TYPE] ?: return
        
        Log.d(TAG, "Processing event type: $eventType")
        
        when (eventType) {
            EVENT_FALL_CONFIRMED, EVENT_IMPACT_ALERT -> {
                showEmergencyNotification(data)
            }
            else -> {
                Log.w(TAG, "Unknown event type: $eventType")
            }
        }
    }

    /**
     * Creates and displays a high-priority notification with full-screen intent.
     * This will wake the device and show AlertComposeActivity even when locked.
     */
    private fun showEmergencyNotification(data: Map<String, String>) {
        val deviceId = data[KEY_DEVICE_ID] ?: "Unknown Device"
        val eventId = data[KEY_EVENT_ID] ?: "evt_${System.currentTimeMillis()}"
        val timestamp = data[KEY_TIMESTAMP] ?: ""
        val impactG = data[KEY_IMPACT_G] ?: ""
        val pitch = data[KEY_PITCH] ?: ""
        val roll = data[KEY_ROLL] ?: ""
        val eventType = data[KEY_EVENT_TYPE] ?: EVENT_FALL_CONFIRMED
        
        // Create intent for AlertComposeActivity with all event data
        val alertIntent = Intent(this, AlertComposeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlertComposeActivity.EXTRA_DEVICE_ID, deviceId)
            putExtra(AlertComposeActivity.EXTRA_EVENT_ID, eventId)
            putExtra(AlertComposeActivity.EXTRA_TIMESTAMP, timestamp)
            putExtra(AlertComposeActivity.EXTRA_IMPACT_G, impactG)
            putExtra(AlertComposeActivity.EXTRA_PITCH, pitch)
            putExtra(AlertComposeActivity.EXTRA_ROLL, roll)
            putExtra(AlertComposeActivity.EXTRA_EVENT_TYPE, eventType)
        }

        // Full-screen pending intent with immutable flag
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // Unique request code
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Standard pending intent for notification tap
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the high-priority notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fall_alert)
            .setContentTitle(getString(R.string.alert_title))
            .setContentText(getString(R.string.alert_subtitle) + " - $deviceId")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Fall detected on device $deviceId\nImpact: ${impactG}g\nTime: $timestamp"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setColor(getColor(R.color.alertPrimary))
            .build()

        // Display notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = eventId.hashCode()
        notificationManager.notify(notificationId, notification)
        
        Log.d(TAG, "Emergency notification displayed with ID: $notificationId")
    }

    /**
     * Called when FCM token is refreshed.
     * In production, this should update the server with the new token.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM Token refreshed: $token")
        // TODO: In production, send this token to your backend
        // For prototype, token is displayed in Developer Mode
    }
}
