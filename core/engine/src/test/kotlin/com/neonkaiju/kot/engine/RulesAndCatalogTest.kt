package com.neonkaiju.kot.engine

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.content.EffectInterpreter
import com.neonkaiju.kot.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class RulesAndCatalogTest {
    private val catalog = CatalogLoader.loadCatalog()
    private fun game() = GameEngine.createGame(42, MonsterId.VOLTRAX, catalog).copy(activePlayerIndex = 0)
    private fun dice(vararg faces: DieFace) = faces.mapIndexed { id, face -> Die(id, face) }
    private fun finish(state: GameState) = GameEngine.reduce(state, GameAction.StopRolling)
    private fun player(state: GameState, id: Int = 0, change: (PlayerState) -> PlayerState) =
        state.copy(players = state.players.map { if (it.id == id) change(it) else it })

    @Test fun RULE_01_02_03_setup() {
        val s = game()
        assertEquals(4, s.players.size)
        assertEquals(4, s.players.map { it.monster }.distinct().size)
        s.players.forEach { assertEquals(10, it.hp); assertEquals(0, it.vp); assertEquals(0, it.malice); assertEquals(0, it.energy) }
        assertNull(s.tokyoOccupantId)
        assertEquals(3, s.shop.size)
        assertEquals(17, s.deck.size)
        assertEquals(mapOf(3 to 4, 6 to 4, 10 to 2), s.availableTiles.groupingBy { it.niveau }.eachCount())
    }

    @Test fun RULE_05_starting_player_is_seeded_and_varies() {
        val starters = (0L..100L).map { GameEngine.createGame(it, MonsterId.VOLTRAX, catalog).activePlayer.id }.toSet()
        assertEquals(setOf(0, 1, 2, 3), starters)
        assertEquals(game(), game())
    }

    @Test fun RULE_06_three_rolls_and_automatic_resolution() {
        val initial = game()
        assertEquals(1, initial.rollsMade)
        val second = GameEngine.reduce(initial, GameAction.Reroll)
        assertEquals(2, second.rollsMade)
        assertEquals(initial.rng.cursor + 6, second.rng.cursor)
        val third = GameEngine.reduce(second, GameAction.Reroll)
        assertEquals(3, third.rollsMade)
        assertEquals(0, third.rollsLeft)
        assertNotEquals(TurnPhase.LANCER, third.phase)
        assertFalse(GameAction.Reroll in GameEngine.legalActions(third))
        assertThrows(IllegalActionException::class.java) { GameEngine.reduce(third, GameAction.Reroll) }
    }

    @Test fun RULE_08_keep_any_face_unlock_and_stop_with_all_kept() {
        var s = game().copy(dice = DieFace.entries.mapIndexed { id, face -> Die(id, face) })
        for (id in 0..5) s = GameEngine.reduce(s, GameAction.ToggleKeep(id))
        assertFalse(GameAction.Reroll in GameEngine.legalActions(s))
        assertTrue(GameAction.StopRolling in GameEngine.legalActions(s))
        val resolved = finish(s)
        assertEquals(s.dice, resolved.dice)
        s = GameEngine.reduce(s, GameAction.ToggleKeep(2))
        val rolled = GameEngine.reduce(s, GameAction.Reroll)
        assertEquals(s.rng.cursor + 1, rolled.rng.cursor)
        s.dice.filter { it.id != 2 }.forEach { assertEquals(it, rolled.dice[it.id]) }
    }

    @TestFactory fun RULE_10_11_12_13_number_combinations() = listOf(DieFace.ONE, DieFace.TWO, DieFace.THREE).flatMapIndexed { index, face ->
        (1..6).map { count -> dynamicTest("$face x$count") {
            val s = game().copy(tokyoOccupantId = 1, dice = dice(*(List(count) { face } + List(6 - count) { DieFace.HEART }).toTypedArray()))
            val result = finish(s)
            assertEquals(if (count < 3) 0 else index + 1 + count - 3, result.activePlayer.vp)
            assertEquals(result.activePlayer.vp, result.lastResolutionGains.getValue(0).numbersVp)
            assertEquals(if (count < 3 || face == DieFace.THREE) 0 else if (face == DieFace.ONE) 2 else 1, result.activePlayer.malice)
        } }
    }

    @Test fun RULE_14_15_malice_interrupt_resumes_once() {
        var s = player(game()) { it.copy(malice = 2) }.copy(dice = dice(DieFace.ONE, DieFace.ONE, DieFace.ONE, DieFace.ENERGY, DieFace.HEART, DieFace.CLAW))
        s = finish(s)
        assertEquals(listOf(3), s.pendingMaliceLevels)
        assertTrue(GameEngine.legalActions(s).all { it is GameAction.PickMaliceTile })
        val action = GameEngine.legalActions(s).first()
        s = GameEngine.reduce(s, action)
        assertEquals(TurnPhase.ACHETER, s.phase)
        assertEquals(1, s.activePlayer.energy)
        assertEquals(2, s.activePlayer.vp) // triple + entry
        assertEquals(setOf(3), s.activePlayer.unlockedMaliceLevels)
        assertThrows(IllegalActionException::class.java) { GameEngine.reduce(s, action) }
    }

    @Test fun RULE_16_energy_only_final_dice_and_no_double_resolution() {
        val s = finish(game().copy(dice = List(6) { Die(it, DieFace.ENERGY) }))
        assertEquals(6, s.activePlayer.energy)
        assertThrows(IllegalActionException::class.java) { GameEngine.reduce(s, GameAction.ResolveSymbols(SymbolResolutionType.ENERGY)) }
        assertEquals(s, GameEngine.advanceAutomatically(s))
    }

    @Test fun RULE_17_tokyo_attacks_all_other_alive_monsters() {
        val s = finish(game().copy(tokyoOccupantId = 0, dice = List(6) { Die(it, DieFace.CLAW) }))
        assertEquals(listOf(10, 4, 4, 4), s.players.map { it.hp })
        assertNull(s.pendingFlee)
    }

    @Test fun RULE_18_19_25_flee_or_stay_resumes_and_enters_without_extra_damage() {
        for (flee in listOf(true, false)) {
            val s = finish(game().copy(tokyoOccupantId = 1, dice = dice(DieFace.CLAW, DieFace.CLAW, DieFace.ENERGY, DieFace.HEART, DieFace.ONE, DieFace.TWO)))
            assertEquals(TurnPhase.DECISION_FUITE, s.phase)
            assertEquals(listOf(10, 8, 10, 10), s.players.map { it.hp })
            assertEquals(1, s.decisionPlayer.id)
            val after = GameEngine.reduce(s, if (flee) GameAction.Flee else GameAction.Stay)
            assertEquals(TurnPhase.ACHETER, after.phase)
            assertEquals(if (flee) 0 else 1, after.tokyoOccupantId)
            assertEquals(8, after.players[1].hp)
            assertEquals(1, after.activePlayer.energy)
        }
    }

    @Test fun RULE_20_card_damage_does_not_open_flee() {
        val base = player(game()) { it.copy(energy = 10) }
        val card = catalog.cards.first { it.id == "bombe_iem" }
        val s = GameEngine.reduce(base.copy(phase = TurnPhase.ACHETER, shop = listOf(card), tokyoOccupantId = 1), GameAction.BuyCard(card.id))
        assertNull(s.pendingFlee)
        assertEquals(TurnPhase.ACHETER, s.phase)
        assertEquals(8, s.players[1].hp)
    }

    @Test fun RULE_21_25_empty_tokyo_entry_even_without_claws() {
        val s = finish(game().copy(dice = List(6) { Die(it, DieFace.HEART) }))
        assertEquals(0, s.tokyoOccupantId)
        assertEquals(1, s.activePlayer.vp)
        assertEquals(TurnPhase.ACHETER, s.phase)
    }

    @Test fun RULE_22_23_38_hearts_outside_cap_and_no_healing_inside() {
        for (inTokyo in listOf(false, true)) {
            val s = player(game()) { it.copy(hp = 8) }.copy(tokyoOccupantId = if (inTokyo) 0 else 1, dice = List(6) { Die(it, DieFace.HEART) })
            val result = finish(s)
            assertEquals(if (inTokyo) 8 else 10, result.activePlayer.hp)
            if (!inTokyo) assertTrue(result.actionLog.any { "récupère +2" in it })
        }
    }

    @Test fun RULE_24_occupant_dies_no_flee_attacker_enters_resources_discarded() {
        val s = player(game(), 1) { it.copy(hp = 1, energy = 5, cards = listOf(catalog.cards.first { c -> c.id == "coeur_nucleaire" }), tiles = listOf(catalog.tiles.first())) }
        val after = finish(s.copy(tokyoOccupantId = 1, dice = List(6) { Die(it, DieFace.CLAW) }))
        val dead = after.players[1]
        assertFalse(dead.isAlive); assertEquals(0, dead.energy); assertTrue(dead.cards.isEmpty()); assertTrue(dead.tiles.isEmpty())
        assertNull(after.pendingFlee); assertEquals(0, after.tokyoOccupantId)
        assertTrue(after.discard.any { it.id == "coeur_nucleaire" })
    }

    @Test fun RULE_29_tokyo_bonus_once_at_turn_start() {
        var s = game().copy(phase = TurnPhase.ACHETER, activePlayerIndex = 3, tokyoOccupantId = 0)
        s = GameEngine.reduce(s, GameAction.EndTurn)
        assertEquals(2, s.players[0].vp)
        s = GameEngine.reduce(s, GameAction.Reroll)
        assertEquals(2, s.players[0].vp)
    }

    @Test fun RULE_31_32_33_shop_cost_replacement_and_action_discard() {
        val card = catalog.cards.first { it.id == "extraction_miniere" }
        val base = player(game()) { it.copy(energy = 8) }.copy(phase = TurnPhase.ACHETER, shop = listOf(card) + catalog.cards.filter { it.id != card.id }.take(2))
        val s = GameEngine.reduce(base, GameAction.BuyCard(card.id))
        assertEquals(7, s.activePlayer.energy)
        assertEquals(3, s.shop.size)
        assertEquals(base.deck.size - 1, s.deck.size)
        assertTrue(s.discard.any { it.id == card.id })
        val swept = GameEngine.reduce(s, GameAction.SweepShop)
        assertEquals(5, swept.activePlayer.energy)
        assertEquals(3, swept.shop.size)
        assertFalse(s.shop.any { old -> swept.shop.any { it.id == old.id } })
    }

    @Test fun RULE_34_35_36_end_turn_effects_precede_victory_and_dead_cannot_win() {
        val balise = catalog.cards.first { it.id == "balise_neon" }
        val s = player(game()) { it.copy(vp = 19, cards = listOf(balise)) }.copy(phase = TurnPhase.ACHETER)
        val result = GameEngine.reduce(s, GameAction.EndTurn)
        assertEquals(TurnPhase.FIN_PARTIE, result.phase); assertEquals(0, result.winnerId)
        val deadWinner = player(s) { it.copy(vp = 20, hp = 0, isAlive = false) }
        assertNotEquals(TurnPhase.FIN_PARTIE, GameEngine.reduce(deadWinner, GameAction.EndTurn).phase)
    }

    @Test fun RULE_35_37_last_survivor_and_mutual_elimination() {
        for (survivor in listOf(true, false)) {
            var s = game()
            for (id in 1..3) s = player(s, id) { it.copy(isAlive = false, hp = 0) }
            if (!survivor) s = player(s) { it.copy(isAlive = false, hp = 0) }
            val result = GameEngine.reduce(s.copy(phase = TurnPhase.ACHETER), GameAction.EndTurn)
            assertEquals(TurnPhase.FIN_PARTIE, result.phase)
            assertEquals(if (survivor) 0 else null, result.winnerId)
            assertTrue(GameEngine.legalActions(result).isEmpty())
        }
    }

    @Test fun RULE_09_all_orders_are_legal_and_automatically_executed() {
        for (order in GameEngine.resolutionOrders) {
            var s = game().copy(dice = List(6) { Die(it, DieFace.ENERGY) })
            s = GameEngine.reduce(s, GameAction.SetResolutionOrder(order))
            assertEquals(order, s.resolutionOrder)
            assertEquals(TurnPhase.ACHETER, finish(s).phase)
        }
        assertEquals(24, GameEngine.resolutionOrders.distinct().size)
        assertThrows(IllegalActionException::class.java) { GameEngine.reduce(game(), GameAction.SetResolutionOrder(emptyList())) }
    }

    @Test fun save_resume_during_flee_and_malice_is_identical() {
        val flee = finish(game().copy(tokyoOccupantId = 1, dice = List(6) { Die(it, DieFace.CLAW) }))
        val malice = finish(player(game()) { it.copy(malice = 2) }.copy(dice = List(6) { Die(it, DieFace.ONE) }))
        for (s in listOf(flee, malice)) {
            val loaded = Json.decodeFromString<GameState>(Json.encodeToString(s))
            assertEquals(s, GameEngine.advanceAutomatically(loaded))
            val action = GameEngine.legalActions(s).first()
            assertEquals(GameEngine.reduce(s, action), GameEngine.reduce(loaded, action))
        }
    }

    @Test fun plasma_only_damages_monsters_hit_by_claws() {
        val card = catalog.cards.first { it.id == "griffes_plasma" }
        val s = finish(player(game()) { it.copy(cards = listOf(card)) }.copy(tokyoOccupantId = 1, dice = List(6) { Die(it, DieFace.CLAW) }))
        assertEquals(listOf(10, 3, 10, 10), s.players.map { it.hp })
    }

    @Test fun implant_changes_face_before_scoring_and_recalibrage_never_adds_rolls() {
        val implant = catalog.cards.first { it.id == "implant_cybernetique" }
        val s = player(game()) { it.copy(cards = listOf(implant)) }.copy(tokyoOccupantId = 1,
            dice = dice(DieFace.ONE, DieFace.ONE, DieFace.ONE, DieFace.ENERGY, DieFace.ENERGY, DieFace.ENERGY))
        val after = finish(s)
        assertEquals(0, after.activePlayer.vp); assertEquals(0, after.activePlayer.malice)
        assertEquals(9, after.players[1].hp)
        val recalibrage = catalog.cards.first { it.id == "recalibrage_optique" }
        val triggered = EffectInterpreter.applyTrigger(player(game()) { it.copy(cards = listOf(recalibrage)) }, EffectTrigger.AVANT_LANCER, 0)
        assertEquals(2, triggered.rollsLeft); assertEquals(3, triggered.maxRolls)
    }

    @Test
    fun `RULE_24_elimination_discards_cards_and_frees_tokyo`() {
        var state = GameEngine.createGame(42L, MonsterId.VOLTRAX, catalog)
        
        // Put player 1 (bot) in Tokyo
        state = state.copy(tokyoOccupantId = 1)
        
        // Give player 1 some cards and energy
        val card = catalog.cards.first { it.id == "coeur_nucleaire" }
        state = state.copy(players = state.players.map { 
            if (it.id == 1) it.copy(hp = 2, energy = 5, cards = listOf(card)) else it
        })
        
        // Player 0 rolls 2 claws
        state = state.copy(dice = listOf(Die(0, DieFace.CLAW), Die(1, DieFace.CLAW), Die(2, DieFace.ONE), Die(3, DieFace.ONE), Die(4, DieFace.ENERGY), Die(5, DieFace.ENERGY)))
        // La résolution des dés est automatique.
        state = GameEngine.reduce(state, GameAction.StopRolling)
        
        // Player 1 should be eliminated, Tokyo free, cards discarded
        val p1 = state.players[1]
        assertFalse(p1.isAlive)
        assertEquals(0, p1.hp)
        assertEquals(0, p1.energy)
        assertEquals(0, p1.cards.size)
        // La Tour libérée revient aussitôt à l'attaquant.
        assertEquals(0, state.tokyoOccupantId)
        
        // Discard pile should contain the card
        assertTrue(state.discard.contains(card))
        
        // Action log should contain the elimination message
        assertTrue(state.actionLog.any { it.contains("élimine") })
    }
}
