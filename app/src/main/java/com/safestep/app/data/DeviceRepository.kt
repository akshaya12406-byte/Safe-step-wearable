package com.safestep.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.safestep.app.model.Device
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * DeviceRepository handles all Firestore operations for device management.
 * 
 * Firestore Structure:
 * devices/{device_id}/meta/info
 *   - last_seen: Timestamp
 *   - battery_pct: Number
 *   - fw_version: String
 *   - fcm_token: String
 */
class DeviceRepository(private val context: Context? = null) {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "DeviceRepository"
    
    companion object {
        private const val PREFS_NAME = "com.safestep.app.DEVICES"
        private const val KEY_PAIRED_DEVICES = "paired_device_ids"
        private const val KEY_PRIMARY_DEVICE = "primary_device_id"
    }
    
    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get list of paired device IDs from local storage.
     */
    fun getPairedDeviceIds(): Set<String> {
        return prefs?.getStringSet(KEY_PAIRED_DEVICES, emptySet()) ?: emptySet()
    }

    /**
     * Add a device to paired list.
     */
    fun addPairedDevice(deviceId: String) {
        val current = getPairedDeviceIds().toMutableSet()
        current.add(deviceId)
        prefs?.edit()?.putStringSet(KEY_PAIRED_DEVICES, current)?.apply()
        
        // If this is the first device, make it primary
        if (current.size == 1) {
            setPrimaryDevice(deviceId)
        }
    }

    /**
     * Remove a device from paired list.
     */
    fun removePairedDevice(deviceId: String) {
        val current = getPairedDeviceIds().toMutableSet()
        current.remove(deviceId)
        prefs?.edit()?.putStringSet(KEY_PAIRED_DEVICES, current)?.apply()
        
        // If removed device was primary, clear or set new primary
        if (getPrimaryDeviceId() == deviceId) {
            prefs?.edit()?.putString(KEY_PRIMARY_DEVICE, current.firstOrNull() ?: "")?.apply()
        }
    }

    /**
     * Get primary device ID.
     */
    fun getPrimaryDeviceId(): String? {
        return prefs?.getString(KEY_PRIMARY_DEVICE, null)
    }

    /**
     * Set primary device.
     */
    fun setPrimaryDevice(deviceId: String) {
        prefs?.edit()?.putString(KEY_PRIMARY_DEVICE, deviceId)?.apply()
    }

    /**
     * Fetch device info from Firestore.
     */
    suspend fun getDevice(deviceId: String): Device? {
        return try {
            val doc = db.collection("devices")
                .document(deviceId)
                .collection("meta")
                .document("info")
                .get()
                .await()
            
            doc.toObject(Device::class.java)?.copy(device_id = deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching device", e)
            null
        }
    }

    /**
     * Fetch all paired devices.
     */
    suspend fun getPairedDevices(): List<Device> {
        val deviceIds = getPairedDeviceIds()
        if (deviceIds.isEmpty()) {
            // Return demo device for testing
            return listOf(
                Device(
                    device_id = "ESP32_DEMO",
                    name = "Demo Device",
                    battery_pct = 85,
                    fw_version = "1.0.0",
                    is_primary = true
                )
            )
        }
        
        val devices = mutableListOf<Device>()
        val primaryId = getPrimaryDeviceId()
        
        for (deviceId in deviceIds) {
            try {
                val device = getDevice(deviceId)
                if (device != null) {
                    devices.add(device.copy(is_primary = deviceId == primaryId))
                } else {
                    // Device exists locally but not in Firestore - add placeholder
                    devices.add(Device(
                        device_id = deviceId,
                        name = deviceId,
                        is_primary = deviceId == primaryId
                    ))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching device $deviceId", e)
            }
        }
        
        // Sort: primary first, then by name
        return devices.sortedWith(compareByDescending<Device> { it.is_primary }.thenBy { it.name })
    }

    /**
     * Real-time flow of a device's status.
     */
    fun observeDevice(deviceId: String): Flow<Device?> = callbackFlow {
        val listenerRegistration: ListenerRegistration = db.collection("devices")
            .document(deviceId)
            .collection("meta")
            .document("info")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                
                val device = snapshot?.toObject(Device::class.java)?.copy(
                    device_id = deviceId,
                    is_primary = deviceId == getPrimaryDeviceId()
                )
                trySend(device)
            }
        
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Update device FCM token in Firestore.
     */
    suspend fun updateDeviceFcmToken(deviceId: String, fcmToken: String) {
        try {
            db.collection("devices")
                .document(deviceId)
                .collection("meta")
                .document("info")
                .update("fcm_token", fcmToken)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating FCM token", e)
        }
    }
}
