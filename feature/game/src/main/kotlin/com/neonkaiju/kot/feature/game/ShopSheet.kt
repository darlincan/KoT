package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.*

private data class MarketConfirmation(val action: GameAction, val revision: Int)

@Composable
fun ShopSheet(state: ShopUiState, onBuy: (String, Int) -> Unit, onSweep: (Int) -> Unit, onClose: () -> Unit) {
    var selectedId by remember(state.buyerId) { mutableStateOf<String?>(null) }
    var confirmation by remember(state.buyerId) { mutableStateOf<MarketConfirmation?>(null) }
    var tooltip by remember { mutableStateOf<String?>(null) }
    val selected = state.cards.firstOrNull { it.id == selectedId }

    Column(Modifier.fillMaxSize().background(NeonPalette.Background).systemBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("MARCHÉ NOIR — ${state.energy} ${if (state.energy > 1) "CELLULES DISPONIBLES" else "CELLULE DISPONIBLE"} ⚡", color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily,
            fontSize = 16.sp, lineHeight = 19.sp)
        state.lastReceipt?.let { Text(it, color = NeonPalette.NeonPink, fontSize = 13.sp, lineHeight = 16.sp) }
        tooltip?.let { Text(it, color = NeonPalette.NeonPink, fontSize = 12.sp, lineHeight = 14.sp) }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.cards.forEach { card ->
                val selectedCard = card.id == selectedId
                Column(Modifier.weight(1f).fillMaxHeight()
                    .semantics { contentDescription = card.accessibilityLabel }
                    .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
                    .border(if (selectedCard) 3.dp else 1.dp, if (selectedCard) NeonPalette.NeonPink else NeonPalette.NeonCyan, RoundedCornerShape(10.dp))
                    .padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.name, color = NeonPalette.NeonPink, fontSize = 14.sp, lineHeight = 17.sp)
                    Text(if (card.type == CardType.ACTION) "ACTION · effet immédiat, puis défaussée" else "POUVOIR · carte conservée",
                        color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 13.sp)
                    Text("Prix : ${card.cost} ⚡${if (card.cost != card.baseCost) " (base ${card.baseCost})" else ""}",
                        color = NeonPalette.NeonCyan, fontSize = 13.sp, lineHeight = 16.sp)
                    Text(card.text, color = NeonPalette.TextPrimary, fontSize = 12.sp, lineHeight = 15.sp,
                        modifier = Modifier.weight(1f))
                    NeonButton(if (selectedCard) "Sélectionnée" else "Sélectionner", enabled = !state.isBotTurn,
                        modifier = Modifier.fillMaxWidth(), onClick = { selectedId = card.id; tooltip = null })
                }
            }
        }
        Text(selected?.let { if (it.energyAfterPurchase >= 0) "${it.name} · prix ${it.cost} ⚡ · après paiement : ${it.energyAfterPurchase} ⚡ (avant effet)"
            else "${it.name} · il manque ${-it.energyAfterPurchase} Cellule(s) pour acheter cette carte" }
            ?: "Sélectionnez une carte, puis confirmez l’achat. Sélectionner ne dépense rien.",
            color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonButton("Renouveler (${state.sweepCost} ⚡)", enabled = state.canSweep, modifier = Modifier.weight(1f),
                disabledReason = "Il faut ${state.sweepCost} Cellules et des cartes de remplacement disponibles", onDisabledLongPress = { tooltip = it },
                onClick = { confirmation = MarketConfirmation(GameAction.SweepShop, state.revision) })
            NeonButton("Acheter la sélection", enabled = selected?.canBuy == true,
                modifier = Modifier.weight(1f),
                disabledReason = selected?.let { "Il vous faut ${it.cost} ⚡ pour acheter ${it.name}" } ?: "Sélectionnez une carte d’abord",
                onDisabledLongPress = { tooltip = it }, onClick = {
                    selected?.let { confirmation = MarketConfirmation(GameAction.BuyCard(it.id), state.revision) }
                })
            NeonButton("Fermer le marché", modifier = Modifier.weight(1f), onClick = onClose)
        }
    }
    confirmation?.let { pending ->
        val buy = pending.action as? GameAction.BuyCard
        val card = buy?.let { action -> state.cards.firstOrNull { it.id == action.cardId } }
        val available = if (buy == null) state.canSweep else card?.canBuy == true
        val valid = pending.revision == state.revision && available
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(if (buy == null) "Renouveler le marché ?" else "Confirmer l’achat de ${card?.name.orEmpty()}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (card != null) {
                    Text("Paiement : ${state.energy} − ${card.cost} = ${card.energyAfterPurchase} Cellules, avant application de l’effet.")
                    Text(card.text)
                    Text(if (card.type == CardType.ACTION) "L’effet s’applique immédiatement ; la carte est défaussée."
                        else "Le pouvoir reste dans vos cartes. Ses effets se déclenchent aux moments indiqués.")
                } else Text("Dépenser ${state.sweepCost} Cellules pour remplacer les trois cartes visibles. Resteraient ${state.energyAfterSweep} Cellules.")
                if (!valid) Text("Cette opération n’est plus disponible. Annulez et vérifiez votre stock et le marché.")
            } },
            confirmButton = { NeonButton("Confirmer le paiement", enabled = valid, onClick = {
                // Consume the confirmation before dispatching; repeated callbacks cannot pay twice.
                if (confirmation == pending) {
                    confirmation = null
                    selectedId = null
                    if (buy != null) onBuy(buy.cardId, pending.revision) else onSweep(pending.revision)
                }
            }) },
            dismissButton = { NeonButton("Annuler", onClick = { confirmation = null }, accentColor = NeonPalette.NeonCyan) })
    }
}
