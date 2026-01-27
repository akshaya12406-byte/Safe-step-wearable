package com.safestep.app.model

import com.google.firebase.Timestamp

/**
 * Device model representing a paired SafeStep wearable device.
 * 
 * Firestore path: devices/{device_id}/meta/info
 */
data class Device(
    val device_id: String = "",
    val name: String = "",
    val last_seen: Timestamp? = null,
    val battery_pct: Int = -1,
    val fw_version: String = "",
    val fcm_token: String = "",
    val is_primary: Boolean = false
) {
    /**
     * Calculate device status based on last_seen timestamp.
     * - Online (Green): Last seen < 2 minutes ago
     * - Warning (Amber): Last seen 2-10 minutes ago
     * - Offline (Red): Last seen > 10 minutes ago
     */
    fun getStatus(): DeviceStatus {
        val lastSeenTime = last_seen?.toDate()?.time ?: return DeviceStatus.UNKNOWN
        val now = System.currentTimeMillis()
        val diffMinutes = (now - lastSeenTime) / (1000 * 60)
        
        return when {
            diffMinutes < 2 -> DeviceStatus.ONLINE
            diffMinutes < 10 -> DeviceStatus.WARNING
            else -> DeviceStatus.OFFLINE
        }
    }
    
    /**
     * Get formatted battery text.
     */
    fun getBatteryText(): String {
        return if (battery_pct >= 0) "$battery_pct%" else "—"
    }
    
    /**
     * Get formatted last seen text.
     */
    fun getLastSeenText(): String {
        val lastSeenTime = last_seen?.toDate()?.time ?: return "Never"
        val now = System.currentTimeMillis()
        val diffMinutes = (now - lastSeenTime) / (1000 * 60)
        
        return when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
            else -> "${diffMinutes / 1440}d ago"
        }
    }
}

enum class DeviceStatus {
    ONLINE,
    WARNING,
    OFFLINE,
    UNKNOWN
}
