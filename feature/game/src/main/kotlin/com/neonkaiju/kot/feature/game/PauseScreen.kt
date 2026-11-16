package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PauseScreen(onResume: () -> Unit, onSaveAndQuit: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().background(NeonPalette.Background).systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PARTIE EN PAUSE", color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontSize = 22.sp, lineHeight = 26.sp)
        Text("Les bots attendent. Chaque action est sauvegardée automatiquement.", color = NeonPalette.TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
        NeonButton("Reprendre la partie", onClick = onResume)
        NeonButton("Sauvegarder et quitter", onClick = onSaveAndQuit)
        NeonButton("Retour à l’accueil", onClick = onHome, accentColor = NeonPalette.NeonCyan)
    }
}

@Composable
fun TurnStatusPanel(state: GameUiState, onEvent: (UiEvent) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(state.botTurn?.let { "Tour de ${it.botName} — le bot joue" } ?: "À TON TOUR",
                color = NeonPalette.NeonPink, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp)
            Text(state.phaseLabel, color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 13.sp)
        }
        NeonButton("≡", onClick = { onEvent(UiEvent.Pause) }, modifier = Modifier.semantics { contentDescription = "Pause" })
    }
}
