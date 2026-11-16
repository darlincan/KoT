package com.neonkaiju.kot.feature.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.DieFace

private const val FACE_CHANGE_MS = 180

/**
 * Dé en verre lumineux : rose et cadenas si gardé, cyan sinon ; grisé et sans lueur quand il n'est
 * pas modifiable. La lueur vient de [neonGlow] (flou à partir de l'API 31, dégradé en dessous).
 */
@Composable
fun NeonDieView(die: DieUi, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val symbol = when (die.face) {
        DieFace.ONE -> "1"
        DieFace.TWO -> "2"
        DieFace.THREE -> "3"
        DieFace.ENERGY -> "⚡"
        DieFace.CLAW -> "🐾"
        DieFace.HEART -> "♥"
    }
    val faceName = stringResource(
        when (die.face) {
            DieFace.ONE -> R.string.face_one
            DieFace.TWO -> R.string.face_two
            DieFace.THREE -> R.string.face_three
            DieFace.ENERGY -> R.string.face_energy
            DieFace.CLAW -> R.string.face_claw
            DieFace.HEART -> R.string.face_heart
        }
    )
    val a11y = stringResource(
        R.string.die_a11y,
        die.id + 1,
        faceName,
        stringResource(if (die.isKept) R.string.die_a11y_locked else R.string.die_a11y_free),
    )
    val accent = if (die.isKept) NeonPalette.NeonPink else NeonPalette.NeonCyan
    // 0,6 : le libellé d'un dé grisé garde un contraste d'au moins 4,5:1 sur la surface.
    val border = if (die.canToggle) accent else accent.copy(alpha = 0.4f)
    val label = if (die.canToggle) NeonPalette.TextPrimary else NeonPalette.TextPrimary.copy(alpha = 0.6f)
    val glass = Brush.radialGradient(listOf(accent.copy(alpha = if (die.canToggle) 0.20f else 0.06f), NeonPalette.Surface))

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    var shownFace by remember { mutableStateOf(die.face) }
    LaunchedEffect(die.face) {
        if (shownFace != die.face) {
            shownFace = die.face
            // Aucune animation si le réglage système « Supprimer les animations » est actif.
            if (context.animationsEnabled()) {
                scale.snapTo(0.8f)
                scale.animateTo(1f, tween(FACE_CHANGE_MS))
            }
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .semantics { contentDescription = a11y }
            .neonGlow(accent, active = die.canToggle)
            .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
            .background(glass, RoundedCornerShape(8.dp))
            .drawBehind {
                // Reflet du verre, en haut du dé.
                val inset = 4.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height * 0.28f),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            .border(2.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = die.canToggle, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.die_label, symbol, stringResource(if (die.isKept) R.string.die_kept else R.string.die_free)),
            color = label,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}
