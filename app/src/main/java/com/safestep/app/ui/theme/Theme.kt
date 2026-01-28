package com.safestep.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * SafeStep Material 3 Theme
 * 
 * Professional dark theme for fall detection app
 * Optimized for emergency visibility and elderly accessibility
 */

private val SafeStepDarkColorScheme = darkColorScheme(
    // Primary (Emergency Red)
    primary = SafeStepColors.Primary,
    onPrimary = SafeStepColors.OnPrimary,
    primaryContainer = SafeStepColors.PrimaryContainer,
    onPrimaryContainer = SafeStepColors.OnPrimaryContainer,
    
    // Secondary (Warning Orange)
    secondary = SafeStepColors.Secondary,
    onSecondary = SafeStepColors.OnSecondary,
    secondaryContainer = SafeStepColors.SecondaryContainer,
    onSecondaryContainer = SafeStepColors.OnSecondaryContainer,
    
    // Tertiary (Success Green)
    tertiary = SafeStepColors.Tertiary,
    onTertiary = SafeStepColors.OnTertiary,
    tertiaryContainer = SafeStepColors.TertiaryContainer,
    onTertiaryContainer = SafeStepColors.OnTertiaryContainer,
    
    // Background & Surface
    background = SafeStepColors.Background,
    onBackground = SafeStepColors.OnBackground,
    surface = SafeStepColors.Surface,
    onSurface = SafeStepColors.OnSurface,
    surfaceVariant = SafeStepColors.SurfaceVariant,
    onSurfaceVariant = SafeStepColors.OnSurfaceMuted,
    
    // Other
    outline = SafeStepColors.Border,
    outlineVariant = SafeStepColors.Divider,
    
    // Error (same as primary for emergency context)
    error = SafeStepColors.Primary,
    onError = SafeStepColors.OnPrimary,
    errorContainer = SafeStepColors.PrimaryContainer,
    onErrorContainer = SafeStepColors.OnPrimaryContainer
)

@Composable
fun SafeStepTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = SafeStepDarkColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SafeStepTypography,
        content = content
    )
}

/**
 * Alert-specific theme for full-screen emergency alerts
 */
@Composable
fun SafeStepAlertTheme(
    content: @Composable () -> Unit
) {
    val alertColorScheme = SafeStepDarkColorScheme.copy(
        background = SafeStepColors.AlertBackground,
        surface = SafeStepColors.AlertSurface,
        onBackground = SafeStepColors.AlertOnBackground,
        onSurface = SafeStepColors.AlertOnBackground
    )
    
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SafeStepColors.AlertBackground.toArgb()
            window.navigationBarColor = SafeStepColors.AlertBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = alertColorScheme,
        typography = SafeStepTypography,
        content = content
    )
}
