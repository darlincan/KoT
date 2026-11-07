package com.neonkaiju.kot.feature.game

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NeonPalette {
    val Background = Color(0xFF0B0B14)
    val Surface = Color(0xFF15152A)
    val NeonPink = Color(0xFFFF3D9A)
    val NeonCyan = Color(0xFF28E0FF)
    val TextPrimary = Color(0xFFE9E7F5)
    val DarkLabel = Color(0xFF0B0B14)
}

private val NeonColorScheme = darkColorScheme(
    background = NeonPalette.Background,
    surface = NeonPalette.Surface,
    primary = NeonPalette.NeonPink,
    secondary = NeonPalette.NeonCyan,
    onPrimary = NeonPalette.DarkLabel,
    onSecondary = NeonPalette.DarkLabel,
    onBackground = NeonPalette.TextPrimary,
    onSurface = NeonPalette.TextPrimary
)

@Composable
fun NeonKaijuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonColorScheme,
        content = content
    )
}

fun Modifier.neonGlow(color: Color, active: Boolean = true): Modifier = this.drawBehind {
    if (active) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.35f), Color.Transparent),
                radius = size.maxDimension * 0.75f
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = NeonPalette.NeonPink,
    disabledReason: String? = null,
    onDisabledLongPress: (String) -> Unit = {}
) {
    val bgColor = if (enabled) accentColor else NeonPalette.Surface
    // 0,55 : le libellé d'un bouton grisé garde un contraste d'au moins 4,5:1 sur la surface.
    val textColor = if (enabled) NeonPalette.DarkLabel else NeonPalette.TextPrimary.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
            .neonGlow(accentColor, active = enabled && Build.VERSION.SDK_INT >= 29)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, if (enabled) accentColor else accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .semantics {
                if (!enabled) {
                    disabled()
                    disabledReason?.let { stateDescription = it }
                }
            }
            .combinedClickable(
                role = Role.Button,
                onClick = { if (enabled) onClick() },
                onLongClick = { if (!enabled && !disabledReason.isNullOrBlank()) onDisabledLongPress(disabledReason) }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontFamily = FontFamily.SansSerif
        )
    }
}
