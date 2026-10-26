package com.neonkaiju.kot.feature.game

import androidx.lifecycle.SavedStateHandle
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
    ) = GameViewModel(SavedStateHandle(), repository, FixedCatalog(), settings, HeuristicBotPolicy(), seedProvider = { 42L }, appVersion = "test")
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

        viewModel.setBotsActive(true)
        advanceTimeBy(GameViewModel.BOT_DELAY_MS - 1)
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

        viewModel.setBotsActive(true)
        advanceTimeBy(GameViewModel.FAST_BOT_DELAY_MS + 1)
        assertNotEquals(botTurn, viewModel.game.value)
    }

    @Test
    fun `illegal action is ignored instead of crashing`() = runTest(dispatcher) {
        val game = newGame()
        val viewModel = viewModel(InMemoryGameRepository(game))

        viewModel.dispatch(GameAction.BuyCard(game.shop.first().id))

        assertEquals(game, viewModel.game.value)
    }
}
