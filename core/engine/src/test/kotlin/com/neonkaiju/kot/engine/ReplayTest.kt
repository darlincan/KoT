package com.neonkaiju.kot.engine

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.model.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class ReplayTest {

    private val catalog = CatalogLoader.loadCatalog()

    /** Joue une partie en choisissant au hasard (graine fixe) parmi les actions légales. */
    private fun playRandomGame(seed: Long, maxActions: Int = 3000): GameState {
        val chooser = Random(seed)
        var state = GameEngine.createGame(seed, MonsterId.HYDROKOR, catalog)
        repeat(maxActions) {
            if (state.phase == TurnPhase.FIN_PARTIE) return state
            val actions = GameEngine.legalActions(state)
            state = GameEngine.reduce(state, actions[chooser.nextInt(actions.size)])
        }
        return state
    }

    @Test
    fun `REPLAY_01_reduce_records_every_action_in_history`() {
        var state = GameEngine.createGame(42L, MonsterId.VOLTRAX, catalog)
        assertTrue(state.history.isEmpty())

        state = GameEngine.reduce(state, GameAction.ToggleKeep(0))
        state = GameEngine.reduce(state, GameAction.Reroll)

        assertEquals(listOf(GameAction.ToggleKeep(0), GameAction.Reroll), state.history)
    }

    @Test
    fun `REPLAY_02_journal_carries_seed_human_monster_and_actions`() {
        val state = playRandomGame(seed = 7L)
        val journal = GameEngine.journalOf(state)

        assertEquals(7L, journal.seed)
        assertEquals(MonsterId.HYDROKOR, journal.humanMonster)
        assertEquals(state.history, journal.actions)
    }

    @Test
    fun `REPLAY_03_replay_from_seed_rebuilds_the_exact_same_game`() {
        listOf(1L, 42L, 2026L, -9L).forEach { seed ->
            val played = playRandomGame(seed)
            val replayed = GameEngine.replay(GameEngine.journalOf(played), catalog)
            assertEquals(played, replayed, "Graine $seed : le rejeu diverge")
        }
    }

    @Test
    fun `REPLAY_04_journal_survives_json_round_trip`() {
        val played = playRandomGame(seed = 123L)
        val json = Json.encodeToString(GameJournal.serializer(), GameEngine.journalOf(played))
        val decoded = Json.decodeFromString(GameJournal.serializer(), json)

        assertEquals(played, GameEngine.replay(decoded, catalog))
    }

    @Test
    fun `REPLAY_05_replay_rejects_an_illegal_journal`() {
        val journal = GameJournal(seed = 42L, humanMonster = MonsterId.VOLTRAX, actions = listOf(GameAction.Flee))
        assertThrows<IllegalActionException> { GameEngine.replay(journal, catalog) }
    }
}
