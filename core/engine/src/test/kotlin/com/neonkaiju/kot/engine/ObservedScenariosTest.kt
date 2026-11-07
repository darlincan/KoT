package com.neonkaiju.kot.engine

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class ObservedScenariosTest {
    private val catalog = CatalogLoader.loadCatalog()
    private fun game() = GameEngine.createGame(42, MonsterId.VOLTRAX, catalog).copy(activePlayerIndex = 0)

    @Test fun three_claws_remove_three_hp_once_and_staying_in_tokyo_does_not_restore_hp() {
        val initial = game().copy(tokyoOccupantId = 1,
            dice = listOf(DieFace.CLAW, DieFace.CLAW, DieFace.CLAW, DieFace.ONE, DieFace.TWO, DieFace.THREE).mapIndexed { id, face -> Die(id, face) })
        val hit = GameEngine.reduce(initial, GameAction.StopRolling)
        assertEquals(7, hit.players[1].hp)
        assertEquals(3, hit.pendingFlee?.damageTaken)
        assertTrue(hit.actionLog.any { "10 → 7 ♥" in it })
        val stay = GameEngine.reduce(hit, GameAction.Stay)
        assertEquals(7, stay.players[1].hp); assertEquals(1, stay.tokyoOccupantId)
        assertEquals(listOf(10, 7, 10, 10), stay.players.map { it.hp })
    }

    @Test fun claws_in_empty_tokyo_damage_nobody_then_entry_gives_one_separate_tokyo_point() {
        val after = GameEngine.reduce(game().copy(dice = List(6) { Die(it, DieFace.CLAW) }), GameAction.StopRolling)
        assertTrue(after.players.all { it.hp == 10 })
        assertNull(after.pendingFlee); assertEquals(0, after.tokyoOccupantId)
        val gains = after.lastResolutionGains.getValue(0)
        assertEquals(1, gains.vp); assertEquals(0, gains.numbersVp); assertEquals(1, gains.tokyoVp)
    }

    @Test fun a_protected_single_claw_can_legally_deal_no_damage() {
        val base = game()
        val protected = base.copy(tokyoOccupantId = 1, players = base.players.map {
            if (it.id == 1) it.copy(cards = listOf(catalog.cards.first { c -> c.id == "blindage_reactif" })) else it
        }, dice = listOf(DieFace.CLAW, DieFace.ONE, DieFace.TWO, DieFace.THREE, DieFace.HEART, DieFace.ENERGY).mapIndexed { id, face -> Die(id, face) })
        val after = GameEngine.reduce(protected, GameAction.StopRolling)
        assertEquals(10, after.players[1].hp); assertNull(after.pendingFlee)
        assertEquals(1, after.players[1].damagePreventedThisTurn)
        assertTrue(after.actionLog.any { "annulé" in it })
    }

    @TestFactory fun malice_crosses_each_configured_threshold_once() = listOf(3, 6, 10).map { level ->
        dynamicTest("palier_$level") {
            val base = game()
            val initial = base.copy(players = base.players.map { if (it.id == 0) it.copy(malice = level - 1) else it },
                dice = listOf(DieFace.TWO, DieFace.TWO, DieFace.TWO, DieFace.ENERGY, DieFace.ENERGY, DieFace.ENERGY).mapIndexed { id, face -> Die(id, face) })
            val pending = GameEngine.reduce(initial, GameAction.StopRolling)
            assertEquals(listOf(level), pending.pendingMaliceLevels)
            val choose = GameEngine.legalActions(pending).first()
            val after = GameEngine.reduce(pending, choose)
            assertEquals(level, after.activePlayer.malice)
            assertEquals(1, after.activePlayer.tiles.count { it.niveau == level })
            assertEquals(1, after.lastResolutionGains.getValue(0).malice)
            assertThrows(IllegalActionException::class.java) { GameEngine.reduce(after, choose) }
        }
    }

    @Test fun malice_gain_display_is_capped_at_ten_with_extra_ones_not_extra_malice() {
        val base = game()
        val after = GameEngine.reduce(base.copy(players = base.players.map { if (it.id == 0) it.copy(malice = 9) else it },
            dice = List(6) { Die(it, DieFace.ONE) }), GameAction.StopRolling)
        assertEquals(10, after.activePlayer.malice)
        assertEquals(1, after.lastResolutionGains.getValue(0).malice)
        assertEquals(4, after.lastResolutionGains.getValue(0).numbersVp)
        assertEquals(listOf(10), after.pendingMaliceLevels)
    }
}
