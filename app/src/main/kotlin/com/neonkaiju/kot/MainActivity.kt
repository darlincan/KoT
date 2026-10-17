package com.neonkaiju.kot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.neonkaiju.kot.ai.HeuristicBotPolicy
import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.feature.game.*
import com.neonkaiju.kot.model.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

enum class AppRoute { HOME, PICK_MONSTER, GAME, SHOP, GAME_OVER, RULES, SETTINGS }

class MainActivity : ComponentActivity() {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val saveFile by lazy { File(filesDir, "saved_game_v1.json") }
    private val catalog by lazy { CatalogLoader.loadCatalog() }
    private val botPolicy = HeuristicBotPolicy()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeonKaijuTheme {
                var route by remember { mutableStateOf(AppRoute.HOME) }
                var selectedMonster by remember { mutableStateOf(MonsterId.VOLTRAX) }
                var gameState by remember { mutableStateOf<GameState?>(loadSavedGame()) }
                var fastBot by remember { mutableStateOf(false) }

                fun persist(newState: GameState) {
                    gameState = newState
                    saveFile.writeText(json.encodeToString(newState))
                    if (newState.phase == TurnPhase.FIN_PARTIE) {
                        saveFile.delete()
                        route = AppRoute.GAME_OVER
                    }
                }

                LaunchedEffect(gameState, fastBot, route) {
                    val current = gameState ?: return@LaunchedEffect
                    if (route != AppRoute.GAME || current.phase == TurnPhase.FIN_PARTIE) return@LaunchedEffect

                    val isBotDecision = (current.phase == TurnPhase.DECISION_FUITE &&
                        current.players.first { it.id == current.pendingFlee!!.occupantId }.isBot) ||
                        (current.phase != TurnPhase.DECISION_FUITE && current.activePlayer.isBot)

                    if (isBotDecision) {
                        delay(if (fastBot) 400L else 650L)
                        val botAction = botPolicy.chooseAction(current)
                        persist(GameEngine.reduce(current, botAction))
                    }
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
                            onEvent = { event ->
                                when (event) {
                                    is UiEvent.DispatchGameAction -> persist(GameEngine.reduce(state, event.action))
                                    UiEvent.OpenShop -> route = AppRoute.SHOP
                                    UiEvent.ToggleFastBot -> fastBot = !fastBot
                                    else -> {}
                                }
                            }
                        )
                    }
                    AppRoute.SHOP -> gameState?.let { state ->
                        ShopSheet(
                            state = state,
                            onBuy = { cardId -> persist(GameEngine.reduce(state, GameAction.BuyCard(cardId))) },
                            onSweep = { persist(GameEngine.reduce(state, GameAction.SweepShop)) },
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
        if (saveFile.exists()) json.decodeFromString<GameState>(saveFile.readText()) else null
    }.getOrNull()
}
