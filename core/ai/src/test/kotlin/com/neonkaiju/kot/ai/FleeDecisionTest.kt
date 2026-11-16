package com.neonkaiju.kot.ai

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.GameAction
import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.MonsterId
import com.neonkaiju.kot.model.PendingFleeDecision
import com.neonkaiju.kot.model.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FleeDecisionTest {

    private val game = GameEngine.createGame(seed = 7L, humanMonster = MonsterId.VOLTRAX, catalog = CatalogLoader.loadCatalog())
    private val policy = HeuristicBotPolicy()

    /** Le joueur 0 vient d'attaquer le bot 1, qui occupe la Tour. */
    private fun decision(hp: Int, vp: Int, eliminated: Set<Int> = emptySet()): GameState = game.copy(
        phase = TurnPhase.DECISION_FUITE,
        tokyoOccupantId = 1,
        pendingFlee = PendingFleeDecision(attackerId = 0, occupantId = 1, damageTaken = 2),
        players = game.players.map { player ->
            when (player.id) {
                1 -> player.copy(hp = hp, vp = vp)
                in eliminated -> player.copy(hp = 0, isAlive = false)
                else -> player
            }
        },
    )

    @Test
    fun `stays at 18 stars with enough hearts to survive`() {
        assertEquals(GameAction.Stay, policy.chooseAction(decision(hp = 5, vp = 18)))
    }

    @Test
    fun `flees at 18 stars when the opponents would probably finish it`() {
        assertEquals(GameAction.Flee, policy.chooseAction(decision(hp = 4, vp = 18)))
    }

    @Test
    fun `flees with 2 hearts against 3 opponents`() {
        assertEquals(GameAction.Flee, policy.chooseAction(decision(hp = 2, vp = 5)))
    }

    @Test
    fun `stays with full hearts`() {
        assertEquals(GameAction.Stay, policy.chooseAction(decision(hp = 10, vp = 5)))
    }

    @Test
    fun `the same hearts are enough against a single opponent`() {
        assertEquals(GameAction.Flee, policy.chooseAction(decision(hp = 5, vp = 5)))
        assertEquals(GameAction.Stay, policy.chooseAction(decision(hp = 5, vp = 5, eliminated = setOf(2, 3))))
    }

    @Test
    fun `the decision depends on the occupant, not on the active player`() {
        val weakAttacker = decision(hp = 10, vp = 5).copy(
            players = decision(hp = 10, vp = 5).players.map { if (it.id == 0) it.copy(hp = 1) else it },
        )

        assertEquals(GameAction.Stay, policy.chooseAction(weakAttacker))
    }
}
