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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.DieFace

private const val FACE_CHANGE_MS = 180

/** Rose et cadenas si le dé est gardé, cyan sinon ; grisé quand il n'est pas modifiable. */
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
    val accent = if (die.isKept) NeonPalette.NeonPink else NeonPalette.NeonCyan
    // 0,6 : le libellé d'un dé grisé garde un contraste d'au moins 4,5:1 sur la surface.
    val border = if (die.canToggle) accent else accent.copy(alpha = 0.4f)
    val label = if (die.canToggle) NeonPalette.TextPrimary else NeonPalette.TextPrimary.copy(alpha = 0.6f)

    // Compose suit le réglage système : l'animation se coupe si les animations sont désactivées.
    val scale = remember { Animatable(1f) }
    var shownFace by remember { mutableStateOf(die.face) }
    LaunchedEffect(die.face) {
        if (shownFace != die.face) {
            shownFace = die.face
            scale.snapTo(0.8f)
            scale.animateTo(1f, tween(FACE_CHANGE_MS))
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .semantics { contentDescription = die.accessibilityLabel }
            .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
            .border(2.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = die.canToggle, role = Role.Button, onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$symbol\n${if (die.isKept) "🔒 Gardé" else "À relancer"}",
            color = label,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}
