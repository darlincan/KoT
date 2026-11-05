package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.*

private data class MarketConfirmation(val action: GameAction, val revision: Int)

@Composable
fun ShopSheet(state: GameState, onBuy: (String, Int) -> Unit, onSweep: (Int) -> Unit, onClose: () -> Unit) {
    val player = state.activePlayer
    val legal = if (player.isBot) emptyList() else GameEngine.legalActions(state)
    var selectedId by remember(player.id) { mutableStateOf<String?>(null) }
    var confirmation by remember(player.id) { mutableStateOf<MarketConfirmation?>(null) }
    var tooltip by remember { mutableStateOf<String?>(null) }
    val selected = state.shop.firstOrNull { it.id == selectedId }?.let { GameEngine.quoteCard(state, it.id) }
    val receipt = state.lastMarketReceipts[player.id]

    Column(Modifier.fillMaxSize().background(NeonPalette.Background).systemBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("MARCHÉ NOIR — ${player.energy} CELLULES DISPONIBLES ⚡", color = NeonPalette.NeonCyan,
            fontSize = 16.sp, lineHeight = 19.sp)
        receipt?.let {
            val effect = when {
                it.effectEnergyChange > 0 -> " + ${it.effectEnergyChange} (effet)"
                it.effectEnergyChange < 0 -> " − ${-it.effectEnergyChange} (effet)"
                else -> ""
            }
            Text("${it.itemName} : ${it.energyBefore} − ${it.paid}$effect = ${it.energyAfter} Cellules",
                color = NeonPalette.NeonPink, fontSize = 13.sp, lineHeight = 16.sp)
        }
        tooltip?.let { Text(it, color = NeonPalette.NeonPink, fontSize = 12.sp, lineHeight = 14.sp) }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.shop.forEach { card ->
                val quote = GameEngine.quoteCard(state, card.id)
                val selectedCard = card.id == selectedId
                Column(Modifier.weight(1f).fillMaxHeight()
                    .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
                    .border(if (selectedCard) 3.dp else 1.dp, if (selectedCard) NeonPalette.NeonPink else NeonPalette.NeonCyan, RoundedCornerShape(10.dp))
                    .padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.nom, color = NeonPalette.NeonPink, fontSize = 14.sp, lineHeight = 17.sp)
                    Text(if (card.type == CardType.ACTION) "ACTION · effet immédiat, puis défaussée" else "POUVOIR · carte conservée",
                        color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 13.sp)
                    Text("Prix : ${quote.cost} ⚡${if (quote.cost != card.cout) " (base ${card.cout})" else ""}",
                        color = NeonPalette.NeonCyan, fontSize = 13.sp, lineHeight = 16.sp)
                    Text(card.texte, color = NeonPalette.TextPrimary, fontSize = 12.sp, lineHeight = 15.sp,
                        modifier = Modifier.weight(1f))
                    NeonButton(if (selectedCard) "Sélectionnée" else "Sélectionner", enabled = !player.isBot,
                        modifier = Modifier.fillMaxWidth(), onClick = { selectedId = card.id; tooltip = null })
                }
            }
        }
        Text(selected?.let { if (it.canAfford) "${it.card.nom} · prix ${it.cost} ⚡ · après paiement : ${it.afterPayment} ⚡ (avant effet)"
            else "${it.card.nom} · il manque ${it.cost - it.energyBefore} Cellule(s) pour acheter cette carte" }
            ?: "Sélectionnez une carte, puis confirmez l’achat. Sélectionner ne dépense rien.",
            color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonButton("Renouveler (2 ⚡)", enabled = GameAction.SweepShop in legal, modifier = Modifier.weight(1f),
                disabledReason = "Il faut 2 Cellules et des cartes de remplacement disponibles", onDisabledLongPress = { tooltip = it },
                onClick = { confirmation = MarketConfirmation(GameAction.SweepShop, state.actionHistory.size) })
            NeonButton("Acheter la sélection", enabled = selected?.let { GameAction.BuyCard(it.card.id) in legal } == true,
                modifier = Modifier.weight(1f),
                disabledReason = selected?.let { "Il faut ${it.cost} Cellules ; vous en avez ${player.energy}" } ?: "Sélectionnez une carte d’abord",
                onDisabledLongPress = { tooltip = it }, onClick = {
                    selected?.let { confirmation = MarketConfirmation(GameAction.BuyCard(it.card.id), it.revision) }
                })
            NeonButton("Fermer le marché", modifier = Modifier.weight(1f), onClick = onClose)
        }
    }
    confirmation?.let { pending ->
        val buy = pending.action as? GameAction.BuyCard
        val card = buy?.let { action -> state.shop.firstOrNull { it.id == action.cardId } }
        val quote = card?.let { GameEngine.quoteCard(state, it.id) }
        val valid = pending.revision == state.actionHistory.size && pending.action in legal
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(if (buy == null) "Renouveler le marché ?" else "Confirmer l’achat de ${card?.nom.orEmpty()}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (quote != null) {
                    Text("Paiement : ${quote.energyBefore} − ${quote.cost} = ${quote.afterPayment} Cellules, avant application de l’effet.")
                    Text(quote.card.texte)
                    Text(if (quote.card.type == CardType.ACTION) "L’effet s’applique immédiatement ; la carte est défaussée."
                        else "Le pouvoir reste dans vos cartes. Ses effets se déclenchent aux moments indiqués.")
                } else Text("Dépenser 2 Cellules pour remplacer les trois cartes visibles. Resteraient ${player.energy - 2} Cellules.")
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
