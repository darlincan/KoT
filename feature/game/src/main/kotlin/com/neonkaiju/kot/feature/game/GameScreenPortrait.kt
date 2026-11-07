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

@Composable
fun GameScreenPortrait(
    state: GameUiState,
    onEvent: (UiEvent) -> Unit,
    tooltipMessage: String?,
    setTooltipMessage: (String?) -> Unit,
    onOpenSummary: () -> Unit
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
        TurnStatusPanel(state, onEvent)
        // Top: Opponents
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            opponents.forEach { bot ->
                Box(modifier = Modifier.weight(1f)) {
                    PlayerCardCompact(player = bot, stacked = true)
                }
            }
        }

        // Middle Top: My Character
        PlayerCardSelf(player = me)

        // Middle: Central Tower
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(NeonPalette.Surface, RoundedCornerShape(12.dp))
                .border(2.dp, NeonPalette.NeonCyan, RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LA TOUR CENTRALE", color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                if (occupant != null) {
                    Text("👑 ${occupant.name}", color = NeonPalette.NeonPink, fontFamily = OrbitronFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("♥ ${occupant.hp}/10   ★ ${occupant.vp}/20", color = NeonPalette.TextPrimary, fontSize = 14.sp)
                    Text("+2 ★ au début de son tour", color = NeonPalette.TextPrimary, fontSize = 12.sp)
                } else {
                    Text("ZONE LIBRE (+1 ★ à l'entrée)", color = NeonPalette.TextPrimary.copy(alpha = 0.7f), fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                val botTurn = state.botTurn
                if (botTurn != null) {
                    BotTurnOverlay(botTurn.botName, botTurn.lastAction, state.isBotFastForward,
                        { onEvent(UiEvent.ToggleFastBot) })
                } else {
                Text("Résumé · toucher pour le détail", color = NeonPalette.NeonCyan, fontSize = 11.sp,
                    modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onOpenSummary))
                Text(
                    text = state.summary.takeLast(3).joinToString("\n"),
                    color = NeonPalette.NeonCyan,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 3
                )
                }
            }
        }
        SurchargeGaugeBar(malice = me.malice, tiers = state.maliceTiers, modifier = Modifier.fillMaxHeight())
        }

        DiceTurnControls(state, onEvent, setTooltipMessage)
    }
}
