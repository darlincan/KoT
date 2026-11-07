package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.*

sealed interface UiEvent {
    data object NewGame : UiEvent
    data object Resume : UiEvent
    data object OpenRules : UiEvent
    data object OpenSettings : UiEvent
    data class PickMonster(val monster: MonsterId) : UiEvent
    data object StartGame : UiEvent
    data class DispatchGameAction(val action: GameAction) : UiEvent
    data object Pause : UiEvent
    data object OpenShop : UiEvent
    data object CloseShop : UiEvent
    data object ToggleFastBot : UiEvent
    data object Replay : UiEvent
    data object BackHome : UiEvent
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (UiEvent) -> Unit
) {
    val canResume = state.canResume
    val version = state.version

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("NÉON KAIJU", color = NeonPalette.NeonCyan, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("LA TOUR CENTRALE VOUS ATTEND", color = NeonPalette.TextPrimary, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Text("Version $version • Édition Sombre", color = NeonPalette.NeonPink, fontSize = 12.sp)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            NeonButton("Nouvelle Partie", onClick = { onEvent(UiEvent.NewGame) }, modifier = Modifier.fillMaxWidth(0.8f))
            NeonButton(
                "Reprendre",
                onClick = { onEvent(UiEvent.Resume) },
                enabled = canResume,
                accentColor = NeonPalette.NeonCyan,
                disabledReason = "Aucune partie sauvegardée en cours",
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(0.8f)) {
                NeonButton("Règles", onClick = { onEvent(UiEvent.OpenRules) }, modifier = Modifier.weight(1f), accentColor = NeonPalette.NeonCyan)
                NeonButton("Réglages", onClick = { onEvent(UiEvent.OpenSettings) }, modifier = Modifier.weight(1f), accentColor = NeonPalette.NeonCyan)
            }
        }
    }
}

@Composable
fun MonsterPickerScreen(
    state: MonsterPickerUiState,
    onEvent: (UiEvent) -> Unit
) {
    val selectedMonster = state.selected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("CHOISISSEZ VOTRE KAIJU BIO-MÉCANIQUE", color = NeonPalette.NeonCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.monsters.forEach { monster ->
                val isSelected = monster == selectedMonster
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp)
                        .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) NeonPalette.NeonPink else NeonPalette.NeonCyan.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onEvent(UiEvent.PickMonster(monster)) }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(monster.displayName, color = NeonPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(if (isSelected) "SÉLECTIONNÉ" else "DISPONIBLE", color = if (isSelected) NeonPalette.NeonPink else NeonPalette.NeonCyan, fontSize = 11.sp)
                    }
                }
            }
        }
        
        
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
            NeonButton("Lancer l'assaut (3 Bots)", onClick = { onEvent(UiEvent.StartGame) })
        }
    }
}

@Composable
fun GameScreen(
    state: GameUiState,
    onEvent: (UiEvent) -> Unit
) {
    var tooltipMessage by remember { mutableStateOf<String?>(null) }
    var showSummary by remember { mutableStateOf(false) }

    val me = state.me
    val opponents = state.opponents
    val occupant = state.tokyoOccupant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            Column(
                modifier = Modifier.weight(0.28f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    opponents.forEach { bot ->
                        PlayerCardCompact(player = bot)
                    }
                }
                PlayerCardSelf(player = me)
            }

            Column(
                modifier = Modifier.weight(0.42f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                TurnStatusPanel(state, onEvent)
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
                        val botTurn = state.botTurn
                        if (botTurn != null) {
                            BotTurnOverlay(botTurn.botName, botTurn.lastAction, state.isBotFastForward,
                                { onEvent(UiEvent.ToggleFastBot) })
                        } else {
                        Text("Résumé · toucher pour le détail", color = NeonPalette.NeonCyan, fontSize = 11.sp,
                            modifier = Modifier.heightIn(min = 48.dp).clickable { showSummary = true })
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
                Spacer(Modifier.height(8.dp))
                SurchargeGaugeBar(malice = me.malice)
            }

            Column(
                modifier = Modifier.weight(0.30f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                DiceTurnControls(state, onEvent) { tooltipMessage = it }
            }
        }
        

        tooltipMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
                    .border(1.dp, NeonPalette.NeonPink, RoundedCornerShape(8.dp))
                    .clickable { tooltipMessage = null }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(msg, color = NeonPalette.TextPrimary, fontSize = 13.sp)
            }
        }

        if (showSummary) {
            AlertDialog(onDismissRequest = { showSummary = false },
                title = { Text("Résumé du tour — ${state.activePlayerName}") },
                text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    state.summary.forEach { Text(it, color = NeonPalette.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp) }
                } },
                confirmButton = { NeonButton("Fermer", onClick = { showSummary = false }) })
        }

        state.flee?.let { flee ->
            FleeDialog(
                flee = flee,
                onFlee = { onEvent(UiEvent.DispatchGameAction(GameAction.Flee)) },
                onStay = { onEvent(UiEvent.DispatchGameAction(GameAction.Stay)) }
            )
        }

        state.malicePick?.let { pick ->
            MalicePickerSheet(
                pick = pick,
                onPickTile = { onEvent(UiEvent.DispatchGameAction(GameAction.PickMaliceTile(it))) }
            )
        }
    }
}

