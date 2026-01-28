package com.safestep.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safestep.app.ui.theme.SafeStepColors

/**
 * Large primary button for emergency actions (CALL, ACKNOWLEDGE)
 * 
 * Sized for elderly accessibility:
 * - Minimum 56dp height (meets accessibility guidelines)
 * - Large text
 * - High contrast
 */
@Composable
fun LargePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = SafeStepColors.ButtonCall,
    textColor: Color = SafeStepColors.ButtonCallText,
    contentDesc: String = text,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .semantics { contentDescription = contentDesc },
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.5f),
            disabledContentColor = textColor.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Outlined secondary button for less critical actions
 */
@Composable
fun LargeOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDesc: String = text
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = contentDesc },
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(SafeStepColors.ButtonOutline)
        )
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Small text button for tertiary actions
 */
@Composable
fun SmallTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = SafeStepColors.OnBackgroundMuted
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = textColor
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * Data badge for displaying metrics (e.g., impact_g)
 */
@Composable
fun DataBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = SafeStepColors.SurfaceVariant,
    textColor: Color = Color.White
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Pulsing background for alert screen
 */
@Composable
fun PulsingBackground(
    modifier: Modifier = Modifier,
    baseColor: Color = SafeStepColors.AlertBackground,
    pulseColor: Color = SafeStepColors.AlertPulse
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .background(baseColor)
    )
}

/**
 * Status indicator dot
 */
@Composable
fun StatusDot(
    status: DeviceStatusType,
    modifier: Modifier = Modifier,
    size: Int = 12
) {
    val color = when (status) {
        DeviceStatusType.ONLINE -> SafeStepColors.StatusOnline
        DeviceStatusType.WARNING -> SafeStepColors.StatusWarning
        DeviceStatusType.OFFLINE -> SafeStepColors.StatusOffline
        DeviceStatusType.UNKNOWN -> SafeStepColors.StatusUnknown
    }
    
    Box(
        modifier = modifier
            .size(size.dp)
            .background(color, RoundedCornerShape(size.dp / 2))
    )
}

enum class DeviceStatusType {
    ONLINE, WARNING, OFFLINE, UNKNOWN
}
