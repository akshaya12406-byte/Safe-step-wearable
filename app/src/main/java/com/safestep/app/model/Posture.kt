package com.safestep.app.model

import com.google.firebase.Timestamp

/**
 * Posture data model representing current posture state from ESP32.
 * 
 * Firestore path: devices/{device_id}/posture/latest
 * 
 * The ESP32 updates this document whenever posture changes.
 * App reads it to display current posture status on dashboard.
 */
data class Posture(
    val state: String = "",  // "GOOD", "BAD", "UNKNOWN"
    val duration_seconds: Long = 0,  // How long in this state
    val last_updated: Timestamp? = null,
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    val confidence: Double = 0.0  // 0.0 to 1.0
) {
    /**
     * Check if posture is good.
     */
    fun isGood(): Boolean = state.equals("GOOD", ignoreCase = true)
    
    /**
     * Get human-readable duration.
     */
    fun getDurationText(): String {
        return when {
            duration_seconds < 60 -> "${duration_seconds}s"
            duration_seconds < 3600 -> "${duration_seconds / 60}m"
            else -> "${duration_seconds / 3600}h ${(duration_seconds % 3600) / 60}m"
        }
    }
    
    /**
     * Get formatted state text.
     */
    fun getStateText(): String {
        return when (state.uppercase()) {
            "GOOD" -> "Good Posture"
            "BAD" -> "Poor Posture"
            else -> "Unknown"
        }
    }
    
    /**
     * Get last updated as relative time.
     */
    fun getLastUpdatedText(): String {
        val lastTime = last_updated?.toDate()?.time ?: return "Never"
        val now = System.currentTimeMillis()
        val diffSeconds = (now - lastTime) / 1000
        
        return when {
            diffSeconds < 60 -> "Just now"
            diffSeconds < 3600 -> "${diffSeconds / 60}m ago"
            else -> "${diffSeconds / 3600}h ago"
        }
    }
}
