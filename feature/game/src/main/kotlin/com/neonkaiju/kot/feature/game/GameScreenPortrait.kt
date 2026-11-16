package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GameScreenPortrait(
    state: GameUiState,
    onEvent: (UiEvent) -> Unit,
    tooltipMessage: String?,
    setTooltipMessage: (String?) -> Unit,
    onOpenSummary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TurnStatusPanel(state, onEvent)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.opponents.forEach { bot ->
                Box(modifier = Modifier.weight(1f)) {
                    PlayerCardCompact(player = bot, stacked = true)
                }
            }
        }
        PlayerCardSelf(player = state.me)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TowerPanel(state, onEvent, onOpenSummary, Modifier.weight(1f).fillMaxHeight())
            SurchargeGaugeBar(malice = state.me.malice, tiers = state.maliceTiers, modifier = Modifier.fillMaxHeight())
        }
        DiceTurnControls(state, onEvent, setTooltipMessage)
    }
}
