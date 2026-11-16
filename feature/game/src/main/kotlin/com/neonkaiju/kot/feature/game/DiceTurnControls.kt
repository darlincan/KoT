package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.neonkaiju.kot.model.*

@Composable
private fun SymbolResolutionType.label(): String = stringResource(
    when (this) {
        SymbolResolutionType.NUMBERS -> R.string.symbol_numbers
        SymbolResolutionType.ENERGY -> R.string.symbol_energy
        SymbolResolutionType.HEARTS -> R.string.symbol_hearts
        SymbolResolutionType.CLAWS -> R.string.symbol_claws
    }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiceTurnControls(state: GameUiState, onEvent: (UiEvent) -> Unit, onTooltip: (String?) -> Unit) {
    var choosingOrder by remember { mutableStateOf(false) }
    var order by remember { mutableStateOf<List<SymbolResolutionType>>(emptyList()) }
    val separator = stringResource(R.string.order_separator)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val remaining = if (state.rollsLeft == 0) stringResource(R.string.rolls_none)
            else frenchPlural(R.plurals.rolls_left, state.rollsLeft, state.rollsLeft)
        Text(stringResource(R.string.roll_counter, state.rollsMade, state.maxRolls, remaining), color = NeonPalette.NeonCyan, fontSize = 12.sp, lineHeight = 14.sp)
        Text(stringResource(if (state.rollsMade == 1) R.string.dice_hint_first else R.string.dice_hint), color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 13.sp)
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
            stringResource(R.string.order_line, state.resolutionOrder.map { it.label() }.joinToString(separator)),
            color = NeonPalette.NeonCyan, fontSize = 10.sp, lineHeight = 12.sp,
            modifier = Modifier.heightIn(min = 48.dp).clickable(enabled = state.canChangeOrder) {
                order = emptyList()
                choosingOrder = true
            }
        )
        if (state.isRolling) {
            NeonButton(stringResource(R.string.action_reroll), enabled = state.reroll.enabled, accentColor = NeonPalette.NeonCyan,
                disabledReason = state.reroll.blockedReason?.label(),
                onDisabledLongPress = { onTooltip(it) }, modifier = Modifier.fillMaxWidth(),
                onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.Reroll)) })
            NeonButton(stringResource(R.string.action_stop_rolling), enabled = state.stopRolling.enabled,
                disabledReason = state.stopRolling.blockedReason?.label(),
                onDisabledLongPress = { onTooltip(it) }, modifier = Modifier.fillMaxWidth(),
                onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.StopRolling)) })
        } else {
            Text(stringResource(if (state.isBuyPhase) R.string.dice_resolved else R.string.decision_pending), color = NeonPalette.TextPrimary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NeonButton(stringResource(R.string.shop_name), enabled = state.openShop.enabled, disabledReason = state.openShop.blockedReason?.label(),
                    onDisabledLongPress = { onTooltip(it) }, modifier = Modifier.weight(1f),
                    onClick = { onEvent(UiEvent.OpenShop) })
                NeonButton(stringResource(R.string.action_end_turn), enabled = state.endTurn.enabled, disabledReason = state.endTurn.blockedReason?.label(),
                    onDisabledLongPress = { onTooltip(it) }, modifier = Modifier.weight(1f),
                    onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.EndTurn)) })
            }
        }
    }
    if (choosingOrder) {
        var confirmHint by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { choosingOrder = false },
            // Plus large que le défaut : les 4 catégories tiennent sur une ligne en paysage.
            modifier = Modifier.fillMaxWidth(0.9f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(stringResource(R.string.order_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(confirmHint ?: stringResource(R.string.order_hint))
                    Text(order.map { it.label() }.joinToString(separator))
                    // Sur une seule ligne si la place le permet : en paysage, la fenêtre est peu haute.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SymbolResolutionType.entries.filter { it !in order }.forEach { symbol ->
                            NeonButton(symbol.label(), onClick = { order = order + symbol })
                        }
                    }
                }
            },
            confirmButton = {
                NeonButton(stringResource(R.string.order_confirm), enabled = order.size == SymbolResolutionType.entries.size,
                    disabledReason = stringResource(R.string.order_confirm_blocked),
                    onDisabledLongPress = { confirmHint = it },
                    onClick = {
                        onEvent(UiEvent.DispatchGameAction(GameAction.SetResolutionOrder(order)))
                        choosingOrder = false
                    })
            },
            dismissButton = {
                NeonButton(stringResource(R.string.order_restart), accentColor = NeonPalette.NeonCyan, onClick = { order = emptyList() })
            })
    }
}
