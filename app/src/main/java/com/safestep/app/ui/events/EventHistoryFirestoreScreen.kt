package com.safestep.app.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Event data model matching Firestore document structure
 */
data class FallEvent(
    val eventId: String = "",
    val deviceId: String = "",
    val eventType: String = "",
    val timestamp: String = "",
    val impactG: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    val firmwareVersion: String = "",
    val acknowledged: Boolean = false,
    val acknowledgedBy: String? = null
)

/**
 * Read events from Firestore as a Flow (real-time updates)
 * 
 * Path: devices/{deviceId}/events
 */
fun getEventsFlow(deviceId: String, limit: Int = 20): Flow<List<FallEvent>> = callbackFlow {
    val db = FirebaseFirestore.getInstance()
    
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
                        timestamp = doc.getString("timestamp") 
                            ?: doc.getTimestamp("timestamp")?.toDate()?.toString() ?: "",
                        impactG = doc.getDouble("impact_g") ?: 0.0,
                        pitch = doc.getDouble("pitch") ?: 0.0,
                        roll = doc.getDouble("roll") ?: 0.0,
                        firmwareVersion = doc.getString("firmware_version") ?: "",
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
 * Event History Screen (Compose) - Displays fall events from Firestore
 */
@Composable
fun EventHistoryFirestoreScreen(
    deviceId: String = "ESP32_01"
) {
    var events by remember { mutableStateOf<List<FallEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Collect events flow
    LaunchedEffect(deviceId) {
        getEventsFlow(deviceId).collect { newEvents ->
            events = newEvents
            isLoading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text(
            text = "Event History",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Text(
            text = "Device: $deviceId",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFF9800))
            }
        } else if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No events recorded yet",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events) { event ->
                    EventCard(event)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: FallEvent) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E1E1E)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Event type badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (event.eventType == "FALL_CONFIRMED") 
                        Color(0xFFB71C1C) else Color(0xFF424242)
                ) {
                    Text(
                        text = event.eventType,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                // Acknowledged indicator
                if (event.acknowledged) {
                    Text(
                        text = "✓ Acknowledged",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Impact info
            Text(
                text = "Impact: ${String.format("%.2f", event.impactG)}g",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            // Angles
            Text(
                text = "Pitch: ${String.format("%.1f", event.pitch)}° | Roll: ${String.format("%.1f", event.roll)}°",
                color = Color.Gray,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Timestamp
            Text(
                text = event.timestamp,
                color = Color(0xFF888888),
                fontSize = 12.sp
            )
        }
    }
}
