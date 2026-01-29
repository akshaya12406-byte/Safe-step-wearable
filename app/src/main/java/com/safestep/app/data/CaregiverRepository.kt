package com.safestep.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * CaregiverRepository handles caregiver registration with Firestore.
 * 
 * When a caregiver pairs with a device, their FCM token is stored at:
 *   devices/{device_id}/recipients/caregivers/{caregiver_uid}
 * 
 * The Worker v3.0 looks up all tokens in this collection and sends FCM to each.
 */
class CaregiverRepository(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "CaregiverRepository"
    
    companion object {
        private const val PREFS_NAME = "com.safestep.app.CAREGIVER"
        private const val KEY_CAREGIVER_UID = "caregiver_uid"
        private const val KEY_CAREGIVER_NAME = "caregiver_name"
        private const val KEY_CAREGIVER_PHONE = "caregiver_phone"
        private const val KEY_PAIRED_DEVICE_ID = "paired_device_id"
        private const val KEY_FCM_TOKEN = "fcm_token"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get or generate a unique caregiver UID for this device.
     */
    fun getCaregiverUid(): String {
        var uid = prefs.getString(KEY_CAREGIVER_UID, null)
        if (uid == null) {
            uid = UUID.randomUUID().toString().take(12)
            prefs.edit().putString(KEY_CAREGIVER_UID, uid).apply()
        }
        return uid
    }

    /**
     * Get the currently paired device ID (the elderly's device).
     */
    fun getPairedDeviceId(): String? {
        return prefs.getString(KEY_PAIRED_DEVICE_ID, null)
    }

    /**
     * Set the paired device ID.
     */
    fun setPairedDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_PAIRED_DEVICE_ID, deviceId).apply()
    }

    /**
     * Get caregiver display name.
     */
    fun getCaregiverName(): String {
        return prefs.getString(KEY_CAREGIVER_NAME, "Caregiver") ?: "Caregiver"
    }

    /**
     * Set caregiver name.
     */
    fun setCaregiverName(name: String) {
        prefs.edit().putString(KEY_CAREGIVER_NAME, name).apply()
    }

    /**
     * Get caregiver phone number.
     */
    fun getCaregiverPhone(): String {
        return prefs.getString(KEY_CAREGIVER_PHONE, "") ?: ""
    }

    /**
     * Set caregiver phone.
     */
    fun setCaregiverPhone(phone: String) {
        prefs.edit().putString(KEY_CAREGIVER_PHONE, phone).apply()
    }

    /**
     * Register this caregiver's FCM token with a device in Firestore.
     * Call this after pairing and whenever the FCM token refreshes.
     * 
     * Writes to: devices/{deviceId}/recipients/caregivers/{caregiverUid}
     */
    suspend fun registerCaregiverToken(deviceId: String): Boolean {
        return try {
            // Get FCM token
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            
            val caregiverUid = getCaregiverUid()
            val caregiverName = getCaregiverName()
            val caregiverPhone = getCaregiverPhone()
            
            // Path: devices/{deviceId}/recipients/caregivers/{caregiverUid}
            val docRef = db.document("devices/$deviceId/recipients/caregivers/$caregiverUid")
            
            val data = hashMapOf(
                "fcm_token" to fcmToken,
                "name" to caregiverName,
                "phone" to caregiverPhone,
                "role" to "caregiver",
                "caregiver_uid" to caregiverUid,
                "created_at" to FieldValue.serverTimestamp(),
                "updated_at" to FieldValue.serverTimestamp()
            )
            
            docRef.set(data).await()
            
            // Save locally
            prefs.edit()
                .putString(KEY_PAIRED_DEVICE_ID, deviceId)
                .putString(KEY_FCM_TOKEN, fcmToken)
                .apply()
            
            Log.d(TAG, "Caregiver registered for device $deviceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register caregiver", e)
            false
        }
    }

    /**
     * Update FCM token in Firestore (call when token refreshes).
     */
    suspend fun updateFcmToken(newToken: String) {
        val deviceId = getPairedDeviceId() ?: return
        val caregiverUid = getCaregiverUid()
        
        try {
            db.document("devices/$deviceId/recipients/caregivers/$caregiverUid")
                .update(
                    mapOf(
                        "fcm_token" to newToken,
                        "updated_at" to FieldValue.serverTimestamp()
                    )
                ).await()
            
            prefs.edit().putString(KEY_FCM_TOKEN, newToken).apply()
            Log.d(TAG, "FCM token updated in Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update FCM token", e)
        }
    }

    /**
     * Unregister this caregiver from the device (on logout/unpair).
     */
    suspend fun unregisterCaregiver(): Boolean {
        val deviceId = getPairedDeviceId() ?: return false
        val caregiverUid = getCaregiverUid()
        
        return try {
            db.document("devices/$deviceId/recipients/caregivers/$caregiverUid")
                .delete()
                .await()
            
            prefs.edit()
                .remove(KEY_PAIRED_DEVICE_ID)
                .remove(KEY_FCM_TOKEN)
                .apply()
            
            Log.d(TAG, "Caregiver unregistered from device $deviceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister caregiver", e)
            false
        }
    }

    /**
     * Check if caregiver is registered with a device.
     */
    fun isRegistered(): Boolean {
        return getPairedDeviceId() != null
    }
}
