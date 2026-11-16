package com.neonkaiju.kot.engine

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.model.*
import com.neonkaiju.kot.model.DieFace.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Règles du §7 non couvertes par [RulesAndCatalogTest]. Le joueur 0 est actif sauf mention contraire. */
class TurnRulesTest {

    private val catalog = CatalogLoader.loadCatalog()

    private fun game() = GameEngine.createGame(42L, MonsterId.VOLTRAX, catalog)

    private fun GameState.player(id: Int) = players.first { it.id == id }

    private fun GameState.withPlayer(id: Int, block: (PlayerState) -> PlayerState) =
        copy(players = players.map { if (it.id == id) block(it) else it })

    private fun GameState.withDice(vararg faces: DieFace) = copy(dice = faces.mapIndexed { i, f -> Die(i, f) })

    private fun GameState.inShop() = copy(phase = TurnPhase.ACHETER)

    // --- Phase 1 : lancer ----------------------------------------------------------------------

    @Test
    fun `RULE_06_three_rolls_at_most_then_resolution`() {
        var state = game()
        assertEquals(2, state.rollsLeft)

        state = GameEngine.reduce(state, GameAction.Reroll)
        state = GameEngine.reduce(state, GameAction.Reroll)

        assertEquals(0, state.rollsLeft)
        assertNotEquals(TurnPhase.LANCER, state.phase)
        assertFalse(GameAction.Reroll in GameEngine.legalActions(state))
    }

    @Test
    fun `RULE_08_kept_dice_are_not_rerolled_and_can_be_taken_back`() {
        var state = game()
        val kept = state.dice[0]

        state = GameEngine.reduce(state, GameAction.ToggleKeep(0))
        state = GameEngine.reduce(state, GameAction.Reroll)
        assertEquals(kept.face, state.dice[0].face)
        assertTrue(state.dice[0].isKept)

        state = GameEngine.reduce(state, GameAction.ToggleKeep(0))
        assertFalse(state.dice[0].isKept)
    }

    @Test
    fun `RULE_08_reroll_is_not_offered_when_every_die_is_kept`() {
        var state = game()
        (0 until 6).forEach { state = GameEngine.reduce(state, GameAction.ToggleKeep(it)) }

        assertFalse(GameAction.Reroll in GameEngine.legalActions(state))
    }

    // --- Phase 2 : résoudre --------------------------------------------------------------------

    @Test
    fun `RULE_14_crossing_level_3_offers_a_tile_of_that_level`() {
        var state = game().withPlayer(0) { it.copy(malice = 2) }.withDice(ONE, ONE, ONE, TWO, TWO, THREE)
        state = GameEngine.reduce(state, GameAction.StopRolling)
        state = GameEngine.reduce(state, GameAction.ResolveSymbols(SymbolResolutionType.NUMBERS))

        assertEquals(listOf(3), state.pendingMaliceLevels)
        val offers = GameEngine.legalActions(state)
        assertEquals(4, offers.size)
        assertTrue(offers.all { it is GameAction.PickMaliceTile })

        val picked = state.availableTiles.first { it.niveau == 3 }
        state = GameEngine.reduce(state, GameAction.PickMaliceTile(picked.id))

        assertEquals(listOf(picked), state.player(0).tiles)
        assertEquals(setOf(3), state.player(0).unlockedMaliceLevels)
        assertFalse(picked in state.availableTiles)
        assertEquals(TurnPhase.ACHETER, state.phase)
    }

    @Test
    fun `RULE_15_a_level_already_unlocked_gives_nothing`() {
        var state = game()
            .withPlayer(0) { it.copy(malice = 2, unlockedMaliceLevels = setOf(3)) }
            .withDice(ONE, ONE, ONE, TWO, TWO, THREE)
        state = GameEngine.reduce(state, GameAction.StopRolling)
        state = GameEngine.reduce(state, GameAction.ResolveSymbols(SymbolResolutionType.NUMBERS))

        assertTrue(state.pendingMaliceLevels.isEmpty())
        assertEquals(10, state.availableTiles.size)
    }

    @Test
    fun `RULE_19_occupant_who_stays_keeps_tokyo_but_loses_hp`() {
        var state = game().copy(tokyoOccupantId = 1).withDice(CLAW, CLAW, CLAW, ONE, TWO, THREE)
        state = GameEngine.reduce(state, GameAction.StopRolling)
        state = GameEngine.reduce(state, GameAction.ResolveSymbols(SymbolResolutionType.CLAWS))
        assertEquals(TurnPhase.DECISION_FUITE, state.phase)

        state = GameEngine.reduce(state, GameAction.Stay)

        assertEquals(1, state.tokyoOccupantId)
        assertEquals(7, state.player(1).hp)
        assertEquals(TurnPhase.ACHETER, state.phase)
    }

