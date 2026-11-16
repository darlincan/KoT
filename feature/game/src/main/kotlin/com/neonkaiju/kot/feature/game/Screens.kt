package com.neonkaiju.kot.feature.game

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonkaiju.kot.model.*
import kotlinx.coroutines.delay

/** Durée du compte à rebours optionnel de Fuir ou Rester (wireframe 05). */
const val FLEE_COUNTDOWN_SECONDS = 6

/** Hauteur à partir de laquelle la Tour affiche aussi son anneau. */
private val TOWER_ROOMY_HEIGHT = 300.dp

/** Lignes du résumé affichées dans la fenêtre : aucun écran ne défile (règle transverse n° 3). */
private const val SUMMARY_MAX_LINES = 8

/** Nombre de bots d'une partie, affichés comme « IA 1 » à « IA 3 » sur le choix du monstre. */
private const val BOT_COUNT = 3

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
    val frame = Modifier
        .fillMaxSize()
        .background(NeonPalette.Background)
        .systemBarsPadding()
        .padding(24.dp)

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

@Composable
private fun HomeTitle(version: String, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment) {
        Text(stringResource(R.string.home_kicker), color = NeonPalette.TextPrimary, fontSize = 16.sp)
        Text(stringResource(R.string.home_title), color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.home_version, version), color = NeonPalette.NeonPink, fontSize = 12.sp)
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
        NeonButton(stringResource(R.string.home_play), onClick = { onEvent(UiEvent.NewGame) }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.home_opponents), color = NeonPalette.TextPrimary, fontSize = 11.sp)
        NeonButton(
            stringResource(R.string.home_resume),
            onClick = { onEvent(UiEvent.Resume) },
            enabled = canResume,
            accentColor = NeonPalette.NeonCyan,
            disabledReason = stringResource(R.string.home_resume_blocked),
            onDisabledLongPress = { tooltip = it },
            modifier = Modifier.fillMaxWidth()
        )
        tooltip?.let { Text(it, color = NeonPalette.NeonPink, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NeonButton(stringResource(R.string.home_rules), onClick = { onEvent(UiEvent.OpenRules) }, modifier = Modifier.weight(1f), accentColor = NeonPalette.NeonCyan)
            NeonButton(stringResource(R.string.home_monsters), onClick = { onEvent(UiEvent.NewGame) }, modifier = Modifier.weight(1f), accentColor = NeonPalette.NeonCyan)
            NeonButton(stringResource(R.string.home_settings), onClick = { onEvent(UiEvent.OpenSettings) }, modifier = Modifier.weight(1f), accentColor = NeonPalette.NeonCyan)
        }
    }
}

@Composable
fun MonsterPickerScreen(
    state: MonsterPickerUiState,
    onEvent: (UiEvent) -> Unit
) {
    val backLabel = stringResource(R.string.picker_back_a11y)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonButton(stringResource(R.string.symbol_back), onClick = { onEvent(UiEvent.BackHome) }, accentColor = NeonPalette.NeonCyan,
                modifier = Modifier.semantics { contentDescription = backLabel })
            Column {
                Text(stringResource(R.string.picker_title), color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.picker_subtitle), color = NeonPalette.TextPrimary, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.monsters.forEach { monster ->
                MonsterCard(monster, monster == state.selected, onEvent, Modifier.weight(1f).fillMaxHeight())
            }
        }
        
    

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.picker_opponents), color = NeonPalette.TextPrimary, fontSize = 12.sp)
                    for (bot in 1..BOT_COUNT) {
                        Text(stringResource(R.string.picker_opponent_chip, bot), color = NeonPalette.NeonCyan, fontSize = 11.sp, modifier = Modifier
                            .border(1.dp, NeonPalette.NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                Text(stringResource(R.string.picker_bots_note), color = NeonPalette.TextPrimary, fontSize = 13.sp)
            }
            NeonButton(stringResource(R.string.picker_start), onClick = { onEvent(UiEvent.StartGame) })
        }
    }
}

