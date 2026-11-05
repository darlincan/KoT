package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.*

private fun SymbolResolutionType.label() = when (this) {
    SymbolResolutionType.NUMBERS -> "Chiffres"
    SymbolResolutionType.ENERGY -> "Cellules"
    SymbolResolutionType.HEARTS -> "Soins"
    SymbolResolutionType.CLAWS -> "Griffes"
}

@Composable
fun DiceTurnControls(state: GameState, onEvent: (UiEvent) -> Unit, onTooltip: (String?) -> Unit) {
    val legal = if (state.decisionPlayer.isBot) emptyList() else GameEngine.legalActions(state)
    var choosingOrder by remember { mutableStateOf(false) }
    var order by remember { mutableStateOf<List<SymbolResolutionType>>(emptyList()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val remaining = when (state.rollsLeft) {
            0 -> "aucun lancer restant"
            1 -> "1 lancer restant"
            else -> "${state.rollsLeft} lancers restants"
        }
        Text("Lancer ${state.rollsMade}/${state.maxRolls} — $remaining", color = NeonPalette.NeonCyan, fontSize = 12.sp, lineHeight = 14.sp)
        Text(if (state.rollsMade == 1) "Premier lancer automatique. Touchez les dés pour les conserver ou les relancer."
            else "Touchez les dés pour les conserver ou les relancer.", color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 13.sp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.dice.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { die ->
                        NeonDieView(die, GameAction.ToggleKeep(die.id) in legal, Modifier.weight(1f)) {
                            onEvent(UiEvent.DispatchGameAction(GameAction.ToggleKeep(die.id)))
                        }
                    }
                }
            }
        }
        Text(
            "Ordre : ${state.resolutionOrder.joinToString(" → ") { it.label() }} · Modifier",
            color = NeonPalette.NeonCyan, fontSize = 10.sp, lineHeight = 12.sp,
            modifier = Modifier.heightIn(min = 48.dp).clickable(enabled = state.phase == TurnPhase.LANCER && !state.decisionPlayer.isBot && state.pendingMaliceLevels.isEmpty()) {
                order = emptyList()
                choosingOrder = true
            }
        )
        if (state.phase == TurnPhase.LANCER) {
            NeonButton("Relancer les dés non conservés", enabled = GameAction.Reroll in legal,
                disabledReason = if (state.decisionPlayer.isBot) "Le bot joue" else "Tous les dés sont conservés : terminez les lancers",
                onDisabledLongPress = { onTooltip(it) }, modifier = Modifier.fillMaxWidth(),
                onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.Reroll)) })
            NeonButton("Terminer les lancers et résoudre", enabled = GameAction.StopRolling in legal,
                accentColor = NeonPalette.NeonCyan, modifier = Modifier.fillMaxWidth(),
                onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.StopRolling)) })
        } else {
            Text(if (state.phase == TurnPhase.ACHETER) "Dés résolus. Achetez des cartes ou terminez le tour."
                else "Décision en cours…", color = NeonPalette.TextPrimary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NeonButton("Marché noir", enabled = GameAction.EndTurn in legal, modifier = Modifier.weight(1f),
                    onClick = { onEvent(UiEvent.OpenShop) })
                NeonButton("Fin du tour", enabled = GameAction.EndTurn in legal, modifier = Modifier.weight(1f),
                    onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.EndTurn)) })
            }
        }
    }
    if (choosingOrder) {
        AlertDialog(onDismissRequest = { choosingOrder = false },
            title = { Text("Ordre de résolution automatique") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choisissez les quatre catégories dans l’ordre souhaité.")
                    Text(order.joinToString(" → ") { it.label() })
                    SymbolResolutionType.entries.filter { it !in order }.forEach { symbol ->
                        NeonButton(symbol.label(), onClick = { order = order + symbol })
                    }
                    NeonButton("Recommencer", onClick = { order = emptyList() })
                }
            },
            confirmButton = {
                NeonButton("Confirmer", enabled = order.size == 4, onClick = {
                    onEvent(UiEvent.DispatchGameAction(GameAction.SetResolutionOrder(order)))
                    choosingOrder = false
                })
            })
    }
}
