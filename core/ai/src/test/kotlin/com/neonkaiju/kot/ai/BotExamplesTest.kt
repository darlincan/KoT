package com.neonkaiju.kot.ai

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BotExamplesTest {
    private val policy = HeuristicBotPolicy()
    private val cards = CatalogLoader.loadCatalog()
    private fun scenario(seed: Long, hp: Int, occupant: Int, faces: List<DieFace>) = GameState(
        rng = DeterministicRng(seed), activePlayerIndex = 1, tokyoOccupantId = occupant,
        players = MonsterId.entries.take(4).mapIndexed { id, monster -> PlayerState(id, monster, id != 0, hp = if (id == 1) hp else 10) },
        dice = faces.mapIndexed { id, face -> Die(id, face) }, shop = cards.cards.take(3), availableTiles = cards.tiles
    )
    private fun runExample(name: String, initial: GameState): GameState {
        var state = initial
        println("EXAMPLE $name initial=${state.dice.map { it.face }}")
        var steps = 0
        while (state.phase == TurnPhase.LANCER && steps++ < 50) {
            val action = policy.chooseAction(state)
            println("EXAMPLE $name $action: ${policy.explainAction(state, action)}")
            state = GameEngine.reduce(state, action)
            if (action == GameAction.Reroll) println("EXAMPLE $name roll=${state.rollsMade} dice=${state.dice.map { it.face }}")
        }
        assertNotEquals(TurnPhase.LANCER, state.phase)
        println("EXAMPLE $name final=${state.dice.map { it.face }} rolls=${state.rollsMade} phase=${state.phase} hp=${state.activePlayer.hp} energy=${state.activePlayer.energy} vp=${state.activePlayer.vp}")
        return state
    }

    @Test fun examples_match_real_policy() {
        val healing = runExample("healing", scenario(0, 3, 0, listOf(DieFace.HEART, DieFace.HEART, DieFace.CLAW, DieFace.ENERGY, DieFace.THREE, DieFace.THREE)))
        assertTrue(healing.activePlayer.hp > 3)
        val early = runExample("early", scenario(1, 10, 0, List(6) { DieFace.ENERGY }))
        assertEquals(1, early.rollsMade); assertEquals(6, early.activePlayer.energy)
        val tokyo = runExample("tokyo", scenario(0, 3, 1, List(6) { DieFace.HEART }))
        assertEquals(3, tokyo.activePlayer.hp)
    }

    @Test fun comparable_valid_options_vary_with_seed_but_each_decision_is_reproducible() {
        val faces = listOf(DieFace.ENERGY, DieFace.THREE, DieFace.THREE, DieFace.HEART, DieFace.ONE, DieFace.TWO)
        val a = scenario(0, 10, 0, faces)
        val b = scenario(1, 10, 0, faces)
        assertNotEquals(policy.chooseAction(a), policy.chooseAction(b))
        assertEquals(policy.chooseAction(a), policy.chooseAction(a))
        assertTrue(policy.chooseAction(a) in GameEngine.legalActions(a))
        assertTrue(policy.chooseAction(b) in GameEngine.legalActions(b))
    }
}
