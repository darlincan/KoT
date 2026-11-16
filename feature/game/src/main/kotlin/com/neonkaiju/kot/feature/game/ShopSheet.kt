package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.*

private data class MarketConfirmation(val action: GameAction, val revision: Int)

@Composable
private fun ReceiptUi.text(): String = when {
    effectChange > 0 -> stringResource(R.string.shop_receipt_gain, itemName, energyBefore, paid, energyAfter, effectChange)
    effectChange < 0 -> stringResource(R.string.shop_receipt_loss, itemName, energyBefore, paid, energyAfter, -effectChange)
    else -> stringResource(R.string.shop_receipt, itemName, energyBefore, paid, energyAfter)
}

@Composable
private fun ShopCardUi.accessibilityLabel(): String = stringResource(
    R.string.shop_card_a11y,
    name,
    stringResource(if (type == CardType.ACTION) R.string.shop_kind_action else R.string.shop_kind_power),
    frenchPlural(R.plurals.a11y_cells, cost, cost),
    text,
)

@Composable
fun ShopSheet(state: ShopUiState, onBuy: (String, Int) -> Unit, onSweep: (Int) -> Unit, onClose: () -> Unit) {
    var selectedId by remember(state.buyerId) { mutableStateOf<String?>(null) }
    var confirmation by remember(state.buyerId) { mutableStateOf<MarketConfirmation?>(null) }
    var tooltip by remember { mutableStateOf<String?>(null) }
    val selected = state.cards.firstOrNull { it.id == selectedId }

    Column(Modifier.fillMaxSize().background(NeonPalette.Background).systemBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(frenchPlural(R.plurals.shop_title, state.energy, state.energy), color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily,
            fontSize = 16.sp, lineHeight = 19.sp)
        state.lastReceipt?.let { Text(it.text(), color = NeonPalette.NeonPink, fontSize = 13.sp, lineHeight = 16.sp) }
        tooltip?.let { Text(it, color = NeonPalette.NeonPink, fontSize = 12.sp, lineHeight = 14.sp) }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.cards.forEach { card ->
                val selectedCard = card.id == selectedId
                val a11y = card.accessibilityLabel()
                Column(Modifier.weight(1f).fillMaxHeight()
                    .semantics { contentDescription = a11y }
                    .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
                    .border(if (selectedCard) 3.dp else 1.dp, if (selectedCard) NeonPalette.NeonPink else NeonPalette.NeonCyan, RoundedCornerShape(10.dp))
                    .padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.name, color = NeonPalette.NeonPink, fontSize = 14.sp, lineHeight = 17.sp)
                    Text(stringResource(if (card.type == CardType.ACTION) R.string.shop_type_action else R.string.shop_type_power),
                        color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 13.sp)
                    Text(if (card.cost != card.baseCost) stringResource(R.string.shop_price_discount, card.cost, card.baseCost) else stringResource(R.string.shop_price, card.cost),
                        color = NeonPalette.NeonCyan, fontSize = 13.sp, lineHeight = 16.sp)
                    Text(card.text, color = NeonPalette.TextPrimary, fontSize = 12.sp, lineHeight = 15.sp,
                        modifier = Modifier.weight(1f))
                    NeonButton(stringResource(if (selectedCard) R.string.shop_selected else R.string.shop_select), enabled = !state.isBotTurn,
                        disabledReason = BlockedReason.BOT_PLAYING.label(), onDisabledLongPress = { tooltip = it },
                        modifier = Modifier.fillMaxWidth(), onClick = { selectedId = card.id; tooltip = null })
                }
            }
        }
        Text(
            when {
                selected == null -> stringResource(R.string.shop_hint)
                selected.energyAfterPurchase >= 0 -> stringResource(R.string.shop_after_payment, selected.name, selected.cost, selected.energyAfterPurchase)
                else -> frenchPlural(R.plurals.shop_missing, -selected.energyAfterPurchase, -selected.energyAfterPurchase, selected.name)
            },
            color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonButton(stringResource(R.string.shop_sweep, state.sweepCost), enabled = state.canSweep, modifier = Modifier.weight(1f),
                disabledReason = stringResource(R.string.shop_sweep_blocked, state.sweepCost), onDisabledLongPress = { tooltip = it },
                onClick = { confirmation = MarketConfirmation(GameAction.SweepShop, state.revision) })
            NeonButton(stringResource(R.string.shop_buy), enabled = selected?.canBuy == true,
                modifier = Modifier.weight(1f),
                disabledReason = selected?.let { stringResource(R.string.shop_buy_blocked, it.cost, it.name) } ?: stringResource(R.string.shop_buy_no_selection),
                onDisabledLongPress = { tooltip = it }, onClick = {
                    selected?.let { confirmation = MarketConfirmation(GameAction.BuyCard(it.id), state.revision) }
                })
            NeonButton(stringResource(R.string.shop_close), modifier = Modifier.weight(1f), onClick = onClose)
        }
    }
    confirmation?.let { pending ->
        val buy = pending.action as? GameAction.BuyCard
        val card = buy?.let { action -> state.cards.firstOrNull { it.id == action.cardId } }
        val available = if (buy == null) state.canSweep else card?.canBuy == true
        val valid = pending.revision == state.revision && available
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(if (buy == null) stringResource(R.string.shop_confirm_sweep_title) else stringResource(R.string.shop_confirm_buy_title, card?.name.orEmpty())) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (card != null) {
                    Text(stringResource(R.string.shop_confirm_payment, state.energy, card.cost, card.energyAfterPurchase))
                    Text(card.text)
                    Text(stringResource(if (card.type == CardType.ACTION) R.string.shop_confirm_action else R.string.shop_confirm_power))
                } else Text(stringResource(R.string.shop_confirm_sweep, state.sweepCost, state.energyAfterSweep))
                if (!valid) Text(stringResource(R.string.shop_confirm_stale))
            } },
            confirmButton = { NeonButton(stringResource(R.string.shop_confirm_button), enabled = valid,
                disabledReason = stringResource(R.string.shop_confirm_stale), onClick = {
                // Consume the confirmation before dispatching; repeated callbacks cannot pay twice.
                if (confirmation == pending) {
                    confirmation = null
                    selectedId = null
                    if (buy != null) onBuy(buy.cardId, pending.revision) else onSweep(pending.revision)
                }
            }) },
            dismissButton = { NeonButton(stringResource(R.string.action_cancel), onClick = { confirmation = null }, accentColor = NeonPalette.NeonCyan) })
    }
}
