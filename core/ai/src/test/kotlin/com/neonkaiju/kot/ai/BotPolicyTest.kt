package com.neonkaiju.kot.ai

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BotPolicyTest {
    private val catalog = CatalogLoader.loadCatalog()
    private val bot = HeuristicBotPolicy()

    @Test fun hundred_complete_seeded_games_use_only_legal_actions_without_blocking() {
        val winners = mutableSetOf<Int?>()
        var maxActions = 0
        for (seed in 0L until 100L) {
            var state = GameEngine.createGame(seed, MonsterId.VOLTRAX, catalog)
            var actions = 0
            while (state.phase != TurnPhase.FIN_PARTIE && actions < 5000) {
                val action = bot.chooseAction(state)
                assertTrue(action in GameEngine.legalActions(state), "seed=$seed action=$action")
                assertEquals(action, bot.chooseAction(state), "A decision must be stable across recompositions")
                val before = state
                state = GameEngine.reduce(state, action)
                state.players.forEach {
                    assertEquals(it.hp > 0, it.isAlive, "seed=$seed action=$action")
                    assertTrue(it.hp in 0..10 && it.vp in 0..20 && it.energy >= 0 && it.malice in 0..10)
                }
                assertTrue(state.rollsLeft in 0..2)
                assertEquals(3, state.maxRolls)
                state.tokyoOccupantId?.let { id -> assertTrue(state.players.first { it.id == id }.isAlive) }
                if (action == GameAction.Reroll) before.dice.filter { it.isKept }.forEach { kept ->
                    // Before-resolution card effects are permitted to transform the final face.
                    if (state.phase == TurnPhase.LANCER) assertEquals(kept, state.dice.first { it.id == kept.id })
                }
                actions++
            }
            assertEquals(TurnPhase.FIN_PARTIE, state.phase, "Blocked game seed=$seed after $actions actions: $state")
            winners.add(state.winnerId)
            maxActions = maxOf(maxActions, actions)
        }
        assertTrue(winners.size >= 3, "Winners should vary: $winners")
        println("100 parties terminées ; gagnants=$winners ; maximum=$maxActions actions")
    }

    @Test fun bot_heals_only_outside_tokyo_and_flees_at_low_health() {
        val initial = GameEngine.createGame(42, MonsterId.VOLTRAX, catalog).copy(activePlayerIndex = 1)
        val weak = initial.copy(players = initial.players.map { if (it.id == 1) it.copy(hp = 2) else it },
            dice = List(6) { Die(it, DieFace.HEART) })
        assertEquals(GameAction.ToggleKeep(0), bot.chooseAction(weak))
        assertEquals(GameAction.Reroll, bot.chooseAction(weak.copy(tokyoOccupantId = 1)))
        val decision = weak.copy(phase = TurnPhase.DECISION_FUITE, pendingFlee = PendingFleeDecision(0, 1, 2))
        assertEquals(GameAction.Flee, bot.chooseAction(decision))
    }

    @Test fun save_replay_same_seed_and_actions_produces_identical_game() {
        var first = GameEngine.createGame(19, MonsterId.VOLTRAX, catalog)
        var second = first
        var actions = 0
        while (first.phase != TurnPhase.FIN_PARTIE && actions++ < 5000) {
            val action = bot.chooseAction(first)
            first = GameEngine.reduce(first, action)
            second = GameEngine.reduce(second, action)
            assertEquals(first, second)
        }
        assertEquals(TurnPhase.FIN_PARTIE, first.phase)
    }
    @Test fun three_bots_complete_their_turns_and_return_control_to_human() {
        var state = GameEngine.createGame(42, MonsterId.VOLTRAX, catalog).copy(
            activePlayerIndex = 0, phase = TurnPhase.ACHETER, tokyoOccupantId = null,
            shop = emptyList(), deck = emptyList(), discard = emptyList()
        )
        state = GameEngine.reduce(state, GameAction.EndTurn)
        val visited = mutableSetOf<Int>()
        var actions = 0
        while (state.activePlayer.id != 0 && actions++ < 200) {
            visited.add(state.activePlayer.id)
            assertTrue(state.decisionPlayer.isBot)
            state = GameEngine.reduce(state, bot.chooseAction(state))
        }
        assertEquals(setOf(1, 2, 3), visited)
        assertEquals(0, state.activePlayer.id)
        assertFalse(state.decisionPlayer.isBot)
        assertEquals(TurnPhase.LANCER, state.phase)
    }

}
