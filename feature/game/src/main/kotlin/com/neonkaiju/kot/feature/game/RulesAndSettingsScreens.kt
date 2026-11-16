package com.neonkaiju.kot.feature.game

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class RulesPage(@StringRes val title: Int, @ArrayRes val lines: Int)

/** Reprend le §7 des spécifications, avec le vocabulaire Néon Kaiju. */
internal val rulesPages = listOf(
    RulesPage(R.string.rules_tab_goal, R.array.rules_goal),
    RulesPage(R.string.rules_tab_flow, R.array.rules_flow),
    RulesPage(R.string.rules_tab_dice, R.array.rules_dice),
    RulesPage(R.string.rules_tab_tower, R.array.rules_tower),
    RulesPage(R.string.rules_tab_malice, R.array.rules_malice),
    RulesPage(R.string.rules_tab_shop, R.array.rules_shop),
)

@Composable
fun RulesScreen(onBack: () -> Unit) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.rules_title), color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            NeonButton(stringResource(R.string.action_back), onClick = onBack, accentColor = NeonPalette.NeonCyan)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rulesPages.forEachIndexed { index, page ->
                RulesTab(stringResource(page.title), isSelected = index == pageIndex, Modifier.weight(1f)) { pageIndex = index }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            stringArrayResource(rulesPages[pageIndex].lines).forEach { line ->
                Text(line, color = NeonPalette.TextPrimary, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun RulesTab(title: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = if (isSelected) NeonPalette.NeonPink else NeonPalette.NeonCyan.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { selected = isSelected }
            .border(1.dp, accent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) NeonPalette.NeonPink else NeonPalette.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onToggleFastBot: () -> Unit,
    onToggleFleeCountdown: () -> Unit,
    onToggleMusic: () -> Unit,
    onToggleSfx: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.settings_title), color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            NeonButton(stringResource(R.string.action_back), onClick = onBack, accentColor = NeonPalette.NeonCyan)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            SettingSwitchRow(
                title = stringResource(R.string.settings_fast_bots),
                description = stringResource(R.string.settings_fast_bots_hint),
                checked = state.fastBots,
                onToggle = onToggleFastBot,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_countdown),
                description = stringResource(R.string.settings_countdown_hint, FLEE_COUNTDOWN_SECONDS),
                checked = state.fleeCountdown,
                onToggle = onToggleFleeCountdown,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            SettingSwitchRow(
                title = stringResource(R.string.settings_music),
                description = stringResource(R.string.settings_music_hint),
                checked = state.musicEnabled,
                onToggle = onToggleMusic,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_sfx),
                description = stringResource(R.string.settings_sfx_hint),
                checked = state.sfxEnabled,
                onToggle = onToggleSfx,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

/** Ligne de réglage : toute la ligne est cliquable, et TalkBack l'annonce comme un seul interrupteur. */
@Composable
private fun SettingSwitchRow(title: String, description: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NeonPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = NeonPalette.TextPrimary.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonPalette.DarkLabel,
                checkedTrackColor = NeonPalette.NeonPink,
            )
        )
    }
}
