package com.safestep.app.model

import com.google.firebase.Timestamp

data class Event(
    val event_id: String = "",
    val device_id: String = "",
    val event_type: String = "",
    val timestamp: String = "", // ISO string or Timestamp from Firestore
    val impact_g: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    val handled: Boolean = false,
    val acknowledged_by: String? = null,
    val firmware_version: String? = null
)
