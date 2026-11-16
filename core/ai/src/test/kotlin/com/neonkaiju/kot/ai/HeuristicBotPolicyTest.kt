package com.neonkaiju.kot.ai

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HeuristicBotPolicyTest {

    private val catalog = CatalogLoader.loadCatalog()
    private val game = GameEngine.createGame(seed = 7L, humanMonster = MonsterId.VOLTRAX, catalog = catalog)
    private val policy = HeuristicBotPolicy()

    private fun newGame(seed: Long) = GameEngine.createGame(seed, MonsterId.VOLTRAX, catalog)

    private fun playUntilEnd(start: GameState, policyOf: (GameState) -> BotPolicy): GameState {
        var state = start
        var steps = 0
        while (state.phase != TurnPhase.FIN_PARTIE) {
            assertTrue(++steps < 20_000, "la partie doit se terminer")
            val action = policyOf(state).chooseAction(state)
            assertTrue(action in GameEngine.legalActions(state), "action illégale : $action")
            state = GameEngine.reduce(state, action)
        }
        return state
    }

    private fun deciderId(state: GameState) = state.decisionPlayer.id

    /** Rejoue les décisions de garde jusqu'à la relance et rend les faces conservées. */
    private fun keptFaces(faces: List<DieFace>, inTower: Boolean = false, hp: Int = 10): List<DieFace> {
        var state = game.copy(
            tokyoOccupantId = if (inTower) 0 else 1,
            dice = faces.mapIndexed { id, face -> Die(id, face) },
            players = game.players.map { if (it.id == 0) it.copy(hp = hp) else it },
        )
        while (true) {
            when (val action = policy.chooseAction(state)) {
                is GameAction.ToggleKeep -> state = GameEngine.reduce(state, action)
                else -> return state.dice.filter { it.isKept }.map { it.face }
            }
        }
    }

    @Test
    fun `keeps a triple of numbers`() {
        val kept = keptFaces(listOf(DieFace.TWO, DieFace.TWO, DieFace.TWO, DieFace.ONE, DieFace.THREE, DieFace.HEART))

        assertEquals(listOf(DieFace.TWO, DieFace.TWO, DieFace.TWO), kept)
    }

    @Test
    fun `keeps only the strongest pair`() {
        val kept = keptFaces(listOf(DieFace.ONE, DieFace.ONE, DieFace.THREE, DieFace.THREE, DieFace.TWO, DieFace.HEART))

        assertEquals(listOf(DieFace.THREE, DieFace.THREE), kept)
    }

    @Test
    fun `keeps claws outside the Tower`() {
        val kept = keptFaces(listOf(DieFace.CLAW, DieFace.CLAW, DieFace.ONE, DieFace.TWO, DieFace.THREE, DieFace.HEART), hp = 2)

        assertEquals(listOf(DieFace.CLAW, DieFace.CLAW, DieFace.HEART), kept)
    }

    @Test
    fun `keeps hearts only when low and outside the Tower`() {
        val faces = List(6) { DieFace.HEART }

        assertEquals(faces, keptFaces(faces, hp = 3))
        assertTrue(keptFaces(faces, hp = 9).isEmpty())
        assertTrue(keptFaces(faces, inTower = true, hp = 3).isEmpty())
    }

    @Test
    fun `buys a useful card rather than ending the turn`() {
        val card = catalog.cards.first { it.id == "extraction_miniere" }
        val shopping = game.copy(
            phase = TurnPhase.ACHETER,
            shop = listOf(card),
            players = game.players.map { if (it.id == 0) it.copy(energy = 2) else it },
        )

        assertEquals(GameAction.BuyCard(card.id), policy.chooseAction(shopping))
    }

    @Test
    fun `does not waste cells on healing at full hearts`() {
        val card = catalog.cards.first { it.id == "nanobots_reparateurs" }
        val shopping = game.copy(
            phase = TurnPhase.ACHETER,
            shop = listOf(card),
            players = game.players.map { if (it.id == 0) it.copy(energy = 3) else it },
        )

        assertEquals(GameAction.EndTurn, policy.chooseAction(shopping))
    }

    @Test
    fun `picks the best tile rather than the first one`() {
        val pending = game.copy(pendingMaliceLevels = listOf(6))

        assertEquals(GameAction.PickMaliceTile("tile_6_arc_electrique"), policy.chooseAction(pending))
        val withoutArc = pending.copy(availableTiles = pending.availableTiles.filter { it.id != "tile_6_arc_electrique" })
        assertEquals(GameAction.PickMaliceTile("tile_6_predateur_neon"), policy.chooseAction(withoutArc))
    }

    @Test
    fun `the same state always gives the same action`() {
        val shopping = newGame(11L).copy(phase = TurnPhase.ACHETER, players = newGame(11L).players.map { it.copy(energy = 8) })

        assertEquals(policy.chooseAction(shopping), policy.chooseAction(shopping))
    }

    @Test
    fun `decisions take a few milliseconds and games always end with legal actions`() {
        repeat(50) { seed ->
            var slowest = 0L
            var state = newGame(seed.toLong())
            while (state.phase != TurnPhase.FIN_PARTIE) {
                val start = System.nanoTime()
                val action = policy.chooseAction(state)
                slowest = maxOf(slowest, System.nanoTime() - start)
                assertTrue(action in GameEngine.legalActions(state), "action illégale : $action")
                state = GameEngine.reduce(state, action)
            }
            assertTrue(slowest < 200_000_000L, "décision trop lente : ${slowest / 1_000_000} ms")
        }
    }

    @Test
    fun `wins clearly more than its share against a naive player`() {
        val naive = object : BotPolicy {
            override fun chooseAction(state: GameState) = GameEngine.legalActions(state).let { legal ->
                legal.firstOrNull { it is GameAction.StopRolling || it is GameAction.EndTurn } ?: legal.first()
            }
        }
        val games = 200
        val wins = (0 until games).count { seed ->
            val heuristicSeat = seed % 4
            val end = playUntilEnd(newGame(seed.toLong())) { state ->
                if (deciderId(state) == heuristicSeat) policy else naive
            }
            end.winnerId == heuristicSeat
        }

        assertTrue(wins > games / 2, "victoires : $wins / $games")
    }
}
