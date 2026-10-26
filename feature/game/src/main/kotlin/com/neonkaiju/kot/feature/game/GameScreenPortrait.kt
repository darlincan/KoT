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

@Composable
fun GameScreenPortrait(
    state: GameUiState,
    onEvent: (UiEvent) -> Unit,
    tooltipMessage: String?,
    setTooltipMessage: (String?) -> Unit
) {
    val me = state.me
    val opponents = state.opponents
    val occupant = state.tokyoOccupant

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
                    PlayerCardCompact(player = bot)
                }
            }
        }

        // Middle Top: My Character
        PlayerCardSelf(player = me)

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
                    Text("👑 ${occupant.name}", color = NeonPalette.NeonPink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("♥ ${occupant.hp}/10   ★ ${occupant.vp}/20", color = NeonPalette.TextPrimary, fontSize = 14.sp)
                } else {
                    Text("ZONE LIBRE (+1 ★ à l'entrée)", color = NeonPalette.TextPrimary.copy(alpha = 0.7f), fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.lastLog,
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
                                NeonDieView(
                                    die = die,
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
                if (state.isResolving) {
                    state.symbolsToResolve.forEach { sym ->
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
                            enabled = state.reroll.enabled,
                            disabledReason = state.reroll.blockedReason?.label(),
                            onDisabledLongPress = { setTooltipMessage(it) },
                            onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.Reroll)) },
                            modifier = Modifier.weight(1f)
                        )
                        NeonButton(
                            text = "Résoudre",
                            enabled = state.stopRolling.enabled,
                            accentColor = NeonPalette.NeonCyan,
                            disabledReason = state.stopRolling.blockedReason?.label(),
                            onDisabledLongPress = { setTooltipMessage(it) },
                            onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.StopRolling)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        NeonButton(
                            text = "Marché noir",
                            enabled = state.openShop.enabled,
                            accentColor = NeonPalette.NeonCyan,
                            disabledReason = state.openShop.blockedReason?.label(),
                            onDisabledLongPress = { setTooltipMessage(it) },
                            onClick = { onEvent(UiEvent.OpenShop) },
                            modifier = Modifier.weight(1f)
                        )
                        NeonButton(
                            text = "Fin Tour",
                            enabled = state.endTurn.enabled,
                            disabledReason = state.endTurn.blockedReason?.label(),
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
