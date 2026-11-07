package com.neonkaiju.kot.feature.game

import androidx.lifecycle.SavedStateHandle
import com.neonkaiju.kot.ai.BotPolicy
import com.neonkaiju.kot.ai.HeuristicBotPolicy
import com.neonkaiju.kot.content.CatalogData
import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.data.CatalogRepository
import com.neonkaiju.kot.data.GameRepository
import com.neonkaiju.kot.data.Settings
import com.neonkaiju.kot.data.SettingsRepository
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.GameAction
import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.MonsterId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val catalog = CatalogLoader.loadCatalog()

    private class InMemoryGameRepository(var saved: GameState? = null) : GameRepository {
        override suspend fun load() = saved
        override suspend fun save(state: GameState) { saved = state }
        override suspend fun clear() { saved = null }
    }

    private inner class FixedCatalog : CatalogRepository {
        override suspend fun catalog(): CatalogData = catalog
    }

    private class InMemorySettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(Settings())
        override val settings: Flow<Settings> = state
        override suspend fun setFastBots(enabled: Boolean) { state.value = state.value.copy(fastBots = enabled) }
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        repository: GameRepository,
        settings: SettingsRepository = InMemorySettingsRepository(),
        policy: BotPolicy = HeuristicBotPolicy(),
    ) = GameViewModel(SavedStateHandle(), repository, FixedCatalog(), settings, policy, seedProvider = { 42L }, appVersion = "test")
        .also { runCurrent() }

    private fun newGame() = GameEngine.createGame(seed = 7L, humanMonster = MonsterId.VOLTRAX, catalog = catalog)

    @Test
    fun `saved game is restored at startup`() = runTest(dispatcher) {
        val saved = newGame()

        val viewModel = viewModel(InMemoryGameRepository(saved))

        assertTrue(viewModel.isLoaded.value)
        assertEquals(saved, viewModel.game.value)
    }

    @Test
    fun `starting a game uses the picked monster, saves it and asks to open the game screen`() = runTest(dispatcher) {
        val repository = InMemoryGameRepository()
        val viewModel = viewModel(repository)

        viewModel.pickMonster(MonsterId.KILOWATT)
        viewModel.startGame()
        advanceUntilIdle()

        val game = viewModel.game.value!!
        assertEquals(42L, game.rng.seed)
        assertEquals(MonsterId.KILOWATT, game.players.first { !it.isBot }.monster)
        assertEquals(game, repository.saved)
        assertEquals(GameEffect.GameStarted, viewModel.effects.first())
    }

    @Test
    fun `bots wait while the game screen is hidden, then play after the visible delay`() = runTest(dispatcher) {
        val botTurn = newGame().copy(activePlayerIndex = 1)
        val viewModel = viewModel(InMemoryGameRepository(botTurn))

        advanceTimeBy(5_000)
        assertEquals(botTurn, viewModel.game.value, "aucun bot ne joue écran caché")

        val delay = botDelayMillis(HeuristicBotPolicy().chooseAction(botTurn), fast = false)
        viewModel.setBotsActive(true)
        advanceTimeBy(delay - 1)
        assertEquals(botTurn, viewModel.game.value, "l'action du bot reste visible au moins le délai prévu")

        advanceTimeBy(2)
        assertNotEquals(botTurn, viewModel.game.value)
    }

    @Test
    fun `fast bots setting is persisted and shortens the bot delay`() = runTest(dispatcher) {
        val settings = InMemorySettingsRepository()
        val botTurn = newGame().copy(activePlayerIndex = 1)
        val viewModel = viewModel(InMemoryGameRepository(botTurn), settings)

        viewModel.toggleFastBots()
        runCurrent()
        assertTrue(settings.settings.first().fastBots)

        val delay = botDelayMillis(HeuristicBotPolicy().chooseAction(botTurn), fast = true)
        viewModel.setBotsActive(true)
        advanceTimeBy(delay + 1)
        assertNotEquals(botTurn, viewModel.game.value)
    }

    @Test
    fun `human action is ignored while a bot decides`() = runTest(dispatcher) {
        val botTurn = newGame().copy(activePlayerIndex = 1)
        val viewModel = viewModel(InMemoryGameRepository(botTurn))

        viewModel.dispatch(GameAction.StopRolling)

        assertEquals(botTurn, viewModel.game.value)
    }

    @Test
    fun `action confirmed on an older state is ignored`() = runTest(dispatcher) {
        val game = newGame()
        val viewModel = viewModel(InMemoryGameRepository(game))

        viewModel.dispatch(GameAction.StopRolling, expectedRevision = game.actionHistory.size + 1)
        assertEquals(game, viewModel.game.value)

        viewModel.dispatch(GameAction.StopRolling, expectedRevision = game.actionHistory.size)
        assertNotEquals(game, viewModel.game.value)
    }

    @Test
    fun `game screen state explains what the bot is doing`() = runTest(dispatcher) {
        val botTurn = newGame().copy(activePlayerIndex = 1)
        val viewModel = viewModel(InMemoryGameRepository(botTurn))
        val job = backgroundScope.launch { viewModel.gameUiState.collect {} }
        runCurrent()

        val explanation = viewModel.gameUiState.value?.botTurn?.lastAction
        assertFalse(explanation.isNullOrBlank())
        job.cancel()
    }

    @Test
    fun `bot pacing leaves time to read and fast mode stays at 400 ms`() {
        assertEquals(450L, botDelayMillis(GameAction.ToggleKeep(0), fast = false))
        assertEquals(1800L, botDelayMillis(GameAction.Reroll, fast = false))
        assertEquals(2200L, botDelayMillis(GameAction.EndTurn, fast = false))
        assertEquals(400L, botDelayMillis(GameAction.EndTurn, fast = true))
    }

    @Test
    fun `bot decides only once per state for the overlay and the move`() = runTest(dispatcher) {
        var decisions = 0
        val policy = object : BotPolicy {
            private val delegate = HeuristicBotPolicy()
            override fun chooseAction(state: GameState): GameAction { decisions++; return delegate.chooseAction(state) }
        }
        val botTurn = newGame().copy(activePlayerIndex = 1)
        val viewModel = viewModel(InMemoryGameRepository(botTurn), policy = policy)
        val job = backgroundScope.launch { viewModel.gameUiState.collect {} }
        viewModel.setBotsActive(true)
        runCurrent()

        assertEquals(1, decisions)
        job.cancel()
    }

    @Test
    fun `illegal action is ignored instead of crashing`() = runTest(dispatcher) {
        val game = newGame()
        val viewModel = viewModel(InMemoryGameRepository(game))

        viewModel.dispatch(GameAction.BuyCard(game.shop.first().id))

        assertEquals(game, viewModel.game.value)
    }
}