@Composable
fun NeonDieView(die: DieUi, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val symbol = when (die.face) {
        DieFace.ONE -> "1"
        DieFace.TWO -> "2"
        DieFace.THREE -> "3"
        DieFace.ENERGY -> "⚡"
        DieFace.CLAW -> "🐾"
        DieFace.HEART -> "♥"
    }
    val borderColor = if (die.isKept) NeonPalette.NeonPink else NeonPalette.NeonCyan
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = "Dé ${die.id + 1}, face $symbol, ${if (die.isKept) "conservé, toucher pour relancer" else "à relancer, toucher pour conserver"}" }
            .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = die.canToggle) { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$symbol\n${if (die.isKept) "🔒 Gardé" else "À relancer"}",
            color = NeonPalette.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun PlayerCardCompact(player: PlayerUi, stacked: Boolean = false) {
    val border = if (player.isActive) NeonPalette.NeonPink else NeonPalette.NeonCyan.copy(alpha = 0.35f)
    val name = "${if (!player.isAlive) "☠ " else if (player.isInTokyo) "👑 " else ""}${player.name}"
    val stats = "♥${player.hp} ★${player.vp} ⚡${player.energy} S${player.malice}"
    val gainsText = player.gains?.let { "Gains : +${it.vp} ★  +${it.energy} ⚡  +${it.healing} ♥" }
    val frame = Modifier.fillMaxWidth()
        .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
        .border(1.dp, border, RoundedCornerShape(8.dp)).padding(8.dp)
    if (stacked) {
        Column(modifier = frame) {
            Text(name, color = NeonPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp)
            Text(stats, color = NeonPalette.NeonCyan, fontSize = 11.sp, lineHeight = 14.sp)
            gainsText?.let { Text(it, color = NeonPalette.NeonPink, fontSize = 10.sp, lineHeight = 12.sp) }
        }
    } else {
        Row(modifier = frame, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = NeonPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp)
            Column(horizontalAlignment = Alignment.End) {
                Text(stats, color = NeonPalette.NeonCyan, fontSize = 12.sp, lineHeight = 16.sp)
                gainsText?.let { Text(it, color = NeonPalette.NeonPink, fontSize = 10.sp, lineHeight = 12.sp) }
            }
        }
    }
}

