package com.safestep.app.ui.developer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.messaging.FirebaseMessaging
import com.safestep.app.ui.theme.SafeStepColors
import com.safestep.app.ui.theme.SafeStepTheme

/**
 * Developer Mode Screen - Hidden behind 7-tap on version
 * 
 * Features:
 * - FCM token display and copy
 * - Event injection for testing
 * - Debug logs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    onBack: () -> Unit = {},
    onInjectEvent: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var fcmToken by remember { mutableStateOf("Loading...") }
    
    // Load FCM token
    LaunchedEffect(Unit) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            fcmToken = token
        }.addOnFailureListener {
            fcmToken = "Failed to load token"
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🔧 Developer Mode",
                        fontSize = 20.sp,
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
                .padding(16.dp)
        ) {
            // Warning Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SafeStepColors.StatusWarning.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "⚠️ Developer features. Use with caution.",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    color = SafeStepColors.StatusWarning
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // FCM Token Section
            SectionLabel("FCM Token")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SafeStepColors.Surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = fcmToken,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SafeStepColors.OnSurface,
                        lineHeight = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("FCM Token", fcmToken))
                            Toast.makeText(context, "Token copied!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafeStepColors.Secondary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📋 Copy Token")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Event Injection Section
            SectionLabel("Test Events")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SafeStepColors.Surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Inject test events locally",
                        fontSize = 14.sp,
                        color = SafeStepColors.OnSurfaceMuted
                    )
                    
                    Button(
                        onClick = { onInjectEvent("FALL_CONFIRMED") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafeStepColors.Primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🚨 Simulate Fall Alert")
                    }
                    
                    OutlinedButton(
                        onClick = { onInjectEvent("IMPACT_ALERT") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("💥 Simulate Impact Alert")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Debug Info
            SectionLabel("Debug Info")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SafeStepColors.Surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DebugRow("Build Type", "Debug")
                    DebugRow("API Endpoint", "safestep-fcm-relay.workers.dev")
                    DebugRow("Compose Version", "2024.02.00")
                }
            }
        }
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
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = SafeStepColors.OnSurfaceMuted
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            color = SafeStepColors.OnSurface
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun DeveloperScreenPreview() {
    SafeStepTheme {
        DeveloperScreen()
    }
}
