package com.safestep.app.ui.pairing

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
 * Device Pairing Screen
 * 
 * Allows user to:
 * - Enter device ID manually
 * - View pairing instructions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    currentDeviceId: String = "",
    onPair: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var deviceId by remember { mutableStateOf(currentDeviceId) }
    var isPairing by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pair Device",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back", color = SafeStepColors.OnSurface)
                    }
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Text(
                text = "📱",
                fontSize = 64.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title
            Text(
                text = "Connect Your SafeStep Device",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = SafeStepColors.OnBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Enter the Device ID printed on your SafeStep wearable",
                fontSize = 16.sp,
                color = SafeStepColors.OnSurfaceMuted,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Device ID Input
            OutlinedTextField(
                value = deviceId,
                onValueChange = { deviceId = it.uppercase() },
                label = { Text("Device ID") },
                placeholder = { Text("e.g. ESP32_01") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SafeStepColors.Secondary,
                    focusedLabelColor = SafeStepColors.Secondary,
                    cursorColor = SafeStepColors.Secondary
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Pair Button
            Button(
                onClick = {
                    if (deviceId.isNotBlank()) {
                        isPairing = true
                        onPair(deviceId)
                        // Simulate pairing delay
                        isPairing = false
                        showSuccess = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = deviceId.isNotBlank() && !isPairing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafeStepColors.Secondary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isPairing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = SafeStepColors.OnSecondary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Pair Device",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Success Message
            if (showSuccess) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SafeStepColors.StatusOnline.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "✓ Device paired successfully!",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 16.sp,
                        color = SafeStepColors.StatusOnline,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Instructions
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SafeStepColors.Surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How to find your Device ID",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = SafeStepColors.OnSurface
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    InstructionStep(number = 1, text = "Look on the back of your SafeStep device")
                    InstructionStep(number = 2, text = "Find the label with ID starting with 'ESP32_'")
                    InstructionStep(number = 3, text = "Enter the complete ID above")
                }
            }
        }
    }
}

@Composable
private fun InstructionStep(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SafeStepColors.Secondary.copy(alpha = 0.2f)
        ) {
            Text(
                text = "$number",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SafeStepColors.Secondary
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = text,
            fontSize = 14.sp,
            color = SafeStepColors.OnSurfaceMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PairingScreenPreview() {
    SafeStepTheme {
        PairingScreen()
    }
}
