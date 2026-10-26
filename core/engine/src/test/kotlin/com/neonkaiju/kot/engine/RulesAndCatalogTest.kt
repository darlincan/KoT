package com.neonkaiju.kot.engine

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RulesAndCatalogTest {

    private val catalog = CatalogLoader.loadCatalog()

    @Test
    fun `CATALOG_validation_exact_20_cards_and_10_tiles`() {
        assertEquals(20, catalog.cards.size, "Le catalogue doit contenir exactement 20 cartes")
        assertEquals(10, catalog.tiles.size, "Le catalogue doit contenir exactement 10 tuiles")
        assertEquals(20, catalog.cards.map { it.id }.distinct().size, "Aucun ID de carte dupliqué")
        assertEquals(10, catalog.tiles.map { it.id }.distinct().size, "Aucun ID de tuile dupliqué")
    }

    @Test
    fun `RULE_01_02_03_initial_setup_4_players_10hp_3_cards`() {
        val state = GameEngine.createGame(seed = 42L, humanMonster = MonsterId.VOLTRAX, catalog = catalog)
        assertEquals(4, state.players.size)
        state.players.forEach {
            assertEquals(10, it.hp)
            assertEquals(0, it.vp)
            assertEquals(0, it.malice)
        }
        assertEquals(3, state.shop.size)
        assertEquals(4, state.availableTiles.count { it.niveau == 3 })
        assertEquals(4, state.availableTiles.count { it.niveau == 6 })
        assertEquals(2, state.availableTiles.count { it.niveau == 10 })
    }

    @Test
    fun `RULE_10_11_12_13_triples_vp_and_malice_points`() {
        var state = GameEngine.createGame(42L, MonsterId.VOLTRAX, catalog)
        val forcedDice = listOf(
            Die(0, DieFace.ONE), Die(1, DieFace.ONE), Die(2, DieFace.ONE),
            Die(3, DieFace.ONE), Die(4, DieFace.ENERGY), Die(5, DieFace.ENERGY)
        )
        state = state.copy(dice = forcedDice)
        state = GameEngine.reduce(state, GameAction.StopRolling)
        state = GameEngine.reduce(state, GameAction.ResolveSymbols(SymbolResolutionType.NUMBERS))

        assertEquals(2, state.activePlayer.vp)
        assertEquals(2, state.activePlayer.malice)
    }

    @Test
    fun `RULE_19_23_25_tokyo_entry_no_heart_heal_and_flee_decision`() {
        var state = GameEngine.createGame(99L, MonsterId.VOLTRAX, catalog)
        state = state.copy(dice = List(6) { Die(it, DieFace.HEART) })
        state = GameEngine.reduce(state, GameAction.StopRolling)
        state = GameEngine.reduce(state, GameAction.ResolveSymbols(SymbolResolutionType.HEARTS))

        assertEquals(0, state.tokyoOccupantId)
        assertEquals(1, state.activePlayer.vp)

        state = GameEngine.reduce(state, GameAction.EndTurn)
        state = state.copy(dice = listOf(
            Die(0, DieFace.CLAW), Die(1, DieFace.CLAW),
            Die(2, DieFace.ONE), Die(3, DieFace.TWO), Die(4, DieFace.THREE), Die(5, DieFace.ENERGY)
        ))
        state = GameEngine.reduce(state, GameAction.StopRolling)
        state = GameEngine.reduce(state, GameAction.ResolveSymbols(SymbolResolutionType.CLAWS))

        assertEquals(TurnPhase.DECISION_FUITE, state.phase)
        assertEquals(8, state.players[0].hp)

        state = GameEngine.reduce(state, GameAction.Flee)
        assertNull(state.tokyoOccupantId)
    }
}