    @Test
    fun `RULE_23_hearts_do_not_heal_inside_tokyo`() {
        var state = game().copy(tokyoOccupantId = 0).withPlayer(0) { it.copy(hp = 5) }.withDice(HEART, HEART, HEART, ONE, TWO, THREE)
        state = GameEngine.reduce(state, GameAction.StopRolling)
        state = GameEngine.reduce(state, GameAction.ResolveSymbols(SymbolResolutionType.HEARTS))

        assertEquals(5, state.player(0).hp)
    }

    // --- Phase 4 : acheter ---------------------------------------------------------------------

    @Test
    fun `RULE_31_bought_card_is_replaced_from_the_reshuffled_discard_when_the_deck_is_empty`() {
        val base = game()
        val bought = base.shop.first()
        val discard = base.deck.take(4)
        var state = base.inShop()
            .copy(deck = emptyList(), discard = discard)
            .withPlayer(0) { it.copy(energy = 99) }

        state = GameEngine.reduce(state, GameAction.BuyCard(bought.id))

        assertEquals(3, state.shop.size)
        assertFalse(bought in state.shop)
        assertTrue(state.shop.last() in discard)
        assertEquals(3, state.deck.size + state.discard.count { it in discard })
    }

    @Test
    fun `RULE_32_two_energy_sweep_the_three_visible_cards`() {
        val base = game()
        val oldShop = base.shop
        var state = base.inShop().withPlayer(0) { it.copy(energy = 2) }

        state = GameEngine.reduce(state, GameAction.SweepShop)

        assertEquals(0, state.player(0).energy)
        assertEquals(base.deck.take(3), state.shop)
        assertTrue(state.discard.containsAll(oldShop))
    }

    @Test
    fun `RULE_32_sweep_reshuffles_the_discard_when_the_deck_runs_short`() {
        val base = game()
        var state = base.inShop()
            .copy(deck = base.deck.take(1), discard = base.deck.drop(1))
            .withPlayer(0) { it.copy(energy = 2) }

        state = GameEngine.reduce(state, GameAction.SweepShop)

        assertEquals(3, state.shop.size)
        assertTrue(state.discard.isEmpty())
        assertEquals(catalog.cards.size, state.shop.size + state.deck.size)
    }

    @Test
    fun `RULE_32_sweep_needs_two_energy`() {
        val state = game().inShop().withPlayer(0) { it.copy(energy = 1) }
        assertFalse(GameAction.SweepShop in GameEngine.legalActions(state))
    }

    // --- Phase 5 et fin de partie --------------------------------------------------------------

    @Test
    fun `RULE_34_turn_passes_to_the_next_living_monster`() {
        val state = GameEngine.reduce(game().inShop().withPlayer(1) { it.copy(isAlive = false, hp = 0) }, GameAction.EndTurn)

        assertEquals(2, state.activePlayer.id)
        assertEquals(TurnPhase.LANCER, state.phase)
    }

    @Test
    fun `RULE_35_twenty_vp_ends_the_game_at_end_of_turn`() {
        val state = GameEngine.reduce(game().inShop().withPlayer(2) { it.copy(vp = 20) }, GameAction.EndTurn)

        assertEquals(TurnPhase.FIN_PARTIE, state.phase)
        assertEquals(2, state.winnerId)
        assertTrue(GameEngine.legalActions(state).isEmpty())
    }

    @Test
    fun `RULE_36_a_dead_monster_with_twenty_vp_does_not_win`() {
        val state = GameEngine.reduce(
            game().inShop().withPlayer(2) { it.copy(vp = 20, hp = 0, isAlive = false) },
            GameAction.EndTurn
        )

        assertNull(state.winnerId)
        assertEquals(TurnPhase.LANCER, state.phase)
    }

    @Test
    fun `RULE_37_nobody_wins_when_every_monster_is_eliminated`() {
        val allDead = game().inShop().copy(players = game().players.map { it.copy(hp = 0, isAlive = false) })
        val state = GameEngine.reduce(allDead, GameAction.EndTurn)

        assertEquals(TurnPhase.FIN_PARTIE, state.phase)
        assertNull(state.winnerId)
    }

    // --- Machine à états -----------------------------------------------------------------------

    @Test
    fun `STATE_an_action_outside_its_phase_is_illegal`() {
        assertThrows<IllegalActionException> { GameEngine.reduce(game(), GameAction.Flee) }
        assertThrows<IllegalActionException> { GameEngine.reduce(game(), GameAction.EndTurn) }
    }

    @Test
    fun `STATE_transient_phases_offer_no_action`() {
        assertTrue(GameEngine.legalActions(game().copy(phase = TurnPhase.ENTRER)).isEmpty())
        assertTrue(GameEngine.legalActions(game().copy(phase = TurnPhase.FIN_TOUR)).isEmpty())
    }
}
