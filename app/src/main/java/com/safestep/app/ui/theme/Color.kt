package com.safestep.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SafeStep Design System - Color Tokens
 * 
 * Professional dark theme optimized for:
 * - Emergency alert visibility
 * - Elderly accessibility (high contrast)
 * - Calm yet authoritative medical aesthetic
 */
object SafeStepColors {
    
    // === PRIMARY (Emergency Red) ===
    val Primary = Color(0xFFB71C1C)           // Deep red - emergency actions
    val PrimaryContainer = Color(0xFF5F1111)  // Darker variant
    val OnPrimary = Color(0xFFFFFFFF)
    val OnPrimaryContainer = Color(0xFFFFDAD6)
    
    // === SECONDARY (Warning Orange) ===
    val Secondary = Color(0xFFFF9800)
    val SecondaryContainer = Color(0xFF5C3C00)
    val OnSecondary = Color(0xFF000000)
    val OnSecondaryContainer = Color(0xFFFFDDB3)
    
    // === TERTIARY (Success Green) ===
    val Tertiary = Color(0xFF2E7D32)
    val TertiaryContainer = Color(0xFF1B4D1E)
    val OnTertiary = Color(0xFFFFFFFF)
    val OnTertiaryContainer = Color(0xFFA8F5AC)
    
    // === BACKGROUND & SURFACE ===
    val Background = Color(0xFF121212)
    val OnBackground = Color(0xFFFFFFFF)
    val OnBackgroundMuted = Color(0xFFB0B0B0)
    
    val Surface = Color(0xFF1E1E1E)
    val SurfaceVariant = Color(0xFF2A2A2A)
    val SurfaceContainer = Color(0xFF252525)
    val OnSurface = Color(0xFFFFFFFF)
    val OnSurfaceMuted = Color(0xFFB0B0B0)
    
    // === STATUS COLORS ===
    val StatusOnline = Color(0xFF4CAF50)
    val StatusWarning = Color(0xFFFF9800)
    val StatusOffline = Color(0xFFD32F2F)
    val StatusUnknown = Color(0xFF757575)
    
    // === ALERT SPECIFIC ===
    val AlertBackground = Color(0xFFB71C1C)
    val AlertSurface = Color(0xFF5F1111)
    val AlertOnBackground = Color(0xFFFFFFFF)
    val AlertPulse = Color(0xFFD32F2F)
    
    // === BUTTON COLORS ===
    val ButtonCall = Color(0xFFD32F2F)
    val ButtonCallText = Color(0xFFFFFFFF)
    val ButtonAcknowledge = Color(0xFF2E7D32)
    val ButtonAcknowledgeText = Color(0xFFFFFFFF)
    val ButtonOutline = Color(0xFF666666)
    
    // === POSTURE COLORS ===
    val PostureGood = Color(0xFF4CAF50)
    val PostureWarning = Color(0xFFFF9800)
    val PosturePoor = Color(0xFFD32F2F)
    
    // === MISC ===
    val Divider = Color(0xFF333333)
    val Border = Color(0xFF444444)
    val Ripple = Color(0x33FFFFFF)
}
