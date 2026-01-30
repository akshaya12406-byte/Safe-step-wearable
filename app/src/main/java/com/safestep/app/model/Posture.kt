package com.safestep.app.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * Posture data model representing current posture state from ESP32.
 * 
 * Firestore path: devices/{device_id}/posture/latest
 * 
 * The ESP32/Worker writes these fields:
 * - posture_state: "GOOD", "FAIR", "POOR"
 * - pitch: Double
 * - roll: Double
 * - timestamp: Timestamp (NOT String! Worker sends timestampValue)
 * - updated_at: Timestamp
 */
data class Posture(
    @get:PropertyName("posture_state")
    @set:PropertyName("posture_state")
    var posture_state: String = "",
    
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    
    // CRITICAL: Worker sends this as timestampValue, NOT String!
    val timestamp: Timestamp? = null,
    
    val updated_at: Timestamp? = null,
    val device_id: String = ""
) {
    // For backward compatibility
    val state: String get() = posture_state
    
    /**
     * Check if posture is good.
     */
    fun isGood(): Boolean = posture_state.equals("GOOD", ignoreCase = true)
    
    /**
     * Get duration text (placeholder - ESP32 doesn't send duration).
     */
    fun getDurationText(): String = "0s"
    
    /**
     * Get formatted state text.
     */
    fun getStateText(): String {
        return when (posture_state.uppercase()) {
            "GOOD" -> "Good Posture"
            "FAIR" -> "Fair Posture"
            "POOR", "BAD" -> "Poor Posture"
            else -> "Unknown"
        }
    }
    
    /**
     * Get last updated as relative time.
     */
    fun getLastUpdatedText(): String {
        val lastTime = updated_at?.toDate()?.time ?: timestamp?.toDate()?.time ?: return "Never"
        val now = System.currentTimeMillis()
        val diffSeconds = (now - lastTime) / 1000
        
        return when {
            diffSeconds < 60 -> "Just now"
            diffSeconds < 3600 -> "${diffSeconds / 60}m ago"
            else -> "${diffSeconds / 3600}h ago"
        }
    }
    
    /**
     * Get formatted timestamp for display.
     */
    fun getFormattedTimestamp(): String {
        val time = timestamp?.toDate() ?: return "Unknown"
        val now = System.currentTimeMillis()
        val diffSeconds = (now - time.time) / 1000
        
        return when {
            diffSeconds < 60 -> "Just now"
            diffSeconds < 3600 -> "${diffSeconds / 60}m ago"
            else -> "${diffSeconds / 3600}h ago"
        }
    }
    
    // No-arg constructor required for Firestore deserialization
    constructor() : this(posture_state = "")
}


