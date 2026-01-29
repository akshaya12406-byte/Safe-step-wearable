package com.safestep.app.model

import com.google.firebase.firestore.PropertyName

/**
 * Event data model matching Firestore document structure.
 * 
 * Firestore path: devices/{device_id}/events/{event_id}
 * 
 * IMPORTANT: Field names must match EXACTLY with Firestore document keys.
 * The Worker writes: event_type, timestamp, impact_g, pitch, roll, acknowledged, etc.
 */
data class Event(
    val event_id: String = "",
    val device_id: String = "",
    val event_type: String = "",
    val timestamp: String = "",
    val impact_g: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    val firmware_version: String = "",
    
    // Firestore uses "acknowledged", we map it to "handled" for code readability
    @get:PropertyName("acknowledged")
    @set:PropertyName("acknowledged")
    var handled: Boolean = false,
    
    val acknowledged_by: String? = null,
    val acknowledged_at: String? = null,
    val created_at: String = ""
) {
    // No-arg constructor required for Firestore deserialization
    constructor() : this(event_id = "")
}
