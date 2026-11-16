package com.neonkaiju.kot.feature.game

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/** Durée maximale du glitch et de l'animation du compteur ♥ (§2.3). */
const val GLITCH_DURATION_MS = 300

private val BANDS = listOf(0.08f to 0.05f, 0.27f to 0.03f, 0.44f to 0.08f, 0.63f to 0.04f, 0.78f to 0.06f, 0.92f to 0.03f)

/** Réglage système « Supprimer les animations ». */
internal fun Context.animationsEnabled(): Boolean = runCatching {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}.getOrDefault(true)

/** Intensité de 1 à 0 après une baisse de [hp] ; reste à 0 si les animations sont supprimées. */
@Composable
fun damageFlash(hp: Int): Float {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    var previousHp by remember { mutableIntStateOf(hp) }
    LaunchedEffect(hp) {
        val hit = hp < previousHp
        previousHp = hp
        if (hit && context.animationsEnabled()) {
            progress.snapTo(1f)
            progress.animateTo(0f, tween(GLITCH_DURATION_MS, easing = LinearEasing))
        }
    }
    return progress.value
}

/** Vibration courte quand le joueur perd des ♥, même pendant le tour d'un bot et sans animations. */
@Composable
fun DamageHaptics(hp: Int) {
    val haptics = LocalHapticFeedback.current
    var previousHp by remember { mutableIntStateOf(hp) }
    LaunchedEffect(hp) {
        if (hp < previousHp) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        previousHp = hp
    }
}

/** Bandes décalées plein écran, sans flou ni RenderEffect : même rendu sur toutes les versions d'Android. */
@Composable
fun GlitchOverlay(intensity: Float) {
    if (intensity <= 0f) return
    Canvas(Modifier.fillMaxSize()) {
        BANDS.forEachIndexed { index, (top, height) ->
            val shift = (if (index % 2 == 0) 1f else -1f) * size.width * 0.04f * intensity
            val color = if (index % 2 == 0) NeonPalette.NeonPink else NeonPalette.NeonCyan
            drawRect(
                color = color.copy(alpha = 0.45f * intensity),
                topLeft = Offset(shift, size.height * top),
                size = Size(size.width, size.height * height),
            )
        }
    }
}
