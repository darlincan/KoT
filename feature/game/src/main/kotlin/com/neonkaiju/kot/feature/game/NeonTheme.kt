package com.neonkaiju.kot.feature.game

import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.Typography
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
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

/** Titres et noms de monstres. */
val OrbitronFamily = FontFamily(Font(R.font.orbitron_bold, FontWeight.Bold))

/** Compteurs, descriptions et boutons. */
val RajdhaniFamily = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

private val NeonTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = OrbitronFamily),
        displayMedium = displayMedium.copy(fontFamily = OrbitronFamily),
        displaySmall = displaySmall.copy(fontFamily = OrbitronFamily),
        headlineLarge = headlineLarge.copy(fontFamily = OrbitronFamily),
        headlineMedium = headlineMedium.copy(fontFamily = OrbitronFamily),
        headlineSmall = headlineSmall.copy(fontFamily = OrbitronFamily, fontSize = 18.sp, lineHeight = 22.sp),
        titleLarge = titleLarge.copy(fontFamily = OrbitronFamily),
        titleMedium = titleMedium.copy(fontFamily = OrbitronFamily),
        titleSmall = titleSmall.copy(fontFamily = OrbitronFamily),
        bodyLarge = bodyLarge.copy(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium),
        bodyMedium = bodyMedium.copy(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium),
        bodySmall = bodySmall.copy(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.copy(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold),
        labelSmall = labelSmall.copy(fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun NeonKaijuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonColorScheme,
        typography = NeonTypography,
        content = content
    )
}

/**
 * Lueur autour d'un composant : vrai flou à partir de l'API 31, dégradé sans flou en dessous
 * (ou quand le canvas n'est pas accéléré, car le flou passe par un RenderNode).
 */
fun Modifier.neonGlow(color: Color, active: Boolean = true): Modifier = if (!active) this else drawWithCache {
    val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurredGlow(size, color, 10.dp.toPx(), 8.dp.toPx()) else null
    onDrawBehind {
        val canvas = drawContext.canvas.nativeCanvas
        if (blurred != null && canvas.isHardwareAccelerated) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) drawBlurredGlow(canvas, blurred, 10.dp.toPx())
        } else {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.35f), Color.Transparent),
                    radius = size.maxDimension * 0.75f
                )
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun blurredGlow(size: Size, color: Color, margin: Float, corner: Float): RenderNode {
    val node = RenderNode("neonGlow")
    val width = size.width + 2 * margin
    val height = size.height + 2 * margin
    node.setPosition(0, 0, width.toInt(), height.toInt())
    node.setRenderEffect(RenderEffect.createBlurEffect(margin / 2, margin / 2, Shader.TileMode.DECAL))
    val recording = node.beginRecording()
    val paint = Paint().apply { this.color = color.copy(alpha = 0.6f).toArgb() }
    recording.drawRoundRect(margin, margin, width - margin, height - margin, corner, corner, paint)
    node.endRecording()
    return node
}

@RequiresApi(Build.VERSION_CODES.S)
private fun drawBlurredGlow(canvas: android.graphics.Canvas, node: RenderNode, margin: Float) {
    canvas.save()
    canvas.translate(-margin, -margin)
    canvas.drawRenderNode(node)
    canvas.restore()
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
            .neonGlow(accentColor, active = enabled)
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
            fontFamily = RajdhaniFamily
        )
    }
}
