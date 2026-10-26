package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.*
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration

sealed interface UiEvent {
    data object NewGame : UiEvent
    data object Resume : UiEvent
    data object OpenRules : UiEvent
    data object OpenSettings : UiEvent
    data class PickMonster(val monster: MonsterId) : UiEvent
    data object StartGame : UiEvent
    data class DispatchGameAction(val action: GameAction) : UiEvent
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
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    if (isPortrait) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NeonPalette.Background)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NÉON KAIJU", color = NeonPalette.NeonCyan, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                Text("LA TOUR CENTRALE VOUS ATTEND", color = NeonPalette.TextPrimary, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Text("Version $version • Édition Sombre", color = NeonPalette.NeonPink, fontSize = 12.sp)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
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
    } else {
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
}

@Composable
fun MonsterPickerScreen(
    state: MonsterPickerUiState,
    onEvent: (UiEvent) -> Unit
) {
    val selectedMonster = state.selected
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("CHOISISSEZ VOTRE KAIJU BIO-MÉCANIQUE", color = NeonPalette.NeonCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        
        if (isPortrait) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.monsters.forEach { monster ->
                    val isSelected = monster == selectedMonster
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
        } else {
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

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    if (isPortrait) {
        GameScreenPortrait(
            state = state,
            onEvent = onEvent,
            tooltipMessage = tooltipMessage,
            setTooltipMessage = { tooltipMessage = it }
        )
        return
    }

    val me = state.me
    val occupant = state.tokyoOccupant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            Column(
                modifier = Modifier.weight(0.28f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.opponents.forEach { bot ->
                        PlayerCardCompact(player = bot)
                    }
                }
                PlayerCardSelf(player = me)
            }

            Column(
                modifier = Modifier.weight(0.42f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
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
                Spacer(Modifier.height(8.dp))
                SurchargeGaugeBar(malice = me.malice)
            }

            Column(
                modifier = Modifier.weight(0.30f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LANCERS RESTANTS : ${state.rollsLeft}/2",
                    color = NeonPalette.NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0 until 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 3) {
                                val die = state.dice.getOrNull(row * 3 + col)
                                if (die != null) {
                                    NeonDieView(
                                        die = die,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.ToggleKeep(die.id))) }
                                    )
                                }
                            }
                        }
                    }
                }

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
                                onDisabledLongPress = { tooltipMessage = it },
                                onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.Reroll)) },
                                modifier = Modifier.weight(1f)
                            )
                            NeonButton(
                                text = "Résoudre",
                                enabled = state.stopRolling.enabled,
                                accentColor = NeonPalette.NeonCyan,
                                disabledReason = state.stopRolling.blockedReason?.label(),
                                onDisabledLongPress = { tooltipMessage = it },
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
                                onDisabledLongPress = { tooltipMessage = it },
                                onClick = { onEvent(UiEvent.OpenShop) },
                                modifier = Modifier.weight(1f)
                            )
                            NeonButton(
                                text = "Fin Tour",
                                enabled = state.endTurn.enabled,
                                disabledReason = state.endTurn.blockedReason?.label(),
                                onDisabledLongPress = { tooltipMessage = it },
                                onClick = { onEvent(UiEvent.DispatchGameAction(GameAction.EndTurn)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
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

        state.botTurn?.let { botTurn ->
            BotTurnOverlay(
                botName = botTurn.botName,
                lastAction = botTurn.lastAction,
                isFast = state.isBotFastForward,
                onToggleFast = { onEvent(UiEvent.ToggleFastBot) },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        state.flee?.let { flee ->
            FleeDialog(
                attackerName = flee.attackerName,
                damageTaken = flee.damageTaken,
                remainingHp = flee.remainingHp,
                onFlee = { onEvent(UiEvent.DispatchGameAction(GameAction.Flee)) },
                onStay = { onEvent(UiEvent.DispatchGameAction(GameAction.Stay)) }
            )
        }

        state.malicePick?.let { pick ->
            MalicePickerSheet(
                level = pick.level,
                availableTiles = pick.tiles,
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
            .semantics { contentDescription = "Dé ${die.id + 1} face $symbol ${if (die.isKept) "verrouillé" else "libre"}" }
            .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = die.canToggle) { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (die.isKept) "$symbol 🔒" else symbol,
            color = NeonPalette.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun PlayerCardCompact(player: PlayerUi) {
    val border = if (player.isActive) NeonPalette.NeonPink else NeonPalette.NeonCyan.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildString {
                if (!player.isAlive) append("💀 ")
                else if (player.isInTokyo) append("👑 ")
                append(player.name)
            },
            color = NeonPalette.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Text("♥${player.hp} ★${player.vp} ⚡${player.energy}", color = NeonPalette.NeonCyan, fontSize = 12.sp)
    }
}

@Composable
fun PlayerCardSelf(player: PlayerUi) {
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
            text = "SURCHARGE : $malice/10 — Paliers 3 / 6 / 10 : choisir un pouvoir",
            color = NeonPalette.NeonCyan,
            fontSize = 11.sp,
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
        Text("TOUR BOT : $botName • $lastAction", color = NeonPalette.TextPrimary, fontSize = 12.sp)
        NeonButton(
            text = if (isFast) "Vitesse x2" else "Accélérer",
            onClick = onToggleFast,
            accentColor = NeonPalette.NeonCyan
        )
    }
}

@Composable
fun ShopSheet(
    state: ShopUiState,
    onBuy: (String) -> Unit,
    onSweep: () -> Unit,
    onClose: () -> Unit
) {
    var tooltip by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background.copy(alpha = 0.97f))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MARCHÉ NOIR — CELLULES DISPONIBLES : ${state.energy} ⚡", color = NeonPalette.NeonCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            tooltip?.let { Text(it, color = NeonPalette.NeonPink, fontSize = 13.sp) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            state.cards.forEach { card ->
                val effectiveCost = card.cost
                val canBuy = card.canBuy
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
                        .border(1.dp, NeonPalette.NeonCyan, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${card.name} (${card.type})", color = NeonPalette.NeonPink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(card.text, color = NeonPalette.TextPrimary, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    NeonButton(
                        text = "Acheter ($effectiveCost ⚡)",
                        enabled = canBuy,
                        disabledReason = "Il vous faut $effectiveCost ⚡ pour acheter ${card.name}",
                        onDisabledLongPress = { tooltip = it },
                        onClick = { onBuy(card.id) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            NeonButton(
                text = "Renouveler (2 ⚡)",
                enabled = state.canSweep,
                accentColor = NeonPalette.NeonCyan,
                disabledReason = "Il vous faut 2 ⚡ pour renouveler les cartes",
                onDisabledLongPress = { tooltip = it },
                onClick = onSweep
            )
            NeonButton("Fermer le Marché", onClick = onClose)
        }
    }
}

@Composable
fun FleeDialog(
    attackerName: String,
    damageTaken: Int,
    remainingHp: Int,
    onFlee: () -> Unit,
    onStay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .background(NeonPalette.Surface, RoundedCornerShape(12.dp))
                .border(2.dp, NeonPalette.NeonPink, RoundedCornerShape(12.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ALERTE BRÈCHE DANS LA TOUR", color = NeonPalette.NeonPink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text("$attackerName vous a infligé $damageTaken dégât(s). Il vous reste $remainingHp ♥.", color = NeonPalette.TextPrimary, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                NeonButton("Fuir la Tour", onClick = onFlee, accentColor = NeonPalette.NeonCyan, modifier = Modifier.weight(1f))
                NeonButton("Rester", onClick = onStay, accentColor = NeonPalette.NeonPink, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MalicePickerSheet(
    level: Int,
    availableTiles: List<TileUi>,
    onPickTile: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background.copy(alpha = 0.94f))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PALIER DE SURCHARGE $level ATTEINT : CHOISIR UN POUVOIR", color = NeonPalette.NeonCyan, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                availableTiles.forEach { tile ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
                            .border(1.dp, NeonPalette.NeonPink, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tile.name, color = NeonPalette.NeonPink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(tile.text, color = NeonPalette.TextPrimary, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        NeonButton("Équiper", onClick = { onPickTile(tile.id) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
fun GameOverScreen(
    state: GameOverUiState,
    onReplay: () -> Unit,
    onBackHome: () -> Unit
) {
    val winnerName = state.winnerName
    val ranking = state.ranking

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    if (isPortrait) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NeonPalette.Background)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (winnerName != null) "VICTOIRE : $winnerName" else "DESTRUCTION MUTUELLE",
                    color = NeonPalette.NeonPink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
                Text("Graine de partie : ${state.seed}", color = NeonPalette.NeonCyan, fontSize = 12.sp)
                ranking.forEachIndexed { idx, p ->
                    Text(
                        text = "${idx + 1}. ${p.name} — ★ ${p.vp} | ♥ ${p.hp} | ⚡ ${p.energy}",
                        color = NeonPalette.TextPrimary,
                        fontSize = 14.sp
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NeonButton("Rejouer", onClick = onReplay, modifier = Modifier.fillMaxWidth())
                NeonButton("Accueil", onClick = onBackHome, accentColor = NeonPalette.NeonCyan, modifier = Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(NeonPalette.Background)
                .systemBarsPadding()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (winnerName != null) "VICTOIRE : $winnerName" else "DESTRUCTION MUTUELLE",
                    color = NeonPalette.NeonPink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
                Text("Graine de partie : ${state.seed}", color = NeonPalette.NeonCyan, fontSize = 12.sp)
                ranking.forEachIndexed { idx, p ->
                    Text(
                        text = "${idx + 1}. ${p.name} — ★ ${p.vp} | ♥ ${p.hp} | ⚡ ${p.energy}",
                        color = NeonPalette.TextPrimary,
                        fontSize = 14.sp
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton("Rejouer", onClick = onReplay)
                NeonButton("Accueil", onClick = onBackHome, accentColor = NeonPalette.NeonCyan)
            }
        }
    }
}

fun BlockedReason.label(): String = when (this) {
    BlockedReason.NO_ROLLS_LEFT -> "Plus de lancers disponibles"
    BlockedReason.ALL_DICE_KEPT -> "Déverrouillez au moins un dé pour relancer"
    BlockedReason.ROLLING_OVER -> "Vos lancers sont terminés pour ce tour"
    BlockedReason.NOT_BUY_PHASE -> "Le Marché noir n'est accessible qu'en phase d'achat"
    BlockedReason.RESOLVE_FIRST -> "Terminez d'abord la résolution de vos dés"
}
