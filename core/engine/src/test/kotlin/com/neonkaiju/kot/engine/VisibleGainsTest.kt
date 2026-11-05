package com.neonkaiju.kot.engine

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VisibleGainsTest {
    private val catalog = CatalogLoader.loadCatalog()
    private fun game() = GameEngine.createGame(42, MonsterId.VOLTRAX, catalog).copy(activePlayerIndex = 0)
    private fun dice(vararg faces: DieFace) = faces.mapIndexed { id, face -> Die(id, face) }

    @Test fun final_gains_match_actual_resources_and_remain_visible_during_bot_turns() {
        val initial = game().copy(players = game().players.map { if (it.id == 0) it.copy(hp = 8) else it },
            dice = dice(DieFace.THREE, DieFace.THREE, DieFace.THREE, DieFace.ENERGY, DieFace.ENERGY, DieFace.HEART))
        val resolved = GameEngine.reduce(initial, GameAction.StopRolling)
        val gains = resolved.lastResolutionGains.getValue(0)
        assertEquals(ResolutionGains(vp = 4, energy = 2, healing = 1, isComplete = true, numbersVp = 3, tokyoVp = 1), gains)
        assertEquals(resolved.players[0].vp - initial.players[0].vp, gains.vp)
        assertEquals(resolved.players[0].energy - initial.players[0].energy, gains.energy)
        var botTurn = GameEngine.reduce(resolved, GameAction.EndTurn)
        botTurn = GameEngine.reduce(botTurn, GameAction.Reroll)
        assertEquals(gains, botTurn.lastResolutionGains[0])
        assertEquals(botTurn, Json.decodeFromString<GameState>(Json.encodeToString(botTurn)))
    }

    @Test fun gains_accumulate_once_across_malice_decision_and_entry_into_tokyo() {
        val initial = game().copy(players = game().players.map { if (it.id == 0) it.copy(malice = 2) else it },
            dice = dice(DieFace.ONE, DieFace.ONE, DieFace.ONE, DieFace.ENERGY, DieFace.ENERGY, DieFace.ENERGY))
        val pending = GameEngine.reduce(initial, GameAction.StopRolling)
        assertEquals(ResolutionGains(vp = 1, malice = 2, numbersVp = 1), pending.lastResolutionGains[0])
        val choice = GameEngine.legalActions(pending).filterIsInstance<GameAction.PickMaliceTile>().first()
        val resolved = GameEngine.reduce(pending, choice)
        assertEquals(ResolutionGains(vp = 2, energy = 3, malice = 2, isComplete = true, numbersVp = 1, tokyoVp = 1), resolved.lastResolutionGains[0])
        assertEquals(resolved, GameEngine.advanceAutomatically(resolved))
    }

    @Test fun flee_decision_preserves_energy_and_does_not_count_it_again() {
        val initial = game().copy(tokyoOccupantId = 1,
            dice = dice(DieFace.ENERGY, DieFace.CLAW, DieFace.HEART, DieFace.ONE, DieFace.TWO, DieFace.THREE))
        val pending = GameEngine.reduce(initial, GameAction.StopRolling)
        assertEquals(ResolutionGains(energy = 1), pending.lastResolutionGains[0])
        val resolved = GameEngine.reduce(pending, GameAction.Flee)
        assertEquals(ResolutionGains(vp = 1, energy = 1, isComplete = true, tokyoVp = 1), resolved.lastResolutionGains[0])
    }

    @Test fun visible_gains_use_actual_capped_values_and_show_zero_when_nothing_is_gained() {
        val initial = game().copy(tokyoOccupantId = 1, players = game().players.map { if (it.id == 0) it.copy(vp = 19, hp = 9) else it },
            dice = dice(DieFace.THREE, DieFace.THREE, DieFace.THREE, DieFace.HEART, DieFace.HEART, DieFace.TWO))
        val resolved = GameEngine.reduce(initial, GameAction.StopRolling)
        assertEquals(ResolutionGains(vp = 1, healing = 1, isComplete = true, numbersVp = 1), resolved.lastResolutionGains[0])
        assertTrue(resolved.actionLog.any { "gagne +1 ★" in it })
        val nothing = GameEngine.reduce(game().copy(tokyoOccupantId = 1, dice = List(6) { Die(it, DieFace.HEART) }), GameAction.StopRolling)
        assertEquals(ResolutionGains(isComplete = true), nothing.lastResolutionGains[0])
    }

    @Test fun start_of_turn_tokyo_bonus_has_its_own_visible_gain_without_changing_the_rules() {
        val initial = game().copy(phase = TurnPhase.ACHETER, activePlayerIndex = 3, tokyoOccupantId = 0)
        val next = GameEngine.reduce(initial, GameAction.EndTurn)
        assertEquals(2, next.players[0].vp)
        assertEquals(ResolutionGains(vp = 2, isComplete = true, isStartOfTurn = true, tokyoVp = 2), next.lastResolutionGains[0])
    }
}
