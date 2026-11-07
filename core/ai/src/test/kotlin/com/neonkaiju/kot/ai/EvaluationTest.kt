package com.neonkaiju.kot.ai

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.MonsterId
import com.neonkaiju.kot.model.PlayerState
import com.neonkaiju.kot.model.TurnPhase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EvaluationTest {

    private val catalog = CatalogLoader.loadCatalog()
    private val game = GameEngine.createGame(seed = 7L, humanMonster = MonsterId.VOLTRAX, catalog = catalog)

    private fun GameState.withPlayer0(block: (PlayerState) -> PlayerState) =
        copy(players = players.map { if (it.id == 0) block(it) else it })

    @Test
    fun `more stars give a better score`() {
        val low = game.withPlayer0 { it.copy(vp = 5) }
        val high = game.withPlayer0 { it.copy(vp = 6) }

        assertTrue(evaluate(high, 0) > evaluate(low, 0))
    }

    @Test
    fun `a star is worth more close to victory`() {
        fun gainOfOneStar(from: Int) =
            evaluate(game.withPlayer0 { it.copy(vp = from + 1) }, 0) - evaluate(game.withPlayer0 { it.copy(vp = from) }, 0)

        assertTrue(gainOfOneStar(18) > gainOfOneStar(3))
    }

    @Test
    fun `losing a heart hurts more under 4 hearts`() {
        fun lossOfOneHeart(from: Int) =
            evaluate(game.withPlayer0 { it.copy(hp = from) }, 0) - evaluate(game.withPlayer0 { it.copy(hp = from - 1) }, 0)

        assertTrue(lossOfOneHeart(3) > lossOfOneHeart(9))
    }

    @Test
    fun `one heart in the Tower is worse than one heart outside`() {
        val outside = game.withPlayer0 { it.copy(hp = 1) }
        val inTower = outside.copy(tokyoOccupantId = 0)

        assertTrue(evaluate(inTower, 0) < evaluate(outside, 0))
    }

    @Test
    fun `the Tower is worth holding with full hearts`() {
        assertTrue(evaluate(game.copy(tokyoOccupantId = 0), 0) > evaluate(game, 0))
    }

    @Test
    fun `zero hearts is the minimal score`() {
        assertEquals(MIN_EVALUATION, evaluate(game.withPlayer0 { it.copy(hp = 0) }, 0))
        assertEquals(MIN_EVALUATION, evaluate(game.withPlayer0 { it.copy(hp = 0, isAlive = false, vp = 19) }, 0))
    }

    @Test
    fun `end of game is decisive`() {
        val over = game.copy(phase = TurnPhase.FIN_PARTIE, winnerId = 0)

        assertEquals(MAX_EVALUATION, evaluate(over, 0))
        assertEquals(MIN_EVALUATION, evaluate(over, 1))
    }

    @Test
    fun `tiles and cells improve the score`() {
        val tile = catalog.tiles.first()

        assertTrue(evaluate(game.withPlayer0 { it.copy(tiles = listOf(tile)) }, 0) > evaluate(game, 0))
        assertTrue(evaluate(game.withPlayer0 { it.copy(energy = 3) }, 0) > evaluate(game, 0))
    }

    @Test
    fun `an opponent close to 20 stars is a threat`() {
        fun withOpponentStars(vp: Int) = game.copy(players = game.players.map { if (it.id == 1) it.copy(vp = vp) else it })

        assertTrue(evaluate(withOpponentStars(19), 0) < evaluate(withOpponentStars(5), 0))
    }

    @Test
    fun `the score is pure`() {
        assertEquals(evaluate(game, 0), evaluate(game, 0))
    }
}
