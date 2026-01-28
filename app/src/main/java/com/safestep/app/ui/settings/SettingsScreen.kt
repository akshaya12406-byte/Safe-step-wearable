package com.safestep.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.safestep.app.ui.theme.SafeStepColors
import com.safestep.app.ui.theme.SafeStepTheme

/**
 * Settings Screen - App configuration
 * 
 * Features:
 * - Emergency contact
 * - Demo mode toggle
 * - Auto-call toggle
 * - Developer mode (hidden)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    emergencyNumber: String = "911",
    isDemoMode: Boolean = false,
    isAutoCallEnabled: Boolean = false,
    appVersion: String = "1.0.0",
    onEmergencyNumberChange: (String) -> Unit = {},
    onDemoModeToggle: (Boolean) -> Unit = {},
    onAutoCallToggle: (Boolean) -> Unit = {},
    onVersionTap: () -> Unit = {}
) {
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var tempEmergencyNumber by remember { mutableStateOf(emergencyNumber) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 22.sp,
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
                .padding(16.dp)
        ) {
            // Emergency Contact Section
            SectionLabel("Emergency Contact")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsCard(
                title = "Emergency Number",
                subtitle = emergencyNumber,
                onClick = { showEmergencyDialog = true }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Safety Features Section
            SectionLabel("Safety Features")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsToggleCard(
                title = "Demo Mode",
                subtitle = "Calls are simulated, not real",
                isEnabled = isDemoMode,
                onToggle = onDemoModeToggle
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsToggleCard(
                title = "Auto-Call on Fall",
                subtitle = "Automatically call after 30 seconds",
                isEnabled = isAutoCallEnabled,
                onToggle = onAutoCallToggle
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // About Section  
            SectionLabel("About")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsCard(
                title = "Version",
                subtitle = appVersion,
                onClick = onVersionTap // Hidden developer mode trigger
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Footer
            Text(
                text = "SafeStep © 2026\nDesigned for elderly care",
                fontSize = 12.sp,
                color = SafeStepColors.OnSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    
    // Emergency Number Dialog
    if (showEmergencyDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyDialog = false },
            title = { Text("Emergency Number") },
            text = {
                OutlinedTextField(
                    value = tempEmergencyNumber,
                    onValueChange = { tempEmergencyNumber = it },
                    label = { Text("Phone Number") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEmergencyNumberChange(tempEmergencyNumber)
                        showEmergencyDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = SafeStepColors.OnSurfaceMuted,
        letterSpacing = 1.sp
    )
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = SafeStepColors.Surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = SafeStepColors.OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = SafeStepColors.OnSurfaceMuted
                )
            }
            
            Text(
                text = "›",
                fontSize = 20.sp,
                color = SafeStepColors.OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun SettingsToggleCard(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SafeStepColors.Surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = SafeStepColors.OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = SafeStepColors.OnSurfaceMuted
                )
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SafeStepColors.StatusOnline,
                    checkedTrackColor = SafeStepColors.StatusOnline.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun SettingsScreenPreview() {
    SafeStepTheme {
        SettingsScreen(
            emergencyNumber = "911",
            isDemoMode = true,
            isAutoCallEnabled = false
        )
    }
}