@Composable
private fun MonsterCard(monster: MonsterId, isSelected: Boolean, onEvent: (UiEvent) -> Unit, modifier: Modifier) {
    val a11y = stringResource(
        R.string.picker_monster_a11y,
        monster.displayName,
        stringResource(if (isSelected) R.string.picker_selected_a11y else R.string.picker_available_a11y),
        monster.bio,
    )
    Column(
        modifier = modifier
            .semantics { contentDescription = a11y }
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
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(if (isSelected) R.string.picker_selected else R.string.picker_available),
            color = if (isSelected) NeonPalette.NeonPink else NeonPalette.NeonCyan,
            fontSize = 11.sp
        )
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
    val glitch = damageFlash(me.hp)
    DamageHaptics(me.hp)

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
                    TowerPanel(state, onEvent, { showSummary = true }, Modifier.weight(1f).fillMaxHeight())
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
                title = { Text(stringResource(R.string.summary_title, state.activePlayerName)) },
                text = { Column {
                    state.summary.takeLast(SUMMARY_MAX_LINES).forEach { Text(it, color = NeonPalette.TextPrimary, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1) }
                } },
                confirmButton = { NeonButton(stringResource(R.string.action_close), onClick = { showSummary = false }) })
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

        GlitchOverlay(glitch)
    }
}

/**
 * La Tour centrale. L'occupant, ses ♥ et ses ★ restent toujours visibles en haut ; le bas (bandeau du bot
 * ou résumé) prend la place restante et s'abrège si besoin. L'anneau est retiré quand la hauteur manque.
 */
