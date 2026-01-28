package com.safestep.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safestep.app.ui.theme.SafeStepColors
import com.safestep.app.ui.theme.SafeStepTheme

/**
 * DeviceCard - Shows device status at a glance
 * 
 * Displays:
 * - Status dot (online/offline/warning)
 * - Device name
 * - Battery percentage
 * - Last seen time
 * - Firmware version
 */
@Composable
fun DeviceCard(
    deviceId: String,
    status: DeviceStatusType,
    batteryPercent: Int?,
    lastSeen: String?,
    firmwareVersion: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SafeStepColors.Surface
        ),
        onClick = { onClick?.invoke() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            StatusDot(status = status, size = 14)
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceId,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = SafeStepColors.OnSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = when (status) {
                        DeviceStatusType.ONLINE -> "Online"
                        DeviceStatusType.WARNING -> "Warning"
                        DeviceStatusType.OFFLINE -> "Offline"
                        DeviceStatusType.UNKNOWN -> "Unknown"
                    } + if (lastSeen != null) " • $lastSeen" else "",
                    fontSize = 14.sp,
                    color = SafeStepColors.OnSurfaceMuted
                )
                
                if (firmwareVersion != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "FW: $firmwareVersion",
                        fontSize = 12.sp,
                        color = SafeStepColors.OnSurfaceMuted.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Battery indicator
            if (batteryPercent != null) {
                BatteryIndicator(percent = batteryPercent)
            }
        }
    }
}

@Composable
private fun BatteryIndicator(percent: Int) {
    val color = when {
        percent > 50 -> SafeStepColors.StatusOnline
        percent > 20 -> SafeStepColors.StatusWarning
        else -> SafeStepColors.StatusOffline
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = "🔋 $percent%",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * PostureCard - Shows current posture status with mini visualization
 */
@Composable
fun PostureCard(
    state: PostureState,
    durationText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SafeStepColors.Surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Posture icon/indicator
            PostureIndicator(state = state)
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Posture info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current Posture",
                    fontSize = 12.sp,
                    color = SafeStepColors.OnSurfaceMuted
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = when (state) {
                        PostureState.GOOD -> "Good"
                        PostureState.WARNING -> "Warning"
                        PostureState.POOR -> "Poor"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (state) {
                        PostureState.GOOD -> SafeStepColors.PostureGood
                        PostureState.WARNING -> SafeStepColors.PostureWarning
                        PostureState.POOR -> SafeStepColors.PosturePoor
                    }
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = durationText,
                    fontSize = 14.sp,
                    color = SafeStepColors.OnSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun PostureIndicator(state: PostureState) {
    val color = when (state) {
        PostureState.GOOD -> SafeStepColors.PostureGood
        PostureState.WARNING -> SafeStepColors.PostureWarning
        PostureState.POOR -> SafeStepColors.PosturePoor
    }
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Simple person icon representation
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
    }
}

enum class PostureState {
    GOOD, WARNING, POOR
}

/**
 * LatestEventStrip - Prominent banner for unacknowledged events
 */
@Composable
fun LatestEventStrip(
    eventType: String,
    deviceId: String,
    timestamp: String,
    isAcknowledged: Boolean,
    modifier: Modifier = Modifier,
    onViewClick: () -> Unit
) {
    if (isAcknowledged) return
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SafeStepColors.Primary
        ),
        onClick = onViewClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (eventType == "FALL_CONFIRMED") "FALL DETECTED" else eventType,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "$deviceId • $timestamp",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            
            Button(
                onClick = onViewClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = SafeStepColors.Primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("VIEW", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun DeviceCardPreview() {
    SafeStepTheme {
        DeviceCard(
            deviceId = "ESP32_01",
            status = DeviceStatusType.ONLINE,
            batteryPercent = 85,
            lastSeen = "2 min ago",
            firmwareVersion = "1.0.0"
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PostureCardPreview() {
    SafeStepTheme {
        PostureCard(
            state = PostureState.GOOD,
            durationText = "15 minutes"
        )
    }
}
