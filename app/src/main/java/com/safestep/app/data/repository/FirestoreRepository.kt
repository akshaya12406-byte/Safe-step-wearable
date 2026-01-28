package com.safestep.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * FirestoreRepository - Handles reading events and posture from Firestore
 * 
 * Collection Structure:
 * - devices/{device_id}/events/{event_id}
 * - devices/{device_id}/posture/latest
 */
class FirestoreRepository {
    
    private val db = FirebaseFirestore.getInstance()
    
    /**
     * Event data model
     */
    data class FallEvent(
        val eventId: String = "",
        val deviceId: String = "",
        val eventType: String = "",
        val timestamp: String = "",
        val impactG: Double = 0.0,
        val pitch: Double = 0.0,
        val roll: Double = 0.0,
        val acknowledged: Boolean = false,
        val acknowledgedBy: String? = null
    )
    
    /**
     * Posture data model
     */
    data class PostureSnapshot(
        val deviceId: String = "",
        val postureState: String = "", // GOOD, WARNING, POOR
        val pitch: Double = 0.0,
        val roll: Double = 0.0,
        val timestamp: String = "",
        val updatedAt: String = ""
    )
    
    /**
     * Get event history for a device (B3 implementation)
     * 
     * @param deviceId The device ID (e.g., "ESP32_01")
     * @param limit Maximum number of events to fetch
     * @param eventTypeFilter Optional filter: "FALL_CONFIRMED", "POSTURE_BAD", or null for all
     * @return List of FallEvent objects
     */
    suspend fun getEventHistory(
        deviceId: String,
        limit: Int = 50,
        eventTypeFilter: String? = null
    ): List<FallEvent> {
        return try {
            var query = db.collection("devices")
                .document(deviceId)
                .collection("events")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
            
            // Apply event type filter if specified
            if (eventTypeFilter != null) {
                query = db.collection("devices")
                    .document(deviceId)
                    .collection("events")
                    .whereEqualTo("event_type", eventTypeFilter)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
            }
            
            val snapshot = query.get().await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    FallEvent(
                        eventId = doc.id,
                        deviceId = doc.getString("device_id") ?: deviceId,
                        eventType = doc.getString("event_type") ?: "UNKNOWN",
                        timestamp = doc.getString("timestamp") ?: "",
                        impactG = doc.getDouble("impact_g") ?: 0.0,
                        pitch = doc.getDouble("pitch") ?: 0.0,
                        roll = doc.getDouble("roll") ?: 0.0,
                        acknowledged = doc.getBoolean("acknowledged") ?: false,
                        acknowledgedBy = doc.getString("acknowledged_by")
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get real-time event updates as Flow
     */
    fun getEventHistoryFlow(deviceId: String, limit: Int = 20): Flow<List<FallEvent>> = callbackFlow {
        val listener = db.collection("devices")
            .document(deviceId)
            .collection("events")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        FallEvent(
                            eventId = doc.id,
                            deviceId = doc.getString("device_id") ?: deviceId,
                            eventType = doc.getString("event_type") ?: "UNKNOWN",
                            timestamp = doc.getString("timestamp") ?: "",
                            impactG = doc.getDouble("impact_g") ?: 0.0,
                            pitch = doc.getDouble("pitch") ?: 0.0,
                            roll = doc.getDouble("roll") ?: 0.0,
                            acknowledged = doc.getBoolean("acknowledged") ?: false,
                            acknowledgedBy = doc.getString("acknowledged_by")
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                
                trySend(events)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get latest posture snapshot for a device
     */
    suspend fun getLatestPosture(deviceId: String): PostureSnapshot? {
        return try {
            val doc = db.collection("devices")
                .document(deviceId)
                .collection("posture")
                .document("latest")
                .get()
                .await()
            
            if (doc.exists()) {
                PostureSnapshot(
                    deviceId = doc.getString("device_id") ?: deviceId,
                    postureState = doc.getString("posture_state") ?: "UNKNOWN",
                    pitch = doc.getDouble("pitch") ?: 0.0,
                    roll = doc.getDouble("roll") ?: 0.0,
                    timestamp = doc.getString("timestamp") ?: "",
                    updatedAt = doc.getString("updated_at") ?: ""
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get real-time posture updates as Flow
     */
    fun getPostureFlow(deviceId: String): Flow<PostureSnapshot?> = callbackFlow {
        val listener = db.collection("devices")
            .document(deviceId)
            .collection("posture")
            .document("latest")
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val posture = if (doc != null && doc.exists()) {
                    PostureSnapshot(
                        deviceId = doc.getString("device_id") ?: deviceId,
                        postureState = doc.getString("posture_state") ?: "UNKNOWN",
                        pitch = doc.getDouble("pitch") ?: 0.0,
                        roll = doc.getDouble("roll") ?: 0.0,
                        timestamp = doc.getString("timestamp") ?: "",
                        updatedAt = doc.getString("updated_at") ?: ""
                    )
                } else {
                    null
                }
                
                trySend(posture)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Acknowledge an event
     */
    suspend fun acknowledgeEvent(deviceId: String, eventId: String, acknowledgedBy: String): Boolean {
        return try {
            db.collection("devices")
                .document(deviceId)
                .collection("events")
                .document(eventId)
                .update(
                    mapOf(
                        "acknowledged" to true,
                        "acknowledged_by" to acknowledgedBy,
                        "acknowledged_at" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
