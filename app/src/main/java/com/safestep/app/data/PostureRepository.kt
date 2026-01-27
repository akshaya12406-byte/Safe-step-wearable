package com.safestep.app.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.safestep.app.model.Posture
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * PostureRepository handles Firestore operations for posture data.
 * 
 * The ESP32 writes posture state to:
 *   devices/{device_id}/posture/latest
 * 
 * The Android app ONLY reads this data - never writes.
 * All posture detection happens on ESP32.
 */
class PostureRepository {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "PostureRepository"

    /**
     * Get current posture for a device (one-time read).
     */
    suspend fun getCurrentPosture(deviceId: String): Posture? {
        return try {
            val doc = db.collection("devices")
                .document(deviceId)
                .collection("posture")
                .document("latest")
                .get()
                .await()
            
            doc.toObject(Posture::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching posture for $deviceId", e)
            null
        }
    }

    /**
     * Real-time flow of posture updates for a device.
     * Use this on the dashboard for live updates.
     */
    fun observePosture(deviceId: String): Flow<Posture?> = callbackFlow {
        val listenerRegistration: ListenerRegistration = db.collection("devices")
            .document(deviceId)
            .collection("posture")
            .document("latest")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Posture listen failed", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                
                val posture = snapshot?.toObject(Posture::class.java)
                trySend(posture)
            }
        
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Get posture history for a device.
     * 
     * Firestore path: devices/{device_id}/posture/history/{timestamp}
     * Note: This is optional - only if ESP32 writes history documents.
     */
    suspend fun getPostureHistory(deviceId: String, limit: Long = 24): List<Posture> {
        return try {
            val snapshot = db.collection("devices")
                .document(deviceId)
                .collection("posture")
                .document("history")
                .collection("entries")
                .orderBy("last_updated", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Posture::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching posture history", e)
            emptyList()
        }
    }
}
