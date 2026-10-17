package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.GameAction
import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.TurnPhase

@Composable
fun GameScreenPortrait(
    state: GameState,
    isBotFastForward: Boolean,
    onEvent: (UiEvent) -> Unit,
    tooltipMessage: String?,
    setTooltipMessage: (String?) -> Unit
) {
    val legal = remember(state) { com.neonkaiju.kot.engine.GameEngine.legalActions(state) }
    val me = state.players.first { !it.isBot }
    val opponents = state.players.filter { it.isBot }
    val occupant = state.players.firstOrNull { it.id == state.tokyoOccupantId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top: Opponents
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            opponents.forEach { bot ->
                Box(modifier = Modifier.weight(1f)) {
                    PlayerCardCompact(
                        player = bot,
                        isOccupant = state.tokyoOccupantId == bot.id,
                        isActive = state.activePlayer.id == bot.id
                    )
                }
            }
        }

        // Middle Top: My Character
        PlayerCardSelf(player = me, isOccupant = state.tokyoOccupantId == me.id)

        // Middle: Central Tower
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(NeonPalette.Surface, RoundedCornerShape(12.dp))
                .border(2.dp, NeonPalette.NeonCyan, RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LA TOUR CENTRALE", color = NeonPalette.NeonCyan, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                if (occupant != null) {
                    Text("👑 ${occupant.monster.displayName}", color = NeonPalette.NeonPink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("♥ ${occupant.hp}/10   ★ ${occupant.vp}/20", color = NeonPalette.TextPrimary, fontSize = 14.sp)
                } else {
                    Text("ZONE LIBRE (+1 ★ à l'entrée)", color = NeonPalette.TextPrimary.copy(alpha = 0.7f), fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.actionLog.lastOrNull() ?: "",
                    color = NeonPalette.NeonCyan,
                    fontSize = 12.sp
                )
            }
        }
        
        SurchargeGaugeBar(malice = me.malice)

        // Bottom: Dice and Controls
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "LANCERS RESTANTS : ${state.rollsLeft}/2",
                color = NeonPalette.NeonCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            // Dice (3 columns x 2 rows)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until 3) {
                            val die = state.dice.getOrNull(row * 3 + col)
                            if (die != null) {
                                val canToggle = GameAction.ToggleKeep(die.id) in legal
                                NeonDieView(
                                    die = die,
                                    enabled = canToggle,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.ToggleKeep(die.id))) }
                                )
                            }
                        }
                    }
                }
            }

            // Actions
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.phase == TurnPhase.RESOUDRE) {
                    state.unresolvedSymbols.forEach { sym ->
                        NeonButton(
                            text = "Résoudre $sym",
                            onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.ResolveSymbols(sym))) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        NeonButton(
                            text = "Relancer",
                            enabled = GameAction.Reroll in legal,
                            disabledReason = if (state.rollsLeft == 0) "Plus de lancers disponibles" else "Déverrouillez au moins un dé pour relancer",
                            onDisabledLongPress = { setTooltipMessage(it) },
                            onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.Reroll)) },
                            modifier = Modifier.weight(1f)
                        )
                        NeonButton(
                            text = "Résoudre",
                            enabled = GameAction.StopRolling in legal,
                            accentColor = NeonPalette.NeonCyan,
                            disabledReason = "Vous avez déjà arrêté vos lancers",
                            onDisabledLongPress = { setTooltipMessage(it) },
                            onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.StopRolling)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        NeonButton(
                            text = "Marché noir",
                            enabled = state.phase == TurnPhase.ACHETER,
                            accentColor = NeonPalette.NeonCyan,
                            disabledReason = "Le Marché noir n'est accessible qu'en phase d'achat",
                            onDisabledLongPress = { setTooltipMessage(it) },
                            onClick = { onEvent(UiEvent.OpenShop) },
                            modifier = Modifier.weight(1f)
                        )
                        NeonButton(
                            text = "Fin Tour",
                            enabled = GameAction.EndTurn in legal,
                            disabledReason = "Terminez d'abord la résolution de vos dés",
                            onDisabledLongPress = { setTooltipMessage(it) },
                            onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.EndTurn)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
