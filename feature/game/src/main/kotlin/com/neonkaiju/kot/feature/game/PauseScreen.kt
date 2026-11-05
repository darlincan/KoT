package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.TurnPhase

@Composable
fun PauseScreen(onResume: () -> Unit, onSaveAndQuit: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().background(NeonPalette.Background).systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PARTIE EN PAUSE", color = NeonPalette.NeonCyan, fontSize = 22.sp, lineHeight = 26.sp)
        Text("Les bots attendent. Chaque action est sauvegardée automatiquement.", color = NeonPalette.TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
        NeonButton("Reprendre la partie", onClick = onResume)
        NeonButton("Sauvegarder et quitter", onClick = onSaveAndQuit)
        NeonButton("Retour à l’accueil", onClick = onHome, accentColor = NeonPalette.NeonCyan)
    }
}

@Composable
fun TurnStatusPanel(state: GameState, onEvent: (UiEvent) -> Unit) {
    val decision = state.decisionPlayer
    val phase = if (state.pendingMaliceLevels.isNotEmpty()) "Choisir un pouvoir de Surcharge" else when (state.phase) {
        TurnPhase.LANCER -> "Lancer les dés"
        TurnPhase.RESOUDRE -> "Résoudre les résultats"
        TurnPhase.DECISION_FUITE -> "Fuir ou rester dans la Tour"
        TurnPhase.ENTRER -> "Entrer dans la Tour"
        TurnPhase.ACHETER -> "Acheter des cartes ou finir le tour"
        TurnPhase.FIN_TOUR -> "Fin du tour"
        TurnPhase.FIN_PARTIE -> "Partie terminée"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(if (decision.isBot) "Tour de ${decision.monster.displayName} — le bot joue" else "À TON TOUR",
                color = NeonPalette.NeonPink, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp)
            Text(phase, color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 13.sp)
        }
        NeonButton("Pause", onClick = { onEvent(UiEvent.Pause) })
    }
}
