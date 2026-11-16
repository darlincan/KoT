package com.neonkaiju.kot.ai

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.MonsterId
import com.neonkaiju.kot.model.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BotSimulationTest {

    private val catalog = CatalogLoader.loadCatalog()
    private val policy = HeuristicBotPolicy()

    @Test
    fun `four bots only play legal actions and every game ends`() {
        for (seed in 1L..200L) {
            var state = GameEngine.createGame(seed, MonsterId.VOLTRAX, catalog)
            state = state.copy(players = state.players.map { it.copy(isBot = true) })

            var actions = 0
            while (state.phase != TurnPhase.FIN_PARTIE && actions < MAX_ACTIONS) {
                val action = policy.chooseAction(state)
                assertTrue(action in GameEngine.legalActions(state), "graine $seed : action illégale $action")
                state = GameEngine.reduce(state, action)
                actions++
            }

            assertEquals(TurnPhase.FIN_PARTIE, state.phase, "graine $seed : partie non terminée après $MAX_ACTIONS actions")
        }
    }

    private companion object {
        const val MAX_ACTIONS = 5_000
    }
}
