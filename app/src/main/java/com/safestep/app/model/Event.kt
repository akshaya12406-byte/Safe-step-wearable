package com.safestep.app.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * Event data model matching Firestore document structure.
 * 
 * Firestore path: devices/{device_id}/events/{event_id}
 * 
 * IMPORTANT: All timestamp fields are stored as Firestore Timestamp type.
 * The Worker writes: event_type, timestamp, impact_g, pitch, roll, acknowledged, etc.
 */
data class Event(
    val event_id: String = "",
    val device_id: String = "",
    val event_type: String = "",
    
    // Worker sends this as timestampValue - MUST be Timestamp, not String!
    val timestamp: Timestamp? = null,
    
    val impact_g: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    val firmware_version: String = "",
    
    // Firestore uses "acknowledged", we map it to "handled" for code readability
    @get:PropertyName("acknowledged")
    @set:PropertyName("acknowledged")
    var handled: Boolean = false,
    
    val acknowledged_by: String? = null,
    val acknowledged_at: Timestamp? = null,
    
    // Firestore stores this as Timestamp type
    val created_at: Timestamp? = null
) {
    // No-arg constructor required for Firestore deserialization
    constructor() : this(event_id = "")
    
    /**
     * Get formatted timestamp string for display.
     */
    fun getFormattedTimestamp(): String {
        val time = timestamp?.toDate() ?: return "Unknown"
        val now = System.currentTimeMillis()
        val diffMinutes = (now - time.time) / (1000 * 60)
        
        return when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
            else -> "${diffMinutes / 1440}d ago"
        }
    }
}

