package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.*

private fun SymbolResolutionType.label() = when (this) {
    SymbolResolutionType.NUMBERS -> "Chiffres"
    SymbolResolutionType.ENERGY -> "Cellules"
    SymbolResolutionType.HEARTS -> "Soins"
    SymbolResolutionType.CLAWS -> "Griffes"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiceTurnControls(state: GameUiState, onEvent: (UiEvent) -> Unit, onTooltip: (String?) -> Unit) {
    var choosingOrder by remember { mutableStateOf(false) }
    var order by remember { mutableStateOf<List<SymbolResolutionType>>(emptyList()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val remaining = when (state.rollsLeft) {
            0 -> "aucun lancer restant"
            1 -> "1 lancer restant"
            else -> "${state.rollsLeft} lancers restants"
        }
        Text("Lancer ${state.rollsMade}/${state.maxRolls} — $remaining", color = NeonPalette.NeonCyan, fontSize = 12.sp, lineHeight = 14.sp)
        Text(if (state.rollsMade == 1) "Premier lancer automatique. Touchez un dé pour le verrouiller."
            else "Touchez un dé pour le verrouiller.", color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 13.sp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.dice.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { die ->
                        NeonDieView(die, Modifier.weight(1f)) {
                            onEvent(UiEvent.DispatchGameAction(GameAction.ToggleKeep(die.id)))
                        }
                    }
                }
            }
        }
        Text(
            "Ordre : ${state.resolutionOrder.joinToString(" → ") { it.label() }} · Modifier",
            color = NeonPalette.NeonCyan, fontSize = 10.sp, lineHeight = 12.sp,
            modifier = Modifier.heightIn(min = 48.dp).clickable(enabled = state.canChangeOrder) {
                order = emptyList()
                choosingOrder = true
            }
        )
        if (state.isRolling) {
            NeonButton("Relancer", enabled = state.reroll.enabled, accentColor = NeonPalette.NeonCyan,
                disabledReason = state.reroll.blockedReason?.label(),
                onDisabledLongPress = { onTooltip(it) }, modifier = Modifier.fillMaxWidth(),
                onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.Reroll)) })
            NeonButton("Résoudre", enabled = state.stopRolling.enabled,
                disabledReason = state.stopRolling.blockedReason?.label(),
                onDisabledLongPress = { onTooltip(it) }, modifier = Modifier.fillMaxWidth(),
                onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.StopRolling)) })
        } else {
            Text(if (state.isBuyPhase) "Dés résolus. Achetez des cartes ou terminez le tour."
                else "Décision en cours…", color = NeonPalette.TextPrimary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NeonButton("Marché noir", enabled = state.openShop.enabled, disabledReason = state.openShop.blockedReason?.label(),
                    onDisabledLongPress = { onTooltip(it) }, modifier = Modifier.weight(1f),
                    onClick = { onEvent(UiEvent.OpenShop) })
                NeonButton("Fin du tour", enabled = state.endTurn.enabled, disabledReason = state.endTurn.blockedReason?.label(),
                    onDisabledLongPress = { onTooltip(it) }, modifier = Modifier.weight(1f),
                    onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.EndTurn)) })
            }
        }
    }
    if (choosingOrder) {
        AlertDialog(onDismissRequest = { choosingOrder = false },
            // Plus large que le défaut : les 4 catégories tiennent sur une ligne en paysage.
            modifier = Modifier.fillMaxWidth(0.9f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Ordre de résolution automatique") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choisissez les quatre catégories dans l’ordre souhaité.")
                    Text(order.joinToString(" → ") { it.label() })
                    // Sur une seule ligne si la place le permet : en paysage, la fenêtre est peu haute.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SymbolResolutionType.entries.filter { it !in order }.forEach { symbol ->
                            NeonButton(symbol.label(), onClick = { order = order + symbol })
                        }
                    }
                }
            },
            confirmButton = {
                NeonButton("Confirmer", enabled = order.size == 4, onClick = {
                    onEvent(UiEvent.DispatchGameAction(GameAction.SetResolutionOrder(order)))
                    choosingOrder = false
                })
            },
            dismissButton = {
                NeonButton("Recommencer", accentColor = NeonPalette.NeonCyan, onClick = { order = emptyList() })
            })
    }
}