@Composable
fun PlayerCardSelf(player: PlayerUi) {
    val gains = player.gains
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
            .border(2.dp, NeonPalette.NeonPink, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            text = "${if (player.isInTokyo) "👑 " else ""}${player.name} (VOUS)",
            color = NeonPalette.NeonPink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
        Text(
            text = "♥ ${player.hp}/10   ★ ${player.vp}/20   ⚡ ${player.energy} Cellules",
            color = NeonPalette.TextPrimary,
            fontSize = 13.sp
        )
        if (gains != null) {
            val title = when {
                gains.isStartOfTurn -> "BONUS DE DÉBUT DE TOUR AJOUTÉS"
                gains.isComplete -> "GAINS DE LA DERNIÈRE RÉSOLUTION AJOUTÉS"
                else -> "GAINS DÉJÀ AJOUTÉS — RÉSOLUTION EN COURS"
            }
            Text(title, color = NeonPalette.NeonCyan, fontSize = 10.sp, lineHeight = 12.sp)
            val otherVp = (gains.vp - gains.numbersVp - gains.tokyoVp).coerceAtLeast(0)
            val vpDetail = "dés +${gains.numbersVp}, Tour +${gains.tokyoVp}" + if (otherVp > 0) ", effets +$otherVp" else ""
            Text("+${gains.vp} ★ ($vpDetail)\n+${gains.energy} Cellules · +${gains.healing} ♥ · +${gains.malice} Surcharge",
                color = NeonPalette.NeonPink, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        } else {
            Text("Les gains seront ajoutés à la résolution.", color = NeonPalette.NeonCyan, fontSize = 11.sp, lineHeight = 13.sp)
        }

    }
}

@Composable
fun SurchargeGaugeBar(malice: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "SURCHARGE (MÉCHANCETÉ) : $malice/10 — Paliers 3 / 6 / 10 : choisir un pouvoir",
            color = NeonPalette.NeonCyan,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BotTurnOverlay(
    botName: String,
    lastAction: String,
    isFast: Boolean,
    onToggleFast: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(NeonPalette.Surface.copy(alpha = 0.95f), RoundedCornerShape(8.dp))
            .border(1.dp, NeonPalette.NeonCyan, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("TOUR BOT : $botName • $lastAction", color = NeonPalette.TextPrimary, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 4, modifier = Modifier.weight(1f))
        NeonButton(
            text = if (isFast) "Vitesse x2" else "Accélérer",
            onClick = onToggleFast,
            accentColor = NeonPalette.NeonCyan
        )
    }
}

/**
 * Une décision en superposition doit être modale : sans récepteur de toucher, un appui hors des boutons
 * traverse le voile et atteint le plateau dessous (Résumé, Pause…). Ici le voile capte le toucher et l'ignore.
 */
private fun Modifier.blockTouchesBehind(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent() }
}

@Composable
fun FleeDialog(
    flee: FleeUi,
    onFlee: () -> Unit,
    onStay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .blockTouchesBehind()
            .background(NeonPalette.Background.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(NeonPalette.Surface, RoundedCornerShape(12.dp))
                .border(2.dp, NeonPalette.NeonPink, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("ALERTE BRÈCHE DANS LA TOUR", color = NeonPalette.NeonPink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text("${flee.attackerName} vous a infligé ${flee.damageTaken} dégât(s).", color = NeonPalette.TextPrimary, fontSize = 14.sp)
            Text("Il vous reste ${flee.remainingHp} ♥", color = NeonPalette.NeonPink, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            ) {
                FleeOption("FUIR", flee.fleeConsequence, NeonPalette.NeonCyan, Modifier.weight(1f))
                FleeOption("RESTER", flee.stayConsequence, NeonPalette.NeonPink, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                NeonButton("Fuir la Tour", onClick = onFlee, accentColor = NeonPalette.NeonCyan, modifier = Modifier.weight(1f))
                NeonButton("Rester", onClick = onStay, accentColor = NeonPalette.NeonPink, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FleeOption(title: String, consequence: String, accent: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, accent, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(consequence, color = NeonPalette.TextPrimary, fontSize = 12.sp, lineHeight = 15.sp)
    }
}

@Composable
fun MalicePickerSheet(
    pick: MalicePickUi,
    onPickTile: (String) -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .blockTouchesBehind()
            .background(NeonPalette.Background.copy(alpha = 0.94f))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Palier ${pick.level} : choisir un pouvoir", color = NeonPalette.NeonCyan, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(3, 6, 10).forEach { tier ->
                    MaliceTierChip(tier, isCurrent = tier == pick.level, Modifier.weight(1f))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            ) {
                pick.tiles.forEach { tile ->
                    MaliceTileOffer(tile, onPickTile, Modifier.weight(1f).fillMaxHeight())
                }
            }
            
            Text(
                text = if (pick.ownedTiles.isEmpty()) "Pouvoirs détenus : aucun" else "Pouvoirs détenus",
                color = NeonPalette.NeonCyan,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth()
            )
            if (pick.ownedTiles.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    pick.ownedTiles.forEach { tile ->
                        Text(
                            text = "${tile.name} · ${tile.text}",
                            color = NeonPalette.TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, NeonPalette.NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaliceTierChip(tier: Int, isCurrent: Boolean, modifier: Modifier) {
    val accent = if (isCurrent) NeonPalette.NeonPink else NeonPalette.NeonCyan.copy(alpha = 0.4f)
    Column(
        modifier = modifier
            .border(1.dp, accent, RoundedCornerShape(6.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Palier $tier", color = if (isCurrent) NeonPalette.NeonPink else NeonPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp)
        Text("Choisir un pouvoir", color = NeonPalette.TextPrimary, fontSize = 10.sp, lineHeight = 12.sp)
    }
}

@Composable
private fun MaliceTileOffer(tile: TileUi, onPickTile: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, NeonPalette.NeonPink, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(tile.name, color = NeonPalette.NeonPink, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.sp)
            Text(tile.text, color = NeonPalette.TextPrimary, fontSize = 12.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.height(4.dp))
        NeonButton("Équiper", onClick = { onPickTile(tile.id) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun GameOverScreen(
    state: GameOverUiState,
    onReplay: () -> Unit,
    onBackHome: () -> Unit
) {
    val frame = Modifier
        .fillMaxSize()
        .background(NeonPalette.Background)
        .systemBarsPadding()
        .padding(16.dp)

    Row(
        modifier = frame,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameOverHeadline(state, Modifier.weight(0.4f))
        Column(modifier = Modifier.weight(0.6f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GameOverRanking(state, Modifier.fillMaxWidth())
            GameOverActions(onReplay, onBackHome)
        }
    }
}

@Composable
private fun GameOverHeadline(state: GameOverUiState, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(state.endReason.label(), color = NeonPalette.NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(
            text = state.winnerName?.let { "$it règne sur la mégapole" } ?: "Personne ne règne sur la mégapole",
            color = NeonPalette.NeonPink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            lineHeight = 28.sp
        )
        Text("Graine de partie : ${state.seed}", color = NeonPalette.TextPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun GameOverRanking(state: GameOverUiState, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.ranking.forEachIndexed { index, player ->
            val isWinner = player.name == state.winnerName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
                    .border(1.dp, if (isWinner) NeonPalette.NeonPink else NeonPalette.NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${if (player.isAlive) index + 1 else "–"}  ${player.name}${if (player.id == state.humanId) " (vous)" else ""}",
                    color = if (isWinner) NeonPalette.NeonPink else NeonPalette.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "★ ${player.vp}  ♥ ${player.hp}  ⚡ ${player.energy}${if (player.isAlive) "" else "  · éliminé"}",
                    color = NeonPalette.NeonCyan,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun GameOverActions(onReplay: () -> Unit, onBackHome: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        NeonButton("Accueil", onClick = onBackHome, accentColor = NeonPalette.NeonCyan, modifier = Modifier.weight(1f))
        NeonButton("Rejouer", onClick = onReplay, modifier = Modifier.weight(1f))
    }
}

fun GameEndReason.label(): String = when (this) {
    GameEndReason.TWENTY_STARS -> "VICTOIRE PAR 20 ★"
    GameEndReason.LAST_SURVIVOR -> "DERNIER SURVIVANT"
    GameEndReason.ALL_ELIMINATED -> "TOUS ÉLIMINÉS"
}

fun BlockedReason.label(): String = when (this) {
    BlockedReason.NO_ROLLS_LEFT -> "Plus de lancers disponibles"
    BlockedReason.ALL_DICE_KEPT -> "Déverrouillez au moins un dé pour relancer"
    BlockedReason.ROLLING_OVER -> "Vos lancers sont terminés pour ce tour"
    BlockedReason.NOT_BUY_PHASE -> "Le Marché noir n'est accessible qu'en phase d'achat"
    BlockedReason.RESOLVE_FIRST -> "Terminez d'abord la résolution de vos dés"
    BlockedReason.BOT_PLAYING -> "Le bot joue"
}
