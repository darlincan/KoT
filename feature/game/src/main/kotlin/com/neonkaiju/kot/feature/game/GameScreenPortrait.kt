package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    botExplanation: String?,
    onEvent: (UiEvent) -> Unit,
    tooltipMessage: String?,
    setTooltipMessage: (String?) -> Unit,
    onOpenSummary: () -> Unit
) {
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
        TurnStatusPanel(state, onEvent)
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
                        isActive = state.activePlayer.id == bot.id,
                        stacked = true,
                        gains = state.lastResolutionGains[bot.id]
                    )
                }
            }
        }

        // Middle Top: My Character
        PlayerCardSelf(player = me, isOccupant = state.tokyoOccupantId == me.id, gains = state.lastResolutionGains[me.id])

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
                if (state.decisionPlayer.isBot) {
                    val result = state.lastResolutionGains[state.activePlayer.id]
                    val explanation = if (state.phase == com.neonkaiju.kot.model.TurnPhase.ACHETER && result != null)
                        "Résultat : +${result.vp} ★, +${result.energy} ⚡, +${result.healing} ♥, +${result.malice} Surcharge.\n${botExplanation.orEmpty()}"
                        else botExplanation.orEmpty()
                    BotTurnOverlay(state.decisionPlayer.monster.displayName, explanation, isBotFastForward,
                        { onEvent(UiEvent.ToggleFastBot) })
                } else {
                Text("Résumé · toucher pour le détail", color = NeonPalette.NeonCyan, fontSize = 11.sp,
                    modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onOpenSummary))
                Text(
                    text = state.resolutionSummary.takeLast(3).joinToString("\n"),
                    color = NeonPalette.NeonCyan,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 3
                )
                }
            }
        }
        
        SurchargeGaugeBar(malice = me.malice)

        DiceTurnControls(state, onEvent, setTooltipMessage)
    }
}
