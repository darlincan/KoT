package com.neonkaiju.kot.ai

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BotTurnPresenterTest {
    private val state = GameEngine.createGame(42, MonsterId.VOLTRAX, CatalogLoader.loadCatalog()).copy(activePlayerIndex = 1)
    private val presenter = BotTurnPresenter(object : BotPolicy {
        override fun chooseAction(state: GameState) = GameAction.Reroll
    })

    @Test fun important_action_waits_without_blocking_and_is_applied_once() = runTest {
        var result = state
        var actions = 0
        launch {
            presenter.nextAction(state, false) { true }?.let {
                result = GameEngine.reduce(result, it); actions++
            }
        }
        runCurrent(); assertEquals(0, actions)
        advanceTimeBy(1799); runCurrent(); assertEquals(0, actions)
        advanceTimeBy(1); runCurrent(); assertEquals(1, actions)
        advanceTimeBy(5000); runCurrent(); assertEquals(1, actions)
        assertEquals(1, result.rollsLeft)
        assertEquals(state.rng.cursor + 6, result.rng.cursor)
    }

    @Test fun cancellation_on_pause_navigation_background_or_game_over_emits_no_action() = runTest {
        for (reason in listOf("pause", "accueil", "arrière-plan", "fin de partie")) {
            var applied = false
            val job = launch { presenter.nextAction(state, false) { true }?.let { applied = true } }
            runCurrent(); advanceTimeBy(600)
            job.cancelAndJoin()
            advanceTimeBy(5000); runCurrent()
            assertFalse(applied, reason)
        }
    }

    @Test fun stale_state_is_rechecked_after_delay_and_cannot_start_a_second_turn() = runTest {
        var current = true
        var action: GameAction? = GameAction.Reroll
        launch { action = presenter.nextAction(state, false) { current } }
        runCurrent(); current = false
        advanceTimeBy(1800); runCurrent()
        assertNull(action)
        assertNull(presenter.nextAction(state, false) { false })
    }

    @Test fun normal_pacing_prioritizes_rolls_and_end_summary_while_fast_remains_available() {
        assertEquals(450L, presenter.delayMillis(GameAction.ToggleKeep(0), false))
        assertEquals(1800L, presenter.delayMillis(GameAction.Reroll, false))
        assertEquals(1800L, presenter.delayMillis(GameAction.BuyCard("coeur_nucleaire"), false))
        assertEquals(2200L, presenter.delayMillis(GameAction.EndTurn, false))
        assertEquals(400L, presenter.delayMillis(GameAction.EndTurn, true))
    }
}
