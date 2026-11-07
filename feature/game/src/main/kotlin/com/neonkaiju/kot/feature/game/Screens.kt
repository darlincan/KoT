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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.*
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** Durée du compte à rebours optionnel de Fuir ou Rester (wireframe 05). */
const val FLEE_COUNTDOWN_SECONDS = 6

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
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val frame = Modifier
        .fillMaxSize()
        .background(NeonPalette.Background)
        .systemBarsPadding()
        .padding(24.dp)

    if (isPortrait) {
        Column(
            modifier = frame,
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeIllustration(Modifier.fillMaxWidth().weight(1f))
            HomeTitle(state.version, Alignment.CenterHorizontally)
            HomeActions(state.canResume, onEvent, Modifier.fillMaxWidth(0.8f))
        }
    } else {
        Row(
            modifier = frame,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HomeIllustration(Modifier.weight(1.2f).fillMaxHeight())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeTitle(state.version, Alignment.Start)
                HomeActions(state.canResume, onEvent, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HomeTitle(version: String, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment) {
        Text("LA GUERRE DES", color = NeonPalette.TextPrimary, fontSize = 16.sp)
        Text("NÉON KAIJU", color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        Text("Version $version • Édition Sombre", color = NeonPalette.NeonPink, fontSize = 12.sp)
    }
}

@Composable
private fun HomeActions(canResume: Boolean, onEvent: (UiEvent) -> Unit, modifier: Modifier) {
    var tooltip by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeonButton("Jouer", onClick = { onEvent(UiEvent.NewGame) }, modifier = Modifier.fillMaxWidth())
        Text("Vous contre 3 monstres IA", color = NeonPalette.TextPrimary, fontSize = 11.sp)
        NeonButton(
            "Reprendre",
            onClick = { onEvent(UiEvent.Resume) },
            enabled = canResume,
            accentColor = NeonPalette.NeonCyan,
            disabledReason = "Aucune partie sauvegardée en cours",
            onDisabledLongPress = { tooltip = it },
            modifier = Modifier.fillMaxWidth()
        )
        tooltip?.let { Text(it, color = NeonPalette.NeonPink, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NeonButton("Règles", onClick = { onEvent(UiEvent.OpenRules) }, modifier = Modifier.weight(1f), accentColor = NeonPalette.NeonCyan)
            NeonButton("Monstres", onClick = { onEvent(UiEvent.NewGame) }, modifier = Modifier.weight(1f), accentColor = NeonPalette.NeonCyan)
            NeonButton("Réglages", onClick = { onEvent(UiEvent.OpenSettings) }, modifier = Modifier.weight(1f), accentColor = NeonPalette.NeonCyan)
        }
    }
}

@Composable
fun MonsterPickerScreen(
    state: MonsterPickerUiState,
    onEvent: (UiEvent) -> Unit
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonButton("‹", onClick = { onEvent(UiEvent.BackHome) }, accentColor = NeonPalette.NeonCyan,
                modifier = Modifier.semantics { contentDescription = "Retour à l'accueil" })
            Column {
                Text("CHOISIS TON KAIJU", color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Vous + 3 IA", color = NeonPalette.TextPrimary, fontSize = 12.sp)
            }
        }

        if (isPortrait) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.monsters.forEach { monster ->
                    MonsterCard(monster, monster == state.selected, onEvent, Modifier.fillMaxWidth().weight(1f))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.monsters.forEach { monster ->
                    MonsterCard(monster, monster == state.selected, onEvent, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Vos adversaires (IA)", color = NeonPalette.TextPrimary, fontSize = 12.sp)
                    listOf("IA 1", "IA 2", "IA 3").forEach { chip ->
                        Text(chip, color = NeonPalette.NeonCyan, fontSize = 11.sp, modifier = Modifier
                            .border(1.dp, NeonPalette.NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                Text("3 bots tirés au sort parmi les 5 autres", color = NeonPalette.TextPrimary, fontSize = 13.sp)
            }
            NeonButton("Entrer dans la ville", onClick = { onEvent(UiEvent.StartGame) })
        }
    }
}

@Composable
private fun MonsterCard(monster: MonsterId, isSelected: Boolean, onEvent: (UiEvent) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .semantics { contentDescription = "${monster.displayName}, ${if (isSelected) "sélectionné" else "disponible"}, ${monster.bio}" }
            .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) NeonPalette.NeonPink else NeonPalette.NeonCyan.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(role = Role.Button) { onEvent(UiEvent.PickMonster(monster)) }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        // Barre décorative : certaines couleurs d'accent n'atteignent pas 4,5:1 en couleur de texte.
        Box(Modifier.fillMaxWidth(0.5f).height(4.dp).background(Color(monster.accentHex), RoundedCornerShape(2.dp)))
        Text(monster.displayName, color = NeonPalette.TextPrimary, fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(
            monster.bio,
            color = NeonPalette.TextPrimary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(if (isSelected) "SÉLECTIONNÉ" else "DISPONIBLE", color = if (isSelected) NeonPalette.NeonPink else NeonPalette.NeonCyan, fontSize = 11.sp)
    }
}

@Composable
fun GameScreen(
    state: GameUiState,
    onEvent: (UiEvent) -> Unit
) {
    var tooltipMessage by remember { mutableStateOf<String?>(null) }
    var showSummary by remember { mutableStateOf(false) }

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
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
        // Fuir ou Rester et le palier couvrent le plateau : TalkBack ne doit lire que la décision à prendre.
        val boardHidden = state.flee != null || state.malicePick != null
        Box(Modifier.fillMaxSize().then(if (boardHidden) Modifier.clearAndSetSemantics {} else Modifier)) {
            if (isPortrait) {
                GameScreenPortrait(state, onEvent, tooltipMessage, { tooltipMessage = it }, { showSummary = true })
            } else {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                    Column(
                        modifier = Modifier.weight(0.28f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TurnStatusPanel(state, onEvent)
                            opponents.forEach { bot ->
                                PlayerCardCompact(player = bot)
                            }
                        }
                        PlayerCardSelf(player = me)
                    }

                    Row(
                        modifier = Modifier.weight(0.42f).fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                                Spacer(Modifier.height(6.dp))
                                TowerRing(occupant?.monster)
                                Spacer(Modifier.height(6.dp))
                                if (occupant != null) {
                                    Text("👑 ${occupant.name}", color = NeonPalette.NeonPink, fontFamily = OrbitronFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("♥ ${occupant.hp}/10   ★ ${occupant.vp}/20", color = NeonPalette.TextPrimary, fontSize = 14.sp)
                                    Text("+2 ★ au début de son tour", color = NeonPalette.TextPrimary, fontSize = 12.sp)
                                } else {
                                    Text("ZONE LIBRE (+1 ★ à l'entrée)", color = NeonPalette.TextPrimary.copy(alpha = 0.7f), fontSize = 14.sp)
                                }
                                Spacer(Modifier.height(4.dp))
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
                        SurchargeGaugeBar(malice = me.malice, tiers = state.maliceTiers, modifier = Modifier.fillMaxHeight())
                    }

                    Column(
                        modifier = Modifier.weight(0.30f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiceTurnControls(state, onEvent) { tooltipMessage = it }
                    }
                }
            }
        }

        tooltipMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .heightIn(min = 48.dp)
                    .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
                    .border(1.dp, NeonPalette.NeonPink, RoundedCornerShape(8.dp))
                    .clickable { tooltipMessage = null }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
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
                countdownEnabled = state.countdownEnabled,
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
fun PlayerCardCompact(player: PlayerUi, stacked: Boolean = false) {
    val border = if (player.isActive) NeonPalette.NeonPink else NeonPalette.NeonCyan.copy(alpha = 0.35f)
    val name = "${if (!player.isAlive) "☠ " else if (player.isInTokyo) "👑 " else ""}${player.name}"
    val stats = "IA · ♥${player.hp} ★${player.vp} ⚡${player.energy} S${player.malice}"
    val gainsText = player.gains?.let { "Gains : +${it.vp} ★  +${it.energy} ⚡  +${it.healing} ♥" }
    val frame = Modifier.fillMaxWidth()
        .semantics(mergeDescendants = true) { contentDescription = player.accessibilityLabel }
        .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
        .border(1.dp, border, RoundedCornerShape(8.dp)).padding(8.dp)
    if (stacked) {
        Column(modifier = frame) {
            Text(name, color = NeonPalette.TextPrimary, fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp)
            Text(stats, color = NeonPalette.NeonCyan, fontSize = 11.sp, lineHeight = 14.sp)
            gainsText?.let { Text(it, color = NeonPalette.NeonPink, fontSize = 10.sp, lineHeight = 12.sp) }
        }
    } else {
        Row(modifier = frame, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = NeonPalette.TextPrimary, fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp)
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
            .semantics(mergeDescendants = true) { contentDescription = "Vous : ${player.accessibilityLabel}" }
            .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
            .border(2.dp, NeonPalette.NeonPink, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            text = "${if (player.isInTokyo) "👑 " else ""}${player.name} (VOUS)",
            color = NeonPalette.NeonPink,
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
        Text(
            text = "♥ ${player.hp}/10   ★ ${player.vp}/20   ⚡ ${player.energy} · ${player.cardCount} ${if (player.cardCount > 1) "cartes" else "carte"}",
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

/** Jauge verticale graduée de 0 au dernier palier (wireframe 03) ; les paliers sont écrits, lisibles sans la couleur. */
@Composable
fun SurchargeGaugeBar(malice: Int, tiers: List<Int>, modifier: Modifier = Modifier) {
    val max = tiers.last()
    Column(
        modifier = modifier
            .width(44.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Surcharge $malice sur $max. Paliers ${tiers.joinToString(", ")} : choisir un pouvoir"
            }
            .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("$malice/$max", color = NeonPalette.NeonCyan, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
        for (level in max downTo 1) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (level in tiers) "$level" else "", color = NeonPalette.TextPrimary, fontSize = 10.sp, lineHeight = 11.sp, modifier = Modifier.width(14.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (level <= malice) NeonPalette.NeonPink else NeonPalette.NeonCyan.copy(alpha = if (level in tiers) 0.6f else 0.2f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
        Text("0", color = NeonPalette.TextPrimary, fontSize = 10.sp, lineHeight = 11.sp)
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

@Composable
fun FleeDialog(
    flee: FleeUi,
    countdownEnabled: Boolean = false,
    onFlee: () -> Unit,
    onStay: () -> Unit
) {
    val context = LocalContext.current
    val talkBackActive = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
    val countdownRuns = countdownEnabled && !talkBackActive
    var secondsLeft by remember(flee) { mutableIntStateOf(FLEE_COUNTDOWN_SECONDS) }
    val latestOnStay by rememberUpdatedState(onStay)
    if (countdownRuns) {
        LaunchedEffect(flee) {
            while (secondsLeft > 0) {
                delay(1_000)
                secondsLeft--
            }
            latestOnStay()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
            Text("ALERTE BRÈCHE DANS LA TOUR", color = NeonPalette.NeonPink, fontFamily = OrbitronFamily, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
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
            if (countdownRuns) {
                Text("Sans réponse dans $secondsLeft s, vous restez", color = NeonPalette.TextPrimary, fontSize = 12.sp)
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
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background.copy(alpha = 0.94f))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Palier ${pick.level} : choisir un pouvoir", color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                pick.tiers.forEach { tier ->
                    MaliceTierChip(tier, isCurrent = tier == pick.level, Modifier.weight(1f))
                }
            }
            if (isPortrait) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    pick.tiles.forEach { tile ->
                        MaliceTileOffer(tile, onPickTile, Modifier.fillMaxWidth())
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                ) {
                    pick.tiles.forEach { tile ->
                        MaliceTileOffer(tile, onPickTile, Modifier.weight(1f).fillMaxHeight())
                    }
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
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Pouvoir ${tile.name} : ${tile.text}" }) {
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
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val frame = Modifier
        .fillMaxSize()
        .background(NeonPalette.Background)
        .systemBarsPadding()
        .padding(16.dp)

    if (isPortrait) {
        Column(modifier = frame, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GameOverHeadline(state, Modifier.fillMaxWidth())
            GameOverRanking(state, Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            GameOverActions(onReplay, onBackHome)
        }
    } else {
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
}

@Composable
private fun GameOverHeadline(state: GameOverUiState, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(state.endReason.label(), color = NeonPalette.NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(
            text = state.winnerName?.let { "$it règne sur la mégapole" } ?: "Personne ne règne sur la mégapole",
            color = NeonPalette.NeonPink,
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
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
