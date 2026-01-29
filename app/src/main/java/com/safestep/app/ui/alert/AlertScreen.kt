package com.safestep.app.ui.alert

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safestep.app.ui.components.DataBadge
import com.safestep.app.ui.components.LargeOutlinedButton
import com.safestep.app.ui.components.LargePrimaryButton
import com.safestep.app.ui.components.SmallTextButton
import com.safestep.app.ui.theme.SafeStepAlertTheme
import com.safestep.app.ui.theme.SafeStepColors

/**
 * Full-Screen Alert Composable
 * 
 * Displays when a fall is detected. Features:
 * - Pulsing red background for urgency
 * - Large, accessible buttons
 * - Clear information hierarchy
 * - Demo mode support (no real calls)
 * - TalkBack accessibility
 */
@Composable
fun AlertScreen(
    eventType: String = "FALL_CONFIRMED",
    deviceId: String = "Unknown Device",
    timestamp: String = "",
    impactG: String = "",
    pitch: String = "",
    roll: String = "",
    eventId: String = "",
    isDemoMode: Boolean = false,
    emergencyNumber: String = "911",
    onCallClick: () -> Unit,
    onAcknowledgeClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    // Pulsing animation for background
    val infiniteTransition = rememberInfiniteTransition(label = "alertPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SafeStepColors.AlertBackground)
    ) {
        // Pulsing background layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulseScale)
                .background(SafeStepColors.AlertPulse.copy(alpha = 0.3f))
        )
        
        // Content with enter animation
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(180)
            ) + fadeIn(animationSpec = tween(180))
        ) {
            AlertContent(
                eventType = eventType,
                deviceId = deviceId,
                timestamp = timestamp,
                impactG = impactG,
                pitch = pitch,
                roll = roll,
                isDemoMode = isDemoMode,
                emergencyNumber = emergencyNumber,
                onCallClick = onCallClick,
                onAcknowledgeClick = onAcknowledgeClick,
                onDismissClick = onDismissClick
            )
        }
    }
}

@Composable
private fun AlertContent(
    eventType: String,
    deviceId: String,
    timestamp: String,
    impactG: String,
    pitch: String,
    roll: String,
    isDemoMode: Boolean,
    emergencyNumber: String,
    onCallClick: () -> Unit,
    onAcknowledgeClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .semantics { 
                contentDescription = "Fall detected alert. Emergency actions available." 
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Header Section
        AlertHeader(eventType = eventType)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Device & Time Info
        AlertInfo(deviceId = deviceId, timestamp = timestamp)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Evidence Data (if available)
        if (impactG.isNotEmpty()) {
            EvidenceSection(
                impactG = impactG,
                pitch = pitch,
                roll = roll
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action Buttons
        ActionButtons(
            isDemoMode = isDemoMode,
            emergencyNumber = emergencyNumber,
            onCallClick = onCallClick,
            onAcknowledgeClick = onAcknowledgeClick,
            onDismissClick = onDismissClick
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AlertHeader(eventType: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Alert Icon
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Main headline
        Text(
            text = if (eventType == "FALL_CONFIRMED") "Fall Detected" else "Alert",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subtitle
        Text(
            text = if (eventType == "FALL_CONFIRMED") "Confirmed" else eventType,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AlertInfo(
    deviceId: String,
    timestamp: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = deviceId,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f)
        )
        
        if (timestamp.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTimestamp(timestamp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EvidenceSection(
    impactG: String,
    pitch: String,
    roll: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Impact badge (main metric)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.15f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${impactG}g",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Impact Force",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
        
        // Pitch & Roll (secondary metrics)
        if (pitch.isNotEmpty() || roll.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (pitch.isNotEmpty()) {
                    MetricChip(label = "Pitch", value = "${pitch}°")
                }
                if (roll.isNotEmpty()) {
                    MetricChip(label = "Roll", value = "${roll}°")
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ActionButtons(
    isDemoMode: Boolean,
    emergencyNumber: String,
    onCallClick: () -> Unit,
    onAcknowledgeClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary: CALL ELDERLY button (caregiver calls the elderly person)
        LargePrimaryButton(
            text = if (isDemoMode) "📞 CALL ELDERLY (Demo)" else "📞 CALL ELDERLY",
            onClick = onCallClick,
            backgroundColor = SafeStepColors.ButtonCall,
            contentDesc = "Call the elderly person to check on them"
        )
        
        // Secondary: ACKNOWLEDGE button (mark as handled)
        LargePrimaryButton(
            text = "✓ ACKNOWLEDGE",
            onClick = onAcknowledgeClick,
            backgroundColor = SafeStepColors.ButtonAcknowledge,
            contentDesc = "Acknowledge alert and mark as handled"
        )
        
        // Tertiary: EMERGENCY 911 button
        LargeOutlinedButton(
            text = "🚨 EMERGENCY $emergencyNumber",
            onClick = onDismissClick,
            contentDesc = "Call emergency services $emergencyNumber"
        )
        
        // Demo mode indicator
        if (isDemoMode) {
            Text(
                text = "Demo Mode — No real calls will be made",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatTimestamp(isoTimestamp: String): String {
    if (isoTimestamp.isEmpty()) return ""
    return try {
        val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        val outputFormat = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
        inputFormat.parse(isoTimestamp.replace("Z", ""))?.let { 
            outputFormat.format(it) 
        } ?: isoTimestamp.take(16)
    } catch (e: Exception) {
        isoTimestamp.take(16)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFB71C1C)
@Composable
private fun AlertScreenPreview() {
    SafeStepAlertTheme {
        AlertScreen(
            eventType = "FALL_CONFIRMED",
            deviceId = "ESP32_01",
            timestamp = "2026-01-28T15:30:00Z",
            impactG = "3.05",
            pitch = "12.4",
            roll = "5.1",
            isDemoMode = false,
            emergencyNumber = "911",
            onCallClick = {},
            onAcknowledgeClick = {},
            onDismissClick = {}
        )
    }
}
