package com.neonkaiju.kot.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neonkaiju.kot.feature.game.*
import com.neonkaiju.kot.model.GameAction
import kotlinx.serialization.Serializable

/** Destinations de l'application. Routes typées : l'équivalent Compose de SafeArgs. */
sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object MonsterPicker : Route
    @Serializable data object Game : Route
    @Serializable data object Shop : Route
    @Serializable data object GameOver : Route
    @Serializable data object Rules : Route
    @Serializable data object Settings : Route
    @Serializable data object Pause : Route
}

/**
 * Graphe de navigation. La pile est sauvegardée par Navigation.
 * après une rotation ou un arrêt du processus, l'utilisateur revient sur le même écran.
 */
@Composable
fun KotNavHost(
    gameViewModel: GameViewModel,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(gameViewModel, navController) {
        gameViewModel.effects.collect { effect ->
            val target = when (effect) {
                GameEffect.GameStarted -> Route.Game
                GameEffect.GameOver -> Route.GameOver
            }
            navController.navigate(target) { popUpTo(Route.Home) }
        }
    }

    NavHost(navController = navController, startDestination = Route.Home) {

        composable<Route.Home> {
            val state by gameViewModel.homeUiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onEvent = { event ->
                    when (event) {
                        UiEvent.NewGame -> navController.navigate(Route.MonsterPicker)
                        UiEvent.Resume -> if (state.canResume) navController.navigate(Route.Game)
                        UiEvent.OpenRules -> navController.navigate(Route.Rules)
                        UiEvent.OpenSettings -> navController.navigate(Route.Settings)
                        else -> Unit
                    }
                }
            )
        }

        composable<Route.MonsterPicker> {
            val state by gameViewModel.pickerUiState.collectAsStateWithLifecycle()
            MonsterPickerScreen(
                state = state,
                onEvent = { event ->
                    when (event) {
                        is UiEvent.PickMonster -> gameViewModel.pickMonster(event.monster)
                        UiEvent.StartGame -> gameViewModel.startGame()
                        UiEvent.BackHome -> navController.backToHome()
                        else -> Unit
                    }
                }
            )
        }

        composable<Route.Game> {
            LifecycleResumeEffect(gameViewModel) {
                gameViewModel.setBotsActive(true)
                onPauseOrDispose { gameViewModel.setBotsActive(false) }
            }
            val state by gameViewModel.gameUiState.collectAsStateWithLifecycle()
            RequireGame(gameViewModel, onMissing = { navController.backToHome() })
            BackHandler { navController.navigate(Route.Pause) }
            state?.let {
                GameScreen(
                    state = it,
                    onEvent = { event ->
                        when (event) {
                            is UiEvent.DispatchGameAction -> gameViewModel.dispatch(event.action)
                            UiEvent.OpenShop -> navController.navigate(Route.Shop)
                            UiEvent.ToggleFastBot -> gameViewModel.toggleFastBots()
                            UiEvent.Pause -> navController.navigate(Route.Pause)
                            else -> Unit
                        }
                    }
                )
            }
        }

        composable<Route.Shop> {
            val state by gameViewModel.shopUiState.collectAsStateWithLifecycle()
            RequireGame(gameViewModel, onMissing = { navController.backToHome() })
            BackHandler { navController.navigate(Route.Pause) }
            state?.let {
                // Le marché se ferme si la décision passe à un bot.
                LaunchedEffect(it.isBotTurn) { if (it.isBotTurn) navController.popBackStack() }
                ShopSheet(
                    state = it,
                    onBuy = { cardId, revision -> gameViewModel.dispatch(GameAction.BuyCard(cardId), revision) },
                    onSweep = { revision -> gameViewModel.dispatch(GameAction.SweepShop, revision) },
                    onClose = { navController.popBackStack() }
                )
            }
        }

        composable<Route.GameOver> {
            val state by gameViewModel.gameOverUiState.collectAsStateWithLifecycle()
            RequireGame(gameViewModel, onMissing = { navController.backToHome() })
            state?.let {
                GameOverScreen(
                    state = it,
                    onReplay = { navController.navigate(Route.MonsterPicker) { popUpTo(Route.Home) } },
                    onBackHome = { navController.backToHome() }
                )
            }
        }

        composable<Route.Pause> {
            PauseScreen(
                onResume = { navController.popBackStack() },
                // Chaque action est déjà sauvegardée par le ViewModel.
                onSaveAndQuit = { navController.backToHome() },
                onHome = { navController.backToHome() }
            )
        }

        composable<Route.Rules> {
            RulesScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.Settings> {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onToggleFastBot = settingsViewModel::toggleFastBots,
                onToggleFleeCountdown = settingsViewModel::toggleFleeCountdown,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Un écran de partie restauré sans partie (sauvegarde effacée en fin de partie, puis processus tué)
 * renvoie à l'accueil au lieu de rester vide.
 */
@Composable
private fun RequireGame(gameViewModel: GameViewModel, onMissing: () -> Unit) {
    val isLoaded by gameViewModel.isLoaded.collectAsStateWithLifecycle()
    LaunchedEffect(isLoaded) {
        if (isLoaded && !gameViewModel.hasGame) onMissing()
    }
}

private fun NavHostController.backToHome() {
    popBackStack(Route.Home, inclusive = false)
}