@Composable
internal fun TowerPanel(state: GameUiState, onEvent: (UiEvent) -> Unit, onOpenSummary: () -> Unit, modifier: Modifier) {
    val occupant = state.tokyoOccupant
    BoxWithConstraints(
        modifier = modifier
            .background(NeonPalette.Surface, RoundedCornerShape(12.dp))
            .border(2.dp, NeonPalette.NeonCyan, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        val roomy = maxHeight >= TOWER_ROOMY_HEIGHT
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.tower_title), color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = if (roomy) 18.sp else 15.sp, maxLines = 1)
            if (roomy) TowerRing(occupant?.monster)
            if (occupant != null) {
                Text(stringResource(R.string.tower_occupant, occupant.name), color = NeonPalette.NeonPink, fontFamily = OrbitronFamily,
                    fontSize = if (roomy) 20.sp else 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Text(stringResource(R.string.tower_stats, occupant.hp, occupant.vp), color = NeonPalette.TextPrimary, fontSize = 14.sp, maxLines = 1)
                Text(stringResource(R.string.tower_bonus), color = NeonPalette.TextPrimary, fontSize = 12.sp, maxLines = 1)
            } else {
                Text(stringResource(R.string.tower_free), color = NeonPalette.TextPrimary.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val botTurn = state.botTurn
                if (botTurn != null) {
                    BotTurnOverlay(botTurn, state.isBotFastForward, { onEvent(UiEvent.ToggleFastBot) }, Modifier.fillMaxWidth())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.summary_hint), color = NeonPalette.NeonCyan, fontSize = 11.sp,
                            modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onOpenSummary))
                        Text(
                            text = state.summary.takeLast(3).joinToString("\n"),
                            color = NeonPalette.NeonCyan,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** Ce que TalkBack annonce pour une fiche : la même information que les symboles affichés. */
@Composable
private fun PlayerUi.accessibilityLabel(): String = buildList {
    add(name)
    if (!isAlive) add(stringResource(R.string.player_a11y_eliminated))
    add(frenchPlural(R.plurals.a11y_hearts, hp, hp))
    add(frenchPlural(R.plurals.a11y_stars, vp, vp))
    add(frenchPlural(R.plurals.a11y_cells, energy, energy))
    add(stringResource(R.string.player_a11y_surcharge, malice))
    if (isInTokyo) add(stringResource(R.string.player_a11y_tower))
}.joinToString(", ")

@Composable
fun PlayerCardCompact(player: PlayerUi, stacked: Boolean = false) {
    val flash = damageFlash(player.hp)
    val shownHp by animateIntAsState(player.hp, tween(GLITCH_DURATION_MS), label = "hp")
    val border = if (flash > 0f || player.isActive) NeonPalette.NeonPink else NeonPalette.NeonCyan.copy(alpha = 0.35f)
    val name = when {
        !player.isAlive -> stringResource(R.string.player_name_eliminated, player.name)
        player.isInTokyo -> stringResource(R.string.player_name_tower, player.name)
        else -> player.name
    }
    val stats = stringResource(R.string.player_stats, shownHp, player.vp, player.energy, player.malice)
    val gainsText = player.gains?.let { stringResource(R.string.player_gains, it.vp, it.energy, it.healing) }
    val a11y = player.accessibilityLabel()
    val frame = Modifier.fillMaxWidth()
        .semantics(mergeDescendants = true) { contentDescription = a11y }
        .background(NeonPalette.NeonPink.copy(alpha = 0.4f * flash), RoundedCornerShape(8.dp))
        .background(NeonPalette.Surface.copy(alpha = 1f - 0.5f * flash), RoundedCornerShape(8.dp))
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
    val shownHp by animateIntAsState(player.hp, tween(GLITCH_DURATION_MS), label = "hp")
    val a11y = stringResource(R.string.player_a11y_self, player.accessibilityLabel())
    val selfName = stringResource(R.string.player_self_name, player.name)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = a11y }
            .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
            .border(2.dp, NeonPalette.NeonPink, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            text = if (player.isInTokyo) stringResource(R.string.player_name_tower, selfName) else selfName,
            color = NeonPalette.NeonPink,
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
        Text(
            text = frenchPlural(R.plurals.player_self_stats, player.cardCount, player.cardCount, shownHp, player.vp, player.energy),
            color = NeonPalette.TextPrimary,
            fontSize = 13.sp
        )
        if (gains != null) {
            val title = when {
                gains.isStartOfTurn -> R.string.gains_start_of_turn
                gains.isComplete -> R.string.gains_complete
                else -> R.string.gains_in_progress
            }
            Text(stringResource(title), color = NeonPalette.NeonCyan, fontSize = 10.sp, lineHeight = 12.sp)
            val otherVp = (gains.vp - gains.numbersVp - gains.tokyoVp).coerceAtLeast(0)
            Text(
                if (otherVp > 0) {
                    stringResource(R.string.gains_detail_effects, gains.vp, gains.numbersVp, gains.tokyoVp, gains.energy, gains.healing, gains.malice, otherVp)
                } else {
                    stringResource(R.string.gains_detail, gains.vp, gains.numbersVp, gains.tokyoVp, gains.energy, gains.healing, gains.malice)
                },
                color = NeonPalette.NeonPink, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(stringResource(R.string.gains_pending), color = NeonPalette.NeonCyan, fontSize = 11.sp, lineHeight = 13.sp)
        }
    }
}

/** Jauge verticale graduée de 0 au dernier palier (wireframe 03) ; les paliers sont écrits, lisibles sans la couleur. */
@Composable
fun SurchargeGaugeBar(malice: Int, tiers: List<Int>, modifier: Modifier = Modifier) {
    val max = tiers.last()
    val a11y = stringResource(R.string.gauge_a11y, malice, max, tiers.joinToString(", "))
    Column(
        modifier = modifier
            .width(44.dp)
            .semantics(mergeDescendants = true) { contentDescription = a11y }
            .background(NeonPalette.Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(stringResource(R.string.gauge_value, malice, max), color = NeonPalette.NeonCyan, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
        for (level in max downTo 1) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (level in tiers) level.toString() else "", color = NeonPalette.TextPrimary, fontSize = 10.sp, lineHeight = 11.sp, modifier = Modifier.width(14.dp))
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
        Text(0.toString(), color = NeonPalette.TextPrimary, fontSize = 10.sp, lineHeight = 11.sp)
    }
}

@Composable
fun BotTurnOverlay(
    botTurn: BotTurnUi,
    isFast: Boolean,
    onToggleFast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = botTurn.result
    val detail = if (result != null) {
        stringResource(R.string.bot_result, result.vp, result.energy, result.healing, result.malice, botTurn.lastAction)
    } else {
        botTurn.lastAction
    }
    Row(
        modifier = modifier
            .background(NeonPalette.Surface.copy(alpha = 0.95f), RoundedCornerShape(8.dp))
            .border(1.dp, NeonPalette.NeonCyan, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.bot_overlay, botTurn.botName, detail), color = NeonPalette.TextPrimary, fontSize = 12.sp, lineHeight = 14.sp,
            maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        NeonButton(
            text = stringResource(if (isFast) R.string.bot_fast_on else R.string.bot_fast_off),
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
            Text(stringResource(R.string.flee_title), color = NeonPalette.NeonPink, fontFamily = OrbitronFamily, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(frenchPlural(R.plurals.flee_damage, flee.damageTaken, flee.damageTaken, flee.attackerName), color = NeonPalette.TextPrimary, fontSize = 14.sp)
            Text(stringResource(R.string.flee_remaining, flee.remainingHp), color = NeonPalette.NeonPink, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            ) {
                FleeOption(stringResource(R.string.flee_option_flee), stringResource(R.string.flee_consequence, flee.attackerName), NeonPalette.NeonCyan, Modifier.weight(1f))
                FleeOption(stringResource(R.string.flee_option_stay), stringResource(R.string.stay_consequence), NeonPalette.NeonPink, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                NeonButton(stringResource(R.string.action_flee), onClick = onFlee, accentColor = NeonPalette.NeonCyan, modifier = Modifier.weight(1f))
                NeonButton(stringResource(R.string.action_stay), onClick = onStay, accentColor = NeonPalette.NeonPink, modifier = Modifier.weight(1f))
            }
            if (countdownRuns) {
                Text(stringResource(R.string.flee_countdown, secondsLeft), color = NeonPalette.TextPrimary, fontSize = 12.sp)
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
            Text(stringResource(R.string.malice_title, pick.level), color = NeonPalette.NeonCyan, fontFamily = OrbitronFamily, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                pick.tiers.forEach { tier ->
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
                text = stringResource(if (pick.ownedTiles.isEmpty()) R.string.malice_owned_none else R.string.malice_owned),
                color = NeonPalette.NeonCyan,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth()
            )
            if (pick.ownedTiles.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    pick.ownedTiles.forEach { tile ->
                        Text(
                            text = stringResource(R.string.malice_owned_item, tile.name, tile.text),
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
        Text(stringResource(R.string.malice_tier, tier), color = if (isCurrent) NeonPalette.NeonPink else NeonPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp)
        Text(stringResource(R.string.malice_tier_hint), color = NeonPalette.TextPrimary, fontSize = 10.sp, lineHeight = 12.sp)
    }
}

@Composable
private fun MaliceTileOffer(tile: TileUi, onPickTile: (String) -> Unit, modifier: Modifier) {
    val a11y = stringResource(R.string.malice_tile_a11y, tile.name, tile.text)
    Column(
        modifier = modifier
            .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, NeonPalette.NeonPink, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = a11y }) {
            Text(tile.name, color = NeonPalette.NeonPink, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.sp)
            Text(tile.text, color = NeonPalette.TextPrimary, fontSize = 12.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.height(4.dp))
        NeonButton(stringResource(R.string.action_equip), onClick = { onPickTile(tile.id) }, modifier = Modifier.fillMaxWidth())
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
            text = state.winnerName?.let { stringResource(R.string.end_winner, it) } ?: stringResource(R.string.end_no_winner),
            color = NeonPalette.NeonPink,
            fontFamily = OrbitronFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            lineHeight = 28.sp
        )
        Text(stringResource(R.string.end_seed, state.seed.toString()), color = NeonPalette.TextPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun GameOverRanking(state: GameOverUiState, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.ranking.forEachIndexed { index, player ->
            val isWinner = player.name == state.winnerName
            val shownName = if (player.id == state.humanId) stringResource(R.string.end_player_you, player.name) else player.name
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
                    text = if (player.isAlive) stringResource(R.string.end_rank, index + 1, shownName) else stringResource(R.string.end_rank_out, shownName),
                    color = if (isWinner) NeonPalette.NeonPink else NeonPalette.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(if (player.isAlive) R.string.end_stats else R.string.end_stats_out, player.vp, player.hp, player.energy),
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
        NeonButton(stringResource(R.string.action_home), onClick = onBackHome, accentColor = NeonPalette.NeonCyan, modifier = Modifier.weight(1f))
        NeonButton(stringResource(R.string.action_replay), onClick = onReplay, modifier = Modifier.weight(1f))
    }
}

@Composable
fun GameEndReason.label(): String = stringResource(
    when (this) {
        GameEndReason.TWENTY_STARS -> R.string.end_twenty_stars
        GameEndReason.LAST_SURVIVOR -> R.string.end_last_survivor
        GameEndReason.ALL_ELIMINATED -> R.string.end_all_eliminated
    }
)

@Composable
fun BlockedReason.label(): String = stringResource(
    when (this) {
        BlockedReason.NO_ROLLS_LEFT -> R.string.blocked_no_rolls
        BlockedReason.ALL_DICE_KEPT -> R.string.blocked_all_kept
        BlockedReason.ROLLING_OVER -> R.string.blocked_rolling_over
        BlockedReason.NOT_BUY_PHASE -> R.string.blocked_not_buy_phase
        BlockedReason.RESOLVE_FIRST -> R.string.blocked_resolve_first
        BlockedReason.BOT_PLAYING -> R.string.blocked_bot_playing
    }
)

@Composable
fun PhaseUi.label(): String = stringResource(
    when (this) {
        PhaseUi.ROLL -> R.string.phase_roll
        PhaseUi.RESOLVE -> R.string.phase_resolve
        PhaseUi.FLEE -> R.string.phase_flee
        PhaseUi.ENTER -> R.string.phase_enter
        PhaseUi.BUY -> R.string.phase_buy
        PhaseUi.END_TURN -> R.string.phase_end_turn
        PhaseUi.GAME_OVER -> R.string.phase_over
        PhaseUi.CHOOSE_POWER -> R.string.phase_choose_power
    }
)
