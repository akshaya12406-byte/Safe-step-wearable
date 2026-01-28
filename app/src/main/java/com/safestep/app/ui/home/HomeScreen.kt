package com.safestep.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safestep.app.ui.components.*
import com.safestep.app.ui.theme.SafeStepColors
import com.safestep.app.ui.theme.SafeStepTheme

/**
 * HomeScreen - Main Dashboard Composable
 * 
 * Displays:
 * - Top app bar with device selector
 * - Latest unacknowledged event (if any)
 * - Device status card
 * - Posture summary card
 * - Quick actions row
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    deviceId: String = "ESP32_01",
    deviceStatus: DeviceStatusType = DeviceStatusType.ONLINE,
    batteryPercent: Int? = 85,
    lastSeen: String? = "2 min ago",
    firmwareVersion: String? = "1.0.0",
    postureState: PostureState = PostureState.GOOD,
    postureDuration: String = "15 minutes",
    hasUnacknowledgedEvent: Boolean = false,
    latestEventType: String = "",
    latestEventTimestamp: String = "",
    onDeviceCardClick: () -> Unit = {},
    onViewEventClick: () -> Unit = {},
    onPairDeviceClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onDeveloperClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SafeStep",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SafeStepColors.Background,
                    titleContentColor = SafeStepColors.OnBackground
                )
            )
        },
        containerColor = SafeStepColors.Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Latest Event Banner (if unacknowledged)
            if (hasUnacknowledgedEvent) {
                LatestEventStrip(
                    eventType = latestEventType,
                    deviceId = deviceId,
                    timestamp = latestEventTimestamp,
                    isAcknowledged = false,
                    onViewClick = onViewEventClick
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Section: Device Status
            SectionHeader(title = "Device")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            DeviceCard(
                deviceId = deviceId,
                status = deviceStatus,
                batteryPercent = batteryPercent,
                lastSeen = lastSeen,
                firmwareVersion = firmwareVersion,
                onClick = onDeviceCardClick
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Section: Posture
            SectionHeader(title = "Posture")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            PostureCard(
                state = postureState,
                durationText = postureDuration
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Quick Actions
            SectionHeader(title = "Quick Actions")
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    emoji = "📱",
                    label = "Pair Device",
                    modifier = Modifier.weight(1f),
                    onClick = onPairDeviceClick
                )
                
                QuickActionButton(
                    emoji = "⚙️",
                    label = "Settings",
                    modifier = Modifier.weight(1f),
                    onClick = onSettingsClick
                )
                
                QuickActionButton(
                    emoji = "🔧",
                    label = "Developer",
                    modifier = Modifier.weight(1f),
                    onClick = onDeveloperClick
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Footer
            Text(
                text = "Monitor your loved one's safety.\nContact support: help@safestep.dev",
                fontSize = 12.sp,
                color = SafeStepColors.OnBackgroundMuted,
                lineHeight = 18.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = SafeStepColors.OnBackgroundMuted,
        letterSpacing = 1.sp
    )
}

@Composable
private fun QuickActionButton(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = SafeStepColors.Surface,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = emoji,
                fontSize = 28.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = label,
                fontSize = 12.sp,
                color = SafeStepColors.OnSurfaceMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun HomeScreenPreview() {
    SafeStepTheme {
        HomeScreen(
            hasUnacknowledgedEvent = true,
            latestEventType = "FALL_CONFIRMED",
            latestEventTimestamp = "2 min ago"
        )
    }
}
