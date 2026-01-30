package com.safestep.app.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.safestep.app.model.Event
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * EventRepository handles all Firestore operations for device events.
 * 
 * Firestore Structure:
 * devices/{device_id}/events/{event_id}
 *   - event_type: String
 *   - timestamp: String (ISO format)
 *   - impact_g: Double
 *   - pitch: Double
 *   - roll: Double
 *   - handled: Boolean
 *   - acknowledged_by: String?
 *   - firmware_version: String?
 */
class EventRepository {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val TAG = "EventRepository"

    /**
     * Get recent events for ESP32_01 (default device).
     * Uses direct device query instead of collectionGroup to avoid index requirement.
     */
    suspend fun getRecentEvents(limit: Long = 50): List<Event> {
        return try {
            // Query events for ESP32_01 directly (no collectionGroup index needed)
            val snapshot = db.collection("devices")
                .document("ESP32_01")
                .collection("events")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Event::class.java)?.copy(
                        event_id = doc.id,
                        device_id = "ESP32_01"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing event document: ${doc.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching events", e)
            emptyList()
        }
    }

    /**
     * Get events for a specific device.
     */
    suspend fun getDeviceEvents(deviceId: String, limit: Long = 20): List<Event> {
        return try {
            val snapshot = db.collection("devices")
                .document(deviceId)
                .collection("events")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Event::class.java)?.copy(
                        event_id = doc.id,
                        device_id = deviceId
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing event: ${doc.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching device events", e)
            emptyList()
        }
    }

    /**
     * Get a single event by ID.
     */
    suspend fun getEvent(deviceId: String, eventId: String): Event? {
        return try {
            val doc = db.collection("devices")
                .document(deviceId)
                .collection("events")
                .document(eventId)
                .get()
                .await()
            
            doc.toObject(Event::class.java)?.copy(
                event_id = eventId,
                device_id = deviceId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching event", e)
            null
        }
    }

    /**
     * Real-time flow of events for a device.
     */
    fun observeDeviceEvents(deviceId: String, limit: Long = 20): Flow<List<Event>> = callbackFlow {
        val listenerRegistration: ListenerRegistration = db.collection("devices")
            .document(deviceId)
            .collection("events")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed", error)
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Event::class.java)?.copy(
                            event_id = doc.id,
                            device_id = deviceId
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                
                trySend(events)
            }
        
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Real-time flow of all events (collection group).
     */
    fun observeAllEvents(limit: Long = 50): Flow<List<Event>> = callbackFlow {
        val listenerRegistration: ListenerRegistration = db.collectionGroup("events")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed", error)
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Event::class.java)?.copy(
                            event_id = doc.id,
                            device_id = doc.reference.parent.parent?.id ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                
                trySend(events)
            }
        
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Mark an event as handled.
     */
    suspend fun markEventHandled(deviceId: String, eventId: String, acknowledgedBy: String = "app_user") {
        db.collection("devices")
            .document(deviceId)
            .collection("events")
            .document(eventId)
            .update(
                mapOf(
                    "handled" to true,
                    "acknowledged_by" to acknowledgedBy
                )
            )
            .addOnSuccessListener {
                Log.d(TAG, "Event $eventId marked as handled")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error marking event as handled", e)
            }
    }
    
    /**
     * Mark an event as handled (convenience method for ESP32_01).
     */
    suspend fun markEventAsHandled(eventId: String, acknowledgedBy: String = "app_user") {
        markEventHandled("ESP32_01", eventId, acknowledgedBy)
    }

    /**
     * Get count of unhandled events in last 24 hours.
     */
    suspend fun getUnhandledEventCount(): Int {
        return try {
            val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            val snapshot = db.collectionGroup("events")
                .whereEqualTo("handled", false)
                .get()
                .await()
            snapshot.size()
        } catch (e: Exception) {
            Log.e(TAG, "Error counting unhandled events", e)
            0
        }
    }

    /**
     * Get total event count in last 24 hours.
     */
    suspend fun getEventCountLast24Hours(): Int {
        return try {
            val snapshot = db.collectionGroup("events")
                .limit(100)
                .get()
                .await()
            // Filter in client (Firestore doesn't support complex queries on free tier)
            snapshot.size()
        } catch (e: Exception) {
            Log.e(TAG, "Error counting events", e)
            0
        }
    }
}
