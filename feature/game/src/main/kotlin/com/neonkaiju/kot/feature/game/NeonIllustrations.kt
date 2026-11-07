package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.MonsterId

private val BUILDINGS = listOf(
    0.00f to 0.34f, 0.11f to 0.52f, 0.23f to 0.28f, 0.34f to 0.44f,
    0.60f to 0.38f, 0.71f to 0.58f, 0.84f to 0.30f, 0.93f to 0.46f,
)

/** Voltrax perché sur une antenne, pluie de néons sur la mégapole (wireframe 01). Décoratif. */
@Composable
fun HomeIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(12.dp))) {
        drawRect(Brush.verticalGradient(listOf(NeonPalette.Background, NeonPalette.Surface, NeonPalette.NeonPink.copy(alpha = 0.25f))))
        for (i in 0 until 26) {
            val x = size.width * ((i * 37 % 100) / 100f)
            val y = size.height * ((i * 53 % 100) / 100f)
            val color = if (i % 2 == 0) NeonPalette.NeonCyan else NeonPalette.NeonPink
            drawLine(color.copy(alpha = 0.35f), Offset(x, y), Offset(x - 5f, y + 26f), strokeWidth = 2f)
        }
        BUILDINGS.forEachIndexed { index, (left, height) ->
            val top = size.height * (1f - height)
            drawRect(NeonPalette.Background, Offset(size.width * left, top), Size(size.width * 0.12f, size.height - top))
            drawRect(
                (if (index % 2 == 0) NeonPalette.NeonCyan else NeonPalette.NeonPink).copy(alpha = 0.55f),
                Offset(size.width * left, top), Size(size.width * 0.12f, size.height - top), style = Stroke(2f),
            )
        }
        drawAntennaAndKaiju()
    }
}

private fun DrawScope.drawAntennaAndKaiju() {
    val cx = size.width * 0.5f
    val base = size.height
    val top = size.height * 0.46f
    drawLine(NeonPalette.NeonCyan, Offset(cx, base), Offset(cx, top), strokeWidth = 5f)
    for (step in 1..3) {
        val y = top + (base - top) * step / 4f
        val half = size.width * 0.012f * (step + 1)
        drawLine(NeonPalette.NeonCyan, Offset(cx - half, y), Offset(cx + half, y), strokeWidth = 3f)
    }
    val unit = size.minDimension * 0.06f
    val body = Path().apply {
        moveTo(cx - 2.4f * unit, top)
        lineTo(cx - 1.2f * unit, top - 2.0f * unit)
        lineTo(cx - 1.0f * unit, top - 3.2f * unit)
        lineTo(cx + 0.4f * unit, top - 3.8f * unit)
        lineTo(cx + 2.0f * unit, top - 3.2f * unit)
        lineTo(cx + 0.9f * unit, top - 2.5f * unit)
        lineTo(cx + 1.4f * unit, top - 1.0f * unit)
        lineTo(cx + 0.8f * unit, top)
        close()
    }
    drawPath(body, NeonPalette.Background)
    drawPath(body, NeonPalette.NeonCyan, style = Stroke(3f))
    drawCircle(NeonPalette.NeonPink, unit * 0.22f, Offset(cx + 0.7f * unit, top - 3.2f * unit))
    drawLine(NeonPalette.NeonPink, Offset(cx - 2.4f * unit, top), Offset(cx - 3.6f * unit, top - 1.2f * unit), strokeWidth = 4f, cap = StrokeCap.Round)
}

/** Anneau de la Tour : couleur et initiale de l'occupant, pointillés si elle est libre (wireframe 03). */
@Composable
fun TowerRing(occupant: MonsterId?, modifier: Modifier = Modifier) {
    val accent = occupant?.let { Color(it.accentHex) } ?: NeonPalette.NeonCyan.copy(alpha = 0.5f)
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(accent.copy(alpha = 0.15f))
            drawCircle(
                accent,
                style = Stroke(3.dp.toPx(), pathEffect = if (occupant == null) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null),
            )
        }
        occupant?.let {
            Text(it.displayName.take(1), color = NeonPalette.TextPrimary, fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}
