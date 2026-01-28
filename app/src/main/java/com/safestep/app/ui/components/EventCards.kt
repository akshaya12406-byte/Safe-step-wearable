package com.safestep.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
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
 * EventCard - Displays a fall/alert event in a list
 */
@Composable
fun EventCard(
    eventType: String,
    deviceId: String,
    timestamp: String,
    impactG: String?,
    isAcknowledged: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAcknowledged) 
                SafeStepColors.Surface 
            else 
                SafeStepColors.Primary.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Event icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isAcknowledged) SafeStepColors.SurfaceVariant
                        else SafeStepColors.Primary.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAcknowledged) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isAcknowledged) SafeStepColors.StatusOnline else SafeStepColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Event info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatEventType(eventType),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isAcknowledged) 
                        SafeStepColors.OnSurface 
                    else 
                        SafeStepColors.Primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "$deviceId • $timestamp",
                    fontSize = 14.sp,
                    color = SafeStepColors.OnSurfaceMuted
                )
            }
            
            // Impact badge
            if (impactG != null && impactG.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SafeStepColors.SurfaceVariant
                ) {
                    Text(
                        text = "${impactG}g",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SafeStepColors.OnSurface
                    )
                }
            }
            
            // Acknowledged badge
            if (isAcknowledged) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SafeStepColors.StatusOnline.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "ACK",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SafeStepColors.StatusOnline
                    )
                }
            }
        }
    }
}

private fun formatEventType(type: String): String {
    return when (type) {
        "FALL_CONFIRMED" -> "Fall Detected"
        "IMPACT_ALERT" -> "Impact Alert"
        else -> type.replace("_", " ")
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    }
}

/**
 * EventDetailCard - Full details for a single event
 */
@Composable
fun EventDetailCard(
    eventType: String,
    deviceId: String,
    timestamp: String,
    impactG: String?,
    pitch: String?,
    roll: String?,
    isAcknowledged: Boolean,
    acknowledgedBy: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SafeStepColors.Surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Text(
                text = formatEventType(eventType),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = SafeStepColors.OnSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "$deviceId • $timestamp",
                fontSize = 14.sp,
                color = SafeStepColors.OnSurfaceMuted
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Telemetry data
            Text(
                text = "TELEMETRY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = SafeStepColors.OnSurfaceMuted,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (impactG != null) {
                    TelemetryItem(label = "Impact", value = "${impactG}g", modifier = Modifier.weight(1f))
                }
                if (pitch != null) {
                    TelemetryItem(label = "Pitch", value = "${pitch}°", modifier = Modifier.weight(1f))
                }
                if (roll != null) {
                    TelemetryItem(label = "Roll", value = "${roll}°", modifier = Modifier.weight(1f))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Status
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAcknowledged) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = SafeStepColors.StatusOnline,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Acknowledged" + if (acknowledgedBy != null) " by $acknowledgedBy" else "",
                        fontSize = 14.sp,
                        color = SafeStepColors.StatusOnline
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SafeStepColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pending acknowledgment",
                        fontSize = 14.sp,
                        color = SafeStepColors.Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = SafeStepColors.SurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = SafeStepColors.OnSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = SafeStepColors.OnSurfaceMuted
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun EventCardPreview() {
    SafeStepTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EventCard(
                eventType = "FALL_CONFIRMED",
                deviceId = "ESP32_01",
                timestamp = "2 min ago",
                impactG = "3.05",
                isAcknowledged = false,
                onClick = {}
            )
            EventCard(
                eventType = "FALL_CONFIRMED",
                deviceId = "ESP32_01",
                timestamp = "1 hour ago",
                impactG = "2.10",
                isAcknowledged = true,
                onClick = {}
            )
        }
    }
}
