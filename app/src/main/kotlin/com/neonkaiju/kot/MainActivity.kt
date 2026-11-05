package com.neonkaiju.kot

import android.os.Bundle
import android.util.AtomicFile
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.neonkaiju.kot.ai.HeuristicBotPolicy
import com.neonkaiju.kot.ai.BotTurnPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.feature.game.*
import com.neonkaiju.kot.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

enum class AppRoute { HOME, PICK_MONSTER, GAME, SHOP, GAME_OVER, RULES, SETTINGS, PAUSE }

class MainActivity : ComponentActivity() {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val saveFile by lazy { File(filesDir, "saved_game_v1.json") }
    private val catalog by lazy { CatalogLoader.loadCatalog() }
    private val botPolicy = HeuristicBotPolicy()
    private val botPresenter = BotTurnPresenter(botPolicy)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeonKaijuTheme {
                var route by rememberSaveable { mutableStateOf(AppRoute.HOME) }
                var selectedMonster by rememberSaveable { mutableStateOf(MonsterId.VOLTRAX) }
                var gameState by remember { mutableStateOf<GameState?>(loadSavedGame()) }
                var pausedRoute by rememberSaveable { mutableStateOf(AppRoute.GAME) }
                var fastBot by rememberSaveable { mutableStateOf(false) }
                var appResumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, _ ->
                        appResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                fun persist(newState: GameState) {
                    gameState = newState
                    val atomic = AtomicFile(saveFile)
                    val output = atomic.startWrite()
                    try {
                        output.write(json.encodeToString(newState).toByteArray(Charsets.UTF_8))
                        atomic.finishWrite(output)
                    } catch (failure: Exception) {
                        atomic.failWrite(output)
                        throw failure
                    }
                    if (route == AppRoute.SHOP && newState.activePlayer.isBot) route = AppRoute.GAME
                    if (newState.phase == TurnPhase.FIN_PARTIE) {
                        route = AppRoute.GAME_OVER
                    }
                }

                LaunchedEffect(gameState, fastBot, route, appResumed) {
                    val current = gameState ?: return@LaunchedEffect
                    if (!appResumed || route != AppRoute.GAME || current.phase == TurnPhase.FIN_PARTIE) return@LaunchedEffect

                    val isBotDecision = current.decisionPlayer.isBot

                    if (isBotDecision) {
                        val botAction = botPresenter.nextAction(current, fastBot) {
                            appResumed && route == AppRoute.GAME && gameState == current
                        } ?: return@LaunchedEffect
                        persist(GameEngine.reduceIfCurrent(current, botAction, current.actionHistory.size))
                    }
                }

                fun dispatch(action: GameAction, expectedRevision: Int? = null) {
                    val latest = gameState ?: return
                    if (latest.decisionPlayer.isBot || action !in GameEngine.legalActions(latest)) return
                    if (expectedRevision != null && latest.actionHistory.size != expectedRevision) return
                    persist(if (expectedRevision == null) GameEngine.reduce(latest, action)
                        else GameEngine.reduceIfCurrent(latest, action, expectedRevision))
                }

                fun pause() {
                    pausedRoute = route
                    route = AppRoute.PAUSE
                }
                BackHandler(enabled = route in listOf(AppRoute.GAME, AppRoute.SHOP, AppRoute.PAUSE)) {
                    if (route == AppRoute.PAUSE) route = pausedRoute else pause()
                }

                when (route) {
                    AppRoute.HOME -> HomeScreen(
                        canResume = gameState != null && gameState?.phase != TurnPhase.FIN_PARTIE,
                        version = "1.0.0",
                        onEvent = { event ->
                            when (event) {
                                UiEvent.NewGame -> route = AppRoute.PICK_MONSTER
                                UiEvent.Resume -> if (gameState != null) route = AppRoute.GAME
                                UiEvent.OpenRules -> route = AppRoute.RULES
                                UiEvent.OpenSettings -> route = AppRoute.SETTINGS
                                else -> {}
                            }
                        }
                    )
                    AppRoute.PICK_MONSTER -> MonsterPickerScreen(
                        selectedMonster = selectedMonster,
                        onEvent = { event ->
                            when (event) {
                                is UiEvent.PickMonster -> selectedMonster = event.monster
                                UiEvent.StartGame -> {
                                    val fresh = GameEngine.createGame(
                                        seed = System.currentTimeMillis(),
                                        humanMonster = selectedMonster,
                                        catalog = catalog
                                    )
                                    persist(fresh)
                                    route = AppRoute.GAME
                                }
                                else -> {}
                            }
                        }
                    )
                    AppRoute.GAME -> gameState?.let { state ->
                        GameScreen(
                            state = state,
                            isBotFastForward = fastBot,
                            botExplanation = if (state.decisionPlayer.isBot) botPolicy.explainAction(state, botPolicy.chooseAction(state)) else null,
                            onEvent = { event ->
                                when (event) {
                                    is UiEvent.DispatchGameAction -> dispatch(event.action)
                                    UiEvent.OpenShop -> if (!state.decisionPlayer.isBot && state.phase == TurnPhase.ACHETER) route = AppRoute.SHOP
                                    UiEvent.ToggleFastBot -> fastBot = !fastBot
                                    UiEvent.Pause -> pause()
                                    else -> {}
                                }
                            }
                        )
                    }
                    AppRoute.SHOP -> gameState?.let { state ->
                        ShopSheet(
                            state = state,
                            onBuy = { cardId, revision -> dispatch(GameAction.BuyCard(cardId), revision) },
                            onSweep = { revision -> dispatch(GameAction.SweepShop, revision) },
                            onClose = { route = AppRoute.GAME }
                        )
                    }
                    AppRoute.GAME_OVER -> gameState?.let { state ->
                        GameOverScreen(
                            state = state,
                            onReplay = { route = AppRoute.PICK_MONSTER },
                            onBackHome = { route = AppRoute.HOME }
                        )
                    }
                    AppRoute.PAUSE -> PauseScreen(
                        onResume = { route = pausedRoute },
                        onSaveAndQuit = { gameState?.let { persist(it) }; route = AppRoute.HOME },
                        onHome = { route = AppRoute.HOME }
                    )
                    AppRoute.RULES -> RulesScreen(onBack = { route = AppRoute.HOME })
                    AppRoute.SETTINGS -> SettingsScreen(
                        fastBot = fastBot,
                        onToggleFastBot = { fastBot = !fastBot },
                        onBack = { route = AppRoute.HOME }
                    )
                }
            }
        }
    }

    private fun loadSavedGame(): GameState? = runCatching {
        val saved = AtomicFile(saveFile).openRead().bufferedReader().use { it.readText() }
        val state = json.decodeFromString<GameState>(saved)
        GameEngine.advanceAutomatically(state.copy(version = 2, maxRolls = 3, rollsLeft = state.rollsLeft.coerceIn(0, 2)))
    }.getOrNull()
}
