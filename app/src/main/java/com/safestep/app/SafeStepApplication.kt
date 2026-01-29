package com.safestep.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.safestep.app.data.DeviceRepository

class SafeStepApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ensureDevicePaired()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Emergency Alerts"
            val descriptionText = "High priority alerts for fall detection"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("CHANNEL_EMERGENCY", name, importance).apply {
                description = descriptionText
                // Important for full screen intent to work in some cases
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Ensure ESP32_01 is paired by default for demo/hackathon.
     * This allows the dashboard to show data immediately without manual pairing.
     */
    private fun ensureDevicePaired() {
        val deviceRepository = DeviceRepository(this)
        val pairedDevices = deviceRepository.getPairedDeviceIds()
        
        // Auto-pair ESP32_01 if no devices are paired
        if (pairedDevices.isEmpty()) {
            deviceRepository.addPairedDevice("ESP32_01")
        }
    }
}

