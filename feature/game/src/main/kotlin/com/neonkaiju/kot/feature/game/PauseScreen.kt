package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PauseScreen(onResume: () -> Unit, onSaveAndQuit: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().background(NeonPalette.Background).systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.pause_title), color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontSize = 22.sp, lineHeight = 26.sp)
        Text(stringResource(R.string.pause_hint), color = NeonPalette.TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
        NeonButton(stringResource(R.string.pause_resume), onClick = onResume)
        NeonButton(stringResource(R.string.pause_save_quit), onClick = onSaveAndQuit)
        NeonButton(stringResource(R.string.pause_home), onClick = onHome, accentColor = NeonPalette.NeonCyan)
    }
}

@Composable
fun TurnStatusPanel(state: GameUiState, onEvent: (UiEvent) -> Unit) {
    val pauseLabel = stringResource(R.string.action_pause)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(state.botTurn?.let { stringResource(R.string.turn_bot, it.botName) } ?: stringResource(R.string.turn_mine),
                color = NeonPalette.NeonPink, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp)
            Text(state.phase.label(), color = NeonPalette.TextPrimary, fontSize = 11.sp, lineHeight = 13.sp)
        }
        NeonButton(stringResource(R.string.symbol_menu), onClick = { onEvent(UiEvent.Pause) }, modifier = Modifier.semantics { contentDescription = pauseLabel })
    }
}
